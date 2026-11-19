package com.lycoslane.pureaudiobooks.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder

data class OnlineBookMatch(
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val source: String
)

object OnlineMetadataClient {

    private const val MAX_JSON_BYTES = 2L * 1024 * 1024 // 2 MB
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024 // 8 MB

    suspend fun search(query: String): List<OnlineBookMatch> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val results = mutableListOf<OnlineBookMatch>()
        runCatching { results += searchItunesAudiobooks(clean) }
        runCatching { results += searchOpenLibrary(clean) }
        deduplicateMatches(results)
    }

    internal fun deduplicateMatches(results: List<OnlineBookMatch>): List<OnlineBookMatch> =
        results.distinctBy { "${it.title.lowercase()}|${it.author.lowercase()}|${it.coverUrl}" }

    private fun searchItunesAudiobooks(query: String): List<OnlineBookMatch> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=audiobook&limit=8&term=$encoded"
        val body = fetchString(url) ?: return emptyList()
        return parseItunesResponse(body)
    }

    private fun searchOpenLibrary(query: String): List<OnlineBookMatch> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://openlibrary.org/search.json?limit=8&q=$encoded"
        val body = fetchString(url) ?: return emptyList()
        return parseOpenLibraryResponse(body)
    }

    internal fun parseItunesResponse(jsonText: String): List<OnlineBookMatch> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<OnlineBookMatch>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("collectionName").ifBlank { item.optString("trackName") }.trim()
            val author = item.optString("artistName").trim()
            if (title.isBlank()) continue
            val rawDesc = item.optString("description").trim()
            val cleanDesc = stripSimpleHtml(rawDesc)
            val art100 = item.optString("artworkUrl100").trim()
            val coverUrl = if (art100.isNotBlank()) {
                art100.replace("100x100bb", "600x600bb")
            } else {
                ""
            }
            out += OnlineBookMatch(
                title = title,
                author = author,
                description = cleanDesc,
                coverUrl = coverUrl.takeIf { NetworkSecurity.isValidHttpUrl(it) } ?: "",
                source = "iTunes"
            )
        }
        return out
    }

    internal fun parseOpenLibraryResponse(jsonText: String): List<OnlineBookMatch> {
        val root = JSONObject(jsonText)
        val docs = root.optJSONArray("docs") ?: return emptyList()
        val out = mutableListOf<OnlineBookMatch>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val title = doc.optString("title").trim()
            if (title.isBlank()) continue
            val authors = doc.optJSONArray("author_name")
            val author = if (authors != null && authors.length() > 0) {
                authors.optString(0).trim()
            } else {
                ""
            }
            val coverId = doc.optLong("cover_i", 0L)
            val coverUrl = if (coverId > 0L) {
                "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
            } else {
                ""
            }
            val firstSentence = doc.optJSONArray("first_sentence")?.optString(0)?.trim().orEmpty()
            val year = doc.optInt("first_publish_year", 0)
            val desc = buildString {
                if (year > 0) append("($year) ")
                if (firstSentence.isNotBlank()) append(firstSentence)
            }.trim()

            out += OnlineBookMatch(
                title = title,
                author = author,
                description = desc,
                coverUrl = coverUrl,
                source = "Open Library"
            )
        }
        return out
    }

    suspend fun downloadCoverBytes(coverUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!NetworkSecurity.isValidHttpUrl(coverUrl)) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = NetworkSecurity.openValidatedConnection(
                rawUrl = coverUrl,
                connectTimeoutMs = 8_000,
                readTimeoutMs = 12_000
            )
            if (conn.responseCode !in 200..299) return@withContext null
            BoundedInputStream(conn.inputStream, MAX_IMAGE_BYTES).use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchString(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = NetworkSecurity.openValidatedConnection(
                rawUrl = url,
                connectTimeoutMs = 8_000,
                readTimeoutMs = 12_000
            )
            if (conn.responseCode !in 200..299) return null
            BoundedInputStream(conn.inputStream, MAX_JSON_BYTES).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun stripSimpleHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}

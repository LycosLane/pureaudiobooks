package com.lycoslane.pureaudiobooks.core.local

import org.json.JSONArray
import org.json.JSONObject

data class AlbumTagCandidate(
    val name: String,
    val trackCount: Int
)

data class ConflictSummary(
    val folderName: String,
    val parentFolderName: String,
    val albumCandidates: List<AlbumTagCandidate>,
    val artistCandidates: List<String>,
    val hasMultipleAlbums: Boolean,
    val hasMissingTags: Boolean,
    val folderVsTagMismatch: Boolean
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("folderName", folderName)
        root.put("parentFolderName", parentFolderName)
        root.put("hasMultipleAlbums", hasMultipleAlbums)
        root.put("hasMissingTags", hasMissingTags)
        root.put("folderVsTagMismatch", folderVsTagMismatch)
        val albumsArr = JSONArray()
        for (c in albumCandidates) {
            albumsArr.put(JSONObject().apply {
                put("name", c.name)
                put("trackCount", c.trackCount)
            })
        }
        root.put("albumCandidates", albumsArr)
        val artistsArr = JSONArray()
        for (a in artistCandidates) {
            artistsArr.put(a)
        }
        root.put("artistCandidates", artistsArr)
        return root.toString()
    }

    companion object {
        fun fromJson(raw: String): ConflictSummary? {
            if (raw.isBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val albumsArr = root.optJSONArray("albumCandidates") ?: JSONArray()
                val albums = buildList {
                    for (i in 0 until albumsArr.length()) {
                        val obj = albumsArr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        val count = obj.optInt("trackCount", 0)
                        if (name.isNotBlank()) add(AlbumTagCandidate(name, count))
                    }
                }
                val artistsArr = root.optJSONArray("artistCandidates") ?: JSONArray()
                val artists = buildList {
                    for (i in 0 until artistsArr.length()) {
                        val a = artistsArr.optString(i).trim()
                        if (a.isNotBlank()) add(a)
                    }
                }
                ConflictSummary(
                    folderName = root.optString("folderName"),
                    parentFolderName = root.optString("parentFolderName"),
                    albumCandidates = albums,
                    artistCandidates = artists,
                    hasMultipleAlbums = root.optBoolean("hasMultipleAlbums", false),
                    hasMissingTags = root.optBoolean("hasMissingTags", false),
                    folderVsTagMismatch = root.optBoolean("folderVsTagMismatch", false)
                )
            }.getOrNull()
        }
    }
}

data class TagAnalysisResult(
    val suggestedTitle: String,
    val suggestedAuthor: String,
    val hasConflict: Boolean,
    val summary: ConflictSummary
)

object TagConflictAnalyzer {

    fun analyze(
        folderName: String,
        parentFolderName: String,
        albumTags: List<String>,
        artistTags: List<String>,
        sidecar: SidecarInfo? = null
    ): TagAnalysisResult {
        val cleanFolder = folderName.trim()
        val cleanParent = parentFolderName.trim()

        val albumCounts = albumTags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { AlbumTagCandidate(it.key, it.value) }

        val uniqueArtists = artistTags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // If user already has a sidecar info.txt with a title, respect it and do not flag conflict
        if (sidecar != null && sidecar.title.isNotBlank()) {
            val summary = ConflictSummary(
                folderName = cleanFolder,
                parentFolderName = cleanParent,
                albumCandidates = albumCounts,
                artistCandidates = uniqueArtists,
                hasMultipleAlbums = albumCounts.size > 1,
                hasMissingTags = albumCounts.isEmpty(),
                folderVsTagMismatch = false
            )
            return TagAnalysisResult(
                suggestedTitle = sidecar.title,
                suggestedAuthor = sidecar.author.ifBlank {
                    uniqueArtists.firstOrNull() ?: cleanParent
                },
                hasConflict = false,
                summary = summary
            )
        }

        val hasMultipleAlbums = albumCounts.size > 1
        val hasMissingTags = albumCounts.isEmpty()
        val primaryAlbum = albumCounts.firstOrNull()?.name.orEmpty()
        val folderVsTagMismatch = primaryAlbum.isNotBlank() &&
            cleanFolder.isNotBlank() &&
            !primaryAlbum.equals(cleanFolder, ignoreCase = true)

        // Do not alarm the user with tag conflict warnings on import;
        // keep candidates in ConflictSummary as a helpful quick-pick ("Schnellauswahl").
        val hasConflict = false

        val suggestedTitle = when {
            cleanFolder.isNotBlank() -> cleanFolder
            primaryAlbum.isNotBlank() -> primaryAlbum
            else -> "Unknown Audiobook"
        }

        val suggestedAuthor = when {
            uniqueArtists.isNotEmpty() -> uniqueArtists.first()
            cleanParent.isNotBlank() -> cleanParent
            else -> ""
        }

        return TagAnalysisResult(
            suggestedTitle = suggestedTitle,
            suggestedAuthor = suggestedAuthor,
            hasConflict = hasConflict,
            summary = ConflictSummary(
                folderName = cleanFolder,
                parentFolderName = cleanParent,
                albumCandidates = albumCounts,
                artistCandidates = uniqueArtists,
                hasMultipleAlbums = hasMultipleAlbums,
                hasMissingTags = hasMissingTags,
                folderVsTagMismatch = folderVsTagMismatch
            )
        )
    }
}

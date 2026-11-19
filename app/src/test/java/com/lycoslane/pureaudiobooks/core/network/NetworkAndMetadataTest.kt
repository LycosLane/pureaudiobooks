package com.lycoslane.pureaudiobooks.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkAndMetadataTest {

    @Test
    fun networkSecurityBlocksInternalAndMalformedUrls() {
        assertFalse(NetworkSecurity.isValidHttpUrl("http://localhost:8080/test"))
        assertFalse(NetworkSecurity.isValidHttpUrl("http://127.0.0.1/test"))
        assertFalse(NetworkSecurity.isValidHttpUrl("http://192.168.1.10/test"))
        assertFalse(NetworkSecurity.isValidHttpUrl("http://10.0.0.1/test"))
        assertFalse(NetworkSecurity.isValidHttpUrl("http://169.254.169.254/latest/meta-data"))
        assertFalse(NetworkSecurity.isValidHttpUrl("http://100.64.0.1/cgnat"))
        assertFalse(NetworkSecurity.isValidHttpUrl("https://user:pass@example.com/a"))
        assertFalse(NetworkSecurity.isValidHttpUrl("file:///etc/passwd"))
        assertTrue(NetworkSecurity.isValidHttpUrl("https://itunes.apple.com/search?term=dune"))
    }

    @Test
    fun parsesItunesAndOpenLibraryResponses() {
        val itunesJson = """
            {
              "results": [
                {
                  "collectionName": "Dune (Unabridged)",
                  "artistName": "Frank Herbert",
                  "description": "<p>Desert planet Arrakis.</p>",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()
        val itunes = OnlineMetadataClient.parseItunesResponse(itunesJson)
        assertEquals(1, itunes.size)
        assertEquals("Dune (Unabridged)", itunes[0].title)
        assertEquals("Frank Herbert", itunes[0].author)
        assertEquals("Desert planet Arrakis.", itunes[0].description)
        assertTrue(itunes[0].coverUrl.contains("600x600bb"))

        val olJson = """
            {
              "docs": [
                {
                  "title": "Dune",
                  "author_name": ["Frank Herbert"],
                  "cover_i": 12345,
                  "first_publish_year": 1965
                }
              ]
            }
        """.trimIndent()
        val ol = OnlineMetadataClient.parseOpenLibraryResponse(olJson)
        assertEquals(1, ol.size)
        assertEquals("Dune", ol[0].title)
        assertEquals("https://covers.openlibrary.org/b/id/12345-L.jpg", ol[0].coverUrl)
        assertEquals("(1965)", ol[0].description)
    }

    @Test
    fun deduplicateMatchesPreservesDifferentCoverEditions() {
        val m1 = OnlineBookMatch("The Martian", "Andy Weir", "", "https://example.com/de.jpg", "iTunes")
        val m2 = OnlineBookMatch("the martian", "andy weir", "", "https://example.com/en.jpg", "Open Library")
        val dup = OnlineBookMatch("The Martian", "Andy Weir", "desc", "https://example.com/de.jpg", "Open Library")

        val merged = OnlineMetadataClient.deduplicateMatches(listOf(m1, m2, dup))
        assertEquals(2, merged.size)
        assertEquals("https://example.com/de.jpg", merged[0].coverUrl)
        assertEquals("https://example.com/en.jpg", merged[1].coverUrl)
    }
}

package com.lycoslane.pureaudiobooks.core.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagConflictAnalyzerTest {

    @Test
    fun matchingFolderAndTagProducesNoConflict() {
        val res = TagConflictAnalyzer.analyze(
            folderName = "Dune",
            parentFolderName = "Frank Herbert",
            albumTags = listOf("Dune", "Dune", "Dune"),
            artistTags = listOf("Frank Herbert")
        )
        assertFalse(res.hasConflict)
        assertEquals("Dune", res.suggestedTitle)
        assertEquals("Frank Herbert", res.suggestedAuthor)
    }

    @Test
    fun missingTagsFlagsConflictAndUsesFolderAndParent() {
        val res = TagConflictAnalyzer.analyze(
            folderName = "The Hobbit",
            parentFolderName = "J.R.R. Tolkien",
            albumTags = listOf("", ""),
            artistTags = emptyList()
        )
        assertTrue(res.hasConflict)
        assertTrue(res.summary.hasMissingTags)
        assertEquals("The Hobbit", res.suggestedTitle)
        assertEquals("J.R.R. Tolkien", res.suggestedAuthor)
    }

    @Test
    fun multipleAlbumsInFolderFlagsConflictAndSortsCandidates() {
        val res = TagConflictAnalyzer.analyze(
            folderName = "SciFi Collection",
            parentFolderName = "",
            albumTags = listOf("Book A", "Book B", "Book B", "Book B"),
            artistTags = listOf("Author 1", "Author 2")
        )
        assertTrue(res.hasConflict)
        assertTrue(res.summary.hasMultipleAlbums)
        assertEquals(2, res.summary.albumCandidates.size)
        assertEquals("Book B", res.summary.albumCandidates[0].name)
        assertEquals(3, res.summary.albumCandidates[0].trackCount)

        val roundtrip = ConflictSummary.fromJson(res.summary.toJson())
        assertNotNull(roundtrip)
        assertEquals(res.summary.albumCandidates, roundtrip!!.albumCandidates)
        assertTrue(roundtrip.hasMultipleAlbums)
    }

    @Test
    fun sidecarOverridesConflict() {
        val res = TagConflictAnalyzer.analyze(
            folderName = "001_weird_folder",
            parentFolderName = "",
            albumTags = listOf("Wrong Tag 1", "Wrong Tag 2"),
            artistTags = emptyList(),
            sidecar = SidecarInfo(title = "Clean Title", author = "Clean Author", description = "Desc")
        )
        assertFalse(res.hasConflict)
        assertEquals("Clean Title", res.suggestedTitle)
        assertEquals("Clean Author", res.suggestedAuthor)
    }
}

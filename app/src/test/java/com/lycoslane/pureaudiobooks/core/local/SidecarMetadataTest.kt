package com.lycoslane.pureaudiobooks.core.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SidecarMetadataTest {

    @Test
    fun formatAndParseRoundTrip() {
        val input = SidecarInfo(
            title = "Der Schwarm",
            author = "Frank Schätzing",
            description = "Line 1\nLine 2"
        )
        val formatted = SidecarMetadata.formatInfoText(input)
        val parsed = SidecarMetadata.parseInfoText(formatted)
        assertEquals(input, parsed)
    }

    @Test
    fun parsesGermanKeys() {
        val raw = """
            Titel: Die Känguru-Chroniken
            Autor: Marc-Uwe Kling
            Beschreibung: Ein Kleinkünstler und ein Känguru.
        """.trimIndent()
        val parsed = SidecarMetadata.parseInfoText(raw)
        assertEquals("Die Känguru-Chroniken", parsed.title)
        assertEquals("Marc-Uwe Kling", parsed.author)
        assertEquals("Ein Kleinkünstler und ein Känguru.", parsed.description)
    }
}

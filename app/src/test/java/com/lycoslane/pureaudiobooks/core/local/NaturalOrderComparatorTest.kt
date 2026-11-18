package com.lycoslane.pureaudiobooks.core.local

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun sortsNumbersNaturally() {
        val input = listOf("Track 10.mp3", "Track 2.mp3", "Track 1.mp3", "Track 20.mp3")
        val sorted = input.sortedWith(NaturalOrderComparator)
        assertEquals(
            listOf("Track 1.mp3", "Track 2.mp3", "Track 10.mp3", "Track 20.mp3"),
            sorted
        )
    }

    @Test
    fun sortsDiscSubfoldersNaturally() {
        val input = listOf("CD10/01.mp3", "CD2/02.mp3", "CD2/10.mp3", "CD1/01.mp3")
        val sorted = input.sortedWith(NaturalOrderComparator)
        assertEquals(
            listOf("CD1/01.mp3", "CD2/02.mp3", "CD2/10.mp3", "CD10/01.mp3"),
            sorted
        )
    }
}

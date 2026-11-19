package com.lycoslane.pureaudiobooks.core.audio

/**
 * Mono downmix / channel-fix mode used across Global Settings, Audiobook overrides,
 * and per-Chapter Audio-Fix overrides.
 */
object AudioMonoMode {
    const val INHERIT = -1
    const val OFF = 0
    const val MIX = 1
    const val AUTO = 2
    const val LEFT = 3
    const val RIGHT = 4

    fun normalizeConcrete(mode: Int): Int = if (mode in OFF..RIGHT) mode else OFF

    fun normalizeOverride(mode: Int): Int = if (mode in INHERIT..RIGHT) mode else INHERIT

    fun resolveEffective(
        globalMonoMode: Int,
        bookMonoOverride: Int,
        trackMonoOverride: Int
    ): Int {
        val track = normalizeOverride(trackMonoOverride)
        if (track != INHERIT) return track
        val book = normalizeOverride(bookMonoOverride)
        if (book != INHERIT) return book
        return normalizeConcrete(globalMonoMode)
    }
}

/**
 * Three-state boolean override used by per-audiobook settings that inherit a global default.
 */
object AudioOverride {
    const val INHERIT = -1
    const val OFF = 0
    const val ON = 1

    fun normalize(value: Int): Int = if (value in INHERIT..ON) value else INHERIT

    fun resolve(globalEnabled: Boolean, bookOverride: Int): Boolean = when (normalize(bookOverride)) {
        OFF -> false
        ON -> true
        else -> globalEnabled
    }
}

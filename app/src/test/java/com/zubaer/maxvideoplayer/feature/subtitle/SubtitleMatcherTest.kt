package com.zubaer.maxvideoplayer.feature.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMatcherTest {
    @Test
    fun exactAndLanguageSuffixedSidecarsScoreStrongly() {
        val exact = SubtitleMatcher.score("Movie.Name.2026.mkv", "Movie.Name.2026.srt")
        val english = SubtitleMatcher.score("Movie.Name.2026.mkv", "Movie.Name.2026.en.srt")
        val german = SubtitleMatcher.score("Movie.Name.2026.mkv", "Movie.Name.2026.de.ass")

        assertTrue((exact?.score ?: 0) >= 90)
        assertTrue((english?.score ?: 0) >= 95)
        assertEquals("en", english?.language)
        assertEquals("de", german?.language)
    }

    @Test
    fun genericOrUnrelatedSubtitleDoesNotCrossMatch() {
        assertNull(SubtitleMatcher.score("Interstellar.mkv", "subtitle.srt"))
        assertNull(SubtitleMatcher.score("Interstellar.mkv", "OtherMovie.srt"))
    }

    @Test
    fun languageAliasesNormalizeDeterministically() {
        assertEquals("en", SubtitleMatcher.canonicalLanguage("eng"))
        assertEquals("de", SubtitleMatcher.canonicalLanguage("ger"))
        assertEquals("bn", SubtitleMatcher.canonicalLanguage("ben"))
    }
}

package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRelinkValidatorTest {
    @Test fun acceptsPlausibleReplacementAndPreservesLargeLongValues() {
        val original = media("old", 5_000_000_000L, 7_200_000L, "video/mp4")
        val replacement = media("new", 5_000_500_000L, 7_200_500L, "video/mp4")

        assertTrue(MediaRelinkValidator.validate(original, replacement).accepted)
    }

    @Test fun rejectsClearlyDifferentSize() {
        val original = media("old", 5_000_000_000L, 120_000L, "video/mp4")
        val replacement = media("new", 1_000_000_000L, 120_000L, "video/mp4")

        assertFalse(MediaRelinkValidator.validate(original, replacement).accepted)
    }

    @Test fun rejectsClearlyDifferentDuration() {
        val original = media("old", 100_000_000L, 120_000L, "video/mp4")
        val replacement = media("new", 100_000_000L, 600_000L, "video/mp4")

        assertFalse(MediaRelinkValidator.validate(original, replacement).accepted)
    }

    @Test fun rejectsNonVideoReplacement() {
        val original = media("old", 100_000_000L, 120_000L, "video/mp4")
        val replacement = media("new", 100_000_000L, 120_000L, "audio/mpeg")

        assertFalse(MediaRelinkValidator.validate(original, replacement).accepted)
    }

    private fun media(id: String, size: Long, duration: Long, mime: String) = AppMedia(
        stableId = id,
        uri = "content://source/$id",
        title = id,
        fileName = "$id.mp4",
        mimeType = mime,
        durationMs = duration,
        sizeBytes = size,
        width = 1920,
        height = 1080,
        sourceType = MediaSourceType.SAF,
    )
}
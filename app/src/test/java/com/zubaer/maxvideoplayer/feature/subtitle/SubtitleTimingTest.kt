package com.zubaer.maxvideoplayer.feature.subtitle

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Test

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SubtitleTimingTest {
    @Test
    fun positiveAndNegativeDelayShiftCueTimingUsingLongs() {
        val original = CuesWithTiming(emptyList(), 1_000_000L, 2_000_000L)
        val positive = OffsetSubtitleParserFactory.shift(original, 750_000L)
        val negative = OffsetSubtitleParserFactory.shift(original, -500_000L)

        assertEquals(1_750_000L, positive.startTimeUs)
        assertEquals(2_000_000L, positive.durationUs)
        assertEquals(500_000L, negative.startTimeUs)
        assertEquals(2_000_000L, negative.durationUs)
    }

    @Test
    fun negativeDelayCrossingZeroClipsStartAndDurationSafely() {
        val original = CuesWithTiming(emptyList(), 100_000L, 1_000_000L)
        val shifted = OffsetSubtitleParserFactory.shift(original, -350_000L)

        assertEquals(0L, shifted.startTimeUs)
        assertEquals(750_000L, shifted.durationUs)
    }

    @Test
    fun timeUnsetIsPreserved() {
        val original = CuesWithTiming(emptyList(), C.TIME_UNSET, C.TIME_UNSET)
        val shifted = OffsetSubtitleParserFactory.shift(original, 1_000_000L)
        assertEquals(C.TIME_UNSET, shifted.startTimeUs)
        assertEquals(C.TIME_UNSET, shifted.durationUs)
    }
}

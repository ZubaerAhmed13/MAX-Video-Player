package com.zubaer.maxvideoplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPreferenceCodecTest {
    @Test
    fun unknownAndOldEnumValuesFallBackSafely() {
        assertEquals(ResizeMode.FIT, PlayerPreferenceCodec.resizeMode("REMOVED_FUTURE_VALUE"))
        assertEquals(OrientationMode.AUTO, PlayerPreferenceCodec.orientationMode(null))
        assertEquals(GestureSensitivity.MEDIUM, PlayerPreferenceCodec.sensitivity("INVALID"))
    }

    @Test
    fun knownEnumNamesRestoreExactly() {
        assertEquals(ResizeMode.CROP, PlayerPreferenceCodec.resizeMode("CROP"))
        assertEquals(OrientationMode.REVERSE_LANDSCAPE, PlayerPreferenceCodec.orientationMode("REVERSE_LANDSCAPE"))
        assertEquals(GestureSensitivity.HIGH, PlayerPreferenceCodec.sensitivity("HIGH"))
    }
}

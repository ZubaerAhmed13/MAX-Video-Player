package com.zubaer.maxvideoplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferenceCodecTest {
    @Test
    fun defaultsAreSafeAndProfessional() {
        val defaults = PlayerPreferencesState()
        assertEquals(10, defaults.doubleTapSeekSeconds)
        assertEquals(GestureSensitivity.MEDIUM, defaults.gestureSensitivity)
        assertTrue(defaults.horizontalSeekEnabled)
        assertTrue(defaults.brightnessGestureEnabled)
        assertTrue(defaults.volumeGestureEnabled)
        assertTrue(defaults.pinchZoomEnabled)
        assertEquals(3_000L, defaults.autoHideMillis)
        assertEquals(OrientationMode.AUTO, defaults.orientationMode)
        assertEquals(ResizeMode.FIT, defaults.defaultResizeMode)
        assertEquals(16f / 9f, defaults.customAspectRatio, 0.0001f)
        assertFalse(defaults.rememberPlaybackSpeed)
        assertEquals(1f, defaults.rememberedPlaybackSpeed, 0.0001f)
        assertFalse(defaults.autoPip)
        assertFalse(defaults.tutorialSeen)
    }

    @Test
    fun interactionModelRepresentsDisabledGesturesAndCustomSettingsWithoutOrdinals() {
        val configured = PlayerPreferencesState(
            doubleTapSeekSeconds = 30,
            gestureSensitivity = GestureSensitivity.HIGH,
            horizontalSeekEnabled = false,
            brightnessGestureEnabled = false,
            volumeGestureEnabled = false,
            pinchZoomEnabled = false,
            defaultResizeMode = ResizeMode.CUSTOM,
            customAspectRatio = 21f / 9f,
            rememberPlaybackSpeed = true,
            rememberedPlaybackSpeed = 1.75f,
            autoPip = true,
            tutorialSeen = true,
        )
        assertEquals(30, configured.doubleTapSeekSeconds)
        assertEquals(GestureSensitivity.HIGH, configured.gestureSensitivity)
        assertFalse(configured.horizontalSeekEnabled)
        assertFalse(configured.brightnessGestureEnabled)
        assertFalse(configured.volumeGestureEnabled)
        assertFalse(configured.pinchZoomEnabled)
        assertEquals(ResizeMode.CUSTOM, configured.defaultResizeMode)
        assertEquals(21f / 9f, configured.customAspectRatio, 0.0001f)
        assertTrue(configured.rememberPlaybackSpeed)
        assertEquals(1.75f, configured.rememberedPlaybackSpeed, 0.0001f)
        assertTrue(configured.autoPip)
        assertTrue(configured.tutorialSeen)
    }

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

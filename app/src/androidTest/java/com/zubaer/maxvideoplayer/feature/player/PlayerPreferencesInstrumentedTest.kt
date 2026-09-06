package com.zubaer.maxvideoplayer.feature.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerPreferencesInstrumentedTest {
    @Test
    fun interactionPreferencesSurviveRepositoryRecreationAndInvalidValuesAreClamped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        raw.edit().clear().commit()

        try {
            val first = PlayerPreferences(context)
            first.setDoubleTapSeekSeconds(30)
            first.setGestureSensitivity(GestureSensitivity.HIGH)
            first.setHorizontalSeekEnabled(false)
            first.setBrightnessGestureEnabled(false)
            first.setVolumeGestureEnabled(false)
            first.setPinchZoomEnabled(false)
            first.setAutoHideMillis(5_000L)
            first.setOrientationMode(OrientationMode.REVERSE_LANDSCAPE)
            first.setDefaultResizeMode(ResizeMode.CUSTOM)
            first.setCustomAspectRatio(21f / 9f)
            first.setRememberPlaybackSpeed(true)
            first.setRememberedPlaybackSpeed(1.75f)
            first.setAutoPip(true)
            first.setTutorialSeen(true)

            val restored = PlayerPreferences(context).state.value
            assertEquals(30, restored.doubleTapSeekSeconds)
            assertEquals(GestureSensitivity.HIGH, restored.gestureSensitivity)
            assertFalse(restored.horizontalSeekEnabled)
            assertFalse(restored.brightnessGestureEnabled)
            assertFalse(restored.volumeGestureEnabled)
            assertFalse(restored.pinchZoomEnabled)
            assertEquals(5_000L, restored.autoHideMillis)
            assertEquals(OrientationMode.REVERSE_LANDSCAPE, restored.orientationMode)
            assertEquals(ResizeMode.CUSTOM, restored.defaultResizeMode)
            assertEquals(21f / 9f, restored.customAspectRatio, 0.0001f)
            assertTrue(restored.rememberPlaybackSpeed)
            assertEquals(1.75f, restored.rememberedPlaybackSpeed, 0.0001f)
            assertTrue(restored.autoPip)
            assertTrue(restored.tutorialSeen)

            first.setDoubleTapSeekSeconds(999)
            first.setAutoHideMillis(100L)
            first.setRememberedPlaybackSpeed(20f)
            first.setCustomAspectRatio(Float.NaN)
            val clamped = PlayerPreferences(context).state.value
            assertEquals(300, clamped.doubleTapSeekSeconds)
            assertEquals(2_000L, clamped.autoHideMillis)
            assertEquals(4f, clamped.rememberedPlaybackSpeed, 0.0001f)
            assertEquals(16f / 9f, clamped.customAspectRatio, 0.0001f)
        } finally {
            raw.edit().clear().commit()
        }
    }

    companion object {
        private const val PREFS_NAME = "player_interaction_preferences_v1"
    }
}

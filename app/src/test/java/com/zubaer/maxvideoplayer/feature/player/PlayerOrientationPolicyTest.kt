package com.zubaer.maxvideoplayer.feature.player

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationPolicyTest {
    @Test
    fun everyOrientationModeMapsToTheIntendedAndroidRequest() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, PlayerOrientationPolicy.requestedOrientation(OrientationMode.AUTO))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, PlayerOrientationPolicy.requestedOrientation(OrientationMode.PORTRAIT))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, PlayerOrientationPolicy.requestedOrientation(OrientationMode.LANDSCAPE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, PlayerOrientationPolicy.requestedOrientation(OrientationMode.REVERSE_PORTRAIT))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, PlayerOrientationPolicy.requestedOrientation(OrientationMode.REVERSE_LANDSCAPE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, PlayerOrientationPolicy.requestedOrientation(OrientationMode.LOCK_CURRENT))
    }
}

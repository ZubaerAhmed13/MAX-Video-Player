package com.zubaer.maxvideoplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInteractionPolicyTest {
    @Test
    fun dragClassificationUsesTouchSlopAndLocksDirection() {
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(2f, 2f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.SEEK, PlayerInteractionPolicy.classifyDrag(80f, 10f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.BRIGHTNESS, PlayerInteractionPolicy.classifyDrag(5f, -80f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.VOLUME, PlayerInteractionPolicy.classifyDrag(5f, 80f, 0.8f, 10f))
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(80f, 10f, 0.2f, 10f, horizontalEnabled = false))
    }

    @Test
    fun seekMathIsLongSafeClampedAndDurationAware() {
        assertEquals(0L, PlayerInteractionPolicy.seekTargetMs(0L, 0L, 500f, 1000f, GestureSensitivity.MEDIUM))
        val twoHour = 2L * 60L * 60L * 1000L
        val forward = PlayerInteractionPolicy.seekTargetMs(twoHour / 2, twoHour, 500f, 1000f, GestureSensitivity.MEDIUM)
        assertTrue(forward > twoHour / 2)
        assertTrue(forward <= twoHour)
        assertEquals(twoHour, PlayerInteractionPolicy.seekTargetMs(twoHour - 1_000L, twoHour, 100_000f, 1000f, GestureSensitivity.HIGH))
        assertEquals(0L, PlayerInteractionPolicy.seekTargetMs(1_000L, twoHour, -100_000f, 1000f, GestureSensitivity.HIGH))
        val veryLong = Long.MAX_VALUE / 4
        val safe = PlayerInteractionPolicy.seekTargetMs(veryLong - 10_000L, veryLong, 5_000f, 1000f, GestureSensitivity.HIGH)
        assertTrue(safe in 0L..veryLong)
    }

    @Test
    fun doubleTapZonesAndClampingAreDeterministic() {
        assertEquals(DoubleTapZone.LEFT, PlayerInteractionPolicy.doubleTapZone(10f, 100f))
        assertEquals(DoubleTapZone.CENTER, PlayerInteractionPolicy.doubleTapZone(50f, 100f))
        assertEquals(DoubleTapZone.RIGHT, PlayerInteractionPolicy.doubleTapZone(90f, 100f))
        assertEquals(0L, PlayerInteractionPolicy.doubleTapTargetMs(3_000L, 60_000L, DoubleTapZone.LEFT, 10))
        assertEquals(60_000L, PlayerInteractionPolicy.doubleTapTargetMs(58_000L, 60_000L, DoubleTapZone.RIGHT, 10))
        assertEquals(20_000L, PlayerInteractionPolicy.doubleTapTargetMs(20_000L, 60_000L, DoubleTapZone.CENTER, 10))
    }

    @Test
    fun brightnessAndDynamicVolumeAreClamped() {
        assertEquals(1f, PlayerInteractionPolicy.brightnessFromDrag(0.5f, -1000f, 1000f), 0.0001f)
        assertEquals(0.01f, PlayerInteractionPolicy.brightnessFromDrag(0.5f, 1000f, 1000f), 0.0001f)
        assertEquals(10, PlayerInteractionPolicy.volumeIndex(0.4f, 25))
        assertEquals(25, PlayerInteractionPolicy.volumeIndex(2f, 25))
        assertEquals(0, PlayerInteractionPolicy.volumeIndex(-1f, 25))
    }

    @Test
    fun zoomPanAndAspectPoliciesRemainBounded() {
        assertEquals(5f, PlayerInteractionPolicy.zoom(4f, 2f), 0.0001f)
        assertEquals(1f, PlayerInteractionPolicy.zoom(1f, 0.1f), 0.0001f)
        val bounds = PlayerInteractionPolicy.panBounds(1000f, 500f, 2f)
        assertEquals(500f, bounds.maxX, 0.0001f)
        assertEquals(250f, bounds.maxY, 0.0001f)
        val clamped = PlayerInteractionPolicy.clampPan(999f, -999f, bounds)
        assertEquals(500f, clamped.first, 0.0001f)
        assertEquals(-250f, clamped.second, 0.0001f)
        assertEquals(16f / 9f, PlayerInteractionPolicy.validAspectRatio(16f, 9f)!!, 0.0001f)
        assertNull(PlayerInteractionPolicy.validAspectRatio(16f, 0f))
    }

    @Test
    fun pipRatioUsesRotationAndAndroidSafeBounds() {
        assertEquals(16 to 9, PlayerInteractionPolicy.pipRatio(1920, 1080, 0))
        assertEquals(9 to 16, PlayerInteractionPolicy.pipRatio(1920, 1080, 90))
        assertEquals(239 to 100, PlayerInteractionPolicy.pipRatio(10_000, 100, 0))
        assertEquals(100 to 239, PlayerInteractionPolicy.pipRatio(100, 10_000, 0))
    }

    @Test
    fun autoHidePolicyRespectsInteractionMenusAndAccessibility() {
        assertTrue(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(false, false, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, true, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, true, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.SETTINGS, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.NONE, false, true))
    }
}

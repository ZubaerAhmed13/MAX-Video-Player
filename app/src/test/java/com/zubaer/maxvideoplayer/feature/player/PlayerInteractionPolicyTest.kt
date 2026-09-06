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
        assertEquals(PlayerGestureKind.SEEK, PlayerInteractionPolicy.classifyDrag(-80f, 10f, 0.8f, 10f))
        assertEquals(PlayerGestureKind.BRIGHTNESS, PlayerInteractionPolicy.classifyDrag(5f, -80f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.BRIGHTNESS, PlayerInteractionPolicy.classifyDrag(5f, 80f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.VOLUME, PlayerInteractionPolicy.classifyDrag(5f, 80f, 0.8f, 10f))
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(20f, 19f, 0.2f, 10f))
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(80f, 10f, 0.2f, 10f, horizontalEnabled = false))
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(5f, -80f, 0.2f, 10f, brightnessEnabled = false))
        assertEquals(PlayerGestureKind.NONE, PlayerInteractionPolicy.classifyDrag(5f, 80f, 0.8f, 10f, volumeEnabled = false))
    }

    @Test
    fun seekMathIsLongSafeClampedAndDurationAware() {
        assertEquals(0L, PlayerInteractionPolicy.seekTargetMs(0L, 0L, 500f, 1000f, GestureSensitivity.MEDIUM))
        assertEquals(5_000L, PlayerInteractionPolicy.seekTargetMs(5_000L, -1L, 500f, 1000f, GestureSensitivity.MEDIUM))
        val twoHour = 2L * 60L * 60L * 1000L
        val low = PlayerInteractionPolicy.seekTargetMs(twoHour / 2, twoHour, 500f, 1000f, GestureSensitivity.LOW)
        val medium = PlayerInteractionPolicy.seekTargetMs(twoHour / 2, twoHour, 500f, 1000f, GestureSensitivity.MEDIUM)
        val high = PlayerInteractionPolicy.seekTargetMs(twoHour / 2, twoHour, 500f, 1000f, GestureSensitivity.HIGH)
        assertTrue(low > twoHour / 2)
        assertTrue(medium > low)
        assertTrue(high > medium)
        assertTrue(high <= twoHour)
        assertEquals(twoHour, PlayerInteractionPolicy.seekTargetMs(twoHour - 1_000L, twoHour, 100_000f, 1000f, GestureSensitivity.HIGH))
        assertEquals(0L, PlayerInteractionPolicy.seekTargetMs(1_000L, twoHour, -100_000f, 1000f, GestureSensitivity.HIGH))
        val veryLong = Long.MAX_VALUE / 4
        val safe = PlayerInteractionPolicy.seekTargetMs(veryLong - 10_000L, veryLong, 5_000f, 1000f, GestureSensitivity.HIGH)
        assertTrue(safe in 0L..veryLong)
    }

    @Test
    fun doubleTapZonesCustomDistanceAndClampingAreDeterministic() {
        assertEquals(DoubleTapZone.LEFT, PlayerInteractionPolicy.doubleTapZone(10f, 100f))
        assertEquals(DoubleTapZone.CENTER, PlayerInteractionPolicy.doubleTapZone(50f, 100f))
        assertEquals(DoubleTapZone.RIGHT, PlayerInteractionPolicy.doubleTapZone(90f, 100f))
        assertEquals(0L, PlayerInteractionPolicy.doubleTapTargetMs(3_000L, 60_000L, DoubleTapZone.LEFT, 10))
        assertEquals(60_000L, PlayerInteractionPolicy.doubleTapTargetMs(58_000L, 60_000L, DoubleTapZone.RIGHT, 10))
        assertEquals(20_000L, PlayerInteractionPolicy.doubleTapTargetMs(20_000L, 60_000L, DoubleTapZone.CENTER, 10))
        assertEquals(50_000L, PlayerInteractionPolicy.doubleTapTargetMs(20_000L, 120_000L, DoubleTapZone.RIGHT, 30))
    }

    @Test
    fun brightnessAndDynamicVolumeAreClampedAndDirectional() {
        assertTrue(PlayerInteractionPolicy.brightnessFromDrag(0.5f, -100f, 1000f) > 0.5f)
        assertTrue(PlayerInteractionPolicy.brightnessFromDrag(0.5f, 100f, 1000f) < 0.5f)
        assertEquals(1f, PlayerInteractionPolicy.brightnessFromDrag(0.5f, -1000f, 1000f), 0.0001f)
        assertEquals(0.01f, PlayerInteractionPolicy.brightnessFromDrag(0.5f, 1000f, 1000f), 0.0001f)
        assertTrue(PlayerInteractionPolicy.volumeFromDrag(0.5f, -100f, 1000f) > 0.5f)
        assertTrue(PlayerInteractionPolicy.volumeFromDrag(0.5f, 100f, 1000f) < 0.5f)
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
        val aspectAware = PlayerInteractionPolicy.panBounds(1000f, 500f, 2f, 1f)
        assertEquals(500f, aspectAware.maxX, 0.0001f)
        assertEquals(0f, aspectAware.maxY, 0.0001f)
        val clamped = PlayerInteractionPolicy.clampPan(999f, -999f, bounds)
        assertEquals(500f, clamped.first, 0.0001f)
        assertEquals(-250f, clamped.second, 0.0001f)
        assertEquals(16f / 9f, PlayerInteractionPolicy.validAspectRatio(16f, 9f)!!, 0.0001f)
        assertEquals(21f / 9f, PlayerInteractionPolicy.forcedAspectRatio(ResizeMode.ASPECT_21_9, 1f)!!, 0.0001f)
        assertEquals(1f, PlayerInteractionPolicy.forcedAspectRatio(ResizeMode.CUSTOM, 1f)!!, 0.0001f)
        assertNull(PlayerInteractionPolicy.validAspectRatio(16f, 0f))
        assertNull(PlayerInteractionPolicy.forcedAspectRatio(ResizeMode.CUSTOM, 0f))
    }

    @Test
    fun transformSeparatesResizeZoomRotationAndPanGeometry() {
        val fit = PlayerInteractionPolicy.transform(
            resizeMode = ResizeMode.FIT,
            customAspectRatio = 16f / 9f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            manualZoom = 1f,
            panX = 0f,
            panY = 0f,
            displayRotationDegrees = 0,
            viewportWidthPx = 1000f,
            viewportHeightPx = 500f,
        )
        assertEquals(1f, fit.scaleX, 0.0001f)
        assertEquals(1f, fit.scaleY, 0.0001f)
        val custom = PlayerInteractionPolicy.transform(
            resizeMode = ResizeMode.CUSTOM,
            customAspectRatio = 1f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            manualZoom = 2f,
            panX = 40f,
            panY = -20f,
            displayRotationDegrees = 90,
            viewportWidthPx = 1000f,
            viewportHeightPx = 500f,
        )
        assertEquals(2f * (1f / (1080f / 1920f)), custom.scaleX, 0.001f)
        assertEquals(2f, custom.scaleY, 0.001f)
        assertEquals(40f, custom.translationX, 0.001f)
        assertEquals(-20f, custom.translationY, 0.001f)
        assertEquals(90f, custom.rotationDegrees, 0.001f)
    }

    @Test
    fun pipRatioUsesRotationAndAndroidSafeBounds() {
        assertEquals(16 to 9, PlayerInteractionPolicy.pipRatio(1920, 1080, 0))
        assertEquals(9 to 16, PlayerInteractionPolicy.pipRatio(1920, 1080, 90))
        assertEquals(239 to 100, PlayerInteractionPolicy.pipRatio(10_000, 100, 0))
        assertEquals(100 to 239, PlayerInteractionPolicy.pipRatio(100, 10_000, 0))
        assertEquals(16 to 9, PlayerInteractionPolicy.pipRatio(null, null, null))
    }

    @Test
    fun autoHidePolicyRespectsInteractionMenusAndAccessibility() {
        assertTrue(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(false, false, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, true, true, false, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, true, PlayerMenu.NONE, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.SETTINGS, false, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.NONE, true, false))
        assertFalse(PlayerInteractionPolicy.shouldAutoHideControls(true, false, true, false, PlayerMenu.NONE, false, true))
    }
}

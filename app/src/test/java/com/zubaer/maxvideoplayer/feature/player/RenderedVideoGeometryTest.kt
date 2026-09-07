package com.zubaer.maxvideoplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedVideoGeometryTest {
    @Test
    fun fitLetterboxUsesRenderedHeightNotViewportHeightForPan() {
        val frame = RenderedVideoGeometry.baseRenderedFrame(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            resizeMode = ResizeMode.FIT,
        )
        assertEquals(1000f, frame.widthPx, 0.01f)
        assertEquals(562.5f, frame.heightPx, 0.01f)

        val bounds = RenderedVideoGeometry.panBounds(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            renderedWidthPx = frame.widthPx,
            renderedHeightPx = frame.heightPx,
            scaleX = 2f,
            scaleY = 2f,
            rotationDegrees = 0f,
        )
        assertEquals(500f, bounds.maxX, 0.01f)
        assertEquals(62.5f, bounds.maxY, 0.01f)
    }

    @Test
    fun cropCanPanAtManualZoomOneBecauseBaseFrameAlreadyOverflows() {
        val frame = RenderedVideoGeometry.baseRenderedFrame(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            resizeMode = ResizeMode.CROP,
        )
        assertEquals(1777.7778f, frame.widthPx, 0.02f)
        assertEquals(1000f, frame.heightPx, 0.01f)

        val bounds = RenderedVideoGeometry.panBounds(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            renderedWidthPx = frame.widthPx,
            renderedHeightPx = frame.heightPx,
            scaleX = 1f,
            scaleY = 1f,
            rotationDegrees = 0f,
        )
        assertEquals(388.8889f, bounds.maxX, 0.02f)
        assertEquals(0f, bounds.maxY, 0.01f)
    }

    @Test
    fun displayRotationSwapsScaledRenderedOverflowAxes() {
        val frame = RenderedVideoGeometry.baseRenderedFrame(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            resizeMode = ResizeMode.FIT,
        )
        val bounds = RenderedVideoGeometry.panBounds(
            viewportWidthPx = 1000f,
            viewportHeightPx = 1000f,
            renderedWidthPx = frame.widthPx,
            renderedHeightPx = frame.heightPx,
            scaleX = 2f,
            scaleY = 2f,
            rotationDegrees = 90f,
        )
        assertEquals(62.5f, bounds.maxX, 0.02f)
        assertEquals(500f, bounds.maxY, 0.02f)
    }

    @Test
    fun fillAndUnknownMetadataRemainConservativeAndFinite() {
        val fill = RenderedVideoGeometry.baseRenderedFrame(
            viewportWidthPx = 800f,
            viewportHeightPx = 600f,
            sourceWidth = 1920,
            sourceHeight = 1080,
            sourceRotationDegrees = 0,
            resizeMode = ResizeMode.FILL,
        )
        assertEquals(800f, fill.widthPx, 0.01f)
        assertEquals(600f, fill.heightPx, 0.01f)

        val unknown = RenderedVideoGeometry.baseRenderedFrame(
            viewportWidthPx = 800f,
            viewportHeightPx = 600f,
            sourceWidth = null,
            sourceHeight = null,
            sourceRotationDegrees = null,
            resizeMode = ResizeMode.FIT,
        )
        assertEquals(800f, unknown.widthPx, 0.01f)
        assertEquals(600f, unknown.heightPx, 0.01f)

        val safe = RenderedVideoGeometry.panBounds(
            viewportWidthPx = Float.NaN,
            viewportHeightPx = 600f,
            renderedWidthPx = Float.POSITIVE_INFINITY,
            renderedHeightPx = 600f,
            scaleX = Float.NaN,
            scaleY = 2f,
            rotationDegrees = Float.NaN,
        )
        assertTrue(safe.maxX.isFinite())
        assertTrue(safe.maxY.isFinite())
        assertTrue(safe.maxX >= 0f)
        assertTrue(safe.maxY >= 0f)
    }
}

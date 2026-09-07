package com.zubaer.maxvideoplayer.feature.player

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometry policy for clamping manual video pan against the pixels that are actually rendered.
 *
 * Pan is a viewport-space translation applied after PlayerView has laid out its video surface.
 * Using the viewport itself as the pre-transform video size is incorrect for letterboxed FIT
 * content and for CROP content that already extends beyond the viewport. This policy models the
 * base rendered frame first, then computes the axis-aligned bounds after scale and rotation.
 */
object RenderedVideoGeometry {
    data class FrameSize(val widthPx: Float, val heightPx: Float)

    fun baseRenderedFrame(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        sourceWidth: Int?,
        sourceHeight: Int?,
        sourceRotationDegrees: Int?,
        resizeMode: ResizeMode,
    ): FrameSize {
        val viewportWidth = viewportWidthPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val viewportHeight = viewportHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        if (viewportWidth <= 0f || viewportHeight <= 0f) return FrameSize(0f, 0f)

        val rawWidth = sourceWidth?.takeIf { it > 0 }?.toFloat()
        val rawHeight = sourceHeight?.takeIf { it > 0 }?.toFloat()
        if (rawWidth == null || rawHeight == null) {
            // Unknown metadata: use the viewport as a conservative, stable fallback. This avoids
            // inventing letterbox/crop geometry until Media3/source metadata becomes available.
            return FrameSize(viewportWidth, viewportHeight)
        }

        val sourceRotation = (sourceRotationDegrees ?: 0).floorMod(360)
        val swap = sourceRotation == 90 || sourceRotation == 270
        val videoWidth = if (swap) rawHeight else rawWidth
        val videoHeight = if (swap) rawWidth else rawHeight
        if (videoWidth <= 0f || videoHeight <= 0f) return FrameSize(viewportWidth, viewportHeight)

        return when (resizeMode) {
            ResizeMode.FILL -> FrameSize(viewportWidth, viewportHeight)
            ResizeMode.CROP -> {
                val scale = max(viewportWidth / videoWidth, viewportHeight / videoHeight)
                FrameSize(videoWidth * scale, videoHeight * scale)
            }
            else -> {
                val scale = min(viewportWidth / videoWidth, viewportHeight / videoHeight)
                FrameSize(videoWidth * scale, videoHeight * scale)
            }
        }
    }

    fun panBounds(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        renderedWidthPx: Float,
        renderedHeightPx: Float,
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float,
    ): PanBounds {
        val viewportWidth = viewportWidthPx.safeNonNegative()
        val viewportHeight = viewportHeightPx.safeNonNegative()
        val renderedWidth = renderedWidthPx.safeNonNegative()
        val renderedHeight = renderedHeightPx.safeNonNegative()
        val safeScaleX = abs(scaleX.takeIf { it.isFinite() } ?: 1f)
        val safeScaleY = abs(scaleY.takeIf { it.isFinite() } ?: 1f)
        val rotation = Math.toRadians((rotationDegrees.takeIf { it.isFinite() } ?: 0f).toDouble())

        val scaledWidth = renderedWidth * safeScaleX
        val scaledHeight = renderedHeight * safeScaleY
        val cosTheta = abs(cos(rotation)).toFloat()
        val sinTheta = abs(sin(rotation)).toFloat()
        val boundingWidth = scaledWidth * cosTheta + scaledHeight * sinTheta
        val boundingHeight = scaledWidth * sinTheta + scaledHeight * cosTheta

        return PanBounds(
            maxX = max(0f, (boundingWidth - viewportWidth) / 2f),
            maxY = max(0f, (boundingHeight - viewportHeight) / 2f),
        )
    }

    private fun Float.safeNonNegative(): Float =
        takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}

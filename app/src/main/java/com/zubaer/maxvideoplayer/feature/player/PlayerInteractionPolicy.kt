package com.zubaer.maxvideoplayer.feature.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PlayerInteractionPolicy {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f

    fun shouldAutoHideControls(
        isPlaying: Boolean,
        controlsLocked: Boolean,
        controlsVisible: Boolean,
        interactionInProgress: Boolean,
        activeMenu: PlayerMenu,
        tutorialVisible: Boolean,
        accessibilityMode: Boolean,
    ): Boolean = isPlaying &&
        !controlsLocked &&
        controlsVisible &&
        !interactionInProgress &&
        activeMenu == PlayerMenu.NONE &&
        !tutorialVisible &&
        !accessibilityMode

    fun classifyDrag(
        dx: Float,
        dy: Float,
        startXFraction: Float,
        touchSlopPx: Float,
        horizontalEnabled: Boolean = true,
        brightnessEnabled: Boolean = true,
        volumeEnabled: Boolean = true,
    ): PlayerGestureKind {
        val adx = abs(dx)
        val ady = abs(dy)
        if (max(adx, ady) < touchSlopPx.coerceAtLeast(0f)) return PlayerGestureKind.NONE
        val horizontal = adx > ady * 1.12f
        if (horizontal && horizontalEnabled) return PlayerGestureKind.SEEK
        if (ady >= adx) {
            return if (startXFraction < 0.5f) {
                if (brightnessEnabled) PlayerGestureKind.BRIGHTNESS else PlayerGestureKind.NONE
            } else {
                if (volumeEnabled) PlayerGestureKind.VOLUME else PlayerGestureKind.NONE
            }
        }
        return PlayerGestureKind.NONE
    }

    fun seekTargetMs(
        startPositionMs: Long,
        durationMs: Long,
        dragDxPx: Float,
        viewportWidthPx: Float,
        sensitivity: GestureSensitivity,
    ): Long {
        if (durationMs <= 0L || viewportWidthPx <= 0f) return startPositionMs.coerceAtLeast(0L)
        val normalized = (dragDxPx / viewportWidthPx).coerceIn(-2f, 2f)
        val windowMs = (durationMs.toDouble() * 0.35)
            .coerceIn(30_000.0, 12 * 60_000.0)
        val deltaMs = (normalized * windowMs * sensitivity.multiplier).toLong()
        return saturatingAdd(startPositionMs, deltaMs).coerceIn(0L, durationMs)
    }

    fun doubleTapTargetMs(
        currentPositionMs: Long,
        durationMs: Long,
        zone: DoubleTapZone,
        seekSeconds: Int,
    ): Long {
        if (durationMs <= 0L) return currentPositionMs.coerceAtLeast(0L)
        val delta = seekSeconds.coerceIn(1, 300).toLong() * 1_000L
        return when (zone) {
            DoubleTapZone.LEFT -> saturatingAdd(currentPositionMs, -delta).coerceIn(0L, durationMs)
            DoubleTapZone.RIGHT -> saturatingAdd(currentPositionMs, delta).coerceIn(0L, durationMs)
            DoubleTapZone.CENTER -> currentPositionMs.coerceIn(0L, durationMs)
        }
    }

    fun doubleTapZone(xPx: Float, widthPx: Float): DoubleTapZone {
        if (widthPx <= 0f) return DoubleTapZone.CENTER
        val fraction = (xPx / widthPx).coerceIn(0f, 1f)
        return when {
            fraction < 0.34f -> DoubleTapZone.LEFT
            fraction > 0.66f -> DoubleTapZone.RIGHT
            else -> DoubleTapZone.CENTER
        }
    }

    fun brightnessFromDrag(start: Float, dragDyPx: Float, viewportHeightPx: Float): Float {
        if (viewportHeightPx <= 0f) return start.coerceIn(0f, 1f)
        return (start - (dragDyPx / viewportHeightPx) * 1.25f).coerceIn(0.01f, 1f)
    }

    fun volumeFromDrag(start: Float, dragDyPx: Float, viewportHeightPx: Float): Float {
        if (viewportHeightPx <= 0f) return start.coerceIn(0f, 1f)
        return (start - (dragDyPx / viewportHeightPx) * 1.25f).coerceIn(0f, 1f)
    }

    fun volumeIndex(fraction: Float, maxVolume: Int): Int {
        if (maxVolume <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * maxVolume.toFloat()).toInt().coerceIn(0, maxVolume)
    }

    fun zoom(current: Float, multiplier: Float): Float =
        (current * multiplier).coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun panBounds(viewportWidthPx: Float, viewportHeightPx: Float, zoom: Float): PanBounds {
        val safeZoom = zoom.coerceAtLeast(1f)
        return PanBounds(
            maxX = max(0f, viewportWidthPx * (safeZoom - 1f) / 2f),
            maxY = max(0f, viewportHeightPx * (safeZoom - 1f) / 2f),
        )
    }

    fun clampPan(x: Float, y: Float, bounds: PanBounds): Pair<Float, Float> =
        x.coerceIn(-bounds.maxX, bounds.maxX) to y.coerceIn(-bounds.maxY, bounds.maxY)

    fun validAspectRatio(width: Float, height: Float): Float? {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
        val ratio = width / height
        return ratio.takeIf { it.isFinite() && it in 0.2f..5f }
    }

    fun forcedAspectRatio(mode: ResizeMode, customAspectRatio: Float): Float? = when (mode) {
        ResizeMode.ASPECT_16_9 -> 16f / 9f
        ResizeMode.ASPECT_4_3 -> 4f / 3f
        ResizeMode.ASPECT_18_9 -> 2f
        ResizeMode.ASPECT_21_9 -> 21f / 9f
        ResizeMode.CUSTOM -> customAspectRatio.takeIf { it.isFinite() && it in 0.2f..5f }
        else -> null
    }

    fun transform(
        resizeMode: ResizeMode,
        customAspectRatio: Float,
        sourceWidth: Int?,
        sourceHeight: Int?,
        sourceRotationDegrees: Int?,
        manualZoom: Float,
        panX: Float,
        panY: Float,
        displayRotationDegrees: Int,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): VideoTransform {
        val rotated = ((sourceRotationDegrees ?: 0) + displayRotationDegrees).floorMod(360)
        val swap = rotated == 90 || rotated == 270
        val sw = (if (swap) sourceHeight else sourceWidth)?.takeIf { it > 0 }
        val sh = (if (swap) sourceWidth else sourceHeight)?.takeIf { it > 0 }
        val sourceAspect = if (sw != null && sh != null) sw.toFloat() / sh.toFloat() else null
        val desired = forcedAspectRatio(resizeMode, customAspectRatio)
        var baseX = 1f
        var baseY = 1f
        if (desired != null && sourceAspect != null && sourceAspect > 0f) {
            baseX = (desired / sourceAspect).coerceIn(0.2f, 5f)
        }
        if (resizeMode == ResizeMode.ORIGINAL && sw != null && sh != null && viewportWidthPx > 0f && viewportHeightPx > 0f) {
            val fitScale = min(viewportWidthPx / sw.toFloat(), viewportHeightPx / sh.toFloat())
            if (fitScale > 0f) {
                val originalScale = (1f / fitScale).coerceIn(0.2f, 8f)
                baseX *= originalScale
                baseY *= originalScale
            }
        }
        val zoom = manualZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return VideoTransform(
            scaleX = baseX * zoom,
            scaleY = baseY * zoom,
            translationX = panX,
            translationY = panY,
            rotationDegrees = displayRotationDegrees.floorMod(360).toFloat(),
        )
    }

    fun pipRatio(width: Int?, height: Int?, rotationDegrees: Int?): Pair<Int, Int> {
        var w = width?.takeIf { it > 0 } ?: 16
        var h = height?.takeIf { it > 0 } ?: 9
        val rotation = (rotationDegrees ?: 0).floorMod(360)
        if (rotation == 90 || rotation == 270) {
            val temp = w
            w = h
            h = temp
        }
        val ratio = w.toDouble() / h.toDouble()
        return when {
            ratio > 2.39 -> 239 to 100
            ratio < (1.0 / 2.39) -> 100 to 239
            else -> w to h
        }
    }

    private fun saturatingAdd(a: Long, b: Long): Long = when {
        b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
        b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
        else -> a + b
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}

object PlayerPreferenceCodec {
    fun resizeMode(raw: String?): ResizeMode = enumValueOrDefault(raw, ResizeMode.FIT)
    fun orientationMode(raw: String?): OrientationMode = enumValueOrDefault(raw, OrientationMode.AUTO)
    fun sensitivity(raw: String?): GestureSensitivity = enumValueOrDefault(raw, GestureSensitivity.MEDIUM)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

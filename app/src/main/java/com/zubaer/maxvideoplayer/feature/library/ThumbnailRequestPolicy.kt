package com.zubaer.maxvideoplayer.feature.library

data class ThumbnailRequestSize(val width: Int, val height: Int)

object ThumbnailRequestPolicy {
    const val MIN_WIDTH = 64
    const val MAX_WIDTH = 640
    const val MIN_HEIGHT = 36
    const val MAX_HEIGHT = 480

    fun bound(width: Int, height: Int): ThumbnailRequestSize = ThumbnailRequestSize(
        width = width.coerceIn(MIN_WIDTH, MAX_WIDTH),
        height = height.coerceIn(MIN_HEIGHT, MAX_HEIGHT),
    )

    fun cacheKey(stableMediaId: String, width: Int, height: Int): String {
        val bounded = bound(width, height)
        return "$stableMediaId:${bounded.width}:${bounded.height}"
    }
}

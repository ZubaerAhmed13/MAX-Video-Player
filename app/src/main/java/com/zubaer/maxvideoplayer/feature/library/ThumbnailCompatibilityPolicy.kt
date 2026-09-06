package com.zubaer.maxvideoplayer.feature.library

internal enum class ThumbnailLoadStrategy {
    CONTENT_RESOLVER,
    SCALED_RETRIEVER,
    LEGACY_RETRIEVER,
}

internal object ThumbnailCompatibilityPolicy {
    fun strategy(apiLevel: Int): ThumbnailLoadStrategy = when {
        apiLevel >= 29 -> ThumbnailLoadStrategy.CONTENT_RESOLVER
        apiLevel >= 27 -> ThumbnailLoadStrategy.SCALED_RETRIEVER
        else -> ThumbnailLoadStrategy.LEGACY_RETRIEVER
    }
}

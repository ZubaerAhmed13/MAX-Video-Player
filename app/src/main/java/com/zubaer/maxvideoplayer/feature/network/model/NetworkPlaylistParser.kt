package com.zubaer.maxvideoplayer.feature.network.model

import java.net.URI

data class NetworkPlaylistItem(val uri: String, val title: String?)

/** Bounded, non-recursive M3U parser. Adaptive HLS manifests remain Media3 sources. */
object NetworkPlaylistParser {
    fun parse(text: String, baseUri: String, maximumItems: Int = 1_000): List<NetworkPlaylistItem> {
        require(maximumItems in 1..10_000)
        val base = URI(baseUri)
        val result = ArrayList<NetworkPlaylistItem>(minOf(maximumItems, 64))
        var pendingTitle: String? = null
        text.lineSequence().forEach { rawLine ->
            if (result.size >= maximumItems) return@forEach
            val line = rawLine.trim().removePrefix("\uFEFF")
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> pendingTitle = line.substringAfter(',', "").trim().takeIf(String::isNotBlank)
                line.isBlank() || line.startsWith('#') -> Unit
                else -> {
                    val resolved = runCatching { base.resolve(URI(line)).normalize() }.getOrNull()
                    if (resolved?.scheme?.lowercase() in setOf("http", "https", "rtsp")) {
                        result += NetworkPlaylistItem(resolved.toString(), pendingTitle)
                    }
                    pendingTitle = null
                }
            }
        }
        return result
    }
}

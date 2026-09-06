package com.zubaer.maxvideoplayer.core.media

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import com.zubaer.maxvideoplayer.core.model.AppMedia
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

object StableMediaIdentity {
    private const val SAMPLE_BYTES = 1024 * 1024

    fun fallbackKey(uri: String, sizeBytes: Long?, durationMs: Long?, title: String): String =
        sha256("v1|$uri|${sizeBytes ?: -1L}|${durationMs ?: -1L}|$title")

    fun forNetwork(uri: String): String = sha256("network|$uri")

    fun sampledFingerprint(contentResolver: ContentResolver, uri: Uri, declaredSize: Long? = null): String? {
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val length = resolvedLength(afd, declaredSize) ?: return@use null
                if (length <= 0L) return@use null
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    val offsets = listOf(0L, (length / 2L - SAMPLE_BYTES / 2L).coerceAtLeast(0L), (length - SAMPLE_BYTES).coerceAtLeast(0L)).distinct()
                    val buffer = ByteBuffer.allocate(SAMPLE_BYTES)
                    for (offset in offsets) {
                        buffer.clear()
                        channel.position(afd.startOffset + offset)
                        var remaining = minOf(SAMPLE_BYTES.toLong(), length - offset).toInt()
                        while (remaining > 0) {
                            buffer.limit(minOf(buffer.capacity(), buffer.position() + remaining))
                            val read = channel.read(buffer)
                            if (read <= 0) break
                            remaining -= read
                        }
                        digest.update(buffer.array(), 0, buffer.position())
                    }
                }
                digest.update(length.toString().toByteArray())
                digest.digest().toHex()
            }
        }.getOrNull()
    }

    fun stableId(contentResolver: ContentResolver, media: AppMedia): String {
        val uri = Uri.parse(media.uri)
        val fingerprint = if (media.sourceType != com.zubaer.maxvideoplayer.core.model.MediaSourceType.NETWORK) {
            sampledFingerprint(contentResolver, uri, media.sizeBytes)
        } else null
        return fingerprint ?: fallbackKey(media.uri, media.sizeBytes, media.durationMs, media.title)
    }

    private fun resolvedLength(afd: AssetFileDescriptor, declaredSize: Long?): Long? {
        val afdLength = afd.length
        return when {
            afdLength != AssetFileDescriptor.UNKNOWN_LENGTH && afdLength >= 0L -> afdLength
            declaredSize != null && declaredSize >= 0L -> declaredSize
            else -> null
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

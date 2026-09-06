package com.zubaer.maxvideoplayer.core.media

import android.content.ContentResolver
import android.net.Uri
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import java.io.FileNotFoundException
import java.lang.SecurityException

class UriAvailabilityChecker(private val resolver: ContentResolver) {
    fun check(uri: Uri): SourceAvailability = try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { } ?: return SourceAvailability.MISSING
        SourceAvailability.AVAILABLE
    } catch (_: SecurityException) {
        SourceAvailability.PERMISSION_LOST
    } catch (_: FileNotFoundException) {
        SourceAvailability.MISSING
    } catch (_: Throwable) {
        SourceAvailability.UNKNOWN
    }
}

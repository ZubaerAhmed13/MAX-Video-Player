package com.zubaer.maxvideoplayer.feature.audio

import androidx.media3.common.C

/** Maps Media3 role metadata into explicit, screen-reader-friendly audio labels. */
object AudioAccessibilityPolicy {
    fun isCommentary(roleFlags: Int): Boolean = roleFlags and C.ROLE_FLAG_COMMENTARY != 0

    fun isAudioDescription(roleFlags: Int): Boolean = roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0

    fun readableLabel(base: String, commentary: Boolean, audioDescription: Boolean): String = buildString {
        append(base)
        if (audioDescription) append(" · Audio description")
        if (commentary) append(" · Commentary")
    }
}

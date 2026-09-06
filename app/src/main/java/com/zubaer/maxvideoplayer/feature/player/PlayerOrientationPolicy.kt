package com.zubaer.maxvideoplayer.feature.player

import android.content.pm.ActivityInfo

object PlayerOrientationPolicy {
    fun requestedOrientation(mode: OrientationMode): Int = when (mode) {
        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        OrientationMode.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        OrientationMode.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        OrientationMode.LOCK_CURRENT -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}

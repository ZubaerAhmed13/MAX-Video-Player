package com.zubaer.maxvideoplayer

import android.app.Application
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class MaxVideoPlayerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Cast.getSingletonInstance(this).initialize(
            CastParams.Builder()
                .setShowSystemOutputSwitcherOnCastButtonClick(true)
                .build(),
        )
    }
}

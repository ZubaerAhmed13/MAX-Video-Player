package com.zubaer.maxvideoplayer

import android.app.Application

class MaxVideoPlayerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

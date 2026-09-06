package com.zubaer.maxvideoplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val externalMedia = MutableStateFlow<AppMedia?>(null)
    private val container: AppContainer get() = (application as MaxVideoPlayerApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.playbackConnection.connect()
        handleViewIntent(intent)
        setContent {
            val pending by externalMedia.collectAsStateWithLifecycle()
            MaxApp(
                container = container,
                externalMedia = pending,
                onExternalConsumed = { externalMedia.value = null },
                persistUriPermission = ::persistUriPermission,
                onEnterPip = ::enterPip,
                onFullscreenChanged = ::setFullscreen,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScope.launch {
            val source = if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "rtsp") MediaSourceType.NETWORK else MediaSourceType.SAF
            externalMedia.value = if (source == MediaSourceType.NETWORK) {
                container.metadataExtractor.fromNetworkUrl(uri.toString())
            } else {
                container.metadataExtractor.fromUri(uri, source)
            }
        }
    }

    private fun persistUriPermission(uri: Uri): Boolean {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching {
            contentResolver.takePersistableUriPermission(uri, readWrite)
            true
        }.getOrElse {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, read)
                true
            }.getOrDefault(false)
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }

    private fun setFullscreen(enabled: Boolean) {
        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                if (enabled) {
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (enabled) {
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else 0
        }
    }
}

package com.zubaer.maxvideoplayer

import android.app.PictureInPictureParams
import android.content.Intent
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
import com.zubaer.maxvideoplayer.feature.audio.BackgroundPlaybackMode
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerInteractionPolicy
import com.zubaer.maxvideoplayer.feature.player.PlayerOrientationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val externalMedia = MutableStateFlow<AppMedia?>(null)
    private val container: AppContainer get() = (application as MaxVideoPlayerApplication).container
    private var currentPipMedia: AppMedia? = null
    private var autoPipEnabled: Boolean = false
    private var audioBackgroundMode: BackgroundPlaybackMode = BackgroundPlaybackMode.CONTINUE_AUDIO
    private var disableVideoInBackground: Boolean = false
    private var backgroundVideoSuppressed: Boolean = false

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
                onOrientationModeChanged = ::setOrientationMode,
                onPlayerHostStateChanged = ::setPlayerHostState,
                onAudioBackgroundPolicyChanged = ::setAudioBackgroundPolicy,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Foreground entry always clears the lifecycle-only suppression flag. This is safe even
        // when the user deliberately selected audio-only mode because the controller combines
        // both policies and keeps user audio-only authoritative.
        container.audioPlaybackController.setBackgroundVideoDisabled(false)
        backgroundVideoSuppressed = false
    }

    override fun onStop() {
        if (!isChangingConfigurations && currentPipMedia != null && !isPipActive()) {
            when (audioBackgroundMode) {
                BackgroundPlaybackMode.PAUSE -> container.playbackConnection.pause()
                BackgroundPlaybackMode.CONTINUE_AUDIO -> suppressVideoForBackgroundIfRequested()
                BackgroundPlaybackMode.PIP_WHEN_POSSIBLE -> {
                    // onUserLeaveHint requests PiP first. If PiP cannot be entered, preserve the
                    // service/session and continue audio rather than stopping unexpectedly.
                    suppressVideoForBackgroundIfRequested()
                }
            }
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val media = currentPipMedia ?: return
        val shouldEnterPip = autoPipEnabled || audioBackgroundMode == BackgroundPlaybackMode.PIP_WHEN_POSSIBLE
        if (shouldEnterPip && Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode) {
            enterPip(media)
        } else if (audioBackgroundMode == BackgroundPlaybackMode.PAUSE) {
            container.playbackConnection.pause()
        } else {
            suppressVideoForBackgroundIfRequested()
        }
    }

    private fun suppressVideoForBackgroundIfRequested() {
        if (!disableVideoInBackground || isPipActive()) return
        container.audioPlaybackController.setBackgroundVideoDisabled(true)
        backgroundVideoSuppressed = true
    }

    private fun isPipActive(): Boolean = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode

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

    internal fun enterPip(media: AppMedia) {
        if (Build.VERSION.SDK_INT < 26 || isInPictureInPictureMode) return
        val (width, height) = PlayerInteractionPolicy.pipRatio(media.width, media.height, media.rotationDegrees)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(width, height))
        if (Build.VERSION.SDK_INT >= 31) builder.setSeamlessResizeEnabled(true)
        enterPictureInPictureMode(builder.build())
    }

    internal fun setPlayerHostState(media: AppMedia?, autoPip: Boolean) {
        currentPipMedia = media
        autoPipEnabled = media != null && autoPip
    }

    internal fun setAudioBackgroundPolicy(mode: BackgroundPlaybackMode, disableVideo: Boolean) {
        audioBackgroundMode = mode
        disableVideoInBackground = disableVideo
    }

    internal fun setOrientationMode(mode: OrientationMode) {
        requestedOrientation = PlayerOrientationPolicy.requestedOrientation(mode)
    }

    internal fun setFullscreen(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                if (enabled) {
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.systemBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (enabled) {
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else 0
        }
    }
}

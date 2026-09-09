package com.zubaer.maxvideoplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Rational
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.audio.BackgroundPlaybackMode
import com.zubaer.maxvideoplayer.feature.output.ExternalDisplayController
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerInteractionPolicy
import com.zubaer.maxvideoplayer.feature.player.PlayerOrientationPolicy
import com.zubaer.maxvideoplayer.feature.privatevault.auth.BiometricPreparation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val externalMedia = MutableStateFlow<AppMedia?>(null)
    private val container: AppContainer get() = (application as MaxVideoPlayerApplication).container
    private lateinit var externalDisplayController: ExternalDisplayController
    private var currentPipMedia: AppMedia? = null
    private var autoPipEnabled: Boolean = false
    private var audioBackgroundMode: BackgroundPlaybackMode = BackgroundPlaybackMode.CONTINUE_AUDIO
    private var disableVideoInBackground: Boolean = false
    private var backgroundVideoSuppressed: Boolean = false
    private var privateSecureSurfaceActive: Boolean = false
    private var biometricCancellation: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.playbackConnection.connect()
        externalDisplayController = ExternalDisplayController(this, container.playbackConnection)
        handleViewIntent(intent)
        setContent {
            val pending by externalMedia.collectAsStateWithLifecycle()
            MaxApp(
                container = container,
                externalDisplayController = externalDisplayController,
                externalMedia = pending,
                onExternalConsumed = { externalMedia.value = null },
                persistUriPermission = ::persistUriPermission,
                onEnterPip = ::enterPip,
                onFullscreenChanged = ::setFullscreen,
                onOrientationModeChanged = ::setOrientationMode,
                onPlayerHostStateChanged = ::setPlayerHostState,
                onAudioBackgroundPolicyChanged = ::setAudioBackgroundPolicy,
                onPrivateSurfaceChanged = ::setPrivateSurfaceProtected,
                onBiometricUnlock = ::requestBiometricUnlock,
                onBiometricEnroll = ::requestBiometricEnrollment,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLockController.onForeground()
        container.removableStorageController.start()
        externalDisplayController.start()
        externalDisplayController.refreshPlayerBinding()
        container.audioPlaybackController.setBackgroundVideoDisabled(false)
        backgroundVideoSuppressed = false
    }

    override fun onStop() {
        container.removableStorageController.stop()
        if (!isChangingConfigurations) {
            container.appLockController.onBackground()
            // Vault key material never survives a real background transition. This remains true
            // even when the user elects to allow screenshots on ordinary/private screens.
            container.privateVaultSession.lock()
        }
        if (!isChangingConfigurations && currentPipMedia != null && !isPipActive()) {
            if (currentPipMedia?.sourceType == MediaSourceType.PRIVATE) {
                container.playbackConnection.pause()
            } else {
                when (audioBackgroundMode) {
                    BackgroundPlaybackMode.PAUSE -> container.playbackConnection.pause()
                    BackgroundPlaybackMode.CONTINUE_AUDIO -> suppressVideoForBackgroundIfRequested()
                    BackgroundPlaybackMode.PIP_WHEN_POSSIBLE -> suppressVideoForBackgroundIfRequested()
                }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        biometricCancellation?.cancel()
        biometricCancellation = null
        if (::externalDisplayController.isInitialized) externalDisplayController.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val media = currentPipMedia ?: return
        if (media.sourceType == MediaSourceType.PRIVATE) {
            container.playbackConnection.pause()
            container.privateVaultSession.lock()
            return
        }
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
        if (uri.scheme.equals("maxvideoplayer", ignoreCase = true) && uri.host.equals("oauth", ignoreCase = true)) {
            lifecycleScope.launch {
                runCatching { container.cloudOAuthCoordinator.handleRedirect(uri) }
            }
            return
        }
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
        if (media.sourceType == MediaSourceType.PRIVATE) return
        if (Build.VERSION.SDK_INT < 26 || isInPictureInPictureMode) return
        val (width, height) = PlayerInteractionPolicy.pipRatio(media.width, media.height, media.rotationDegrees)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(width, height))
        if (Build.VERSION.SDK_INT >= 31) builder.setSeamlessResizeEnabled(true)
        enterPictureInPictureMode(builder.build())
    }

    internal fun setPlayerHostState(media: AppMedia?, autoPip: Boolean) {
        currentPipMedia = media
        autoPipEnabled = media != null && media.sourceType != MediaSourceType.PRIVATE && autoPip
        if (media?.sourceType == MediaSourceType.PRIVATE && ::externalDisplayController.isInitialized) {
            externalDisplayController.returnToPhone()
        }
        if (::externalDisplayController.isInitialized) externalDisplayController.refreshPlayerBinding()
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

    internal fun setPrivateSurfaceProtected(enabled: Boolean) {
        if (privateSecureSurfaceActive == enabled) return
        privateSecureSurfaceActive = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun requestBiometricEnrollment() {
        when (val preparation = container.privateVaultBiometricKeyManager.prepareEnrollment()) {
            is BiometricPreparation.Ready -> showBiometricPrompt(
                title = "Enable biometric unlock",
                cipher = preparation.cipher,
                onSuccess = { authenticatedCipher ->
                    if (container.privateVaultBiometricKeyManager.completeEnrollment(authenticatedCipher)) {
                        container.settingsRepository.setBiometricUnlockEnabled(true)
                        Toast.makeText(this, "Biometric unlock enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Biometric unlock could not be enabled. PIN remains available.", Toast.LENGTH_LONG).show()
                    }
                },
            )
            is BiometricPreparation.Unavailable -> Toast.makeText(this, preparation.reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestBiometricUnlock() {
        when (val preparation = container.privateVaultBiometricKeyManager.prepareUnlock()) {
            is BiometricPreparation.Ready -> showBiometricPrompt(
                title = "Unlock Private Vault",
                cipher = preparation.cipher,
                onSuccess = { authenticatedCipher ->
                    if (!container.privateVaultBiometricKeyManager.completeUnlock(authenticatedCipher)) {
                        Toast.makeText(this, "Biometric unlock failed. Use PIN.", Toast.LENGTH_LONG).show()
                    }
                },
            )
            is BiometricPreparation.Unavailable -> Toast.makeText(this, preparation.reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun showBiometricPrompt(
        title: String,
        cipher: javax.crypto.Cipher,
        onSuccess: (javax.crypto.Cipher) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "Biometric unlock is unavailable on this Android version. Use PIN.", Toast.LENGTH_LONG).show()
            return
        }
        biometricCancellation?.cancel()
        val cancellation = CancellationSignal()
        biometricCancellation = cancellation
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(title)
            .setSubtitle("MAX Video Player")
            .setNegativeButton("Use PIN", mainExecutor) { _, _ -> cancellation.cancel() }
            .build()
        prompt.authenticate(
            android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher),
            cancellation,
            mainExecutor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                    biometricCancellation = null
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) onSuccess(authenticatedCipher)
                    else Toast.makeText(this@MainActivity, "Biometric result did not contain cryptographic authorization. Use PIN.", Toast.LENGTH_LONG).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricCancellation = null
                    if (errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                    ) {
                        Toast.makeText(this@MainActivity, "Biometric unlock unavailable. Use PIN.", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

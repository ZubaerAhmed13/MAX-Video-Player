package com.zubaer.maxvideoplayer.feature.decoder.runtime

import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderCandidate
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderDiagnostics
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFailure
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFailureCode
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFallbackEvent
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFormatSnapshot
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderSessionState
import com.zubaer.maxvideoplayer.feature.decoder.persistence.DecoderMediaStateDao
import com.zubaer.maxvideoplayer.feature.decoder.persistence.DecoderMediaStateEntity
import com.zubaer.maxvideoplayer.feature.player.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class DecoderRepository(
    private val dao: DecoderMediaStateDao,
    private val playerPreferences: PlayerPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialMode = playerPreferences.state.value.defaultDecoderMode
    private val _state = MutableStateFlow(
        DecoderSessionState(
            requestedMode = initialMode,
            diagnostics = DecoderDiagnostics(requestedMode = initialMode),
        ),
    )
    val state: StateFlow<DecoderSessionState> = _state.asStateFlow()

    private val _modeRequests = MutableSharedFlow<DecoderMode>(extraBufferCapacity = 8)
    val modeRequests: SharedFlow<DecoderMode> = _modeRequests.asSharedFlow()

    private val candidatesByName = ConcurrentHashMap<String, DecoderCandidate>()
    private val rejectedNames = Collections.synchronizedSet(linkedSetOf<String>())
    @Volatile private var currentOverride: DecoderMode? = null

    init {
        scope.launch {
            playerPreferences.state.collect { preferences ->
                val current = _state.value
                if (!preferences.rememberDecoderPerVideo || currentOverride == null) {
                    applyRequestedMode(
                        mode = preferences.defaultDecoderMode,
                        mediaId = current.mediaId,
                        usingOverride = false,
                        resetAttempts = true,
                        emitRequest = current.requestedMode != preferences.defaultDecoderMode,
                    )
                }
            }
        }
    }

    fun activateMedia(mediaId: String?) {
        if (mediaId == null) {
            currentOverride = null
            applyRequestedMode(
                playerPreferences.state.value.defaultDecoderMode,
                mediaId = null,
                usingOverride = false,
                resetAttempts = true,
                emitRequest = false,
            )
            return
        }
        scope.launch {
            val preferences = playerPreferences.state.value
            val stored = if (preferences.rememberDecoderPerVideo) dao.get(mediaId) else null
            val override = stored?.requestedMode?.let(::decodeMode)
            currentOverride = override
            val requested = override ?: preferences.defaultDecoderMode
            applyRequestedMode(
                mode = requested,
                mediaId = mediaId,
                usingOverride = override != null,
                resetAttempts = true,
                emitRequest = true,
            )
        }
    }

    fun requestModeForCurrentMedia(mediaId: String?, mode: DecoderMode) {
        val remember = playerPreferences.state.value.rememberDecoderPerVideo && mediaId != null
        currentOverride = mode.takeIf { remember }
        applyRequestedMode(mode, mediaId, usingOverride = remember, resetAttempts = true, emitRequest = true)
        if (remember && mediaId != null) {
            scope.launch {
                dao.upsert(DecoderMediaStateEntity(mediaId, mode.name, System.currentTimeMillis()))
            }
        }
    }

    fun useGlobalForCurrentMedia(mediaId: String?) {
        currentOverride = null
        val mode = playerPreferences.state.value.defaultDecoderMode
        applyRequestedMode(mode, mediaId, usingOverride = false, resetAttempts = true, emitRequest = true)
        if (mediaId != null) scope.launch { dao.delete(mediaId) }
    }

    fun setGlobalDefault(mode: DecoderMode) {
        playerPreferences.setDefaultDecoderMode(mode)
    }

    fun setRememberPerVideo(enabled: Boolean) {
        playerPreferences.setRememberDecoderPerVideo(enabled)
        if (!enabled) {
            currentOverride = null
            applyRequestedMode(
                playerPreferences.state.value.defaultDecoderMode,
                _state.value.mediaId,
                usingOverride = false,
                resetAttempts = true,
                emitRequest = true,
            )
        }
    }

    fun setShowDiagnostics(enabled: Boolean) = playerPreferences.setShowDecoderDiagnostics(enabled)

    fun resetDecoderPreferences() {
        currentOverride = null
        playerPreferences.setDefaultDecoderMode(DecoderMode.AUTO)
        playerPreferences.setRememberDecoderPerVideo(true)
        playerPreferences.setShowDecoderDiagnostics(false)
        scope.launch { dao.clear() }
        applyRequestedMode(DecoderMode.AUTO, _state.value.mediaId, usingOverride = false, resetAttempts = true, emitRequest = true)
    }

    fun requestedMode(): DecoderMode = _state.value.requestedMode

    fun rejectedDecoderNames(): Set<String> = synchronized(rejectedNames) { rejectedNames.toSet() }

    fun recordCandidateQuery(candidates: List<DecoderCandidate>) {
        candidatesByName.clear()
        candidates.forEach { candidatesByName[it.name] = it }
        val current = _state.value
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(candidateNames = candidates.map { it.name }),
        )
    }

    fun recordNoCompatibleDecoder(mode: DecoderMode, mimeType: String) {
        val code = if (mode == DecoderMode.SOFTWARE) {
            DecoderFailureCode.SOFTWARE_BACKEND_UNAVAILABLE
        } else {
            DecoderFailureCode.NO_COMPATIBLE_DECODER
        }
        recordFailure(
            code = code,
            decoderName = null,
            message = when (mode) {
                DecoderMode.HARDWARE -> "No compatible hardware decoder is available for this video."
                DecoderMode.ENHANCED_HARDWARE -> "No compatible hardware decoder is available for this video."
                DecoderMode.SOFTWARE -> "No compatible software decoder is available for this video."
                DecoderMode.AUTO -> "No compatible decoder is available for $mimeType."
            },
            recoverable = true,
            addFallbackEvent = false,
        )
    }

    fun recordDecoderInitialized(decoderName: String, initializationDurationMs: Long) {
        val candidate = candidatesByName[decoderName]
        val backend = candidate?.backend ?: DecoderBackendType.UNKNOWN
        val effective = when (backend) {
            DecoderBackendType.HARDWARE -> DecoderMode.HARDWARE
            DecoderBackendType.SOFTWARE -> DecoderMode.SOFTWARE
            DecoderBackendType.UNKNOWN -> null
        }
        val current = _state.value
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(
                effectiveMode = effective,
                effectiveBackend = backend,
                activeDecoderName = decoderName,
                activeDecoderCanonicalName = candidate?.canonicalName,
                hardwareAccelerated = candidate?.hardwareAccelerated,
                softwareOnly = candidate?.softwareOnly,
                vendor = candidate?.vendor,
                secure = candidate?.secure,
                decoderInitializationDurationMs = initializationDurationMs,
                videoDecoderActive = true,
                switching = false,
                lastFailure = null,
                statusMessage = when (backend) {
                    DecoderBackendType.HARDWARE -> "Hardware decoder active"
                    DecoderBackendType.SOFTWARE -> "Software decoder active"
                    DecoderBackendType.UNKNOWN -> "Decoder active"
                },
            ),
        )
    }

    fun recordDecoderReleased(decoderName: String) {
        val current = _state.value
        if (current.diagnostics.activeDecoderName != decoderName) return
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(videoDecoderActive = false),
        )
    }

    fun recordInputFormat(format: DecoderFormatSnapshot) {
        val current = _state.value
        _state.value = current.copy(diagnostics = current.diagnostics.copy(inputFormat = format))
    }

    fun addDroppedFrames(count: Int) {
        if (count <= 0) return
        val current = _state.value
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(
                droppedFrames = (current.diagnostics.droppedFrames + count.toLong()).coerceAtLeast(0L),
            ),
        )
    }

    fun markVideoDecoderInactive(reason: String = "Video decoder inactive") {
        val current = _state.value
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(
                videoDecoderActive = false,
                activeDecoderName = null,
                activeDecoderCanonicalName = null,
                effectiveMode = null,
                effectiveBackend = null,
                switching = false,
                statusMessage = reason,
            ),
        )
    }

    fun markSwitching() {
        val current = _state.value
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(
                switching = true,
                statusMessage = "Switching decoder…",
            ),
        )
    }

    fun recordCodecFailure(message: String, runtime: Boolean): Boolean {
        val current = _state.value
        val failed = current.diagnostics.activeDecoderName
            ?: current.diagnostics.candidateNames.firstOrNull { it !in rejectedDecoderNames() }
        if (failed != null) rejectedNames += failed
        val code = if (runtime) DecoderFailureCode.DECODER_RUNTIME_FAILED else DecoderFailureCode.DECODER_INIT_FAILED
        recordFailure(code, failed, message, recoverable = true, addFallbackEvent = true)
        return hasRemainingCandidateForCurrentMode()
    }

    fun recordFailure(
        code: DecoderFailureCode,
        decoderName: String?,
        message: String,
        recoverable: Boolean,
        addFallbackEvent: Boolean,
    ) {
        val current = _state.value
        val failure = DecoderFailure(code, decoderName, message, recoverable)
        val candidate = decoderName?.let(candidatesByName::get)
        val oldHistory = current.diagnostics.fallbackHistory
        val history = if (addFallbackEvent) {
            (oldHistory + DecoderFallbackEvent(decoderName, candidate?.backend, code, message)).takeLast(MAX_FALLBACK_EVENTS)
        } else {
            oldHistory
        }
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(
                lastFailure = failure,
                fallbackHistory = history,
                fallbackCount = history.size,
                switching = false,
                statusMessage = message,
            ),
        )
    }

    fun hasRemainingCandidateForCurrentMode(): Boolean {
        val rejected = rejectedDecoderNames()
        val mode = requestedMode()
        return candidatesByName.values.any { candidate ->
            candidate.name !in rejected && when (mode) {
                DecoderMode.HARDWARE,
                DecoderMode.ENHANCED_HARDWARE,
                -> candidate.backend == DecoderBackendType.HARDWARE
                DecoderMode.SOFTWARE -> candidate.backend == DecoderBackendType.SOFTWARE
                DecoderMode.AUTO -> true
            }
        }
    }

    private fun applyRequestedMode(
        mode: DecoderMode,
        mediaId: String?,
        usingOverride: Boolean,
        resetAttempts: Boolean,
        emitRequest: Boolean,
    ) {
        if (resetAttempts) {
            rejectedNames.clear()
            candidatesByName.clear()
        }
        val old = _state.value
        val diagnostics = if (resetAttempts) {
            DecoderDiagnostics(
                requestedMode = mode,
                inputFormat = old.diagnostics.inputFormat,
                switching = emitRequest && mediaId != null,
                statusMessage = if (emitRequest && mediaId != null) "Switching decoder…" else null,
            )
        } else {
            old.diagnostics.copy(requestedMode = mode)
        }
        _state.value = DecoderSessionState(
            mediaId = mediaId,
            requestedMode = mode,
            usingMediaOverride = usingOverride,
            diagnostics = diagnostics,
        )
        if (emitRequest) _modeRequests.tryEmit(mode)
    }

    private fun decodeMode(raw: String): DecoderMode =
        DecoderMode.entries.firstOrNull { it.name == raw } ?: DecoderMode.AUTO

    private companion object {
        const val MAX_FALLBACK_EVENTS = 16
    }
}

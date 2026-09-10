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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    private val mediaOverrides = ConcurrentHashMap<String, DecoderMode>()
    private val activeDecoderInstances = ConcurrentHashMap<String, AtomicInteger>()
    private val activationGeneration = AtomicLong(0L)
    @Volatile private var currentOverride: DecoderMode? = null

    init {
        scope.launch {
            playerPreferences.state.collect { preferences ->
                val current = _state.value
                val override = currentOverride.takeIf { preferences.rememberDecoderPerVideo }
                val desiredMode = override ?: preferences.defaultDecoderMode
                val usingOverride = override != null
                if (current.requestedMode != desiredMode || current.usingMediaOverride != usingOverride) {
                    applyRequestedMode(
                        mode = desiredMode,
                        mediaId = current.mediaId,
                        usingOverride = usingOverride,
                        resetAttempts = true,
                        emitRequest = current.mediaId != null && current.requestedMode != desiredMode,
                    )
                }
            }
        }
    }

    fun activateMedia(mediaId: String?) {
        val current = _state.value
        if (current.mediaId == mediaId) return

        val generation = activationGeneration.incrementAndGet()
        val preferences = playerPreferences.state.value
        val defaultMode = preferences.defaultDecoderMode

        if (mediaId == null) {
            currentOverride = null
            applyRequestedMode(
                mode = defaultMode,
                mediaId = null,
                usingOverride = false,
                resetAttempts = true,
                emitRequest = false,
            )
            return
        }

        // A mode selected during this process lifetime is authoritative immediately. Restoring it
        // from memory avoids a default-mode window while Room is queried and also avoids racing a
        // just-scheduled DAO upsert when the user quickly navigates away and back to the same item.
        val cachedOverride = mediaOverrides[mediaId].takeIf { preferences.rememberDecoderPerVideo }
        currentOverride = cachedOverride
        val immediateMode = cachedOverride ?: defaultMode
        val restoreNeedsReprepare = cachedOverride != null && current.requestedMode != immediateMode

        // Publish the new media identity before Media3 selects a decoder for the transitioned item.
        // If an in-session override changes the requested backend, emit immediately so renderer
        // reuse cannot silently keep the previous item's decoder policy.
        applyRequestedMode(
            mode = immediateMode,
            mediaId = mediaId,
            usingOverride = cachedOverride != null,
            resetAttempts = true,
            emitRequest = restoreNeedsReprepare,
        )

        // Cached overrides were created or confirmed by this repository and are newer than any
        // asynchronous lookup that may still be queued. Do not let a stale null DAO read erase one.
        if (!preferences.rememberDecoderPerVideo || cachedOverride != null) return

        scope.launch {
            val latestPreferences = playerPreferences.state.value
            val stored = if (latestPreferences.rememberDecoderPerVideo) dao.get(mediaId) else null
            if (activationGeneration.get() != generation || _state.value.mediaId != mediaId) return@launch

            val override = stored?.requestedMode?.let(::decodeMode)
            if (override != null) mediaOverrides[mediaId] = override else mediaOverrides.remove(mediaId)
            currentOverride = override
            val requested = override ?: latestPreferences.defaultDecoderMode
            val latest = _state.value
            val usingOverride = override != null

            if (latest.requestedMode == requested) {
                if (latest.usingMediaOverride != usingOverride) {
                    applyRequestedMode(
                        mode = requested,
                        mediaId = mediaId,
                        usingOverride = usingOverride,
                        resetAttempts = false,
                        emitRequest = false,
                    )
                }
            } else {
                applyRequestedMode(
                    mode = requested,
                    mediaId = mediaId,
                    usingOverride = usingOverride,
                    resetAttempts = true,
                    emitRequest = true,
                )
            }
        }
    }

    fun requestModeForCurrentMedia(mediaId: String?, mode: DecoderMode) {
        activationGeneration.incrementAndGet()
        val remember = playerPreferences.state.value.rememberDecoderPerVideo && mediaId != null
        currentOverride = mode.takeIf { remember }
        if (remember && mediaId != null) mediaOverrides[mediaId] = mode

        val current = _state.value
        val sameEffectiveRequest = current.mediaId == mediaId && current.requestedMode == mode
        if (sameEffectiveRequest) {
            // Selecting the mode that is already active must not reprepare the player. A redundant
            // reprepare can recreate the same codec name; a late release callback from the old
            // instance can then make diagnostics look inactive even though the replacement codec is
            // already running. Preserve live diagnostics/candidates and only update override ownership.
            if (current.usingMediaOverride != remember) {
                applyRequestedMode(
                    mode = mode,
                    mediaId = mediaId,
                    usingOverride = remember,
                    resetAttempts = false,
                    emitRequest = false,
                )
            }
        } else {
            applyRequestedMode(mode, mediaId, usingOverride = remember, resetAttempts = true, emitRequest = true)
        }

        if (remember && mediaId != null) {
            scope.launch {
                dao.upsert(DecoderMediaStateEntity(mediaId, mode.name, System.currentTimeMillis()))
            }
        }
    }

    fun useGlobalForCurrentMedia(mediaId: String?) {
        activationGeneration.incrementAndGet()
        currentOverride = null
        if (mediaId != null) mediaOverrides.remove(mediaId)
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
            activationGeneration.incrementAndGet()
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
        activationGeneration.incrementAndGet()
        currentOverride = null
        mediaOverrides.clear()
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
        // A reprepare can overlap the release callback of the preceding codec instance and Media3
        // is free to choose the same codec name again. Count live instances by name so a late
        // release from the older instance cannot mark the newly initialized replacement inactive.
        activeDecoderInstances.computeIfAbsent(decoderName) { AtomicInteger(0) }.incrementAndGet()
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
        val counter = activeDecoderInstances[decoderName] ?: return
        val remaining = counter.decrementAndGet()
        if (remaining > 0) return
        activeDecoderInstances.remove(decoderName, counter)
        val current = _state.value
        if (current.diagnostics.activeDecoderName != decoderName) return
        _state.value = current.copy(
            diagnostics = current.diagnostics.copy(videoDecoderActive = false),
        )
    }

    /** Called when the owning playback engine is fully released and no analytics callbacks remain. */
    fun resetActiveDecoderInstances() {
        activeDecoderInstances.clear()
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

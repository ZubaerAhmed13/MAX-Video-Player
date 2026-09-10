package com.zubaer.maxvideoplayer.feature.sleeptimer

import android.os.SystemClock
import androidx.media3.common.Player
import com.zubaer.maxvideoplayer.feature.settings.SleepFadeDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface MonotonicClock {
    fun nowMs(): Long
}

object AndroidMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}

sealed interface SleepTimerMode {
    data class Duration(val durationMs: Long) : SleepTimerMode
    data object EndOfCurrentMedia : SleepTimerMode
    data object EndOfQueue : SleepTimerMode
}

data class SleepTimerState(
    val active: Boolean = false,
    val mode: SleepTimerMode? = null,
    val remainingMs: Long? = null,
    val fading: Boolean = false,
    val error: String? = null,
)

/** App-process facade; actual timing is attached and owned by PlaybackService. */
class SleepTimerRepository {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()
    @Volatile private var controller: SleepTimerController? = null

    internal fun attach(controller: SleepTimerController) {
        this.controller = controller
    }

    internal fun detach(controller: SleepTimerController) {
        if (this.controller === controller) this.controller = null
        _state.value = SleepTimerState()
    }

    internal fun publish(state: SleepTimerState) {
        _state.value = state
    }

    fun startDuration(durationMs: Long): Boolean {
        if (durationMs !in MIN_DURATION_MS..MAX_DURATION_MS) return false
        return controller?.start(SleepTimerMode.Duration(durationMs)) ?: false
    }

    fun endOfCurrentMedia(): Boolean = controller?.start(SleepTimerMode.EndOfCurrentMedia) ?: false
    fun endOfQueue(): Boolean = controller?.start(SleepTimerMode.EndOfQueue) ?: false
    fun cancel(): Boolean = controller?.cancel() ?: false

    companion object {
        const val MIN_DURATION_MS = 1_000L
        const val MAX_DURATION_MS = 24L * 60L * 60L * 1_000L
    }
}

class SleepTimerController(
    private val player: Player,
    private val repository: SleepTimerRepository,
    private val scope: CoroutineScope,
    private val fadeDuration: () -> SleepFadeDuration,
    private val clock: MonotonicClock = AndroidMonotonicClock,
) : Player.Listener {
    private var ticker: Job? = null
    private var mode: SleepTimerMode? = null
    private var deadlineMs: Long? = null
    private var startingMediaIndex = -1
    private var basePlayerVolume = 1f
    private var fadeApplied = false

    init {
        repository.attach(this)
        player.addListener(this)
    }

    fun start(next: SleepTimerMode): Boolean {
        if (next is SleepTimerMode.Duration && next.durationMs !in SleepTimerRepository.MIN_DURATION_MS..SleepTimerRepository.MAX_DURATION_MS) {
            return false
        }
        cancelInternal(publish = false)
        mode = next
        basePlayerVolume = player.volume
        startingMediaIndex = player.currentMediaItemIndex
        deadlineMs = if (next is SleepTimerMode.Duration) Math.addExact(clock.nowMs(), next.durationMs) else null
        publish()
        if (next is SleepTimerMode.Duration) startTicker()
        return true
    }

    fun cancel(): Boolean {
        val hadTimer = mode != null
        cancelInternal(publish = true)
        return hadTimer
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        when (mode) {
            SleepTimerMode.EndOfCurrentMedia -> if (startingMediaIndex >= 0 && player.currentMediaItemIndex != startingMediaIndex) expire()
            else -> Unit
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        when (mode) {
            SleepTimerMode.EndOfCurrentMedia,
            SleepTimerMode.EndOfQueue -> expire()
            else -> Unit
        }
    }

    fun release() {
        cancelInternal(publish = false)
        player.removeListener(this)
        repository.detach(this)
    }

    /** Deterministic hook used by unit/instrumentation clocks without real waits. */
    fun evaluateNowForTest() {
        evaluateDuration()
    }

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive && mode is SleepTimerMode.Duration) {
                evaluateDuration()
                delay(TICK_MS)
            }
        }
    }

    private fun evaluateDuration() {
        val activeMode = mode as? SleepTimerMode.Duration ?: return
        val deadline = deadlineMs ?: return
        val remaining = (deadline - clock.nowMs()).coerceAtLeast(0L)
        if (remaining <= 0L) {
            expire()
            return
        }
        val fadeMs = fadeDuration().millis
        if (fadeMs > 0L && remaining <= fadeMs && player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) {
            val multiplier = (remaining.toDouble() / fadeMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
            player.volume = (basePlayerVolume * multiplier).coerceIn(0f, 1f)
            fadeApplied = true
        }
        repository.publish(
            SleepTimerState(
                active = true,
                mode = activeMode,
                remainingMs = remaining,
                fading = fadeApplied,
            ),
        )
    }

    private fun expire() {
        if (mode == null) return
        restoreVolume()
        ticker?.cancel()
        ticker = null
        player.pause()
        mode = null
        deadlineMs = null
        repository.publish(SleepTimerState())
    }

    private fun publish() {
        val currentMode = mode
        repository.publish(
            SleepTimerState(
                active = currentMode != null,
                mode = currentMode,
                remainingMs = (deadlineMs?.minus(clock.nowMs()))?.coerceAtLeast(0L),
                fading = fadeApplied,
            ),
        )
    }

    private fun cancelInternal(publish: Boolean) {
        ticker?.cancel()
        ticker = null
        restoreVolume()
        mode = null
        deadlineMs = null
        startingMediaIndex = -1
        if (publish) repository.publish(SleepTimerState())
    }

    private fun restoreVolume() {
        if (fadeApplied && player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) player.volume = basePlayerVolume.coerceIn(0f, 1f)
        fadeApplied = false
    }

    private companion object {
        const val TICK_MS = 500L
    }
}

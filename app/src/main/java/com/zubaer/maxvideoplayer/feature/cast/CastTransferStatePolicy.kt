package com.zubaer.maxvideoplayer.feature.cast

import androidx.media3.common.Player
import com.zubaer.maxvideoplayer.core.model.RepeatMode

/** State that must remain semantically continuous while CastPlayer moves between local/remote. */
data class CastTransferSnapshot(
    val queueMediaIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
    val playWhenReady: Boolean,
)

enum class CastTransferEndpoint {
    LOCAL,
    REMOTE,
}

data class CastTransferContinuity(
    val from: CastTransferEndpoint,
    val to: CastTransferEndpoint,
    val queuePreserved: Boolean,
    val indexPreserved: Boolean,
    val positionPreserved: Boolean,
    val repeatPreserved: Boolean,
    val shufflePreserved: Boolean,
    val playIntentPreserved: Boolean,
) {
    val preserved: Boolean
        get() = queuePreserved && indexPreserved && positionPreserved && repeatPreserved &&
            shufflePreserved && playIntentPreserved
}

/**
 * Deterministic policy for software certification of local -> Cast -> local state continuity.
 * Media3 CastPlayer performs the actual transfer; this policy defines and verifies the app-level
 * invariants without requiring a physical receiver.
 */
object CastTransferStatePolicy {
    const val DEFAULT_POSITION_TOLERANCE_MS = 2_000L

    fun compare(
        from: CastTransferEndpoint,
        to: CastTransferEndpoint,
        before: CastTransferSnapshot,
        after: CastTransferSnapshot,
        positionToleranceMs: Long = DEFAULT_POSITION_TOLERANCE_MS,
    ): CastTransferContinuity {
        require(positionToleranceMs >= 0L)
        return CastTransferContinuity(
            from = from,
            to = to,
            queuePreserved = before.queueMediaIds == after.queueMediaIds,
            indexPreserved = before.currentIndex == after.currentIndex,
            positionPreserved = kotlin.math.abs(before.positionMs - after.positionMs) <= positionToleranceMs,
            repeatPreserved = before.repeatMode == after.repeatMode,
            shufflePreserved = before.shuffleEnabled == after.shuffleEnabled,
            playIntentPreserved = before.playWhenReady == after.playWhenReady,
        )
    }

    fun snapshot(player: Player): CastTransferSnapshot = CastTransferSnapshot(
        queueMediaIds = if (player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) {
            List(player.mediaItemCount) { index -> player.getMediaItemAt(index).mediaId }
        } else {
            emptyList()
        },
        currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
        positionMs = player.currentPosition.coerceAtLeast(0L),
        repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
        shuffleEnabled = player.shuffleModeEnabled,
        playWhenReady = player.playWhenReady,
    )
}

/** Small service-side observer used to keep transfer continuity visible to diagnostics/tests. */
class CastTransferContinuityMonitor {
    private var endpoint: CastTransferEndpoint? = null
    private var snapshot: CastTransferSnapshot? = null

    @Volatile
    var lastTransfer: CastTransferContinuity? = null
        private set

    fun observe(nextEndpoint: CastTransferEndpoint, nextSnapshot: CastTransferSnapshot): CastTransferContinuity? {
        val previousEndpoint = endpoint
        val previousSnapshot = snapshot
        endpoint = nextEndpoint
        snapshot = nextSnapshot
        if (previousEndpoint == null || previousSnapshot == null || previousEndpoint == nextEndpoint) return null
        return CastTransferStatePolicy.compare(previousEndpoint, nextEndpoint, previousSnapshot, nextSnapshot)
            .also { lastTransfer = it }
    }
}

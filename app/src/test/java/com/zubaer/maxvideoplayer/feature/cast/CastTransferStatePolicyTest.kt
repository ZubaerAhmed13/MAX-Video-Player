package com.zubaer.maxvideoplayer.feature.cast

import com.zubaer.maxvideoplayer.core.model.RepeatMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastTransferStatePolicyTest {
    private val local42s = CastTransferSnapshot(
        queueMediaIds = listOf("A", "B", "C", "D"),
        currentIndex = 1,
        positionMs = 42_000L,
        repeatMode = RepeatMode.ALL,
        shuffleEnabled = true,
        playWhenReady = true,
    )

    @Test
    fun localToRemote_preservesQueueIndexPositionRepeatShuffleAndPlayIntent() {
        val remote = local42s.copy(positionMs = 42_650L)
        val result = CastTransferStatePolicy.compare(
            CastTransferEndpoint.LOCAL,
            CastTransferEndpoint.REMOTE,
            local42s,
            remote,
        )
        assertTrue(result.preserved)
    }

    @Test
    fun remoteToLocal_preservesRemoteProgressAndQueueState() {
        val remote84s = local42s.copy(positionMs = 84_000L)
        val localAfterReturn = remote84s.copy(positionMs = 84_700L)
        val monitor = CastTransferContinuityMonitor()

        monitor.observe(CastTransferEndpoint.REMOTE, remote84s)
        val result = monitor.observe(CastTransferEndpoint.LOCAL, localAfterReturn)

        assertTrue(result?.preserved == true)
    }

    @Test
    fun changedQueueOrIndex_isReportedAsTransferRegression() {
        val remote = local42s.copy(
            queueMediaIds = listOf("A", "C", "D"),
            currentIndex = 0,
        )
        val result = CastTransferStatePolicy.compare(
            CastTransferEndpoint.LOCAL,
            CastTransferEndpoint.REMOTE,
            local42s,
            remote,
        )

        assertFalse(result.preserved)
        assertFalse(result.queuePreserved)
        assertFalse(result.indexPreserved)
    }
}

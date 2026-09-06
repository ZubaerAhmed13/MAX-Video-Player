package com.zubaer.maxvideoplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueStateTest {
    private fun media(id: String) = AppMedia(id, "content://$id", id, sourceType = MediaSourceType.MEDIA_STORE)

    @Test fun queueMovesWithoutLeavingBounds() {
        val queue = PlaybackQueueState.of(listOf(media("a"), media("b"), media("c")), 1)
        assertEquals("b", queue.current?.stableId)
        assertEquals("c", queue.moveNext().current?.stableId)
        assertEquals("a", queue.movePrevious().current?.stableId)
    }

    @Test fun emptyQueueIsSafe() {
        val queue = PlaybackQueueState.of(emptyList(), 99)
        assertEquals(-1, queue.currentIndex)
        assertNull(queue.current)
    }
}

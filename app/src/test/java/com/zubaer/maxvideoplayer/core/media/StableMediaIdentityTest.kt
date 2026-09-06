package com.zubaer.maxvideoplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableMediaIdentityTest {
    @Test fun fallbackIsDeterministic() {
        val a = StableMediaIdentity.fallbackKey("content://video/1", 3_500_000_000L, 90_000L, "movie.mkv")
        val b = StableMediaIdentity.fallbackKey("content://video/1", 3_500_000_000L, 90_000L, "movie.mkv")
        assertEquals(a, b)
    }

    @Test fun identityChangesWhenLargeFileSizeChanges() {
        val a = StableMediaIdentity.fallbackKey("content://video/1", 3_500_000_000L, 90_000L, "movie.mkv")
        val b = StableMediaIdentity.fallbackKey("content://video/1", 3_500_000_001L, 90_000L, "movie.mkv")
        assertNotEquals(a, b)
    }
}

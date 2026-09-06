package com.zubaer.maxvideoplayer.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailRequestPolicyTest {
    @Test fun boundsTinyAndHugeRequests() {
        assertEquals(ThumbnailRequestSize(64, 36), ThumbnailRequestPolicy.bound(1, 1))
        assertEquals(ThumbnailRequestSize(640, 480), ThumbnailRequestPolicy.bound(8_000, 8_000))
        assertEquals(ThumbnailRequestSize(240, 135), ThumbnailRequestPolicy.bound(240, 135))
    }

    @Test fun cacheKeyUsesBoundedDimensionsAndStableIdentity() {
        assertEquals("media-a:640:480", ThumbnailRequestPolicy.cacheKey("media-a", 4_000, 3_000))
        assertEquals(
            ThumbnailRequestPolicy.cacheKey("media-a", 640, 480),
            ThumbnailRequestPolicy.cacheKey("media-a", 4_000, 3_000),
        )
    }
}

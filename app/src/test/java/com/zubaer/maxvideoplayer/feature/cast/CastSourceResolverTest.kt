package com.zubaer.maxvideoplayer.feature.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastSourceResolverTest {
    private val resolver = CastSourceResolver()

    @Test
    fun publicHttps_isDirectCast() {
        assertEquals(
            CastSourceMode.DIRECT_CAST,
            resolver.resolve("https://example.test/movie.mp4").mode,
        )
    }

    @Test
    fun contentUri_isRelay() {
        assertEquals(
            CastSourceMode.LOCAL_RELAY,
            resolver.resolve("content://media/external/video/42").mode,
        )
    }

    @Test
    fun privateSmb_isRelay() {
        assertEquals(
            CastSourceMode.LOCAL_RELAY,
            resolver.resolve("maxsmb://server/share/movie.mkv").mode,
        )
    }

    @Test
    fun authenticatedAdaptive_isManifestRelay() {
        assertEquals(
            CastSourceMode.MANIFEST_RELAY,
            resolver.resolve(
                "https://example.test/private/master.m3u8",
                isAdaptiveManifest = true,
                requiresPrivateHeaders = true,
            ).mode,
        )
    }

    @Test
    fun rtsp_isTruthfullyUnsupported() {
        assertEquals(
            CastSourceMode.UNSUPPORTED_CAST,
            resolver.resolve("rtsp://camera.test/live").mode,
        )
    }
}

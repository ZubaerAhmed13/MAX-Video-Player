package com.zubaer.maxvideoplayer.feature.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class CastAdaptiveManifestRewriterTest {
    private val source = URI("https://media.example.test/private/master.m3u8")
    private val relay = "http://192.168.1.20:32100/cast/session/media"

    @Test
    fun hls_rewrites_playlist_segment_and_key_references() {
        val input = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
            #EXT-X-STREAM-INF:BANDWIDTH=800000
            low/index.m3u8
        """.trimIndent()

        val output = CastManifestRewriter.rewrite(input, source, relay)

        assertTrue(output.contains("URI=\"$relay?p="))
        assertTrue(output.contains("$relay?p="))
        assertFalse(output.contains("keys/key.bin\""))
        assertFalse(output.contains("low/index.m3u8\n"))
    }

    @Test
    fun dash_preserves_template_markers_for_receiver_substitution() {
        val input = """
            <?xml version="1.0"?>
            <MPD>
              <BaseURL>video/</BaseURL>
              <SegmentTemplate initialization="init-${'$'}RepresentationID${'$'}.m4s" media="chunk-${'$'}Number${'$'}.m4s" />
            </MPD>
        """.trimIndent()

        val output = CastManifestRewriter.rewrite(
            input,
            URI("https://media.example.test/private/manifest.mpd"),
            relay,
        )

        assertTrue(output.contains(relay))
        assertTrue(output.contains("${'$'}RepresentationID${'$'}"))
        assertTrue(output.contains("${'$'}Number${'$'}"))
        assertFalse(output.contains("%24Number%24"))
    }

    @Test
    fun harmless_live_sequence_query_is_not_treated_as_a_secret() {
        val input = "#EXTM3U\nsegment.ts?sequence=12\n"

        val output = CastManifestRewriter.rewrite(input, source, relay)

        assertTrue(output.contains("$relay?p="))
        assertTrue(output.contains("sequence%3D12"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sensitive_child_query_withoutOpaqueMapper_is_never_copiedIntoReceiverUrl() {
        CastManifestRewriter.rewrite(
            "#EXTM3U\nhttps://media.example.test/segment.ts?access_token=secret\n",
            source,
            relay,
        )
    }

    @Test
    fun signedHlsChild_isRewrittenToOpaqueReceiverReference_withoutSecretLeakage() {
        var captured: URI? = null
        val opaqueUrl = "$relay?r=0123456789abcdef01234567"

        val output = CastManifestRewriter.rewrite(
            "#EXTM3U\nsegment001.ts?token=SUPER_SECRET\n",
            source,
            relay,
            opaqueReference = { original ->
                captured = original
                opaqueUrl
            },
        )

        assertEquals("https://media.example.test/private/segment001.ts?token=SUPER_SECRET", captured.toString())
        assertTrue(output.contains(opaqueUrl))
        assertFalse(output.contains("SUPER_SECRET"))
        assertFalse(output.contains("token="))
        assertFalse(output.contains("media.example.test"))
    }

    @Test
    fun signedDashTemplate_keepsSecretPhoneSide_andExposesOnlyOpaqueIdAndTemplateValue() {
        val signedBase = URI("https://cdn.example.test/video/manifest.mpd?x-amz-signature=PHONE_ONLY")
        val captured = mutableListOf<URI>()
        val input = """
            <MPD>
              <SegmentTemplate media="chunk-${'$'}Number${'$'}.m4s?x-goog-signature=SECRET_SIG" />
            </MPD>
        """.trimIndent()

        val output = CastManifestRewriter.rewrite(
            input,
            signedBase,
            relay,
            opaqueReference = { original ->
                captured += original
                "$relay?r=abcdefabcdefabcdefabcdef&v0=${'$'}Number${'$'}"
            },
        )

        assertTrue(captured.single().toString().contains("x-goog-signature=SECRET_SIG"))
        assertTrue(output.contains("r=abcdefabcdefabcdefabcdef"))
        assertTrue(output.contains("${'$'}Number${'$'}"))
        assertFalse(output.contains("SECRET_SIG"))
        assertFalse(output.contains("x-goog-signature"))
        assertFalse(output.contains("cdn.example.test"))
    }
}

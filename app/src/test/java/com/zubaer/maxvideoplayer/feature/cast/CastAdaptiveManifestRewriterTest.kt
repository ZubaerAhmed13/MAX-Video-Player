package com.zubaer.maxvideoplayer.feature.cast

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
    fun sensitive_child_query_is_never_copied_into_receiver_url() {
        CastManifestRewriter.rewrite(
            "#EXTM3U\nhttps://media.example.test/segment.ts?access_token=secret\n",
            source,
            relay,
        )
    }
}

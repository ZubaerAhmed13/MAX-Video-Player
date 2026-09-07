package com.zubaer.maxvideoplayer.feature.subtitle

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SubtitleParserFormatsInstrumentedTest {
    @Test
    fun realMedia3ParsersCoverStep4TextFormatsAndUnicode() {
        val cases = listOf(
            Case(
                name = "SRT",
                mime = MimeTypes.APPLICATION_SUBRIP,
                expected = "বাংলা English العربية 日本語",
                payload = """1
00:00:00,100 --> 00:00:01,700
বাংলা English العربية 日本語
""".trimIndent(),
            ),
            Case(
                name = "WebVTT",
                mime = MimeTypes.TEXT_VTT,
                expected = "Step 4 WebVTT fixture",
                payload = """WEBVTT

00:00.100 --> 00:01.700
Step 4 WebVTT fixture
""".trimIndent(),
            ),
            Case(
                name = "ASS",
                mime = MimeTypes.TEXT_SSA,
                expected = "Step 4 ASS fixture",
                payload = assPayload("Step 4 ASS fixture"),
            ),
            Case(
                name = "SSA",
                mime = MimeTypes.TEXT_SSA,
                expected = "Step 4 SSA fixture",
                payload = assPayload("Step 4 SSA fixture"),
            ),
            Case(
                name = "TTML",
                mime = MimeTypes.APPLICATION_TTML,
                expected = "Step 4 TTML fixture",
                payload = """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:00.100" end="00:00:01.700">Step 4 TTML fixture</p></div></body></tt>
""".trimIndent(),
            ),
        )

        cases.forEach { case ->
            val format = Format.Builder().setSampleMimeType(case.mime).build()
            val factory = DefaultSubtitleParserFactory()
            assertTrue("Media3 factory does not support ${case.name}", factory.supportsFormat(format))
            val parser = factory.create(format)
            val output = mutableListOf<CuesWithTiming>()
            val data = case.payload.toByteArray(Charsets.UTF_8)
            parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.allCues()) { output += it }
            val text = output.flatMap { it.cues }.mapNotNull { it.text?.toString() }.joinToString("\n")
            assertTrue("${case.name} parser did not emit expected cue. Actual=$text", case.expected in text)
        }
    }

    private data class Case(
        val name: String,
        val mime: String,
        val expected: String,
        val payload: String,
    )

    private companion object {
        fun assPayload(text: String): String = """[Script Info]
ScriptType: v4.00+

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.10,0:00:01.70,Default,,0,0,0,,$text
""".trimIndent()
    }
}

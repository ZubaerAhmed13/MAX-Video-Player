package com.zubaer.maxvideoplayer.feature.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class MaxAudioProcessorTest {
    @Test
    fun neutralPcm16IsBitTransparent() {
        val samples = shortArrayOf(-32768, -12000, -1, 0, 1, 12000, 32767)
        val output = process16(samples, channels = 1, params = AudioDspParameters(revision = 1L))
        assertArrayEquals(samples, output)
    }

    @Test
    fun neutralFloatPcmIsBitTransparent() {
        val samples = floatArrayOf(-1f, -0.75f, -0.1f, 0f, 0.1f, 0.75f, 1f)
        val output = processFloat(samples, channels = 1, params = AudioDspParameters(revision = 2L))
        assertArrayEquals(samples, output, 0f)
    }

    @Test
    fun preampAndBoostChangeRealPcmAndLimiterKeepsLegalRange() {
        val low = ShortArray(1024) { 2_000 }
        val preamped = process16(low, 1, AudioDspParameters(preampDb = 6f, revision = 3L))
        val boosted = process16(low, 1, AudioDspParameters(boostDb = 6f, revision = 4L))
        assertTrue(abs(preamped[100].toInt()) > abs(low[100].toInt()))
        assertTrue(abs(boosted[100].toInt()) > abs(low[100].toInt()))

        val nearFull = ShortArray(1024) { 30_000 }
        val limited = process16(nearFull, 1, AudioDspParameters(preampDb = 12f, boostDb = 12f, revision = 5L))
        assertTrue(limited.all { it.toInt() in -32768..32767 })
        assertTrue(limited.maxOf { abs(it.toInt()) } <= 32767)
    }

    @Test
    fun monoLeftAndRightModesPerformRealStereoMapping() {
        val stereo = shortArrayOf(12_000, -4_000, 8_000, 2_000)
        val mono = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.MONO, revision = 6L))
        assertTrue(abs(mono[0].toInt() - mono[1].toInt()) <= 1)
        assertTrue(abs(mono[2].toInt() - mono[3].toInt()) <= 1)

        val left = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.LEFT, revision = 7L))
        assertTrue(abs(left[0].toInt() - left[1].toInt()) <= 1)
        assertTrue(abs(left[2].toInt() - left[3].toInt()) <= 1)

        val right = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.RIGHT, revision = 8L))
        assertTrue(abs(right[0].toInt() - right[1].toInt()) <= 1)
        assertTrue(abs(right[2].toInt() - right[3].toInt()) <= 1)
    }

    @Test
    fun balanceAttenuatesOnlyTheRequestedStereoSide() {
        val stereo = floatArrayOf(0.5f, 0.5f, -0.5f, -0.5f)
        val fullLeft = processFloat(stereo, 2, AudioDspParameters(balance = -1f, revision = 9L))
        assertTrue(abs(fullLeft[0]) > 0.49f)
        assertTrue(abs(fullLeft[1]) < 0.0001f)
        assertTrue(abs(fullLeft[2]) > 0.49f)
        assertTrue(abs(fullLeft[3]) < 0.0001f)

        val fullRight = processFloat(stereo, 2, AudioDspParameters(balance = 1f, revision = 10L))
        assertTrue(abs(fullRight[0]) < 0.0001f)
        assertTrue(abs(fullRight[1]) > 0.49f)
        assertTrue(abs(fullRight[2]) < 0.0001f)
        assertTrue(abs(fullRight[3]) > 0.49f)
    }

    @Test
    fun multichannelLayoutIsPreservedWhenStereoOnlyControlsAreSet() {
        val frame = floatArrayOf(0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f)
        val output = processFloat(
            frame,
            channels = 6,
            params = AudioDspParameters(channelMode = AudioChannelMode.LEFT, balance = -1f, revision = 11L),
        )
        assertArrayEquals("5.1 channels must not be remapped by stereo-only controls", frame, output, 0.000001f)

        val state = AudioEngineState(
            tracks = listOf(
                AudioTrackInfo(
                    key = "5.1",
                    label = "5.1",
                    language = "en",
                    mimeType = "audio/aac",
                    codec = "mp4a.40.2",
                    channelCount = 6,
                    sampleRate = 48_000,
                    bitrate = 384_000,
                    commentary = false,
                    selected = true,
                    supported = true,
                    external = false,
                ),
            ),
        )
        assertEquals(6, state.selectedChannelCount)
        assertFalse(state.stereoChannelControlsAvailable)
    }

    @Test
    fun stereoTrackEnablesStereoOnlyControls() {
        val state = AudioEngineState(
            tracks = listOf(
                AudioTrackInfo("2.0", "Stereo", "en", "audio/aac", null, 2, 48_000, null, false, true, true, false),
            ),
        )
        assertEquals(2, state.selectedChannelCount)
        assertTrue(state.stereoChannelControlsAvailable)
    }

    @Test
    fun positiveAndNegativeDelayHaveDeterministicSampleSemantics() {
        val sampleRate = 1_000
        val frames = ShortArray(200) { (it + 1).toShort() }
        val positive = process16(frames, 1, AudioDspParameters(delayMs = 50L, revision = 12L), sampleRate)
        assertEquals(200, positive.size)
        assertTrue(positive.take(50).all { it.toInt() == 0 })
        assertEquals(frames[0], positive[50])

        val negative = process16(frames, 1, AudioDspParameters(delayMs = -50L, revision = 13L), sampleRate)
        assertEquals(150, negative.size)
        assertEquals(frames[50], negative[0])
    }

    @Test
    fun tenBandEqBoostsTargetFrequencyMoreThanDistantFrequency() {
        val bands = MutableList(10) { 0f }.also { it[5] = 6f }
        val params = AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 14L)
        val oneKhz = sine(1_000.0, 48_000, 1.0, 0.04)
        val eightKhz = sine(8_000.0, 48_000, 1.0, 0.04)
        val out1k = process16(oneKhz, 1, params, 48_000)
        val out8k = process16(eightKhz, 1, params.copy(revision = 15L), 48_000)
        val inputRms = rms(oneKhz.copyOfRange(24_000, 48_000))
        val targetRms = rms(out1k.copyOfRange(24_000, out1k.size))
        val distantRms = rms(out8k.copyOfRange(24_000, out8k.size))
        assertTrue("Target band must produce real gain", targetRms > inputRms * 1.45)
        assertTrue("EQ must not be disguised global gain", targetRms > distantRms * 1.25)
    }

    @Test
    fun presetDefinitionsAreRealTenBandCurves() {
        EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach { preset ->
            val bands = AudioPolicy.preset(preset)
            assertEquals(10, bands.size)
            assertTrue(bands.all { it in AudioPolicy.MIN_EQ_DB..AudioPolicy.MAX_EQ_DB })
        }
        assertTrue(AudioPolicy.preset(EqualizerPreset.BASS)[0] > AudioPolicy.preset(EqualizerPreset.BASS)[9])
        assertTrue(AudioPolicy.preset(EqualizerPreset.TREBLE)[9] > AudioPolicy.preset(EqualizerPreset.TREBLE)[0])
    }

    @Test
    fun realtimeRevisionChangesAreAppliedWithoutRecreatingProcessor() {
        val source = AtomicReference(AudioDspParameters(revision = 16L))
        val processor = MaxAudioProcessor(source)
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val first = processFloatChunk(processor, floatArrayOf(0.2f))
        assertEquals(0.2f, first.single(), 0f)

        source.set(AudioDspParameters(boostDb = 6f, revision = 17L))
        val second = processFloatChunk(processor, floatArrayOf(0.2f))
        assertTrue(second.single() > 0.35f)
        processor.reset()
    }

    @Test
    fun extremeLegalSettingsRemainBoundedAndFiniteForLongFixture() {
        val bands = List(10) { 12f }
        val fixture = sine(997.0, 48_000, 3.0, 0.8)
        val output = process16(
            fixture,
            1,
            AudioDspParameters(
                equalizerEnabled = true,
                equalizerBandsDb = bands,
                preampDb = 12f,
                boostDb = 12f,
                revision = 18L,
            ),
            48_000,
        )
        assertEquals(fixture.size, output.size)
        assertTrue(output.all { it.toInt() in -32768..32767 })
    }

    private fun process16(
        samples: ShortArray,
        channels: Int,
        params: AudioDspParameters,
        sampleRate: Int = 48_000,
    ): ShortArray {
        val processor = MaxAudioProcessor(AtomicReference(params))
        processor.configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2)
        for (i in result.indices) result[i] = output.short
        processor.reset()
        return result
    }

    private fun processFloat(
        samples: FloatArray,
        channels: Int,
        params: AudioDspParameters,
        sampleRate: Int = 48_000,
    ): FloatArray {
        val processor = MaxAudioProcessor(AtomicReference(params))
        processor.configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val result = processFloatChunk(processor, samples)
        processor.reset()
        return result
    }

    private fun processFloatChunk(processor: MaxAudioProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        val result = FloatArray(output.remaining() / 4)
        for (i in result.indices) result[i] = output.float
        return result
    }

    private fun sine(frequency: Double, sampleRate: Int, seconds: Double, amplitude: Double): ShortArray {
        val size = (sampleRate * seconds).toInt()
        return ShortArray(size) { index ->
            val value = sin(2.0 * PI * frequency * index / sampleRate.toDouble()) * amplitude
            (value * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        samples.forEach { sample ->
            val normalized = sample.toDouble() / 32768.0
            sum += normalized * normalized
        }
        return sqrt(sum / samples.size.toDouble())
    }
}

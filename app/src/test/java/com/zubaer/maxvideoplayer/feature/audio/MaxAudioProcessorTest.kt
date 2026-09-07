package com.zubaer.maxvideoplayer.feature.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun boostChangesRealPcmAndLimiterKeepsLegalRange() {
        val low = ShortArray(1024) { 2_000 }
        val boosted = process16(low, 1, AudioDspParameters(boostDb = 6f, revision = 2L))
        assertTrue(abs(boosted[100].toInt()) > abs(low[100].toInt()))

        val nearFull = ShortArray(1024) { 30_000 }
        val limited = process16(nearFull, 1, AudioDspParameters(preampDb = 12f, boostDb = 12f, revision = 3L))
        assertTrue(limited.all { it.toInt() in -32768..32767 })
        assertTrue(limited.maxOf { abs(it.toInt()) } <= 32767)
    }

    @Test
    fun monoAndLeftRightModesPerformRealChannelMapping() {
        val stereo = shortArrayOf(12_000, -4_000, 8_000, 2_000)
        val mono = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.MONO, revision = 4L))
        assertTrue(abs(mono[0].toInt() - mono[1].toInt()) <= 1)
        assertTrue(abs(mono[2].toInt() - mono[3].toInt()) <= 1)

        val left = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.LEFT, revision = 5L))
        assertTrue(abs(left[0].toInt() - left[1].toInt()) <= 1)
        assertTrue(abs(left[2].toInt() - left[3].toInt()) <= 1)

        val right = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.RIGHT, revision = 6L))
        assertTrue(abs(right[0].toInt() - right[1].toInt()) <= 1)
        assertTrue(abs(right[2].toInt() - right[3].toInt()) <= 1)
    }

    @Test
    fun positiveAndNegativeDelayHaveDeterministicSampleSemantics() {
        val sampleRate = 1_000
        val frames = ShortArray(200) { (it + 1).toShort() }
        val positive = process16(frames, 1, AudioDspParameters(delayMs = 50L, revision = 7L), sampleRate)
        assertEquals(200, positive.size)
        assertTrue(positive.take(50).all { it.toInt() == 0 })
        assertEquals(frames[0], positive[50])

        val negative = process16(frames, 1, AudioDspParameters(delayMs = -50L, revision = 8L), sampleRate)
        assertEquals(150, negative.size)
        assertEquals(frames[50], negative[0])
    }

    @Test
    fun tenBandEqBoostsTargetFrequencyMoreThanDistantFrequency() {
        val bands = MutableList(10) { 0f }.also { it[5] = 6f }
        val params = AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 9L)
        val oneKhz = sine(1_000.0, 48_000, 1.0, 0.04)
        val eightKhz = sine(8_000.0, 48_000, 1.0, 0.04)
        val out1k = process16(oneKhz, 1, params, 48_000)
        val out8k = process16(eightKhz, 1, params.copy(revision = 10L), 48_000)
        val inputRms = rms(oneKhz.copyOfRange(24_000, 48_000))
        val targetRms = rms(out1k.copyOfRange(24_000, out1k.size))
        val distantRms = rms(out8k.copyOfRange(24_000, out8k.size))
        assertTrue("Target band must produce real gain", targetRms > inputRms * 1.45)
        assertTrue("EQ must not be disguised global gain", targetRms > distantRms * 1.25)
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
                revision = 11L,
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
        val source = AtomicReference(params)
        val processor = MaxAudioProcessor(source)
        val format = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
        processor.configure(format)
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

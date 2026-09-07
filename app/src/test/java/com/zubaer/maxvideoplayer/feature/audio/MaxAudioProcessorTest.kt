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
import kotlin.math.log10
import kotlin.math.pow
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
    fun enabledFlatEqRemainsTransparentWithinNumericalTolerance() {
        val samples = sineFloat(997.0, 48_000, 0.5, 0.2)
        val output = processFloat(
            samples,
            channels = 1,
            params = AudioDspParameters(
                equalizerEnabled = true,
                equalizerBandsDb = List(10) { 0f },
                revision = 3L,
            ),
        )
        assertArrayEquals(samples, output, 1e-6f)
    }

    @Test
    fun eqFrequencyResponseMatchesKnownSixDbGainAt62Hz1kHzAnd8kHz() {
        val cases = listOf(1 to 62.0, 5 to 1_000.0, 8 to 8_000.0)
        cases.forEachIndexed { caseIndex, (bandIndex, frequency) ->
            val bands = MutableList(10) { 0f }.also { it[bandIndex] = 6f }
            val input = sineFloat(frequency, 48_000, 2.0, 0.04)
            val output = processFloat(
                input,
                1,
                AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 10L + caseIndex),
                48_000,
            )
            val settle = 48_000
            val measuredDb = gainDb(
                rmsFloat(input.copyOfRange(settle, input.size)),
                rmsFloat(output.copyOfRange(settle, output.size)),
            )
            assertTrue(
                "$frequency Hz +6 dB band measured $measuredDb dB",
                measuredDb in 5.0..7.0,
            )
        }
    }

    @Test
    fun tenBandEqBoostsTargetFrequencyMoreThanDistantFrequency() {
        val bands = MutableList(10) { 0f }.also { it[5] = 6f }
        val params = AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 20L)
        val oneKhz = sine(1_000.0, 48_000, 1.0, 0.04)
        val eightKhz = sine(8_000.0, 48_000, 1.0, 0.04)
        val out1k = process16(oneKhz, 1, params, 48_000)
        val out8k = process16(eightKhz, 1, params.copy(revision = 21L), 48_000)
        val inputRms = rms(oneKhz.copyOfRange(24_000, 48_000))
        val targetRms = rms(out1k.copyOfRange(24_000, out1k.size))
        val distantRms = rms(out8k.copyOfRange(24_000, out8k.size))
        assertTrue("Target band must produce real gain", targetRms > inputRms * 1.45)
        assertTrue("EQ must not be disguised global gain", targetRms > distantRms * 1.25)
    }

    @Test
    fun nyquistUnsafeBandIsSafelyBypassed() {
        val input = sineFloat(1_000.0, 32_000, 0.5, 0.1)
        val bands = MutableList(10) { 0f }.also { it[9] = 12f }
        val output = processFloat(
            input,
            1,
            AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 22L),
            32_000,
        )
        assertArrayEquals("16 kHz band must not create an unstable Nyquist filter at 32 kHz", input, output, 1e-6f)
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
    fun preampMinusSixZeroAndPlusSixDbMatchExpectedLinearGainBeforeLimiter() {
        val input = sineFloat(440.0, 48_000, 0.5, 0.05)
        val inputRms = rmsFloat(input)
        val expectedPlus = 10.0.pow(6.0 / 20.0)
        val expectedMinus = 10.0.pow(-6.0 / 20.0)

        val zero = processFloat(input, 1, AudioDspParameters(preampDb = 0f, revision = 30L))
        assertArrayEquals(input, zero, 0f)

        val plus = processFloat(input, 1, AudioDspParameters(preampDb = 6f, revision = 31L))
        val minus = processFloat(input, 1, AudioDspParameters(preampDb = -6f, revision = 32L))
        assertEquals(expectedPlus, rmsFloat(plus) / inputRms, 0.05)
        assertEquals(expectedMinus, rmsFloat(minus) / inputRms, 0.03)
    }

    @Test
    fun boostChangesRealPcmAndLimiterKeepsIntegerAndFloatFormatsLegal() {
        val low = ShortArray(1024) { 2_000 }
        val boosted = process16(low, 1, AudioDspParameters(boostDb = 6f, revision = 40L))
        assertTrue(abs(boosted[100].toInt()) > abs(low[100].toInt()))

        val nearFull = ShortArray(1024) { 30_000 }
        val limited16 = process16(nearFull, 1, AudioDspParameters(preampDb = 12f, boostDb = 12f, revision = 41L))
        assertTrue(limited16.all { it.toInt() in -32768..32767 })
        assertTrue(limited16.maxOf { abs(it.toInt()) } <= 32767)

        val nearFullFloat = FloatArray(1024) { if (it % 2 == 0) 0.95f else -0.95f }
        val limitedFloat = processFloat(
            nearFullFloat,
            1,
            AudioDspParameters(preampDb = 12f, boostDb = 12f, revision = 42L),
        )
        assertTrue(limitedFloat.all { it.isFinite() && abs(it) <= 1f })
    }

    @Test
    fun invalidFloatInputCannotEscapeAsNanOrInfinityWhenDspIsActive() {
        val input = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.25f, -0.25f)
        val output = processFloat(input, 1, AudioDspParameters(boostDb = 0.1f, revision = 43L))
        assertTrue(output.all { it.isFinite() && abs(it) <= 1f })
        assertEquals(0f, output[0], 0f)
        assertEquals(0f, output[1], 0f)
        assertEquals(0f, output[2], 0f)
    }

    @Test
    fun balanceAttenuatesStereoSidesAccordingToEqualPowerPolicy() {
        val stereo = floatArrayOf(0.5f, 0.5f, -0.5f, -0.5f)
        val fullLeft = processFloat(stereo, 2, AudioDspParameters(balance = -1f, revision = 50L))
        assertTrue(abs(fullLeft[0]) > 0.49f)
        assertTrue(abs(fullLeft[1]) < 0.0001f)
        val fullRight = processFloat(stereo, 2, AudioDspParameters(balance = 1f, revision = 51L))
        assertTrue(abs(fullRight[0]) < 0.0001f)
        assertTrue(abs(fullRight[1]) > 0.49f)

        val halfRight = processFloat(floatArrayOf(0.5f, 0.5f), 2, AudioDspParameters(balance = 0.5f, revision = 52L))
        val expectedLeft = 0.5f * sqrt(0.5f)
        assertEquals(expectedLeft, halfRight[0], 0.0001f)
        assertEquals(0.5f, halfRight[1], 0.0001f)
    }

    @Test
    fun monoLeftAndRightModesPerformRealStereoMappingWithoutChannelInversion() {
        val stereo = shortArrayOf(12_000, -4_000, 8_000, 2_000)
        val mono = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.MONO, revision = 60L))
        assertTrue(abs(mono[0].toInt() - mono[1].toInt()) <= 1)
        assertTrue(abs(mono[2].toInt() - mono[3].toInt()) <= 1)

        val left = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.LEFT, revision = 61L))
        assertTrue(left[0] > 0 && left[1] > 0)
        assertTrue(left[2] > 0 && left[3] > 0)
        assertTrue(abs(left[0].toInt() - left[1].toInt()) <= 1)
        assertTrue(abs(left[2].toInt() - left[3].toInt()) <= 1)

        val right = process16(stereo, 2, AudioDspParameters(channelMode = AudioChannelMode.RIGHT, revision = 62L))
        assertTrue(right[0] < 0 && right[1] < 0)
        assertTrue(right[2] > 0 && right[3] > 0)
        assertTrue(abs(right[0].toInt() - right[1].toInt()) <= 1)
        assertTrue(abs(right[2].toInt() - right[3].toInt()) <= 1)
    }

    @Test
    fun multichannelLayoutIsPreservedWhenStereoOnlyControlsAreSet() {
        val frame = floatArrayOf(0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f)
        val output = processFloat(
            frame,
            channels = 6,
            params = AudioDspParameters(channelMode = AudioChannelMode.LEFT, balance = -1f, revision = 70L),
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
    fun zeroAudioDelayDoesNotAddOrRemoveSamples() {
        val input = sineFloat(500.0, 48_000, 0.1, 0.1)
        val output = processFloat(
            input,
            1,
            AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = List(10) { 0f }, delayMs = 0L, revision = 80L),
        )
        assertEquals(input.size, output.size)
        assertArrayEquals(input, output, 1e-6f)
    }

    @Test
    fun positiveFiveHundredMsDelayAt48kStereoHasExactLeadingSilenceAndOffset() {
        val frames = 30_000
        val stereo = ShortArray(frames * 2) { index -> ((index / 2) % 10_000 + 1).toShort() }
        val output = process16(stereo, 2, AudioDspParameters(delayMs = 500L, revision = 81L), 48_000)
        val delayedFrames = 24_000
        assertEquals(stereo.size, output.size)
        assertTrue(output.take(delayedFrames * 2).all { it.toInt() == 0 })
        assertEquals(stereo[0], output[delayedFrames * 2])
        assertEquals(stereo[1], output[delayedFrames * 2 + 1])
    }

    @Test
    fun negativeFiveHundredMsDelayAt48kStereoTrimsExactFramesWithoutUnderflow() {
        val frames = 30_000
        val stereo = ShortArray(frames * 2) { index -> ((index / 2) % 10_000 + 1).toShort() }
        val output = process16(stereo, 2, AudioDspParameters(delayMs = -500L, revision = 82L), 48_000)
        val skippedFrames = 24_000
        assertEquals((frames - skippedFrames) * 2, output.size)
        assertEquals(stereo[skippedFrames * 2], output[0])
        assertEquals(stereo[skippedFrames * 2 + 1], output[1])
    }

    @Test
    fun audioDelayBoundsClampEnormousPositiveAndNegativeValues() {
        assertEquals(AudioPolicy.MAX_DELAY_MS, AudioPolicy.clampDelay(Long.MAX_VALUE))
        assertEquals(AudioPolicy.MIN_DELAY_MS, AudioPolicy.clampDelay(Long.MIN_VALUE))
        assertEquals(AudioPolicy.MAX_DELAY_MS, AudioPolicy.effectiveDelay(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(AudioPolicy.MIN_DELAY_MS, AudioPolicy.effectiveDelay(Long.MIN_VALUE, Long.MIN_VALUE))
    }

    @Test
    fun delayBufferFlushDropsAllPreSeekSamples() {
        val params = AtomicReference(AudioDspParameters(delayMs = 2L, revision = 90L))
        val processor = MaxAudioProcessor(params)
        processor.configure(AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()

        val first = process16Chunk(processor, shortArrayOf(11, 12, 13, 14))
        assertArrayEquals(shortArrayOf(0, 0, 11, 12), first)

        processor.flush()
        val afterSeek = process16Chunk(processor, shortArrayOf(21, 22, 23, 24))
        assertArrayEquals("flush/seek must not emit delayed pre-seek samples", shortArrayOf(0, 0, 21, 22), afterSeek)
        processor.reset()
    }

    @Test
    fun eqAndDspRemainStableAt44100_48000And96000Hz() {
        val sampleRates = intArrayOf(44_100, 48_000, 96_000)
        sampleRates.forEachIndexed { index, rate ->
            val bands = MutableList(10) { 0f }.also { it[5] = 6f }
            val input = sineFloat(1_000.0, rate, 0.25, 0.05)
            val output = processFloat(
                input,
                1,
                AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 100L + index),
                rate,
            )
            assertEquals(input.size, output.size)
            assertTrue("DSP output must be finite at $rate Hz", output.all { it.isFinite() && abs(it) <= 1f })
            assertTrue("1 kHz EQ must increase signal at $rate Hz", rmsFloat(output) > rmsFloat(input))
        }
    }

    @Test
    fun filterHistoryResetsOnFlushAndMatchesFreshProcessorState() {
        val bands = MutableList(10) { 0f }.also { it[5] = 12f }
        val params = AtomicReference(AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, revision = 110L))
        val processor = MaxAudioProcessor(params)
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        processFloatChunk(processor, sineFloat(1_000.0, 48_000, 0.1, 0.1))
        processor.flush()
        val probe = sineFloat(1_000.0, 48_000, 0.02, 0.1)
        val postFlush = processFloatChunk(processor, probe)
        processor.reset()

        val fresh = processFloat(probe, 1, params.get(), 48_000)
        assertArrayEquals("flush must reset EQ/filter history", fresh, postFlush, 1e-6f)
    }

    @Test
    fun realtimeRevisionChangesAreAppliedWithoutRecreatingProcessorAndWithoutExtremeBoundaryJump() {
        val source = AtomicReference(AudioDspParameters(revision = 120L))
        val processor = MaxAudioProcessor(source)
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val firstInput = sineFloat(440.0, 48_000, 0.05, 0.1)
        val first = processFloatChunk(processor, firstInput)
        source.set(AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = AudioPolicy.preset(EqualizerPreset.VOCAL), revision = 121L))
        val secondInput = sineFloat(440.0, 48_000, 0.05, 0.1, phaseOffsetSamples = firstInput.size)
        val second = processFloatChunk(processor, secondInput)

        assertTrue(second.all { it.isFinite() && abs(it) <= 1f })
        val boundaryJump = abs(second.first() - first.last())
        assertTrue("live DSP change created an extreme discontinuity: $boundaryJump", boundaryJump < 0.5f)
        processor.reset()
    }

    @Test
    fun activeDspDoesNotIntroduceMaterialDcOffsetOrUnboundedClipping() {
        val input = sineFloat(997.0, 48_000, 2.0, 0.2)
        val bands = MutableList(10) { 0f }.also { it[5] = 6f }
        val output = processFloat(
            input,
            1,
            AudioDspParameters(equalizerEnabled = true, equalizerBandsDb = bands, preampDb = 3f, revision = 130L),
            48_000,
        )
        val settled = output.copyOfRange(48_000, output.size)
        assertTrue(settled.all { it.isFinite() && abs(it) <= 1f })
        assertTrue("DSP introduced material DC offset", abs(mean(settled)) < 0.002f)
    }

    @Test
    fun extremeLegalSettingsRemainFiniteBoundedAndStableForLongStreamingFixture() {
        val bands = List(10) { 12f }
        val source = AtomicReference(
            AudioDspParameters(
                equalizerEnabled = true,
                equalizerBandsDb = bands,
                preampDb = 12f,
                boostDb = 12f,
                revision = 140L,
            ),
        )
        val processor = MaxAudioProcessor(source)
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val chunkFrames = 1_024
        val totalFrames = 48_000 * 30
        var offset = 0
        while (offset < totalFrames) {
            val count = minOf(chunkFrames, totalFrames - offset)
            val chunk = FloatArray(count) { i ->
                (sin(2.0 * PI * 997.0 * (offset + i) / 48_000.0) * 0.8).toFloat()
            }
            val output = processFloatChunk(processor, chunk)
            assertEquals(chunk.size, output.size)
            assertTrue(output.all { it.isFinite() && abs(it) <= 1f })
            offset += count
        }
        processor.reset()
    }

    @Test
    fun unsupportedPcmFormatIsTruthfullyRejectedInsteadOfReinterpreted() {
        var available = true
        var reason: String? = null
        val processor = MaxAudioProcessor(AtomicReference(AudioDspParameters())) { ok, why ->
            available = ok
            reason = why
        }
        val configured = processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, configured)
        assertFalse(available)
        assertTrue(!reason.isNullOrBlank())
        processor.reset()
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
        val result = process16Chunk(processor, samples)
        processor.reset()
        return result
    }

    private fun process16Chunk(processor: MaxAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        val result = ShortArray(output.remaining() / 2)
        for (i in result.indices) result[i] = output.short
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

    private fun sineFloat(
        frequency: Double,
        sampleRate: Int,
        seconds: Double,
        amplitude: Double,
        phaseOffsetSamples: Int = 0,
    ): FloatArray {
        val size = (sampleRate * seconds).toInt()
        return FloatArray(size) { index ->
            (sin(2.0 * PI * frequency * (phaseOffsetSamples + index) / sampleRate.toDouble()) * amplitude).toFloat()
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

    private fun rmsFloat(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        samples.forEach { sample -> sum += sample.toDouble() * sample.toDouble() }
        return sqrt(sum / samples.size.toDouble())
    }

    private fun gainDb(inputRms: Double, outputRms: Double): Double = 20.0 * log10(outputRms / inputRms)

    private fun mean(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        samples.forEach { sum += it.toDouble() }
        return (sum / samples.size).toFloat()
    }
}

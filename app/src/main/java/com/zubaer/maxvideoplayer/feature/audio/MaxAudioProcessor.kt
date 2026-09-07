package com.zubaer.maxvideoplayer.feature.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** App-owned deterministic PCM DSP. No I/O, Room, coroutines or UI work occurs in queueInput. */
class MaxAudioProcessor(
    private val repository: AudioRepository,
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var seenRevision = Long.MIN_VALUE
    private var params = AudioDspParameters()
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var frame = FloatArray(0)
    private var delayRing = FloatArray(0)
    private var delayWrite = 0
    private var delayFilled = 0
    private var negativeFramesToSkip = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            repository.setDspAvailability(false, "Audio processing unavailable for this PCM/output format")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        repository.setDspAvailability(true)
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        frame = FloatArray(channels.coerceAtLeast(1))
        filters = Array(channels.coerceAtLeast(1)) { Array(EQ_FREQUENCIES_HZ.size) { Biquad() } }
        seenRevision = Long.MIN_VALUE
        applyLatestParameters(force = true)
        filters.forEach { row -> row.forEach(Biquad::reset) }
        resetDelayState()
    }

    override fun onReset() {
        filters = emptyArray()
        frame = FloatArray(0)
        delayRing = FloatArray(0)
        delayWrite = 0
        delayFilled = 0
        negativeFramesToSkip = 0L
        seenRevision = Long.MIN_VALUE
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        applyLatestParameters(force = false)

        if (params.neutral()) {
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frameBytes = channels * bytesPerSample
        if (frameBytes <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val completeFrames = inputBuffer.remaining() / frameBytes
        val maxOutputBytes = completeFrames * frameBytes
        val output = replaceOutputBuffer(maxOutputBytes)
        var frameIndex = 0
        while (frameIndex < completeFrames) {
            for (ch in 0 until channels) frame[ch] = readSample(inputBuffer)
            processFrame(frame)

            if (negativeFramesToSkip > 0L) {
                negativeFramesToSkip--
            } else if (delayRing.isEmpty()) {
                for (ch in 0 until channels) writeSample(output, frame[ch])
            } else {
                for (ch in 0 until channels) {
                    val sample = frame[ch]
                    val delayed = if (delayFilled < delayRing.size) 0f else delayRing[delayWrite]
                    delayRing[delayWrite] = sample
                    delayWrite++
                    if (delayWrite == delayRing.size) delayWrite = 0
                    if (delayFilled < delayRing.size) delayFilled++
                    writeSample(output, delayed)
                }
            }
            frameIndex++
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onQueueEndOfStream() {
        if (delayRing.isEmpty() || delayFilled == 0) return
        val bytes = delayFilled * bytesPerSample
        val output = replaceOutputBuffer(bytes)
        val count = delayFilled
        val start = if (delayFilled == delayRing.size) delayWrite else 0
        for (i in 0 until count) {
            val index = (start + i) % delayRing.size
            writeSample(output, delayRing[index])
        }
        output.flip()
        delayFilled = 0
        delayWrite = 0
    }

    private fun applyLatestParameters(force: Boolean) {
        val latest = repository.realtimeParameters.get()
        if (!force && latest.revision == seenRevision) return
        val delayChanged = latest.delayMs != params.delayMs
        params = latest.copy(
            equalizerBandsDb = latest.equalizerBandsDb.take(10).let { bands ->
                if (bands.size == 10) bands else List(10) { 0f }
            },
        )
        seenRevision = latest.revision
        updateFilters()
        if (force || delayChanged) resetDelayState()
    }

    private fun updateFilters() {
        if (filters.isEmpty() || sampleRate <= 0) return
        filters.forEach { row ->
            row.forEachIndexed { index, filter ->
                val frequency = EQ_FREQUENCIES_HZ[index].toDouble()
                val enabled = params.equalizerEnabled && frequency < sampleRate * 0.49
                filter.setTarget(
                    sampleRate = sampleRate.toDouble(),
                    frequency = frequency,
                    gainDb = if (enabled) params.equalizerBandsDb[index].toDouble() else 0.0,
                    q = 1.0,
                )
            }
        }
    }

    private fun resetDelayState() {
        delayWrite = 0
        delayFilled = 0
        negativeFramesToSkip = 0L
        val delay = AudioPolicy.clampDelay(params.delayMs)
        if (delay == 0L || channels <= 0 || sampleRate <= 0) {
            delayRing = FloatArray(0)
            return
        }
        val delayFrames = ((abs(delay).toDouble() * sampleRate.toDouble()) / 1000.0).roundToInt().coerceAtLeast(0)
        if (delay < 0L) {
            negativeFramesToSkip = delayFrames.toLong()
            delayRing = FloatArray(0)
            return
        }
        val sampleCountLong = delayFrames.toLong() * channels.toLong()
        val byteCountLong = sampleCountLong * 4L
        if (byteCountLong > MAX_DELAY_BUFFER_BYTES || sampleCountLong > Int.MAX_VALUE) {
            delayRing = FloatArray(0)
            repository.setDspAvailability(false, "Audio delay unavailable for this high-rate/channel format")
            return
        }
        delayRing = FloatArray(sampleCountLong.toInt())
    }

    private fun processFrame(samples: FloatArray) {
        if (channels == 2) {
            val left = samples[0]
            val right = samples[1]
            when (params.channelMode) {
                AudioChannelMode.STEREO -> Unit
                AudioChannelMode.MONO -> {
                    val mono = (left + right) * 0.5f
                    samples[0] = mono
                    samples[1] = mono
                }
                AudioChannelMode.LEFT -> { samples[0] = left; samples[1] = left }
                AudioChannelMode.RIGHT -> { samples[0] = right; samples[1] = right }
            }
            val balance = AudioPolicy.clampBalance(params.balance)
            val leftGain = sqrt((1f - balance).coerceIn(0f, 1f))
            val rightGain = sqrt((1f + balance).coerceIn(0f, 1f))
            samples[0] *= leftGain
            samples[1] *= rightGain
        }

        val globalGain = params.linearGain()
        for (ch in samples.indices) {
            var value = samples[ch]
            if (params.equalizerEnabled) {
                for (band in EQ_FREQUENCIES_HZ.indices) value = filters[ch][band].process(value)
            }
            value *= globalGain
            samples[ch] = softLimit(value)
        }
    }

    private fun readSample(buffer: ByteBuffer): Float = if (encoding == C.ENCODING_PCM_FLOAT) {
        buffer.float.let { if (it.isFinite()) it.coerceIn(-4f, 4f) else 0f }
    } else {
        buffer.short.toFloat() / 32768f
    }

    private fun writeSample(buffer: ByteBuffer, sample: Float) {
        val safe = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        if (encoding == C.ENCODING_PCM_FLOAT) {
            buffer.putFloat(safe)
        } else {
            buffer.putShort((safe * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }

    private fun softLimit(value: Float): Float {
        if (!value.isFinite()) return 0f
        val magnitude = abs(value)
        if (magnitude <= LIMITER_THRESHOLD) return value
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val limited = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * (1f - exp(-excess))
        return if (value < 0f) -limited.coerceAtMost(1f) else limited.coerceAtMost(1f)
    }

    private class Biquad {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var tb0 = 1.0; private var tb1 = 0.0; private var tb2 = 0.0; private var ta1 = 0.0; private var ta2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        private var ramp = 0

        fun setTarget(sampleRate: Double, frequency: Double, gainDb: Double, q: Double) {
            if (frequency <= 0.0 || frequency >= sampleRate * 0.5 || abs(gainDb) < 1e-6) {
                setTargetCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * frequency / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val c = cos(w0)
            val rawB0 = 1.0 + alpha * a
            val rawB1 = -2.0 * c
            val rawB2 = 1.0 - alpha * a
            val rawA0 = 1.0 + alpha / a
            val rawA1 = -2.0 * c
            val rawA2 = 1.0 - alpha / a
            setTargetCoefficients(rawB0 / rawA0, rawB1 / rawA0, rawB2 / rawA0, rawA1 / rawA0, rawA2 / rawA0)
        }

        private fun setTargetCoefficients(nb0: Double, nb1: Double, nb2: Double, na1: Double, na2: Double) {
            tb0 = nb0; tb1 = nb1; tb2 = nb2; ta1 = na1; ta2 = na2
            ramp = COEFFICIENT_RAMP_SAMPLES
        }

        fun process(input: Float): Float {
            if (ramp > 0) {
                val factor = 1.0 / ramp.toDouble()
                b0 += (tb0 - b0) * factor; b1 += (tb1 - b1) * factor; b2 += (tb2 - b2) * factor
                a1 += (ta1 - a1) * factor; a2 += (ta2 - a2) * factor
                ramp--
            }
            val x = input.toDouble()
            var y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            if (!y.isFinite() || abs(y) > 1e6) { reset(); y = 0.0 }
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y.toFloat()
        }

        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
    }

    companion object {
        private const val LIMITER_THRESHOLD = 0.90f
        private const val COEFFICIENT_RAMP_SAMPLES = 256
        private const val MAX_DELAY_BUFFER_BYTES = 40L * 1024L * 1024L
    }
}

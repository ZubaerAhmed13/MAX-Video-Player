package com.zubaer.maxvideoplayer.feature.decoder

import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.feature.decoder.capability.CodecClassifier
import com.zubaer.maxvideoplayer.feature.decoder.model.CodecProfileLevel
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderCandidate
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderSupportStatus
import com.zubaer.maxvideoplayer.feature.decoder.model.VideoDecoderRequirement
import com.zubaer.maxvideoplayer.feature.decoder.selection.DecoderSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderSelectionPolicyTest {
    private val hardwareA = candidate("HardwareA", DecoderBackendType.HARDWARE, priority = 0)
    private val hardwareB = candidate("HardwareB", DecoderBackendType.HARDWARE, priority = 1)
    private val softwareA = candidate("SoftwareA", DecoderBackendType.SOFTWARE, priority = 2)

    @Test
    fun hardwareReturnsOnlyPreferredHardwareCandidate() {
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.HARDWARE,
            listOf(hardwareB, softwareA, hardwareA),
        )
        assertEquals(listOf("HardwareA"), decision.candidates.map { it.name })
        assertTrue(decision.candidates.all { it.backend == DecoderBackendType.HARDWARE })
    }

    @Test
    fun enhancedHardwareReturnsHardwareChainAndNeverSoftware() {
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.ENHANCED_HARDWARE,
            listOf(hardwareB, softwareA, hardwareA),
        )
        assertEquals(listOf("HardwareA", "HardwareB"), decision.candidates.map { it.name })
        assertTrue(decision.candidates.all { it.backend == DecoderBackendType.HARDWARE })
        assertFalse(decision.candidates.any { it.backend == DecoderBackendType.SOFTWARE })
    }

    @Test
    fun softwareReturnsSoftwareOnly() {
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.SOFTWARE,
            listOf(hardwareA, softwareA, hardwareB),
        )
        assertEquals(listOf("SoftwareA"), decision.candidates.map { it.name })
        assertTrue(decision.candidates.all { it.backend == DecoderBackendType.SOFTWARE })
    }

    @Test
    fun autoOrdersHardwareBeforeSoftware() {
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.AUTO,
            listOf(softwareA, hardwareB, hardwareA),
        )
        assertEquals(listOf("HardwareA", "HardwareB", "SoftwareA"), decision.candidates.map { it.name })
    }

    @Test
    fun sessionRejectedCandidateIsNotRetriedAndLoopTerminates() {
        val first = DecoderSelectionPolicy.decide(
            DecoderMode.AUTO,
            listOf(hardwareA, hardwareB, softwareA),
            sessionRejected = setOf("HardwareA"),
        )
        assertEquals(listOf("HardwareB", "SoftwareA"), first.candidates.map { it.name })

        val exhausted = DecoderSelectionPolicy.decide(
            DecoderMode.AUTO,
            listOf(hardwareA, hardwareB, softwareA),
            sessionRejected = setOf("HardwareA", "HardwareB", "SoftwareA"),
        )
        assertTrue(exhausted.candidates.isEmpty())
    }

    @Test
    fun profileAndLevelCompatibilitySelectsMain10CapableDecoder() {
        val mainOnly = hardwareA.copy(profileLevels = listOf(CodecProfileLevel(profile = 1, level = 120)))
        val main10 = hardwareB.copy(profileLevels = listOf(CodecProfileLevel(profile = 2, level = 150)))
        val requirement = VideoDecoderRequirement(
            mimeType = "video/hevc",
            profile = 2,
            level = 150,
            width = 3840,
            height = 2160,
            frameRate = 30.0,
        )
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.ENHANCED_HARDWARE,
            listOf(mainOnly, main10),
            requirement,
        )
        assertEquals(listOf("HardwareB"), decision.candidates.map { it.name })
    }

    @Test
    fun explicitlyUnsupportedSizeRateIsRejected() {
        val limited = hardwareA.copy(sizeRateSupport = DecoderSupportStatus.UNSUPPORTED)
        val capable = hardwareB.copy(sizeRateSupport = DecoderSupportStatus.SUPPORTED)
        val requirement = VideoDecoderRequirement(
            mimeType = "video/avc",
            width = 3840,
            height = 2160,
            frameRate = 60.0,
        )
        val decision = DecoderSelectionPolicy.decide(
            DecoderMode.ENHANCED_HARDWARE,
            listOf(limited, capable),
            requirement,
        )
        assertEquals(listOf("HardwareB"), decision.candidates.map { it.name })
    }

    @Test
    fun unknownProfileMetadataAllowsControlledAttemptInsteadOfFalseRejection() {
        val unknown = hardwareA.copy(profileLevels = emptyList(), sizeRateSupport = DecoderSupportStatus.UNKNOWN)
        val result = DecoderSelectionPolicy.supports(
            unknown,
            VideoDecoderRequirement("video/avc", profile = 8, level = 512, width = 3840, height = 2160),
        )
        assertEquals(DecoderSupportStatus.UNKNOWN, result)
    }

    @Test
    fun classifierUsesPlatformFlagsAndConservativeLegacyFallback() {
        assertEquals(
            DecoderBackendType.HARDWARE,
            CodecClassifier.classify(hardwareAccelerated = true, softwareOnly = false, codecName = "c2.vendor.avc.decoder"),
        )
        assertEquals(
            DecoderBackendType.SOFTWARE,
            CodecClassifier.classify(hardwareAccelerated = false, softwareOnly = true, codecName = "unexpected.name"),
        )
        assertEquals(
            DecoderBackendType.SOFTWARE,
            CodecClassifier.classify(hardwareAccelerated = null, softwareOnly = null, codecName = "c2.android.avc.decoder"),
        )
        assertEquals(
            DecoderBackendType.UNKNOWN,
            CodecClassifier.classify(hardwareAccelerated = null, softwareOnly = null, codecName = "mystery.decoder"),
        )
    }

    private fun candidate(
        name: String,
        backend: DecoderBackendType,
        priority: Int,
    ): DecoderCandidate = DecoderCandidate(
        name = name,
        mimeType = if (name.contains("HardwareB")) "video/avc" else "video/avc",
        backend = backend,
        hardwareAccelerated = backend == DecoderBackendType.HARDWARE,
        softwareOnly = backend == DecoderBackendType.SOFTWARE,
        vendor = backend == DecoderBackendType.HARDWARE,
        secure = false,
        tunneling = false,
        platformPriority = priority,
    )
}

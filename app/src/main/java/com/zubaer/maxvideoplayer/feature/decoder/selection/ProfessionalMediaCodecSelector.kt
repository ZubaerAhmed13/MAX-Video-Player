package com.zubaer.maxvideoplayer.feature.decoder.selection

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.zubaer.maxvideoplayer.feature.decoder.model.CodecProfileLevel
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderCandidate
import com.zubaer.maxvideoplayer.feature.decoder.runtime.DecoderRepository

class ProfessionalMediaCodecSelector(
    private val repository: DecoderRepository,
    private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT,
) : MediaCodecSelector {
    @Throws(MediaCodecUtil.DecoderQueryException::class)
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val platformCandidates = delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        if (!mimeType.startsWith("video/", ignoreCase = true)) return platformCandidates

        val decision = DecoderSelectionPolicy.decide(
            mode = repository.requestedMode(),
            candidates = platformCandidates.mapIndexed { index, info -> info.toCandidate(index) },
            sessionRejected = repository.rejectedDecoderNames(),
        )
        repository.recordCandidateQuery(decision.candidates)
        if (decision.candidates.isEmpty()) {
            repository.recordNoCompatibleDecoder(repository.requestedMode(), mimeType)
            return emptyList()
        }
        val selectedNames = decision.candidates.mapTo(linkedSetOf()) { it.name }
        return platformCandidates.filter { it.name in selectedNames }
            .sortedBy { selectedNames.indexOf(it.name) }
    }

    private fun MediaCodecInfo.toCandidate(priority: Int): DecoderCandidate = DecoderCandidate(
        name = name,
        mimeType = mimeType,
        backend = when {
            softwareOnly -> DecoderBackendType.SOFTWARE
            hardwareAccelerated -> DecoderBackendType.HARDWARE
            else -> DecoderBackendType.UNKNOWN
        },
        hardwareAccelerated = hardwareAccelerated,
        softwareOnly = softwareOnly,
        vendor = vendor,
        secure = secure,
        tunneling = tunneling,
        adaptive = adaptive,
        profileLevels = getProfileLevels().map { CodecProfileLevel(it.profile, it.level) },
        platformPriority = priority,
    )
}

private fun <T> Set<T>.indexOf(value: T): Int {
    var index = 0
    for (item in this) {
        if (item == value) return index
        index++
    }
    return Int.MAX_VALUE
}

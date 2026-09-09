package com.zubaer.maxvideoplayer.feature.cast

typealias PlaybackTarget = com.zubaer.maxvideoplayer.core.model.PlaybackTarget

enum class CastConnectionState {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    DISCONNECTED,
    FAILED,
}

enum class CastSourceMode {
    DIRECT_CAST,
    LOCAL_RELAY,
    MANIFEST_RELAY,
    UNSUPPORTED_CAST,
}

data class PlaybackTargetState(
    val requestedTarget: PlaybackTarget = PlaybackTarget.LOCAL_DEVICE,
    val effectiveTarget: PlaybackTarget = PlaybackTarget.LOCAL_DEVICE,
    val deviceName: String? = null,
    val castConnectionState: CastConnectionState = CastConnectionState.NOT_CONNECTED,
)

data class OutputDeviceState(
    val videoTarget: PlaybackTarget = PlaybackTarget.LOCAL_DEVICE,
    val displayId: Int? = null,
    val displayName: String? = null,
    val castReceiverName: String? = null,
)

data class CastSourceDecision(
    val mode: CastSourceMode,
    val reason: String,
)

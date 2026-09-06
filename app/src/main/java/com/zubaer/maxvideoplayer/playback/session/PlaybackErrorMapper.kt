package com.zubaer.maxvideoplayer.playback.session

import androidx.media3.common.PlaybackException
import com.zubaer.maxvideoplayer.core.model.PlaybackError

object PlaybackErrorMapper {
    fun map(error: PlaybackException): PlaybackError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.SourceUnavailable
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError.PermissionLost
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackError.MalformedMedia
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackError.UnsupportedFormat
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackError.DecoderInitialization
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackError.UnsupportedDecoder
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlaybackError.Network
        else -> PlaybackError.Failure(error.errorCode)
    }
}

package org.aulama.iptv.mobile.data.playback

import android.media.MediaCodecList

internal object DevicePlaybackCapabilities {
    fun supportsHevc4k(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(HEVC_MIME, true) } }
            .any { info ->
                val capabilities = info.getCapabilitiesForType(HEVC_MIME).videoCapabilities
                capabilities?.isSizeSupported(3840, 2160) == true
            }
    }.getOrDefault(false)

    private const val HEVC_MIME = "video/hevc"
}

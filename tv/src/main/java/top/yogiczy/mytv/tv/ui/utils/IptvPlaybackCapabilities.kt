package top.yogiczy.mytv.tv.ui.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/** 保守判斷裝置能否以硬件持續解碼翡翠台使用嘅 4K HEVC Main10 50fps。 */
object IptvPlaybackCapabilities {
    val supportsSmooth4kHevc: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
                if (codec.isEncoder || !codec.supportedTypes.any {
                        it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                    }
                ) return@any false

                val softwareDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    !codec.isHardwareAccelerated
                } else {
                    val name = codec.name.lowercase()
                    name.startsWith("omx.google.") || name.startsWith("c2.android.") ||
                        name.contains("software") || name.contains("ffmpeg")
                }
                if (softwareDecoder) return@any false

                val capabilities = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                val main10 = capabilities.profileLevels.any { profileLevel ->
                    profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                        profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)
                }
                if (!main10) return@any false

                val video = capabilities.videoCapabilities ?: return@any false
                if (!video.areSizeAndRateSupported(3840, 2160, 50.0)) return@any false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val points = video.supportedPerformancePoints
                    if (!points.isNullOrEmpty()) {
                        return@any points.any { point ->
                            point.covers(MediaCodecInfo.VideoCapabilities.PerformancePoint(3840, 2160, 50))
                        }
                    }
                }

                val achievable = runCatching {
                    video.getAchievableFrameRatesFor(3840, 2160)
                }.getOrNull()
                achievable == null || achievable.upper >= 50.0
            }
        }.getOrDefault(false)
    }
}

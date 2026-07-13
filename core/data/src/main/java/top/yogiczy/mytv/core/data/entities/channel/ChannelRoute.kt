package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable

/** 同一頻道其中一條可播放線路。 */
@Immutable
data class ChannelRoute(
    val url: String,
    val quality: ChannelQuality = ChannelQuality.UNKNOWN,
    val label: String = "自動線路",
    val referrer: String? = null,
    val userAgent: String? = null,
    val sourceOrder: Int = 0,
) {
    val requestHeaders: Map<String, String>
        get() = buildMap {
            referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        }
}

enum class ChannelQuality(
    val rank: Int,
    val label: String,
) {
    UHD_4K(4, "4K"),
    FULL_HD(3, "1080p"),
    HD(2, "720p"),
    SD(1, "標準畫質"),
    UNKNOWN(0, "自動畫質");

    companion object {
        fun detect(vararg values: String?): ChannelQuality {
            val text = values.filterNotNull().joinToString(" ").uppercase()
            return when {
                Regex("(?:^|\\D)(?:2160P?|4K|UHD)(?:\\D|$)").containsMatchIn(text) -> UHD_4K
                Regex("(?:^|\\D)(?:1080P?|FHD)(?:\\D|$)").containsMatchIn(text) -> FULL_HD
                Regex("(?:^|\\D)720P?(?:\\D|$)").containsMatchIn(text) -> HD
                Regex("(?:^|\\D)(?:576P?|480P?|SD)(?:\\D|$)").containsMatchIn(text) -> SD
                else -> UNKNOWN
            }
        }
    }
}

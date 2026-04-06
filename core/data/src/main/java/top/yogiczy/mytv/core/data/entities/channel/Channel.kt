package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicInteger

/**
 * 頻道
 */
@Immutable
data class Channel(
    /**
     * 頻道ID
     */
    val id: String = "0", // 使用序號生成 ID
    /**
     * 頻道名稱
     */
    val name: String = "",

    /**
     * 節目單名稱，用於查詢節目單
     */
    val epgName: String = "",

    /**
     * 播放地址
     */
    val urlList: List<String> = listOf("http://1.2.3.4"),

    /**
     * 台標
     */
    val logo: String? = null,
) {
    companion object {
        val EXAMPLE = Channel(
            id = "1",
            name = "CCTV-1 綜合",
            epgName = "cctv1",
            urlList = listOf(
                "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8",
                "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS",
            ),
            logo = "https://live.fanmingming.com/tv/CCTV1.png"
        )
    }
}
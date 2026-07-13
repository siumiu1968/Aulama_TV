package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable

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

    /** 同一頻道嘅播放線路；介面只顯示一個頻道。 */
    val routes: List<ChannelRoute> = listOf(ChannelRoute("http://1.2.3.4")),

    /**
     * 台標
     */
    val logo: String? = null,
) {
    val urlList: List<String>
        get() = routes.map(ChannelRoute::url)

    companion object {
        val EXAMPLE = Channel(
            id = "1",
            name = "CCTV-1 綜合",
            epgName = "cctv1",
            routes = listOf(
                ChannelRoute("http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8"),
                ChannelRoute("http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS"),
            ),
            logo = "https://live.fanmingming.com/tv/CCTV1.png"
        )
    }
}

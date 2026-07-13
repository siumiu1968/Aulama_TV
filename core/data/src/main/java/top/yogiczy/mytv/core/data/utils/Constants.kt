package top.yogiczy.mytv.core.data.utils

import android.webkit.WebView
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

/**
 * 常量
 */
object Constants {
    /**
     * 應用 標題
     */
    const val APP_TITLE = "Aulama TV"

    /**
     * 應用 代碼倉庫
     */
    const val APP_REPO = "https://github.com/siumiu1968/mytv-tv-origpatch"

    const val DEFAULT_IPTV_SOURCE_URL =
        "https://gist.githubusercontent.com/siumiu1968/b6f1358a2504d228636149de4ca8d5e0/raw/hk_channels_merged_v2.m3u"

    const val MAINLAND_IPTV_SOURCE_URL =
        "https://gist.githubusercontent.com/siumiu1968/b6f1358a2504d228636149de4ca8d5e0/raw/cn_channels.m3u"

    const val INTERNATIONAL_IPTV_SOURCE_URL =
        "https://gist.githubusercontent.com/siumiu1968/b6f1358a2504d228636149de4ca8d5e0/raw/international_channels.m3u"

    const val DEFAULT_IPTV_SOURCE_NAME = "Aulama 三區頻道（預設）"

    val AULAMA_REGION_SOURCE_LIST = listOf(
        "香港" to IptvSource(name = "香港", url = DEFAULT_IPTV_SOURCE_URL),
        "中國" to IptvSource(name = "中國", url = MAINLAND_IPTV_SOURCE_URL),
        "國際" to IptvSource(name = "國際", url = INTERNATIONAL_IPTV_SOURCE_URL),
    )

    fun isAulamaManagedSource(source: IptvSource) =
        AULAMA_REGION_SOURCE_LIST.any { (_, regionSource) -> regionSource.url == source.url }

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://gh.monlor.com/"

    const val WEBVIEW_CHANNELS_URL="https://raw.githubusercontent.com/minyoad/my-iptv/refs/heads/master/list/webview_channels.txt"

    /**
     * IPTV直播源
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = DEFAULT_IPTV_SOURCE_NAME,
                url = DEFAULT_IPTV_SOURCE_URL,
            ),
        )
    )

    /**
     * IPTV源緩存時間（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小時

    /**
     * 節目單來源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默認節目單 my吧",
                url = "https://iptv-cdn.mybacc.com/epg/e.xml.gz",
            ),
            EpgSource(
                name = "默認節目單 老張的EPG",
                url = "http://epg.51zmt.top:8000/e.xml.gz",
            ),
            EpgSource(
                name = "默認節目單 回看七天",
                url = "https://e.erw.cc/all.xml.gz",
            ),
            EpgSource(
                name = "默認節目單 mybacc 備用",
//                url = "https://iptv-cdn.mybacc.com/epg/e.xml.gz",
                url = GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/my-iptv/refs/heads/master/epg/e.xml.gz",
            ),
        )
    )

    /**
     * 頻道logo來源
     */
    const val CHANNEL_LOGO_SOURCE="https://iptv-cdn.mybacc.com/logo/"

    /**
     * 節目單刷新時間閾值（小時）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2點不刷新

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to "https://api.github.com/repos/siumiu1968/mytv-tv-origpatch/releases/latest",
        "beta" to "https://api.github.com/repos/siumiu1968/mytv-tv-origpatch/releases?per_page=20",
    )

    /**
     * HTTP請求重試次數
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP請求重試間隔時間（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 播放器加載超時
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 日誌歷史最大保留條數
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 界面 臨時頻道界面顯示時間
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒

    /**
     * 界面 超時未操作自動關閉界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 時間顯示前後範圍
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前後30秒
}

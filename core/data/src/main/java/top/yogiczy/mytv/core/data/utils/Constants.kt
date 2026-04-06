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
    const val APP_TITLE = "我的電視"

    /**
     * 應用 代碼倉庫
     */
    const val APP_REPO = "https://github.com/minyoad/mytv-android"

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
                name = "香港台精選（預設）",
                url = "https://gist.githubusercontent.com/siumiu1968/b6f1358a2504d228636149de4ca8d5e0/raw/a66692b51dc3ab65003c44b13da245252eba632a/hk_channels_merged_v2.m3u"
            ),
            IptvSource(
                name = "默認直播源 fanmingming（IPV6）",
                url = "https://live.fanmingming.cn/tv/m3u/ipv6.m3u",
            ),
            IptvSource(
                name = "默認直播源 冰茶",
                url = GITHUB_PROXY+"https://raw.githubusercontent.com/ls125781003/tvboxtg/refs/heads/main/%E9%A5%AD%E5%A4%AA%E7%A1%AC/lives/%E5%86%B0%E8%8C%B6.txt",
            ),
            IptvSource(
                name = "默認直播源 yuanzl77（IPV4/IPV6）",
                url = GITHUB_PROXY+"https://raw.githubusercontent.com/yuanzl77/IPTV/main/live.m3u",
            ),
            IptvSource(
                name = "默認直播源-電影列表",
                url = "https://iptv-cdn.mybacc.com/list/movies.txt",
//                url = GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/my-iptv/refs/heads/master/list/movies.txt",
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
        "stable" to GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/mytv-android-update/main/tv-stable.json",
        "beta" to GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/mytv-android-update/main/tv-beta.json",
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

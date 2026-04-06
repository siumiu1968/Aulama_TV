package top.yogiczy.mytv.core.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ChannelUtil {
    private fun standardCctvChannelNameTest(keys: List<List<String>>): (String) -> Boolean {
        return { name: String -> keys.any { it.all { word -> word.lowercase() in name.lowercase() } } }
    }

    private val standardChannelNameTest: Map<String, (String) -> Boolean> = mapOf(
        "CCTV-5+賽事" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "5+"),
                listOf("cctv", "5plus"),
                listOf("cctv", "體育"),
                listOf("中央", "5+"),
                listOf("中央", "五+"),
            )
        ),
        "CCTV-10科教" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "10"),
                listOf("cctv", "科教"),
                listOf("中央", "10"),
                listOf("中央", "十"),
            )
        ),
        "CCTV-11戲曲" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "11"),
                listOf("cctv", "戲曲"),
                listOf("中央", "11"),
                listOf("中央", "十一"),
            )
        ),
        "CCTV-12社法" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "12"),
                listOf("cctv", "社法"),
                listOf("cctv", "法治"),
                listOf("cctv", "法制"),
                listOf("cctv", "社會與法"),
                listOf("中央", "12"),
                listOf("中央", "十二"),
            )
        ),
        "CCTV-13新聞" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "13"),
                listOf("cctv", "新聞"),
                listOf("中央", "13"),
                listOf("中央", "十三"),
            )
        ),
        "CCTV-14少兒" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "14"),
                listOf("cctv", "少兒"),
                listOf("中央", "14"),
                listOf("中央", "十四"),
                listOf("中央", "少兒"),
                listOf("中央", "少兒"),
            )
        ),
        "CCTV-15音樂" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "15"),
                listOf("cctv", "音樂"),
                listOf("中央", "15"),
                listOf("中央", "十五"),
            )
        ),
        "CCTV-16奧匹" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "16"),
                listOf("cctv", "奧林匹克"),
                listOf("中央", "16"),
                listOf("中央", "十六"),
            )
        ),
        "CCTV-17農村" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "17"),
                listOf("cctv", "農村"),
                listOf("cctv", "農業"),
                listOf("中央", "17"),
                listOf("中央", "十七"),
            )
        ),
        "CCTV-1綜合" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "1"),
                listOf("cctv", "綜合"),
                listOf("中央", "1"),
                listOf("中央", "一"),
            )
        ),
        "CCTV-2財經" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "2"),
                listOf("cctv", "財經"),
                listOf("中央", "2"),
                listOf("中央", "二"),
            )
        ),
        "CCTV-3綜藝" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "3"),
                listOf("cctv", "綜藝"),
                listOf("中央", "3"),
                listOf("中央", "三"),
            )
        ),
        "CCTV-4國際" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "4"),
                listOf("cctv", "國際"),
                listOf("中央", "4"),
                listOf("中央", "四"),
            )
        ),
        "CCTV-5體育" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "5"),
                listOf("cctv", "體育"),
                listOf("中央", "5"),
                listOf("中央", "五"),
            )
        ),
        "CCTV-6電影" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "6"),
                listOf("cctv", "電影"),
                listOf("中央", "6"),
                listOf("中央", "六"),
            )
        ),
        "CCTV-7軍事" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "7"),
                listOf("cctv", "軍事"),
                listOf("cctv", "國防"),
                listOf("cctv", "軍農"),
                listOf("中央", "7"),
                listOf("中央", "七"),
            )
        ),
        "CCTV-8電視" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "8"),
                listOf("cctv", "電視"),
                listOf("中央", "8"),
                listOf("中央", "八"),
            )
        ),
        "CCTV-9紀錄" to standardCctvChannelNameTest(
            listOf(
                listOf("cctv", "9"),
                listOf("cctv", "紀錄"),
                listOf("中央", "9"),
                listOf("中央", "九"),
            )
        ),
        "上海衞視" to { name: String ->
            name.contains("上海衞視")
                    || name.contains("東方衞視")
                    || name.contains("上海台")
                    || name.contains("上海東方衞視")
        },
        "福建衞視" to { name: String ->
            name.contains("福建衞視")
                    || name.contains("福建東南衞視")
                    || name.contains("東南衞視")
        },
    )

    // 1. 將原先的 hybridWebViewUrl 改名為 defaultHybridWebViewUrl 作為兜底默認值
    private val defaultHybridWebViewUrl = mapOf(
        "CCTV-1綜合" to listOf(
            "https://tv.cctv.com/live/cctv1/",
            "https://yangshipin.cn/tv/home?pid=600001859",
            "https://v.lib.tju.edu.cn/tv-show-detail/3",
            "https://app.hfbtc.cn/shows/2/6.html",
            "https://m-live.cctvnews.cctv.com/live/landscape.html?liveRoomNumber=11200132825562653886"
        ),
        "CCTV-2財經" to listOf(
            "https://tv.cctv.com/live/cctv2/",
            "https://yangshipin.cn/tv/home?pid=600001800",
        ),
        "CCTV-3綜藝" to listOf(
            "https://tv.cctv.com/live/cctv3/",
            "http://m.miguvideo.com/m/liveDetail/624878271?channelId=10010001005"
        ),
        "CCTV-4國際" to listOf(
            "https://tv.cctv.com/live/cctv4/",
            "https://yangshipin.cn/tv/home?pid=600001814",
        ),
        "CCTV-5體育" to listOf(
            "https://tv.cctv.com/live/cctv5/",
            "https://yangshipin.cn/tv/home?pid=600001818",
        ),
        "CCTV-5+賽事" to listOf(
            "https://tv.cctv.com/live/cctv5plus/",
            "https://yangshipin.cn/tv/home?pid=600001817",
        ),
        "CCTV-6電影" to listOf(
            "https://tv.cctv.com/live/cctv6/",
        ),
        "CCTV-7軍事" to listOf(
            "https://tv.cctv.com/live/cctv7/",
            "https://yangshipin.cn/tv/home?pid=600004092",
        ),
        "CCTV-8電視" to listOf(
            "https://tv.cctv.com/live/cctv8/",
        ),
        "CCTV-9紀錄" to listOf(
            "https://tv.cctv.com/live/cctvjilu/",
            "https://yangshipin.cn/tv/home?pid=600004078",
        ),
        "CCTV-10科教" to listOf(
            "https://tv.cctv.com/live/cctv10/",
            "https://yangshipin.cn/tv/home?pid=600001805",
        ),
        "CCTV-11戲曲" to listOf(
            "https://tv.cctv.com/live/cctv11/",
            "https://yangshipin.cn/tv/home?pid=600001806",
        ),
        "CCTV-12社法" to listOf(
            "https://tv.cctv.com/live/cctv12/",
            "https://yangshipin.cn/tv/home?pid=600001807",
        ),
        "CCTV-13新聞" to listOf(
            "https://tv.cctv.com/live/cctv13/",
            "https://yangshipin.cn/tv/home?pid=600001811",
            "https://v.douyin.com/BYo9353pcyI/"
        ),
        "CCTV-14少兒" to listOf(
            "https://tv.cctv.com/live/cctvchild/",
            "https://yangshipin.cn/tv/home?pid=600001809",
        ),
        "CCTV-15音樂" to listOf(
            "https://tv.cctv.com/live/cctv15/",
            "https://yangshipin.cn/tv/home?pid=600001815",
        ),
        "CCTV-16奧匹" to listOf(
            "https://tv.cctv.com/live/cctv16/",
            "https://yangshipin.cn/tv/home?pid=600098637",
        ),
        "CCTV-17農村" to listOf(
            "https://tv.cctv.com/live/cctv17/",
        ),
        "北京衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002309",
        ),
        "江蘇衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002521",
        ),
        "上海衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002483",
        ),
        "浙江衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002520",
        ),
        "湖南衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002475",
        ),
        "湖北衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002508",
        ),
        "廣東衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002485",
        ),
        "廣西衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002509",
        ),
        "黑龍江衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002498",
        ),
        "海南衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002506",
        ),
        "重慶衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002531",
        ),
        "深圳衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002481",
        ),
        "四川衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002516",
        ),
        "河南衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002525",
        ),
        "福建衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002484",
        ),
        "貴州衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002490",
        ),
        "江西衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002503",
        ),
        "遼寧衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002505",
        ),
        "安徽衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002532",
        ),
        "河北衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002493",
        ),
        "山東衞視" to listOf(
            "https://yangshipin.cn/tv/home?pid=600002513",
        ),
        "福建新聞" to listOf(
            "https://live.fjtv.net/xwpd/"
        ),
        "福建旅遊" to listOf(
            "https://live.fjtv.net/dspd/"
        )
    )

    // 2. 聲明一個用於存儲從遠端加載的自定義配置的變量
    private var customHybridWebViewUrl: Map<String, List<String>>? = null

    /**
     * 3. 提供一個異步方法，從指定的 URL 加載自定義的 WebView 解析規則
     *
     * 期望遠端 URL 返回的文本格式示例：
     * CCTV-1綜合,webview://https://custom.url.com/1
     * CCTV-1綜合,webview://https://custom.url.com/2
     * 湖南衞視,webview://https://custom.url.com/hunan
     */
    suspend fun loadHybridWebViewUrlFromRemote(url: String) {
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val newMap = mutableMapOf<String, MutableList<String>>()

                    // 遍歷文本的每一行，解析成 Map<String, List<String>>
                    responseText.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            val parts = line.split(",", limit = 2)
                            if (parts.size == 2) {
                                val channelName = parts[0].trim()
                                var webUrl = parts[1].trim()

                                // 移除 "webview://" 前綴（如果存在）
                                val webviewPrefix = "webview://"
                                if (webUrl.startsWith(webviewPrefix)) {
                                    webUrl = webUrl.substring(webviewPrefix.length)
                                }

                                newMap.getOrPut(channelName) { mutableListOf() }.add(webUrl)
                            }
                        }
                    }

                    // 只有成功解析且不出錯時，才覆蓋自定義配置
                    if (newMap.isNotEmpty()) {
                        customHybridWebViewUrl = newMap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 網絡異常或解析失敗時，不做任何處理。
                // customHybridWebViewUrl 依然為空，系統會自動降級使用 default 配置。
            }
        }
    }


    private fun standardChannelName(name: String): String {
        return standardChannelNameTest.entries.firstOrNull { it.value.invoke(name) }?.key
            ?: name
    }

    const val HYBRID_WEB_VIEW_URL_PREFIX = "webview://"

    // 4. 優化獲取邏輯：優先從 custom 字典中獲取，若無則回退到 default 字典中查找
    fun getHybridWebViewUrl(channelName: String): List<String>? {
        val name = standardChannelName(channelName)

        // 優先嚐試獲取自定義配置，如果沒有自定義配置或配置中不包含該頻道，再回退使用默認配置
        val urls = customHybridWebViewUrl?.get(name) ?: defaultHybridWebViewUrl[name]

        return urls?.map { "${HYBRID_WEB_VIEW_URL_PREFIX}${it}" }
    }

    fun isHybridWebViewUrl(url: String): Boolean {
        return url.startsWith(HYBRID_WEB_VIEW_URL_PREFIX)
    }

    fun getHybridWebViewUrlProvider(url: String): String {
        return if (url.contains("https://tv.cctv.com")) "央視網"
        else if (url.contains("https://yangshipin.cn")) "央視頻"
        else if (url.contains("https://v.douyin.com")) "抖音"
        else if (url.contains("http://m.miguvideo.com")) "咪咕視頻"
        else "未知"
    }

    fun urlSupportPlayback(url: String): Boolean {
        return listOf("pltv", "PLTV", "tvod", "TVOD").any { url.contains(it) }
    }

    fun urlToCanPlayback(url: String): String {
        return url
            .replace("PLTV", "tvod")
            .replace("pltv", "tvod")
    }
}
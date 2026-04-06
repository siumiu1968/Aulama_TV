package top.yogiczy.mytv.core.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser

import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 節目單獲取
 */
class EpgRepository(
    source: EpgSource,
) : FileCacheRepository("epg-${source.url.hashCode().toUInt().toString(16)}.json") {
    private val log = Logger.create(javaClass.simpleName)
    private val epgXmlRepository = EpgXmlRepository(source.url)

    /**
     * 解析節目單xml
     */
    private suspend fun parseFromXml(
        xmlString: String,
        filteredChannels: List<String> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())
        val lowerFilteredChannels = filteredChannels.map { it.lowercase() }
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xmlString))
        }

        val epgMap = mutableMapOf<String, Epg>()
        var currentChannelId: String? = null

        fun getSafeText(): String {
            return try {
                parser.nextText().trim().replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
            } catch (e: Exception) {
                log.e("解析XML文本失敗", e)
                ""
            }
        }

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            try {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> {
                            currentChannelId = parser.getAttributeValue(null, "id")
                            parser.nextTag()
                            val channelName = getSafeText()
//                            log.d("解析頻道: id=$currentChannelId, name=$channelName")  // 添加日誌

                            if (lowerFilteredChannels.isEmpty() ||
                                channelName.lowercase() in lowerFilteredChannels) {
                                epgMap[currentChannelId] = Epg(channelName, EpgProgrammeList())
                            }
                        }
                        "programme" -> {
                            val channelId = parser.getAttributeValue(null, "channel")
                            val startTime = parser.getAttributeValue(null, "start")
                            val stopTime = parser.getAttributeValue(null, "stop")
                            parser.nextTag()
                            val title = getSafeText()
//                            log.i("解析節目: channelId=$channelId, start=$startTime, stop=$stopTime, title=$title")  // 添加日誌

                            epgMap[channelId]?.let { epg ->
                                val startAt = dateFormat.parse(startTime)?.time ?: 0
                                val endAt = dateFormat.parse(stopTime)?.time ?: 0
                                if (startAt == 0L || endAt == 0L) {
                                    log.w("節目時間解析失敗: start=$startTime, stop=$stopTime")
                                }

                                val newProgramme = EpgProgramme(
                                    startAt = startAt,
                                    endAt = endAt,
                                    title = title
                                )

                                // 檢查是否已存在相同時間的節目或者開始時間在其他節目結束時間之前
                                val isDuplicate = epg.programmeList.any { prog ->
                                    timeFormat.format(prog.startAt) == timeFormat.format(newProgramme.startAt) ||
                                            newProgramme.startAt < prog.endAt && newProgramme.endAt > prog.startAt
                                }

                                if (!isDuplicate) {
                                    epgMap[channelId] = epg.copy(
                                        programmeList = epg.programmeList + newProgramme
                                    )
                                } else {
                                    log.d("發現重複節目: ${newProgramme.title} (${timeFormat.format(newProgramme.startAt)})")
                                }
                            } ?: run {
//                                log.w("節目所屬頻道未找到: channelId=$channelId")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.e("解析XML標籤失敗", e)
                continue
            }
        }

        log.i("解析節目單完成，共${epgMap.size}個頻道，${epgMap.values.sumOf { it.programmeList.size }}個節目")
        EpgList(epgMap.values.toList())
    }

    // Add this helper function to your project
    fun String.removeBom(): String {
        val bom = "\uFEFF"
        if (this.startsWith(bom)) {
            return this.removePrefix(bom)
        }
        return this
    }

    /**
     * 獲取節目單列表
     */
    suspend fun getEpgList(
        filteredChannels: List<String> = emptyList(),
        refreshTimeThreshold: Int,
    ): EpgList = withContext(Dispatchers.Default) {
        try {
//            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold) {
//                log.i("未到時間點，不刷新節目單")
//                return@withContext EpgList()
//            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val xmlJson = getOrRefresh({ lastModified, cachedContent ->
                // 如果緩存為空，強制刷新；或者日期不一致也刷新
                cachedContent.isNullOrEmpty() ||dateFormat.format(System.currentTimeMillis()) != dateFormat.format(lastModified)
            }) {
                val xmlString = epgXmlRepository.getEpgXml().removeBom()
                Json.encodeToString(
                    parseFromXml(
                        xmlString,
                        filteredChannels.map { it.lowercase() },
                    )
                )
            }

            return@withContext Json.decodeFromString(xmlJson)
        } catch (ex: Exception) {
            log.e("獲取節目單失敗", ex)
            throw Exception(ex)
        }
    }
}

/**
 * 節目單xml獲取
 */
private class EpgXmlRepository(
    private val url: String
) : FileCacheRepository("epg-${url.hashCode().toUInt().toString(16)}.xml") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 獲取遠程xml
     */
    private suspend fun fetchXml(): String {
        log.i("獲取節目單xml: $url")

        val client = OkHttp.client
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            val fetcher = EpgFetcher.instances.first { it.isSupport(url) }
            return withContext(Dispatchers.IO) {
                fetcher.fetch(response)
            }
        } catch (ex: Exception) {
            log.e("獲取節目單xml失敗", ex)
            throw Exception("獲取節目單xml失敗，請檢查網絡連接", ex)
        }
    }

    /**
     * 獲取xml
     */
    suspend fun getEpgXml(): String {
        return getOrRefresh(0) {
            fetchXml()
        }
    }
}

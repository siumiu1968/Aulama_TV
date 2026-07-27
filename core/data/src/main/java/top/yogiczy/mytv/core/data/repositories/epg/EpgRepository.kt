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
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** Fetches and parses an XMLTV guide while retaining only requested channels. */
class EpgRepository(
    private val source: EpgSource,
) {
    private val log = Logger.create(javaClass.simpleName)
    private val cachePrefix = "epg-${source.url.hashCode().toUInt().toString(16)}"
    private val usesVerifiedChannelIds = source.url.contains("/zzq1234567890/epg/") &&
        source.url.substringBefore('?').endsWith("epg.xml.gz")
    private val cacheJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getEpgList(
        filteredChannels: List<String> = emptyList(),
        @Suppress("UNUSED_PARAMETER") refreshTimeThreshold: Int,
    ): EpgList {
        val signature = EpgChannelMatcher.filterSignature(filteredChannels)
        val cache = EpgCache("$cachePrefix-$signature.json")
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(HONG_KONG_TIMEZONE)
        }

        return try {
            val cachedJson = cache.load({ lastModified, cachedContent ->
                val isDifferentGuideDay =
                    dayFormat.format(System.currentTimeMillis()) != dayFormat.format(lastModified)
                val cached = cachedContent
                    ?.takeIf(String::isNotBlank)
                    ?.let { runCatching { cacheJson.decodeFromString<EpgList>(it) }.getOrNull() }

                cachedContent.isNullOrBlank() ||
                    isDifferentGuideDay ||
                    cached == null ||
                    cached.filterSignature != signature
            }) {
                cacheJson.encodeToString(fetchAndParse(filteredChannels, signature))
            }
            cacheJson.decodeFromString(cachedJson)
        } catch (error: Exception) {
            log.e("獲取節目單失敗", error)
            throw Exception("獲取節目單失敗，請檢查網絡連接", error)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Globals.cacheDir.listFiles()
            ?.filter { it.name.startsWith(cachePrefix) }
            ?.forEach { it.delete() }
    }

    private suspend fun fetchAndParse(
        filteredChannels: List<String>,
        signature: String,
    ): EpgList = withContext(Dispatchers.IO) {
        log.i("串流獲取節目單: ${source.url}")
        val response = OkHttp.client.newCall(Request.Builder().url(source.url).build()).await()
        response.use {
            if (!it.isSuccessful) error("${it.code}: ${it.message}")
            val body = it.body ?: error("節目單內容為空")
            val networkStream = BufferedInputStream(body.byteStream())
            val xmlStream: InputStream = if (source.url.substringBefore('?').endsWith(".gz")) {
                GZIPInputStream(networkStream)
            } else {
                networkStream
            }
            xmlStream.use { input -> parseFromXml(input, filteredChannels, signature) }
        }
    }

    private fun parseFromXml(
        input: InputStream,
        filteredChannels: List<String>,
        signature: String,
    ): EpgList {
        val requestedGuideIds = filteredChannels
            .map(EpgChannelMatcher::preferredGuideId)
            .associateBy(EpgChannelMatcher::normalize)
        val includeAll = requestedGuideIds.isEmpty()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

        data class SelectedChannel(
            val guideId: String,
            val displayName: String,
            val aliases: List<String>,
        )

        val selectedChannels = linkedMapOf<String, SelectedChannel>()
        val selectedGuideKeys = mutableSetOf<String>()
        val programmes = linkedMapOf<String, MutableList<EpgProgramme>>()
        val seenProgrammeStarts = mutableMapOf<String, MutableSet<Long>>()

        var channelId: String? = null
        var channelNames = mutableListOf<String>()
        var programmeChannelId: String? = null
        var programmeStart = 0L
        var programmeEnd = 0L
        var programmeTitle = ""
        var programmeDescription = ""
        var programmeCategory = ""

        fun readText(): String = runCatching {
            parser.nextText().trim().replace(Regex("\\s+"), " ")
        }.getOrDefault("")

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        channelId = parser.getAttributeValue(null, "id")?.trim()
                        channelNames = mutableListOf()
                    }

                    "display-name" -> if (channelId != null) {
                        readText().takeIf(String::isNotBlank)?.let(channelNames::add)
                    }

                    "programme" -> {
                        programmeChannelId = parser.getAttributeValue(null, "channel")?.trim()
                            ?.takeIf(selectedChannels::containsKey)
                        programmeStart = if (programmeChannelId != null) {
                            XmlTvTimeParser.parse(parser.getAttributeValue(null, "start"))
                        } else {
                            0L
                        }
                        programmeEnd = if (programmeChannelId != null) {
                            XmlTvTimeParser.parse(parser.getAttributeValue(null, "stop"))
                        } else {
                            0L
                        }
                        programmeTitle = ""
                        programmeDescription = ""
                        programmeCategory = ""
                    }

                    "title" -> if (programmeChannelId != null) {
                        val text = readText()
                        if (programmeTitle.isBlank()) programmeTitle = text
                    }

                    "desc" -> if (programmeChannelId != null && programmeDescription.isBlank()) {
                        programmeDescription = readText()
                    }

                    "category" -> if (programmeChannelId != null && programmeCategory.isBlank()) {
                        programmeCategory = readText()
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> {
                        val sourceId = channelId
                        if (!sourceId.isNullOrBlank()) {
                            val observed = (listOf(sourceId) + channelNames).filter(String::isNotBlank)
                            val matchedGuideId = if (includeAll) {
                                sourceId
                            } else if (usesVerifiedChannelIds) {
                                requestedGuideIds[EpgChannelMatcher.normalize(sourceId)]
                            } else {
                                observed.firstNotNullOfOrNull { requestedGuideIds[EpgChannelMatcher.normalize(it)] }
                            }
                            val guideKey = EpgChannelMatcher.normalize(matchedGuideId.orEmpty())
                            if (matchedGuideId != null && (includeAll || selectedGuideKeys.add(guideKey))) {
                                selectedChannels[sourceId] = SelectedChannel(
                                    guideId = matchedGuideId,
                                    displayName = channelNames.firstOrNull().orEmpty().ifBlank { sourceId },
                                    aliases = observed.distinct(),
                                )
                                programmes[sourceId] = mutableListOf()
                                seenProgrammeStarts[sourceId] = mutableSetOf()
                            }
                        }
                        channelId = null
                        channelNames.clear()
                    }

                    "programme" -> {
                        val sourceId = programmeChannelId
                        if (
                            sourceId != null &&
                            programmeStart > 0L &&
                            programmeEnd > programmeStart &&
                            programmeTitle.isNotBlank()
                        ) {
                            if (seenProgrammeStarts.getValue(sourceId).add(programmeStart)) {
                                programmes.getValue(sourceId).add(
                                    EpgProgramme(
                                        startAt = programmeStart,
                                        endAt = programmeEnd,
                                        title = programmeTitle,
                                        description = programmeDescription,
                                        category = programmeCategory,
                                    )
                                )
                            }
                        }
                        programmeChannelId = null
                    }
                }
            }
        }

        val epgList = selectedChannels.map { (sourceId, selected) ->
            Epg(
                guideId = selected.guideId,
                channel = selected.displayName,
                aliases = selected.aliases,
                programmeList = programmes[sourceId].orEmpty().sortedBy(EpgProgramme::startAt),
            )
        }.filter { it.programmeList.isNotEmpty() }

        log.i("解析節目單完成，共${epgList.size}個頻道，${epgList.sumOf { it.programmeList.size }}個節目")
        return EpgList(
            value = epgList,
            source = source.name,
            updatedAt = System.currentTimeMillis(),
            timezone = HONG_KONG_TIMEZONE,
            filterSignature = signature,
        )
    }

    private companion object {
        const val HONG_KONG_TIMEZONE = "Asia/Hong_Kong"
    }
}

private class EpgCache(fileName: String) : FileCacheRepository(fileName) {
    suspend fun load(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String = super.getOrRefresh(isExpired, refreshOp)
}

/** XMLTV permits second, minute, hour, or date precision and an optional UTC offset. */
internal object XmlTvTimeParser {
    private val valuePattern = Regex("^(\\d{8,14})(?:\\s*([+-]\\d{4}|Z))?")

    fun parse(value: String?): Long {
        val match = valuePattern.find(value?.trim().orEmpty()) ?: return 0L
        val digits = match.groupValues[1]
        val precision = when {
            digits.length >= 14 -> 14
            digits.length >= 12 -> 12
            digits.length >= 10 -> 10
            else -> 8
        }
        val dateValue = digits.take(precision)
        val datePattern = when (precision) {
            14 -> "yyyyMMddHHmmss"
            12 -> "yyyyMMddHHmm"
            10 -> "yyyyMMddHH"
            else -> "yyyyMMdd"
        }
        val offset = match.groupValues.getOrNull(2).orEmpty().replace("Z", "+0000")
        val formatter = SimpleDateFormat(
            if (offset.isBlank()) datePattern else "$datePattern Z",
            Locale.ROOT,
        ).apply {
            isLenient = false
            if (offset.isBlank()) timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        }
        return runCatching {
            formatter.parse(if (offset.isBlank()) dateValue else "$dateValue $offset")?.time ?: 0L
        }.getOrDefault(0L)
    }
}

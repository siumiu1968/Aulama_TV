package top.yogiczy.mytv.tv.account

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.repositories.epg.EpgChannelMatcher
import top.yogiczy.mytv.core.data.utils.Logger
import java.time.OffsetDateTime

/** Hong Kong guides share the website's snapshot and live API, without numeric-id guessing. */
internal class AulamaWebEpgRepository(
    private val fetchSnapshot: suspend () -> String,
    private val fetchChannel: suspend (String) -> String?,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class Cached(val guide: Epg?, val fetchedAt: Long)
    private val channels = mutableMapOf<String, Cached>()
    private var snapshot = EpgList()
    private var snapshotFetchedAt: Long? = null

    suspend fun getEpgList(
        requested: List<Channel>,
        onUpdate: (EpgList) -> Unit = {},
    ): EpgList = coroutineScope {
        val names = requested.mapNotNull {
            AulamaWebEpgParser.webChannelName(it.epgName.ifBlank { it.name })
        }.distinct()
        if (names.isEmpty()) return@coroutineScope EpgList()
        val now = nowMs()
        fun result(): EpgList = EpgList(
            value = names.mapNotNull { name ->
                channels[name]?.guide?.takeIf { AulamaWebEpgParser.isUsable(it, nowMs()) }
                    ?: snapshot.firstOrNull { it.channel == name && AulamaWebEpgParser.isUsable(it, nowMs()) }
            },
            source = "Aulama 網頁版節目單",
            updatedAt = nowMs(),
        )
        // Publish the last usable guide immediately, including while XMLTV is still loading.
        result().takeIf { it.isNotEmpty() }?.let(onUpdate)
        if (snapshotFetchedAt == null || now - snapshotFetchedAt!! >= SNAPSHOT_TTL_MS) {
            safely {
                withTimeoutOrNull(5_000L) {
                    snapshot = AulamaWebEpgParser.parseSnapshot(fetchSnapshot(), nowMs())
                }
            }
            snapshotFetchedAt = now
        }
        result().takeIf { it.isNotEmpty() }?.let(onUpdate)
        val permits = Semaphore(3)
        names.map { name ->
            async {
                permits.withPermit {
                    val cached = channels[name]
                    val publicGuide = snapshot.firstOrNull { it.channel == name }
                    val ttl = if (cached?.guide == null) NEGATIVE_TTL_MS else CHANNEL_TTL_MS
                    if (cached != null && now - cached.fetchedAt in 0 until ttl &&
                        (cached.guide == null || AulamaWebEpgParser.isUsable(cached.guide, now))) {
                        return@withPermit
                    }
                    if (publicGuide != null && AulamaWebEpgParser.isUsable(publicGuide, now)) {
                        return@withPermit
                    }
                    safely {
                        withTimeoutOrNull(12_000L) {
                            fetchChannel(name)?.let { json ->
                                val guide = AulamaWebEpgParser.parseChannel(json, name, nowMs())
                                // A transient failure or stale response must not erase a usable guide.
                                if (guide != null || cached?.guide == null ||
                                    !AulamaWebEpgParser.isUsable(cached.guide, nowMs())) {
                                    channels[name] = Cached(guide, nowMs())
                                }
                            }
                        }
                    }
                    result().takeIf { it.isNotEmpty() }?.let(onUpdate)
                }
            }
        }.awaitAll()
        result()
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // XMLTV and the last valid web guide remain available if a source is unreachable.
        }
    }

    companion object {
        const val CHANNEL_TTL_MS = 2 * 60_000L
        private const val SNAPSHOT_TTL_MS = 15 * 60_000L
        private const val NEGATIVE_TTL_MS = 10 * 60_000L

        fun create(): AulamaWebEpgRepository {
            val api = AulamaAccountApi()
            val log = Logger.create("AulamaWebEpg")
            return AulamaWebEpgRepository(
                fetchSnapshot = { api.getWebEpgSnapshot() },
                fetchChannel = { channel ->
                    try {
                        AulamaAccount.manager.withAccessToken { api.getWebEpg(it, channel) }.also {
                            log.i("網頁節目單 $channel: ${if (it == null) "未登入" else "已接收"}")
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val reason = (error as? AulamaAccountApiException.HttpFailure)?.statusCode?.toString()
                            ?: error.javaClass.simpleName
                        log.w("網頁節目單 $channel 請求失敗: $reason")
                        throw error
                    }
                },
            )
        }
    }
}

internal object AulamaWebEpgParser {
    private val webNames = listOf(
        "翡翠台", "TVB Plus", "無綫新聞台", "明珠台", "TVB星河", "ViuTV", "Now新聞台",
        "HOY76", "HOY77", "HOY78", "港台電視31", "港台電視32", "港台電視33",
        "港台電視34", "港台電視35", "鳳凰衛視香港台",
    ).associateBy { EpgChannelMatcher.normalize(EpgChannelMatcher.preferredGuideId(it)) }

    fun webChannelName(label: String): String? {
        val key = EpgChannelMatcher.normalize(EpgChannelMatcher.preferredGuideId(label))
        // The web backend's canonical ViuTV key is Viu 頻道.
        return webNames[key]?.let { if (it == "ViuTV") "Viu 頻道" else it }
    }

    fun isUsable(guide: Epg, now: Long): Boolean = guide.programmeList.any {
        it.endAt > now && it.startAt <= now + 24 * 60 * 60_000L
    }

    fun parseChannel(json: String, name: String, now: Long): Epg? {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("available")?.asBoolean == false) return null
        // Never attach another channel's schedule to this channel.
        val returnedName = root.text("channel").ifBlank { name }
        if (guideKey(returnedName) != guideKey(name)) return null
        val entries = buildList {
            root.get("now")?.takeIf { it.isJsonObject }?.let(::add)
            root.getAsJsonArray("next")?.forEach(::add)
        }
        return guide(name, entries, now)
    }

    fun parseSnapshot(json: String, now: Long): EpgList {
        val root = JsonParser.parseString(json).asJsonObject
        val entries = root.getAsJsonObject("channels") ?: return EpgList()
        return EpgList(
            value = entries.entrySet().mapNotNull { (label, programmes) ->
                val name = if (label == "Viu 頻道") label else webChannelName(label)
                if (name == null || !programmes.isJsonArray) null
                else guide(name, programmes.asJsonArray.toList(), now)
            },
            source = "Aulama 網頁版節目單",
            updatedAt = now,
        )
    }

    private fun guide(name: String, entries: List<JsonElement>, now: Long): Epg? {
        val programmes = entries.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val entry = element.asJsonObject
            val start = parseTime(entry.text("start")) ?: return@mapNotNull null
            val end = parseTime(entry.text("end")) ?: return@mapNotNull null
            val title = entry.text("title").trim()
            if (end <= start || title.isBlank()) return@mapNotNull null
            EpgProgramme(start, end, title, entry.text("description"), entry.text("category"))
        }.distinctBy { it.startAt }.sortedBy { it.startAt }
        val label = if (name == "Viu 頻道") "ViuTV" else name
        return Epg(
            guideId = EpgChannelMatcher.preferredGuideId(label),
            channel = name,
            aliases = listOf(name, label),
            programmeList = programmes,
        ).takeIf { isUsable(it, now) }
    }

    private fun guideKey(value: String) = EpgChannelMatcher.normalize(
        EpgChannelMatcher.preferredGuideId(if (value == "Viu 頻道") "ViuTV" else value)
    )

    private fun parseTime(value: String): Long? = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}

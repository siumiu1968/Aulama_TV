package top.yogiczy.mytv.tv.ui.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.SP
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

data class IptvRouteHealth(
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val averageStartupMs: Long = 0,
    val stableWatchMs: Long = 0,
    val quickExitCount: Int = 0,
    val lastSuccessAt: Long = 0,
    val lastFailureAt: Long = 0,
    val lastWatchAt: Long = 0,
    val preferredPlaybackMode: String? = null,
    val successEwma: Double? = null,
    val bufferRatioEwma: Double? = null,
    val lastBufferIssueAt: Long = 0,
    val lastBufferIssueMode: String? = null,
    val stableWatchSinceBufferIssueMs: Long = 0,
    val fatalErrorCount: Int = 0,
    val lastUpdatedAt: Long = 0,
)

enum class IptvPlaybackMode {
    IJK,
    IJK_SOFTWARE,
    MEDIA3,
}

/**
 * 長期分數只根據實際觀看結果學習；短期輕量 probe 由播放頁另外處理，唔會污染呢度。
 * 非冷卻線路先按畫質排序，再以成功率、啟動速度及近期表現選出同畫質最佳線路。
 * 近期確實失敗嘅線路會進入冷卻，避免每次換台都重試同一條壞線。
 */
object IptvRouteHealthStore {
    private const val key = "IPTV_ROUTE_HEALTH_V2"
    private const val playbackProfileKey = "IPTV_PLAYBACK_PROFILE"
    private const val playbackProfile = "IJK_STABLE_MODE_V3"
    private const val initialCooldownMs = 2 * 60 * 1000L
    private const val maxCooldownMs = 30 * 60 * 1000L
    private const val decayHalfLifeMs = 7 * 24 * 60 * 60 * 1000L
    private const val switchScoreMargin = 20.0
    private const val maxLearnedWatchMs = 12 * 60 * 60 * 1000L
    private const val maxEntries = 250
    private val VOLATILE_ROUTE_PARAMETER = Regex(
        "(?:^|[_-])(?:token|auth|authorization|signature|sig|expires?|expiry|credential|" +
            "policy|session|jwt|nonce|timestamp|ts|hdnea|hdnts|wssecret|wstime)(?:[_-]|$)",
    )
    private val autoDeprioritizedHosts = setOf("120.234.44.98")
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, IptvRouteHealth>>() {}.type

    @Synchronized
    fun rankedIndices(
        routes: List<ChannelRoute>,
        now: Long = System.currentTimeMillis(),
        supports4k: Boolean = true,
        transportIds: List<String> = listOf(DIRECT_TRANSPORT_ID),
        networkScope: IptvNetworkScope? = null,
    ): List<Int> = rankedIndices(routes, read(), now, supports4k, transportIds, networkScope)

    internal fun rankedIndices(
        routes: List<ChannelRoute>,
        health: Map<String, IptvRouteHealth>,
        now: Long,
        supports4k: Boolean = true,
        transportIds: List<String> = listOf(DIRECT_TRANSPORT_ID),
        networkScope: IptvNetworkScope? = null,
    ): List<Int> {
        val availableTransportIds = transportIds.distinct().ifEmpty {
            listOf(DIRECT_TRANSPORT_ID)
        }
        return routes.indices.sortedWith(
            compareBy<Int> { index ->
                val records = availableTransportIds.mapNotNull { transportId ->
                    candidateHealth(health, routes[index].url, transportId, networkScope)
                }
                if (records.isNotEmpty() && records.all { isCoolingDown(it, now) }) 1 else 0
            }
                .thenByDescending { index ->
                    val quality = routes[index].quality
                    if (!supports4k && quality ==
                        top.yogiczy.mytv.core.data.entities.channel.ChannelQuality.UHD_4K
                    ) {
                        -1
                    } else {
                        quality.rank
                    }
                }
                .thenByDescending { index ->
                    availableTransportIds.maxOf { transportId ->
                        performanceScore(
                            health = candidateHealth(
                                health,
                                routes[index].url,
                                transportId,
                                networkScope,
                            ),
                            now = now,
                            quality = routes[index].quality,
                            supports4k = supports4k,
                        )
                    } - autoSelectionPenalty(routes[index].url)
                }
                .thenBy { index -> routes[index].sourceOrder }
        )
    }

    fun candidateKey(routeUrl: String, transportId: String): String =
        "${transportId.ifBlank { DIRECT_TRANSPORT_ID }}::$routeUrl"

    fun scopedCandidateKey(
        routeUrl: String,
        transportId: String,
        networkScope: IptvNetworkScope,
    ): String = buildString {
        append("v3|")
        append(networkScope.name.lowercase(Locale.US))
        append('|')
        append(transportId.ifBlank { DIRECT_TRANSPORT_ID })
        append('|')
        append(routeFingerprint(routeUrl))
    }

    @Synchronized
    fun rankedTransportIds(
        routeUrl: String,
        orderedTransportIds: List<String>,
        quality: top.yogiczy.mytv.core.data.entities.channel.ChannelQuality,
        supports4k: Boolean,
        networkScope: IptvNetworkScope? = null,
        now: Long = System.currentTimeMillis(),
    ): List<String> = rankedTransportIds(
        routeUrl = routeUrl,
        orderedTransportIds = orderedTransportIds,
        health = read(),
        quality = quality,
        supports4k = supports4k,
        networkScope = networkScope,
        now = now,
    )

    internal fun rankedTransportIds(
        routeUrl: String,
        orderedTransportIds: List<String>,
        health: Map<String, IptvRouteHealth>,
        quality: top.yogiczy.mytv.core.data.entities.channel.ChannelQuality,
        supports4k: Boolean,
        networkScope: IptvNetworkScope? = null,
        now: Long,
    ): List<String> {
        val sourceOrder = orderedTransportIds.distinct()
        val rank = sourceOrder.withIndex().associate { (index, id) -> id to index }
        return sourceOrder.sortedWith(
            compareBy<String> { transportId ->
                if (isCoolingDown(candidateHealth(health, routeUrl, transportId, networkScope), now)) 1 else 0
            }.thenByDescending { transportId ->
                performanceScore(
                    health = candidateHealth(health, routeUrl, transportId, networkScope),
                    now = now,
                    quality = quality,
                    supports4k = supports4k,
                )
            }.thenBy { transportId -> rank.getValue(transportId) }
        )
    }

    internal fun autoSelectionPenalty(url: String): Double {
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host in autoDeprioritizedHosts) 28.0 else 0.0
    }

    internal fun candidateHealth(
        health: Map<String, IptvRouteHealth>,
        routeUrl: String,
        transportId: String,
        networkScope: IptvNetworkScope? = null,
    ): IptvRouteHealth? = networkScope?.let {
        health[scopedCandidateKey(routeUrl, transportId, it)]
            ?: health[legacyRawScopedCandidateKey(routeUrl, transportId, it)]
    } ?: health[candidateKey(routeUrl, transportId)]
        ?: health[routeUrl].takeIf { transportId == DIRECT_TRANSPORT_ID }

    internal fun previousHealthForWrite(
        health: Map<String, IptvRouteHealth>,
        key: String,
        legacyKey: String? = null,
    ): IptvRouteHealth = health[key]
        ?: legacyKey?.let { oldKey ->
            health[oldKey] ?: oldKey.removePrefix("$DIRECT_TRANSPORT_ID::")
                .takeIf { oldKey.startsWith("$DIRECT_TRANSPORT_ID::") }
                ?.let(health::get)
        }
        ?: IptvRouteHealth()

    private fun removeMigratedLegacyHealth(
        health: MutableMap<String, IptvRouteHealth>,
        legacyKey: String?,
    ) {
        if (legacyKey == null) return
        health.remove(legacyKey)
        if (legacyKey.startsWith("$DIRECT_TRANSPORT_ID::")) {
            health.remove(legacyKey.removePrefix("$DIRECT_TRANSPORT_ID::"))
        }
    }

    private fun legacyRawScopedCandidateKey(
        routeUrl: String,
        transportId: String,
        networkScope: IptvNetworkScope,
    ): String = "${networkScope.name.lowercase(Locale.US)}::${candidateKey(routeUrl, transportId)}"

    /**
     * 新紀錄只保存不可逆 fingerprint；常見簽名參數會先移除值，token 更新後仍可沿用經驗。
     * 舊 V2 完整 URL 只作向後兼容讀取，不會複製到新 key。
     */
    private fun routeFingerprint(routeUrl: String): String {
        val canonical = canonicalRouteIdentity(routeUrl)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(Locale.US, byte.toInt() and 0xff)
            }
    }

    internal fun canonicalRouteIdentity(routeUrl: String): String {
        val trimmed = routeUrl.trim().substringBefore('#')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host?.lowercase(Locale.US)
        val authority = when {
            host != null -> buildString {
                append(host)
                val isDefaultPort = (scheme == "http" && uri.port == 80) ||
                    (scheme == "https" && uri.port == 443)
                if (uri.port >= 0 && !isDefaultPort) append(":${uri.port}")
            }
            uri.rawAuthority != null -> uri.rawAuthority.substringAfterLast('@').lowercase(Locale.US)
            else -> null
        }
        val normalizedQuery = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotBlank() }
            ?.map { part ->
                val rawName = part.substringBefore('=')
                val rawValue = part.substringAfter('=', missingDelimiterValue = "")
                val decodedName = runCatching {
                    URLDecoder.decode(rawName, Charsets.UTF_8.name())
                }.getOrDefault(rawName)
                if (isVolatileRouteParameter(decodedName)) "$rawName=*" else "$rawName=$rawValue"
            }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        return buildString {
            if (scheme != null) append("$scheme:")
            if (authority != null) append("//$authority")
            append(uri.rawPath.orEmpty())
            if (normalizedQuery.isNotEmpty()) append("?$normalizedQuery")
        }
    }

    private fun isVolatileRouteParameter(name: String): Boolean {
        val normalized = name.lowercase(Locale.US)
        if (normalized.startsWith("x-amz-")) return true
        return VOLATILE_ROUTE_PARAMETER.containsMatchIn(normalized)
    }

    internal fun performanceScore(
        health: IptvRouteHealth?,
        now: Long = System.currentTimeMillis(),
        quality: top.yogiczy.mytv.core.data.entities.channel.ChannelQuality =
            top.yogiczy.mytv.core.data.entities.channel.ChannelQuality.UNKNOWN,
        supports4k: Boolean = true,
    ): Double {
        val qualityBonus = if (
            supports4k &&
            quality == top.yogiczy.mytv.core.data.entities.channel.ChannelQuality.UHD_4K
        ) 12.0 else 0.0
        if (health == null) return 50.0 + qualityBonus

        val observations = health.successCount + health.failureCount
        val betaSuccess = (health.successCount + 2.0) / (observations + 4.0)
        val success = health.successEwma?.takeIf { it in 0.0..1.0 } ?: betaSuccess
        val successScore = (success - 0.5) * 80.0
        val startupScore = if (health.averageStartupMs <= 0L) {
            0.0
        } else {
            ((2_000L - health.averageStartupMs) / 350.0).coerceIn(-22.0, 5.0)
        }
        val stableMinutes = health.stableWatchMs / 60_000.0
        val stableWatchBonus = min(30.0, stableMinutes * 0.75)
        val bufferPenalty = (health.bufferRatioEwma ?: 0.0).coerceIn(0.0, 1.0) * 120.0
        val fatalPenalty = min(health.fatalErrorCount, 3) * 18.0
        val repeatedFailurePenalty = min(health.consecutiveFailures, 5) * 9.0
        val quickExitPenalty = min(health.quickExitCount, 4) * 6.0
        val lastActivity = maxOf(
            health.lastUpdatedAt,
            health.lastSuccessAt,
            health.lastFailureAt,
            health.lastWatchAt,
        )
        val ageMs = (now - lastActivity).coerceAtLeast(0L)
        val decay = 0.5.pow(ageMs.toDouble() / decayHalfLifeMs)

        val evidence = successScore + startupScore + stableWatchBonus - bufferPenalty -
            fatalPenalty - repeatedFailurePenalty - quickExitPenalty
        return 50.0 + qualityBonus + evidence * decay
    }

    internal fun cooldownDurationMs(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val multiplier = 1L shl (consecutiveFailures - 1).coerceAtMost(4)
        return (initialCooldownMs * multiplier).coerceAtMost(maxCooldownMs)
    }

    internal fun shouldAutoSwitch(
        currentScore: Double,
        candidateScore: Double,
        currentUnavailable: Boolean = false,
    ): Boolean = currentUnavailable || candidateScore >= currentScore + switchScoreMargin

    @Synchronized
    fun preferredPlaybackMode(url: String): IptvPlaybackMode? = read()[url]
        ?.preferredPlaybackMode
        ?.let { value -> runCatching { IptvPlaybackMode.valueOf(value) }.getOrNull() }

    @Synchronized
    fun preferredPlaybackMode(
        routeUrl: String,
        transportId: String,
        networkScope: IptvNetworkScope? = null,
    ): IptvPlaybackMode? = candidateHealth(read(), routeUrl, transportId, networkScope)
        ?.preferredPlaybackMode
        ?.let { value -> runCatching { IptvPlaybackMode.valueOf(value) }.getOrNull() }

    @Synchronized
    internal fun healthFor(url: String): IptvRouteHealth? = read()[url]

    @Synchronized
    internal fun healthFor(
        routeUrl: String,
        transportId: String,
        networkScope: IptvNetworkScope? = null,
    ): IptvRouteHealth? = candidateHealth(read(), routeUrl, transportId, networkScope)

    @Synchronized
    fun markSuccess(
        url: String,
        startupMs: Long,
        playbackMode: IptvPlaybackMode? = null,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        val sample = startupMs.coerceIn(100, 60_000)
        val average = if (previous.successCount == 0 || previous.averageStartupMs == 0L) {
            sample
        } else {
            (previous.averageStartupMs * 0.72 + sample * 0.28).roundToLong()
        }
        health[url] = previous.copy(
            successCount = previous.successCount + 1,
            consecutiveFailures = 0,
            averageStartupMs = average,
            lastSuccessAt = now,
            preferredPlaybackMode = playbackMode?.name ?: previous.preferredPlaybackMode,
            successEwma = ewma(previous.successEwma, 1.0),
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markFailure(
        url: String,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        val consecutiveFailures = previous.consecutiveFailures + 1
        health[url] = previous.copy(
            failureCount = previous.failureCount + 1,
            consecutiveFailures = consecutiveFailures,
            lastFailureAt = now,
            preferredPlaybackMode = previous.preferredPlaybackMode.takeIf {
                consecutiveFailures < 2
            },
            successEwma = ewma(previous.successEwma, 0.0),
            fatalErrorCount = previous.fatalErrorCount + 1,
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markDegraded(
        url: String,
        reason: String,
        playbackMode: IptvPlaybackMode? = null,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        val ratio = reason.substringAfter("buffer-ratio:", "")
            .toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
        health[url] = previous.copy(
            failureCount = previous.failureCount + 1,
            consecutiveFailures = previous.consecutiveFailures + 1,
            lastFailureAt = now,
            successEwma = ewma(previous.successEwma, 0.25),
            bufferRatioEwma = ratio?.let { ewma(previous.bufferRatioEwma, it) }
                ?: previous.bufferRatioEwma,
            lastBufferIssueAt = if (isBufferingIssue(reason)) now else previous.lastBufferIssueAt,
            lastBufferIssueMode = if (isBufferingIssue(reason)) {
                playbackMode?.name
            } else {
                previous.lastBufferIssueMode
            },
            stableWatchSinceBufferIssueMs = if (isBufferingIssue(reason)) 0L else {
                previous.stableWatchSinceBufferIssueMs
            },
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    /**
     * 即使同線重載尚未計作一次失敗，都先記低真實重緩衝經驗，
     * 令下一個 physical player session 可以立即採用穩定緩衝檔。
     */
    @Synchronized
    fun markBufferingIssue(
        url: String,
        reason: String,
        playbackMode: IptvPlaybackMode? = null,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!isBufferingIssue(reason)) return
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        health[url] = previous.copy(
            lastBufferIssueAt = now,
            lastBufferIssueMode = playbackMode?.name,
            stableWatchSinceBufferIssueMs = 0L,
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markStableWatch(
        url: String,
        watchedMs: Long,
        playbackMode: IptvPlaybackMode? = null,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (watchedMs <= 0L) return
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        val sample = watchedMs.coerceAtMost(30 * 60 * 1000L)
        val recoveredQuickExits = (sample / (2 * 60 * 1000L)).toInt().coerceAtLeast(1)
        val recoveredAfterBufferIssue = previous.lastBufferIssueMode == playbackMode?.name
        health[url] = previous.copy(
            stableWatchMs = (previous.stableWatchMs + sample).coerceAtMost(maxLearnedWatchMs),
            quickExitCount = (previous.quickExitCount - recoveredQuickExits).coerceAtLeast(0),
            lastSuccessAt = maxOf(previous.lastSuccessAt, now),
            lastWatchAt = now,
            preferredPlaybackMode = playbackMode?.name ?: previous.preferredPlaybackMode,
            stableWatchSinceBufferIssueMs = if (recoveredAfterBufferIssue) {
                (previous.stableWatchSinceBufferIssueMs + sample)
                    .coerceAtMost(BUFFER_ISSUE_RECOVERY_WATCH_MS)
            } else {
                previous.stableWatchSinceBufferIssueMs
            },
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markQuickExit(
        url: String,
        legacyKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = previousHealthForWrite(health, url, legacyKey)
        removeMigratedLegacyHealth(health, legacyKey)
        health[url] = previous.copy(
            quickExitCount = (previous.quickExitCount + 1).coerceAtMost(10),
            lastWatchAt = now,
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    internal fun isCoolingDown(health: IptvRouteHealth?, now: Long): Boolean {
        if (health == null || health.consecutiveFailures == 0) return false
        if (health.lastSuccessAt > health.lastFailureAt) return false
        val duration = cooldownDurationMs(health.consecutiveFailures)
        return now < health.lastFailureAt + duration
    }

    private fun ewma(previous: Double?, sample: Double): Double =
        previous?.let { it * 0.72 + sample * 0.28 } ?: sample

    private const val DIRECT_TRANSPORT_ID = "direct"

    private fun read(): MutableMap<String, IptvRouteHealth> = try {
        val json = SP.getString(key, "{}")
        val health = gson.fromJson<MutableMap<String, IptvRouteHealth>>(json, mapType)
            ?: mutableMapOf()
        if (SP.getString(playbackProfileKey, "") != playbackProfile) {
            health.entries.forEach { entry ->
                entry.setValue(entry.value.copy(preferredPlaybackMode = null))
            }
            write(health)
            SP.putString(playbackProfileKey, playbackProfile)
        }
        health
    } catch (_: Exception) {
        mutableMapOf()
    }

    private fun write(value: Map<String, IptvRouteHealth>) {
        SP.putString(key, gson.toJson(value))
    }

    private fun trim(value: MutableMap<String, IptvRouteHealth>): Map<String, IptvRouteHealth> {
        if (value.size <= maxEntries) return value
        return value.entries
            .sortedByDescending {
                maxOf(it.value.lastSuccessAt, it.value.lastFailureAt, it.value.lastWatchAt)
            }
            .take(maxEntries)
            .associate { it.toPair() }
    }
}

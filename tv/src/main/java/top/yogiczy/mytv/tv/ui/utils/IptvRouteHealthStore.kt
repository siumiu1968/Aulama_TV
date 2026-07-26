package top.yogiczy.mytv.tv.ui.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.SP
import java.net.URI
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
    val fatalErrorCount: Int = 0,
    val lastUpdatedAt: Long = 0,
)

enum class IptvPlaybackMode {
    IJK,
    IJK_SOFTWARE,
    MEDIA3,
}

/**
 * 只根據實際觀看結果學習，不會每次開機逐條測速。
 * 健康線路先按畫質排序，再以成功率、啟動速度及近期表現選出同畫質最佳線路。
 * 近期確實失敗嘅線路會進入冷卻，避免每次換台都重試同一條壞線。
 */
object IptvRouteHealthStore {
    private const val key = "IPTV_ROUTE_HEALTH_V2"
    private const val playbackProfileKey = "IPTV_PLAYBACK_PROFILE"
    private const val playbackProfile = "IJK_AC3_HEALTH_V2"
    private const val initialCooldownMs = 2 * 60 * 1000L
    private const val maxCooldownMs = 30 * 60 * 1000L
    private const val decayHalfLifeMs = 7 * 24 * 60 * 60 * 1000L
    private const val switchScoreMargin = 20.0
    private const val maxLearnedWatchMs = 12 * 60 * 60 * 1000L
    private const val maxEntries = 250
    private val autoDeprioritizedHosts = setOf("120.234.44.98")
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, IptvRouteHealth>>() {}.type

    @Synchronized
    fun rankedIndices(
        routes: List<ChannelRoute>,
        now: Long = System.currentTimeMillis(),
        supports4k: Boolean = true,
    ): List<Int> = rankedIndices(routes, read(), now, supports4k)

    internal fun rankedIndices(
        routes: List<ChannelRoute>,
        health: Map<String, IptvRouteHealth>,
        now: Long,
        supports4k: Boolean = true,
    ): List<Int> {
        return routes.indices.sortedWith(
            compareBy<Int> { index -> if (isCoolingDown(health[routes[index].url], now)) 1 else 0 }
                .thenByDescending { index ->
                    performanceScore(
                        health = health[routes[index].url],
                        now = now,
                        quality = routes[index].quality,
                        supports4k = supports4k,
                    ) - autoSelectionPenalty(routes[index].url)
                }
                .thenBy { index -> routes[index].sourceOrder }
        )
    }

    internal fun autoSelectionPenalty(url: String): Double {
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host in autoDeprioritizedHosts) 28.0 else 0.0
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
    internal fun healthFor(url: String): IptvRouteHealth? = read()[url]

    @Synchronized
    fun markSuccess(
        url: String,
        startupMs: Long,
        playbackMode: IptvPlaybackMode? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
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
    fun markFailure(url: String, now: Long = System.currentTimeMillis()) {
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
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
        now: Long = System.currentTimeMillis(),
    ) {
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
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
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markStableWatch(
        url: String,
        watchedMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        if (watchedMs <= 0L) return
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
        val sample = watchedMs.coerceAtMost(30 * 60 * 1000L)
        val recoveredQuickExits = (sample / (2 * 60 * 1000L)).toInt().coerceAtLeast(1)
        health[url] = previous.copy(
            stableWatchMs = (previous.stableWatchMs + sample).coerceAtMost(maxLearnedWatchMs),
            quickExitCount = (previous.quickExitCount - recoveredQuickExits).coerceAtLeast(0),
            lastSuccessAt = maxOf(previous.lastSuccessAt, now),
            lastWatchAt = now,
            lastUpdatedAt = now,
        )
        write(trim(health))
    }

    @Synchronized
    fun markQuickExit(url: String, now: Long = System.currentTimeMillis()) {
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
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

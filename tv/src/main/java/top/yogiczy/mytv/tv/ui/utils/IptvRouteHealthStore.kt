package top.yogiczy.mytv.tv.ui.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.SP
import java.net.URI
import kotlin.math.min
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
    private const val shortCooldownMs = 30 * 60 * 1000L
    private const val longCooldownMs = 2 * 60 * 60 * 1000L
    private const val maxLearnedWatchMs = 12 * 60 * 60 * 1000L
    private const val maxEntries = 250
    private val autoDeprioritizedHosts = setOf("120.234.44.98")
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, IptvRouteHealth>>() {}.type

    @Synchronized
    fun rankedIndices(
        routes: List<ChannelRoute>,
        now: Long = System.currentTimeMillis(),
    ): List<Int> = rankedIndices(routes, read(), now)

    internal fun rankedIndices(
        routes: List<ChannelRoute>,
        health: Map<String, IptvRouteHealth>,
        now: Long,
    ): List<Int> {
        return routes.indices.sortedWith(
            compareByDescending<Int> { index -> routes[index].quality.rank }
                .thenBy { index -> if (isCoolingDown(health[routes[index].url], now)) 1 else 0 }
                .thenBy { index -> autoSelectionPenalty(routes[index].url) }
                .thenByDescending { index -> performanceScore(health[routes[index].url]) }
                .thenBy { index -> routes[index].sourceOrder }
        )
    }

    internal fun autoSelectionPenalty(url: String): Int {
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host in autoDeprioritizedHosts) 1 else 0
    }

    internal fun performanceScore(health: IptvRouteHealth?): Double {
        if (health == null) return 50.0

        // Beta(2, 2) 先驗令未測試線路保持中性，少量樣本不會過度影響排序。
        val observations = health.successCount + health.failureCount
        val reliability = (health.successCount + 2.0) / (observations + 4.0) * 100.0
        val startupScore = if (health.averageStartupMs == 0L) {
            0.0
        } else {
            ((8_000L - health.averageStartupMs) / 700.0).coerceIn(-12.0, 10.0)
        }
        val provenSuccessBonus = min(health.successCount, 5) * 1.5
        val recencyScore = when {
            health.lastSuccessAt > health.lastFailureAt -> 8.0
            health.lastFailureAt > health.lastSuccessAt -> -8.0
            else -> 0.0
        }
        val repeatedFailurePenalty = min(health.consecutiveFailures, 3) * 12.0
        val stableWatchBonus = when {
            health.stableWatchMs >= 60 * 60 * 1000L -> 30.0
            health.stableWatchMs >= 30 * 60 * 1000L -> 24.0
            health.stableWatchMs >= 10 * 60 * 1000L -> 16.0
            health.stableWatchMs >= 3 * 60 * 1000L -> 9.0
            health.stableWatchMs >= 60 * 1000L -> 4.0
            else -> 0.0
        }
        val quickExitPenalty = min(health.quickExitCount, 4) * 6.0

        return reliability + startupScore + provenSuccessBonus + recencyScore +
            stableWatchBonus - repeatedFailurePenalty - quickExitPenalty
    }

    @Synchronized
    fun preferredPlaybackMode(url: String): IptvPlaybackMode? = read()[url]
        ?.preferredPlaybackMode
        ?.let { value -> runCatching { IptvPlaybackMode.valueOf(value) }.getOrNull() }

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
        )
        write(trim(health))
    }

    private fun isCoolingDown(health: IptvRouteHealth?, now: Long): Boolean {
        if (health == null || health.consecutiveFailures == 0) return false
        if (health.lastSuccessAt > health.lastFailureAt) return false
        val duration = if (health.consecutiveFailures >= 2) longCooldownMs else shortCooldownMs
        return now < health.lastFailureAt + duration
    }

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

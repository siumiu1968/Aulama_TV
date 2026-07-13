package top.yogiczy.mytv.tv.ui.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.SP
import kotlin.math.roundToLong

data class IptvRouteHealth(
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val averageStartupMs: Long = 0,
    val lastSuccessAt: Long = 0,
    val lastFailureAt: Long = 0,
)

/**
 * 只根據實際觀看結果學習，不會每次開機逐條測速。
 * 畫質先分級（4K > 1080p > 其他），同級先比較本機過往穩定度。
 */
object IptvRouteHealthStore {
    private const val key = "IPTV_ROUTE_HEALTH_V1"
    private const val shortCooldownMs = 30 * 60 * 1000L
    private const val longCooldownMs = 6 * 60 * 60 * 1000L
    private const val maxEntries = 250
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, IptvRouteHealth>>() {}.type

    @Synchronized
    fun rankedIndices(
        routes: List<ChannelRoute>,
        preferredIndex: Int? = null,
        now: Long = System.currentTimeMillis(),
    ): List<Int> {
        val health = read()
        val smooth4k = IptvPlaybackCapabilities.supportsSmooth4kHevc
        return routes.indices.sortedWith(
            compareBy<Int> { index -> if (isCoolingDown(health[routes[index].url], now)) 1 else 0 }
                .thenBy { index ->
                    if (routes[index].quality == ChannelQuality.UHD_4K && !smooth4k) 1 else 0
                }
                .thenByDescending { index -> routes[index].quality.rank }
                .thenBy { index -> routeCost(health[routes[index].url], index == preferredIndex, now) }
                .thenBy { index -> routes[index].sourceOrder }
        )
    }

    @Synchronized
    fun markSuccess(url: String, startupMs: Long, now: Long = System.currentTimeMillis()) {
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
        )
        write(trim(health))
    }

    @Synchronized
    fun markFailure(url: String, now: Long = System.currentTimeMillis()) {
        val health = read()
        val previous = health[url] ?: IptvRouteHealth()
        health[url] = previous.copy(
            failureCount = previous.failureCount + 1,
            consecutiveFailures = previous.consecutiveFailures + 1,
            lastFailureAt = now,
        )
        write(trim(health))
    }

    private fun routeCost(health: IptvRouteHealth?, preferred: Boolean, now: Long): Long {
        if (health == null) return if (preferred) 2_100 else 2_500
        val latency = health.averageStartupMs.takeIf { it > 0 } ?: 2_500
        val recentSuccessBonus = if (now - health.lastSuccessAt < 7 * 24 * 60 * 60 * 1000L) 450 else 0
        val preferredBonus = if (preferred) 300 else 0
        return latency + health.consecutiveFailures * 5_000L - recentSuccessBonus - preferredBonus
    }

    private fun isCoolingDown(health: IptvRouteHealth?, now: Long): Boolean {
        if (health == null || health.consecutiveFailures == 0) return false
        if (health.lastSuccessAt > health.lastFailureAt) return false
        val duration = if (health.consecutiveFailures >= 2) longCooldownMs else shortCooldownMs
        return now < health.lastFailureAt + duration
    }

    private fun read(): MutableMap<String, IptvRouteHealth> = try {
        val json = SP.getString(key, "{}")
        gson.fromJson<MutableMap<String, IptvRouteHealth>>(json, mapType) ?: mutableMapOf()
    } catch (_: Exception) {
        mutableMapOf()
    }

    private fun write(value: Map<String, IptvRouteHealth>) {
        SP.putString(key, gson.toJson(value))
    }

    private fun trim(value: MutableMap<String, IptvRouteHealth>): Map<String, IptvRouteHealth> {
        if (value.size <= maxEntries) return value
        return value.entries
            .sortedByDescending { maxOf(it.value.lastSuccessAt, it.value.lastFailureAt) }
            .take(maxEntries)
            .associate { it.toPair() }
    }
}

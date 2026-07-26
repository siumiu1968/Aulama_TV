package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvRouteHealthStoreTest {
    private val now = 10_000_000L

    @Test
    fun `healthy 4K route is preferred over proven 1080p route`() {
        val routes = listOf(
            route("1080", ChannelQuality.FULL_HD, 0),
            route("4k", ChannelQuality.UHD_4K, 1),
        )
        val health = mapOf(
            "1080" to successfulHealth(successCount = 20, startupMs = 900),
            "4k" to successfulHealth(successCount = 1, startupMs = 4_000),
        )

        assertEquals(listOf(1, 0), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `4K route remains first priority while cooling`() {
        val routes = listOf(
            route("4k", ChannelQuality.UHD_4K, 0),
            route("1080", ChannelQuality.FULL_HD, 1),
        )
        val health = mapOf(
            "4k" to IptvRouteHealth(
                failureCount = 1,
                consecutiveFailures = 1,
                lastFailureAt = now - 1_000,
            ),
            "1080" to successfulHealth(successCount = 2, startupMs = 1_500),
        )

        assertEquals(listOf(0, 1), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `past performance selects the best route within the same quality`() {
        val routes = listOf(
            route("unknown", ChannelQuality.FULL_HD, 0),
            route("slow", ChannelQuality.FULL_HD, 1),
            route("fast", ChannelQuality.FULL_HD, 2),
        )
        val health = mapOf(
            "slow" to successfulHealth(successCount = 2, startupMs = 12_000),
            "fast" to successfulHealth(successCount = 4, startupMs = 1_200),
        )

        assertEquals(listOf(2, 1, 0), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `unknown routes retain source order`() {
        val routes = listOf(
            route("second", ChannelQuality.FULL_HD, 2),
            route("first", ChannelQuality.FULL_HD, 1),
        )

        assertEquals(listOf(1, 0), IptvRouteHealthStore.rankedIndices(routes, emptyMap(), now))
    }

    @Test
    fun `known stuttering host is not automatically preferred for low startup time`() {
        val stableUrl = "http://stable.example/news.m3u8"
        val stutteringUrl = "http://120.234.44.98/live/news.m3u8"
        val routes = listOf(
            route(stableUrl, ChannelQuality.FULL_HD, 0),
            route(stutteringUrl, ChannelQuality.FULL_HD, 1),
        )
        val health = mapOf(
            stableUrl to successfulHealth(successCount = 2, startupMs = 2_500),
            stutteringUrl to successfulHealth(successCount = 20, startupMs = 200),
        )

        assertEquals(listOf(0, 1), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    private fun route(url: String, quality: ChannelQuality, sourceOrder: Int) = ChannelRoute(
        url = url,
        quality = quality,
        sourceOrder = sourceOrder,
    )

    private fun successfulHealth(successCount: Int, startupMs: Long) = IptvRouteHealth(
        successCount = successCount,
        averageStartupMs = startupMs,
        lastSuccessAt = now - 1_000,
    )
}

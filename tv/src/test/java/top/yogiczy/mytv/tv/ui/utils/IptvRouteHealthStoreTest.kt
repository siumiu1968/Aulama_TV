package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvRouteHealthStoreTest {
    private val now = 10_000_000L

    @Test
    fun `4K gets a capability bonus only when output supports it`() {
        val routes = listOf(
            route("1080", ChannelQuality.FULL_HD, 0),
            route("4k", ChannelQuality.UHD_4K, 1),
        )
        val health = mapOf(
            "1080" to successfulHealth(successCount = 20, startupMs = 900),
            "4k" to successfulHealth(successCount = 1, startupMs = 4_000),
        )

        assertEquals(listOf(0, 1), IptvRouteHealthStore.rankedIndices(routes, health, now, false))
        assertEquals(
            true,
            IptvRouteHealthStore.performanceScore(
                health["4k"], now, ChannelQuality.UHD_4K, true,
            ) > IptvRouteHealthStore.performanceScore(
                health["4k"], now, ChannelQuality.UHD_4K, false,
            ),
        )
    }

    @Test
    fun `cooling route is placed behind a usable candidate`() {
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

        assertEquals(listOf(1, 0), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `past performance selects the best route and leaves a poor route behind unknown`() {
        val routes = listOf(
            route("unknown", ChannelQuality.FULL_HD, 0),
            route("slow", ChannelQuality.FULL_HD, 1),
            route("fast", ChannelQuality.FULL_HD, 2),
        )
        val health = mapOf(
            "slow" to successfulHealth(successCount = 2, startupMs = 12_000),
            "fast" to successfulHealth(successCount = 4, startupMs = 1_200),
        )

        assertEquals(listOf(2, 0, 1), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `usable 1080 route stays ahead of a faster 720 route`() {
        val routes = listOf(
            route("fast-720", ChannelQuality.HD, 0),
            route("slower-1080", ChannelQuality.FULL_HD, 1),
        )
        val health = mapOf(
            "fast-720" to successfulHealth(successCount = 20, startupMs = 500),
            "slower-1080" to successfulHealth(successCount = 2, startupMs = 3_000),
        )

        assertEquals(listOf(1, 0), IptvRouteHealthStore.rankedIndices(routes, health, now))
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

    @Test
    fun `transport health is isolated for the same route`() {
        val routeUrl = "https://example.test/live.m3u8"
        val health = mapOf(
            IptvRouteHealthStore.candidateKey(routeUrl, "hk_relay") to IptvRouteHealth(
                failureCount = 2,
                consecutiveFailures = 2,
                lastFailureAt = now - 1_000L,
            ),
            IptvRouteHealthStore.candidateKey(routeUrl, "jp_relay") to
                successfulHealth(successCount = 8, startupMs = 1_100L),
        )

        assertEquals(
            listOf("jp_relay", "direct", "hk_relay"),
            IptvRouteHealthStore.rankedTransportIds(
                routeUrl = routeUrl,
                orderedTransportIds = listOf("hk_relay", "jp_relay", "direct"),
                health = health,
                quality = ChannelQuality.FULL_HD,
                supports4k = true,
                now = now,
            ),
        )
    }

    @Test
    fun `unknown transports retain server order`() {
        assertEquals(
            listOf("hk_relay", "jp_relay", "direct"),
            IptvRouteHealthStore.rankedTransportIds(
                routeUrl = "https://example.test/live.m3u8",
                orderedTransportIds = listOf("hk_relay", "jp_relay", "direct"),
                health = emptyMap(),
                quality = ChannelQuality.FULL_HD,
                supports4k = true,
                now = now,
            ),
        )
    }

    @Test
    fun `route ranking uses its best available transport`() {
        val fastViaJapan = "https://example.test/a.m3u8"
        val failedEverywhere = "https://example.test/b.m3u8"
        val routes = listOf(
            route(failedEverywhere, ChannelQuality.FULL_HD, 0),
            route(fastViaJapan, ChannelQuality.FULL_HD, 1),
        )
        val health = mapOf(
            IptvRouteHealthStore.candidateKey(failedEverywhere, "hk_relay") to
                IptvRouteHealth(consecutiveFailures = 1, lastFailureAt = now - 1_000L),
            IptvRouteHealthStore.candidateKey(failedEverywhere, "jp_relay") to
                IptvRouteHealth(consecutiveFailures = 1, lastFailureAt = now - 1_000L),
            IptvRouteHealthStore.candidateKey(fastViaJapan, "jp_relay") to
                successfulHealth(successCount = 5, startupMs = 900L),
        )

        assertEquals(
            listOf(1, 0),
            IptvRouteHealthStore.rankedIndices(
                routes = routes,
                health = health,
                now = now,
                transportIds = listOf("hk_relay", "jp_relay"),
            ),
        )
    }

    @Test
    fun `long stable viewing raises route score within the same quality`() {
        val routes = listOf(
            route("brief", ChannelQuality.FULL_HD, 0),
            route("long", ChannelQuality.FULL_HD, 1),
        )
        val health = mapOf(
            "brief" to successfulHealth(successCount = 2, startupMs = 900),
            "long" to successfulHealth(successCount = 2, startupMs = 1_200).copy(
                stableWatchMs = 35 * 60 * 1000L,
            ),
        )

        assertEquals(listOf(1, 0), IptvRouteHealthStore.rankedIndices(routes, health, now))
    }

    @Test
    fun `repeated quick exits lower route score without forcing cooldown`() {
        val stable = successfulHealth(successCount = 2, startupMs = 1_200)
        val abandoned = stable.copy(quickExitCount = 3)

        assertEquals(
            true,
            IptvRouteHealthStore.performanceScore(stable, now) >
                IptvRouteHealthStore.performanceScore(abandoned, now),
        )
    }

    @Test
    fun `failure cooldown starts at two minutes and caps at thirty`() {
        assertEquals(2 * 60 * 1000L, IptvRouteHealthStore.cooldownDurationMs(1))
        assertEquals(4 * 60 * 1000L, IptvRouteHealthStore.cooldownDurationMs(2))
        assertEquals(30 * 60 * 1000L, IptvRouteHealthStore.cooldownDurationMs(8))
    }

    @Test
    fun `seven day decay moves learned evidence toward neutral`() {
        val health = successfulHealth(successCount = 12, startupMs = 600).copy(
            successEwma = 0.95,
            stableWatchMs = 60 * 60 * 1000L,
            lastUpdatedAt = now,
        )
        val fresh = IptvRouteHealthStore.performanceScore(health, now)
        val old = IptvRouteHealthStore.performanceScore(health, now + 28L * 24 * 60 * 60 * 1000)

        assertTrue(fresh > old)
        assertTrue(kotlin.math.abs(old - 50.0) < kotlin.math.abs(fresh - 50.0))
    }

    @Test
    fun `automatic switch requires twenty point advantage unless current is unavailable`() {
        assertEquals(false, IptvRouteHealthStore.shouldAutoSwitch(50.0, 69.9))
        assertEquals(true, IptvRouteHealthStore.shouldAutoSwitch(50.0, 70.0))
        assertEquals(true, IptvRouteHealthStore.shouldAutoSwitch(90.0, 10.0, true))
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

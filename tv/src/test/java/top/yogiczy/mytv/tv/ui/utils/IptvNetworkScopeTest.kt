package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality

class IptvNetworkScopeTest {
    @Test
    fun `scoped record wins while legacy V2 record remains a fallback`() {
        val routeUrl = "https://example.test/live.m3u8"
        val legacyKey = IptvRouteHealthStore.candidateKey(routeUrl, "direct")
        val wifiKey = IptvRouteHealthStore.scopedCandidateKey(
            routeUrl,
            "direct",
            IptvNetworkScope.WIFI,
        )
        val legacy = IptvRouteHealth(preferredPlaybackMode = IptvPlaybackMode.IJK.name)
        val wifi = IptvRouteHealth(preferredPlaybackMode = IptvPlaybackMode.MEDIA3.name)

        assertEquals(
            legacy,
            IptvRouteHealthStore.candidateHealth(
                mapOf(legacyKey to legacy),
                routeUrl,
                "direct",
                IptvNetworkScope.WIFI,
            ),
        )
        assertEquals(
            wifi,
            IptvRouteHealthStore.candidateHealth(
                mapOf(legacyKey to legacy, wifiKey to wifi),
                routeUrl,
                "direct",
                IptvNetworkScope.WIFI,
            ),
        )
    }

    @Test
    fun `different network scopes cannot share a learned candidate key`() {
        val routeUrl = "https://example.test/live.m3u8"
        assertNotEquals(
            IptvRouteHealthStore.scopedCandidateKey(routeUrl, "direct", IptvNetworkScope.WIFI),
            IptvRouteHealthStore.scopedCandidateKey(routeUrl, "direct", IptvNetworkScope.ETHERNET),
        )
    }

    @Test
    fun `hashed scoped key can lazily inherit its legacy V2 candidate`() {
        val routeUrl = "https://example.test/live.m3u8"
        val legacy = IptvRouteHealthStore.candidateKey(
            routeUrl,
            "direct",
        )
        val scoped = IptvRouteHealthStore.scopedCandidateKey(
            routeUrl,
            "direct",
            IptvNetworkScope.WIFI,
        )

        val oldHealth = IptvRouteHealth(
            successCount = 8,
            preferredPlaybackMode = IptvPlaybackMode.IJK.name,
        )

        assertFalse(scoped.contains(routeUrl))
        assertEquals(
            oldHealth,
            IptvRouteHealthStore.previousHealthForWrite(
                mapOf(legacy to oldHealth),
                scoped,
                legacy,
            ),
        )
    }

    @Test
    fun `token rotation keeps the same private fingerprint without merging channel ids`() {
        val first = "https://example.test/live.m3u8?id=82&token=secret-one&expires=100"
        val rotated = "https://example.test/live.m3u8?expires=200&id=82&token=secret-two"
        val otherChannel = "https://example.test/live.m3u8?id=83&token=secret-two&expires=200"

        val firstKey = IptvRouteHealthStore.scopedCandidateKey(
            first,
            "direct",
            IptvNetworkScope.WIFI,
        )
        val rotatedKey = IptvRouteHealthStore.scopedCandidateKey(
            rotated,
            "direct",
            IptvNetworkScope.WIFI,
        )

        assertEquals(firstKey, rotatedKey)
        assertNotEquals(
            firstKey,
            IptvRouteHealthStore.scopedCandidateKey(
                otherChannel,
                "direct",
                IptvNetworkScope.WIFI,
            ),
        )
        assertFalse(firstKey.contains("secret"))
        assertFalse(firstKey.contains("example.test"))
    }

    @Test
    fun `static channel key remains part of the route identity`() {
        val channelA = IptvRouteHealthStore.scopedCandidateKey(
            "https://example.test/live.m3u8?key=channel-a",
            "direct",
            IptvNetworkScope.WIFI,
        )
        val channelB = IptvRouteHealthStore.scopedCandidateKey(
            "https://example.test/live.m3u8?key=channel-b",
            "direct",
            IptvNetworkScope.WIFI,
        )

        assertNotEquals(channelA, channelB)
    }

    @Test
    fun `handover is distinct from an unchanged network scope`() {
        assertFalse(isIptvNetworkHandover(IptvNetworkScope.WIFI, IptvNetworkScope.WIFI))
        assertTrue(isIptvNetworkHandover(IptvNetworkScope.WIFI, IptvNetworkScope.ETHERNET))
        assertFalse(isIptvNetworkHandover(IptvNetworkScope.WIFI, IptvNetworkScope.UNKNOWN))
        assertFalse(isIptvNetworkHandover(IptvNetworkScope.UNKNOWN, IptvNetworkScope.WIFI))
    }

    @Test
    fun `scoped transport ranking does not reuse another network result`() {
        val routeUrl = "https://example.test/live.m3u8"
        val now = 1_000_000L
        val health = mapOf(
            IptvRouteHealthStore.scopedCandidateKey(routeUrl, "direct", IptvNetworkScope.WIFI) to
                IptvRouteHealth(consecutiveFailures = 1, lastFailureAt = now - 1L),
            IptvRouteHealthStore.scopedCandidateKey(routeUrl, "hk_relay", IptvNetworkScope.WIFI) to
                IptvRouteHealth(successCount = 8, averageStartupMs = 900L, lastSuccessAt = now - 1L),
            IptvRouteHealthStore.scopedCandidateKey(routeUrl, "direct", IptvNetworkScope.ETHERNET) to
                IptvRouteHealth(successCount = 8, averageStartupMs = 900L, lastSuccessAt = now - 1L),
        )

        assertEquals(
            listOf("hk_relay", "direct"),
            IptvRouteHealthStore.rankedTransportIds(
                routeUrl = routeUrl,
                orderedTransportIds = listOf("direct", "hk_relay"),
                health = health,
                quality = ChannelQuality.FULL_HD,
                supports4k = true,
                networkScope = IptvNetworkScope.WIFI,
                now = now,
            ),
        )
    }
}

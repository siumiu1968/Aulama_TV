package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvRoutePriorityStoreTest {
    private val routes = listOf(
        ChannelRoute("route-1"),
        ChannelRoute("route-2"),
        ChannelRoute("route-3"),
        ChannelRoute("route-4"),
    )

    @Test
    fun `manual priorities are attempted before automatic ranking`() {
        assertEquals(
            listOf(2, 0, 3, 1),
            mergeRouteAttemptOrder(
                routes = routes,
                priorityUrls = listOf("route-3", "route-1"),
                automaticIndices = listOf(3, 1, 0, 2),
            ),
        )
    }

    @Test
    fun `explicitly selected route temporarily overrides saved priorities`() {
        assertEquals(
            listOf(1, 2, 0, 3),
            mergeRouteAttemptOrder(
                routes = routes,
                priorityUrls = listOf("route-3", "route-1"),
                automaticIndices = listOf(3, 1, 0, 2),
                requestedIndex = 1,
            ),
        )
    }

    @Test
    fun `stale and duplicate priority urls are ignored`() {
        assertEquals(
            listOf(2, 0, 1, 3),
            mergeRouteAttemptOrder(
                routes = routes,
                priorityUrls = listOf("missing", "route-3", "route-3"),
                automaticIndices = listOf(0, 1, 2, 3),
            ),
        )
    }


    @Test
    fun `manual 4K tries every other 4K before any lower quality fallback`() {
        val mixedRoutes = listOf(
            ChannelRoute("4k-primary", ChannelQuality.UHD_4K),
            ChannelRoute("1080-priority", ChannelQuality.FULL_HD),
            ChannelRoute("4k-backup-1", ChannelQuality.UHD_4K),
            ChannelRoute("720", ChannelQuality.HD),
            ChannelRoute("4k-backup-2", ChannelQuality.UHD_4K),
        )

        assertEquals(
            listOf(2, 0, 4, 1, 3),
            keepManualFourKFallbacksTogether(
                routes = mixedRoutes,
                attemptOrder = listOf(2, 1, 3, 0, 4),
                requestedIndex = 2,
            ),
        )
    }

    @Test
    fun `manual 1080p keeps the existing attempt order`() {
        val mixedRoutes = listOf(
            ChannelRoute("4k", ChannelQuality.UHD_4K),
            ChannelRoute("1080", ChannelQuality.FULL_HD),
            ChannelRoute("720", ChannelQuality.HD),
        )
        val order = listOf(1, 0, 2)

        assertEquals(order, keepManualFourKFallbacksTogether(mixedRoutes, order, 1))
    }
}

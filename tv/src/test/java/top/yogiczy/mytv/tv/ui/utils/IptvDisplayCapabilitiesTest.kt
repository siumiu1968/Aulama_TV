package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvDisplayCapabilitiesTest {
    private val routes = listOf(
        ChannelRoute("4k-primary", ChannelQuality.UHD_4K),
        ChannelRoute("1080", ChannelQuality.FULL_HD),
        ChannelRoute("4k-backup", ChannelQuality.UHD_4K),
    )

    @Test
    fun `automatic selection prefers SDR route when display has no HDR output`() {
        assertEquals(
            listOf(1, 0, 2),
            orderRoutesForDisplay(
                routes = routes,
                rankedIndices = listOf(0, 2, 1),
                requestedIndex = null,
                supportsHdrOutput = false,
            ),
        )
    }

    @Test
    fun `manual 4K selection remains first on display without HDR output`() {
        assertEquals(
            listOf(2, 1, 0),
            orderRoutesForDisplay(
                routes = routes,
                rankedIndices = listOf(0, 2, 1),
                requestedIndex = 2,
                supportsHdrOutput = false,
            ),
        )
    }

    @Test
    fun `HDR display keeps quality and health ranking`() {
        assertEquals(
            listOf(0, 2, 1),
            orderRoutesForDisplay(
                routes = routes,
                rankedIndices = listOf(0, 2, 1),
                requestedIndex = null,
                supportsHdrOutput = true,
            ),
        )
    }
}

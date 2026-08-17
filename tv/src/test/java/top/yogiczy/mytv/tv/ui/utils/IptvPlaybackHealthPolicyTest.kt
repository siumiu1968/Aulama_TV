package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality

class IptvPlaybackHealthPolicyTest {
    @Test
    fun `direct 4K waits fifteen seconds while relay keeps its longer window`() {
        assertEquals(
            15_000L,
            IptvPlaybackHealthPolicy.firstFrameTimeoutMsFor(
                quality = ChannelQuality.UHD_4K,
                isRelay = false,
            ),
        )
        assertEquals(
            30_000L,
            IptvPlaybackHealthPolicy.firstFrameTimeoutMsFor(
                quality = ChannelQuality.UHD_4K,
                isRelay = true,
            ),
        )
        assertEquals(
            12_000L,
            IptvPlaybackHealthPolicy.firstFrameTimeoutMsFor(
                quality = ChannelQuality.FULL_HD,
                isRelay = false,
            ),
        )
    }

    @Test
    fun `first frame is degraded only after twelve seconds`() {
        val state = IptvPlaybackHealthPolicy.start(1_000L)

        assertNull(IptvPlaybackHealthPolicy.evaluate(state, 12_999L))
        assertEquals(
            IptvDegradationReason.FirstFrameTimeout,
            IptvPlaybackHealthPolicy.evaluate(state, 13_000L),
        )
    }

    @Test
    fun `relay can use a longer first frame deadline`() {
        val state = IptvPlaybackHealthPolicy.start(1_000L)

        assertNull(IptvPlaybackHealthPolicy.evaluate(state, 20_000L, 30_000L))
        assertEquals(
            IptvDegradationReason.FirstFrameTimeout,
            IptvPlaybackHealthPolicy.evaluate(state, 31_000L, 30_000L),
        )
    }

    @Test
    fun `three stalls inside forty five seconds trigger degradation`() {
        var state = IptvPlaybackHealthPolicy.onFirstFrame(
            IptvPlaybackHealthPolicy.start(0L),
            1_000L,
        )
        listOf(10_000L, 30_000L, 54_000L).forEach { timestamp ->
            state = IptvPlaybackHealthPolicy.onBuffering(state, true, timestamp)
            state = IptvPlaybackHealthPolicy.onBuffering(state, false, timestamp + 500L)
        }

        assertEquals(
            IptvDegradationReason.RepeatedStalls,
            IptvPlaybackHealthPolicy.evaluate(state, 54_000L),
        )
    }

    @Test
    fun `continuous buffering triggers recovery after twelve seconds`() {
        var state = IptvPlaybackHealthPolicy.onFirstFrame(
            IptvPlaybackHealthPolicy.start(0L),
            1_000L,
        )
        state = IptvPlaybackHealthPolicy.onBuffering(state, true, 10_000L)

        assertNull(IptvPlaybackHealthPolicy.evaluate(state, 21_999L))
        assertEquals(
            IptvDegradationReason.LongRebuffer,
            IptvPlaybackHealthPolicy.evaluate(state, 22_000L),
        )
    }

    @Test
    fun `buffer ratio requires sixty second window and more than fifteen percent`() {
        var state = IptvPlaybackHealthPolicy.onFirstFrame(
            IptvPlaybackHealthPolicy.start(0L),
            1_000L,
        )
        state = IptvPlaybackHealthPolicy.onBuffering(state, true, 20_000L)
        state = IptvPlaybackHealthPolicy.onBuffering(state, false, 30_000L)

        assertNull(IptvPlaybackHealthPolicy.evaluate(state, 60_999L))
        val reason = IptvPlaybackHealthPolicy.evaluate(state, 61_000L)
        assertTrue(reason is IptvDegradationReason.ExcessiveBuffering)
        assertTrue((reason as IptvDegradationReason.ExcessiveBuffering).ratio > 0.15)
    }
}

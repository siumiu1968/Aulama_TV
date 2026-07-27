package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvPlaybackHealthPolicyTest {
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

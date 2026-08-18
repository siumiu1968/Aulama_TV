package top.yogiczy.mytv.tv.ui.screens.videoplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerContinuousHealthTest {
    @Test
    fun `buffering resets the continuous healthy playback clock`() {
        var healthyStartMs = updatedContinuousHealthStartMs(0L, 1_000L, healthy = true)
        assertEquals(20_000L, continuousHealthyDurationMs(healthyStartMs, 21_000L, true))

        healthyStartMs = updatedContinuousHealthStartMs(
            previousStartMs = healthyStartMs,
            nowMs = 21_000L,
            healthy = false,
        )
        assertEquals(0L, healthyStartMs)

        healthyStartMs = updatedContinuousHealthStartMs(healthyStartMs, 26_000L, healthy = true)
        assertEquals(35_000L, continuousHealthyDurationMs(healthyStartMs, 61_000L, true))
    }

    @Test
    fun `healthy clock keeps its original start until playback degrades`() {
        val healthyStartMs = updatedContinuousHealthStartMs(1_000L, 30_000L, healthy = true)
        assertEquals(1_000L, healthyStartMs)
        assertEquals(60_000L, continuousHealthyDurationMs(healthyStartMs, 61_000L, true))
    }
}

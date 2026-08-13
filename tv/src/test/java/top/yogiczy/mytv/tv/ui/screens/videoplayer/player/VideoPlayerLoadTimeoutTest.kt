package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerLoadTimeoutTest {
    @Test
    fun `4K requested first-frame wait overrides an aggressive global timeout`() {
        assertEquals(15_000L, effectiveFirstFrameTimeoutMs(5_000L, 15_000L))
    }

    @Test
    fun `a longer user timeout remains respected`() {
        assertEquals(20_000L, effectiveFirstFrameTimeoutMs(20_000L, 15_000L))
    }

    @Test
    fun `IJK live telemetry disables the position-only interrupt watchdog`() {
        assertFalse(shouldArmTimelineStallWatchdog(false, 10_000L, 11_000L))
        assertTrue(shouldArmTimelineStallWatchdog(true, 10_000L, 11_000L))
    }
}

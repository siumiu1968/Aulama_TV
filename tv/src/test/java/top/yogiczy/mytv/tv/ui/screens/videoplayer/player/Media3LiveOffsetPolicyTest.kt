package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3LiveOffsetPolicyTest {
    @Test
    fun `seek backwards when target delay is larger`() {
        assertEquals(
            14_000L,
            liveOffsetSeekPositionMs(
                currentPositionMs = 20_000L,
                currentOffsetMs = 4_000L,
                targetOffsetMs = 10_000L,
            ),
        )
    }

    @Test
    fun `seek forwards when playback is too far behind live`() {
        assertEquals(
            24_000L,
            liveOffsetSeekPositionMs(
                currentPositionMs = 20_000L,
                currentOffsetMs = 14_000L,
                targetOffsetMs = 10_000L,
            ),
        )
    }

    @Test
    fun `skip tiny correction and clamp at window start`() {
        assertNull(liveOffsetSeekPositionMs(20_000L, 10_200L, 10_000L))
        assertEquals(0L, liveOffsetSeekPositionMs(2_000L, 1_000L, 10_000L))
    }

    @Test
    fun `successful offset verification resets consecutive failures`() {
        assertEquals(
            4,
            updatedLiveOffsetAdjustmentAttempts(
                currentAttempts = 3,
                synchronized = false,
            ),
        )
        assertEquals(
            0,
            updatedLiveOffsetAdjustmentAttempts(
                currentAttempts = 3,
                synchronized = true,
            ),
        )
        assertEquals(
            1,
            updatedLiveOffsetAdjustmentAttempts(
                currentAttempts = 0,
                synchronized = false,
            ),
        )
    }
}

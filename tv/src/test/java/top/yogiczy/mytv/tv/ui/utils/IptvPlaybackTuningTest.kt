package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvPlaybackTuningTest {
    @Test
    fun `fast and stable Media3 buffer values stay deliberate`() {
        assertEquals(Media3BufferTuning(8_000, 35_000, 1_200, 3_000), media3BufferTuning(IptvStabilityProfile.FAST_START))
        assertEquals(Media3BufferTuning(12_000, 45_000, 2_500, 6_000), media3BufferTuning(IptvStabilityProfile.STABLE_LIVE))
    }

    @Test
    fun `stable IJK enables bounded packet buffering without enlarging protocol buffer`() {
        assertEquals(IjkBufferTuning(256 * 1024L, 1, 0), ijkBufferTuning(IptvStabilityProfile.FAST_START))
        assertEquals(IjkBufferTuning(256 * 1024L, 0, 1), ijkBufferTuning(IptvStabilityProfile.STABLE_LIVE))
    }

    @Test
    fun `only actual buffering reasons create a stability hint`() {
        assertTrue(isBufferingIssue("long-rebuffer"))
        assertTrue(isBufferingIssue("stall-threshold"))
        assertTrue(isBufferingIssue("audio-underrun"))
        assertTrue(isBufferingIssue("ijk-playback-stalled"))
        assertTrue(isBufferingIssue("buffer-ratio:0.250"))
        assertFalse(isBufferingIssue("ijk-decode-stalled"))
        assertFalse(isBufferingIssue("slow-rendering"))
        assertFalse(isBufferingIssue("first-frame-timeout"))
    }

    @Test
    fun `recent unrecovered issue only stabilizes the matching decoder mode`() {
        val now = 1_000_000L
        val health = IptvRouteHealth(
            lastBufferIssueAt = now - 1L,
            lastBufferIssueMode = IptvPlaybackMode.MEDIA3.name,
        )
        assertEquals(IptvStabilityProfile.STABLE_LIVE, selectIptvStabilityProfile(health, IptvPlaybackMode.MEDIA3, now))
        assertEquals(IptvStabilityProfile.FAST_START, selectIptvStabilityProfile(health, IptvPlaybackMode.IJK, now))
        assertEquals(
            IptvStabilityProfile.FAST_START,
            selectIptvStabilityProfile(
                health.copy(stableWatchSinceBufferIssueMs = BUFFER_ISSUE_RECOVERY_WATCH_MS),
                IptvPlaybackMode.MEDIA3,
                now,
            ),
        )
        assertEquals(
            IptvStabilityProfile.FAST_START,
            selectIptvStabilityProfile(
                health,
                IptvPlaybackMode.MEDIA3,
                now + BUFFER_ISSUE_PROFILE_EXPIRY_MS + 1L,
            ),
        )
    }

    @Test
    fun `engine fallback never inherits stable buffering from a different decoder`() {
        assertEquals(
            IptvStabilityProfile.FAST_START,
            profileAfterPlaybackModeFallback(
                IptvStabilityProfile.STABLE_LIVE,
                IptvPlaybackMode.IJK,
                IptvPlaybackMode.MEDIA3,
            ),
        )
    }
}

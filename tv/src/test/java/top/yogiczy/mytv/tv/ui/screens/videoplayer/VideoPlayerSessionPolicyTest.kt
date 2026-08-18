package top.yogiczy.mytv.tv.ui.screens.videoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackMode

class VideoPlayerSessionPolicyTest {
    @Test
    fun `old player callback cannot affect the active session`() {
        assertFalse(acceptsPlayerSessionCallback(4L, 5L, hasTerminalRetry = false))
        assertTrue(acceptsPlayerSessionCallback(5L, 5L, hasTerminalRetry = false))
    }

    @Test
    fun `terminal retry rejects even the active player callback`() {
        assertFalse(acceptsPlayerSessionCallback(5L, 5L, hasTerminalRetry = true))
        assertFalse(acceptsPlayerSessionCallback(5L, 6L, hasTerminalRetry = false))
    }

    @Test
    fun `pending prepare belongs only to its creating session`() {
        assertTrue(acceptsPendingPlayerPrepare(5L, 5L))
        assertFalse(acceptsPendingPlayerPrepare(5L, 6L))
        assertFalse(acceptsPendingPlayerPrepare(null, 5L))
    }

    @Test
    fun `old AndroidView output cannot prepare the new session`() {
        assertFalse(acceptsVideoOutputGeneration(4, 5))
        assertTrue(acceptsVideoOutputGeneration(5, 5))
    }

    @Test
    fun `new route Media3 item carrying target is verified without double prepare`() {
        val desired = LiveCaptionMediaItemKey(
            routeUrl = "https://example.com/live.m3u8",
            targetOffsetMs = 7_000L,
        )

        assertEquals(
            LiveCaptionOffsetAction.VERIFY_CURRENT_MEDIA_ITEM,
            liveCaptionOffsetAction(
                desired = desired,
                currentMode = IptvPlaybackMode.MEDIA3,
                preparedMedia3Item = desired,
                lastMedia3Reprepare = null,
            ),
        )
    }

    @Test
    fun `caption Media3 fallback cannot loop back for the same route and target`() {
        val desired = LiveCaptionMediaItemKey(
            routeUrl = "https://example.com/live.m3u8",
            targetOffsetMs = 7_000L,
        )

        assertEquals(
            LiveCaptionOffsetAction.REPREPARE_MEDIA3,
            liveCaptionOffsetAction(
                desired = desired,
                currentMode = IptvPlaybackMode.IJK,
                preparedMedia3Item = null,
                lastMedia3Reprepare = null,
            ),
        )
        assertEquals(
            LiveCaptionOffsetAction.REJECT,
            liveCaptionOffsetAction(
                desired = desired,
                currentMode = IptvPlaybackMode.IJK,
                preparedMedia3Item = null,
                lastMedia3Reprepare = desired,
            ),
        )

        val changedTarget = desired.copy(targetOffsetMs = 10_000L)
        assertEquals(
            LiveCaptionOffsetAction.REPREPARE_MEDIA3,
            liveCaptionOffsetAction(
                desired = changedTarget,
                currentMode = IptvPlaybackMode.IJK,
                preparedMedia3Item = null,
                lastMedia3Reprepare = desired,
            ),
        )
    }

    @Test
    fun `OFF rebuilds a targeted Media3 item once and is then clear`() {
        val routeUrl = "https://example.com/live.m3u8"
        val targeted = LiveCaptionMediaItemKey(routeUrl, 10_000L)
        val clear = LiveCaptionMediaItemKey(routeUrl, null)

        assertEquals(
            LiveCaptionOffsetAction.REPREPARE_MEDIA3,
            liveCaptionOffsetAction(
                desired = clear,
                currentMode = IptvPlaybackMode.MEDIA3,
                preparedMedia3Item = targeted,
                lastMedia3Reprepare = null,
            ),
        )
        assertEquals(
            LiveCaptionOffsetAction.VERIFY_CURRENT_MEDIA_ITEM,
            liveCaptionOffsetAction(
                desired = clear,
                currentMode = IptvPlaybackMode.MEDIA3,
                preparedMedia3Item = clear,
                lastMedia3Reprepare = clear,
            ),
        )
        assertEquals(
            LiveCaptionOffsetAction.ALREADY_CLEAR,
            liveCaptionOffsetAction(
                desired = clear,
                currentMode = IptvPlaybackMode.IJK,
                preparedMedia3Item = null,
                lastMedia3Reprepare = null,
            ),
        )
    }
}

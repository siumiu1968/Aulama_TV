package top.yogiczy.mytv.tv.ui.screens.videoplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

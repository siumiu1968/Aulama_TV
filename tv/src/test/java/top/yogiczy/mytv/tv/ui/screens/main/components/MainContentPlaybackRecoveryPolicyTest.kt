package top.yogiczy.mytv.tv.ui.screens.main.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainContentPlaybackRecoveryPolicyTest {
    @Test
    fun `only first frame timeout forces startup recovery`() {
        assertTrue(isFirstFrameTimeoutDegradation("first-frame-timeout"))
        assertFalse(isFirstFrameTimeoutDegradation("stall-threshold"))
        assertFalse(isFirstFrameTimeoutDegradation("buffer-ratio:0.200"))
        assertFalse(isFirstFrameTimeoutDegradation("audio-underrun"))
        assertFalse(isFirstFrameTimeoutDegradation("ijk-slow-rendering"))
    }
}

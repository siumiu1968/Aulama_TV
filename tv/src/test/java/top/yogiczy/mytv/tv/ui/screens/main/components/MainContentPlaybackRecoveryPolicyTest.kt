package top.yogiczy.mytv.tv.ui.screens.main.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainContentPlaybackRecoveryPolicyTest {
    @Test
    fun `hard playback stalls force recovery`() {
        assertTrue(requiresImmediatePlaybackRecovery("first-frame-timeout"))
        assertTrue(requiresImmediatePlaybackRecovery("long-rebuffer"))
        assertTrue(requiresImmediatePlaybackRecovery("ijk-playback-stalled"))
        assertTrue(requiresImmediatePlaybackRecovery("ijk-decode-stalled"))
        assertTrue(requiresImmediatePlaybackRecovery("slow-rendering"))
        assertTrue(requiresImmediatePlaybackRecovery("dropped-frames"))
        assertTrue(requiresImmediatePlaybackRecovery("audio-underrun"))
    }

    @Test
    fun `statistical buffering only switches to a clearly better route`() {
        assertFalse(requiresImmediatePlaybackRecovery("stall-threshold"))
        assertFalse(requiresImmediatePlaybackRecovery("buffer-ratio:0.200"))
        assertFalse(requiresImmediatePlaybackRecovery("ijk-av-sync-drift"))
    }
}

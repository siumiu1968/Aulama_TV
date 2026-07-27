package top.yogiczy.mytv.tv.ui.screens.videoplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerFallbackPolicyTest {
    @Test
    fun `network and timeout failures move to next transport`() {
        assertFalse(shouldTryPlaybackModeFallback("LOAD_TIMEOUT", 10003))
        assertFalse(shouldTryPlaybackModeFallback("ERROR_CODE_IO_UNSPECIFIED", 2001))
        assertFalse(shouldTryPlaybackModeFallback("IJKPlayerError", -1004))
    }

    @Test
    fun `decoder and unsupported errors try a compatible player`() {
        assertTrue(shouldTryPlaybackModeFallback("ERROR_CODE_DECODING_FAILED", 4003))
        assertTrue(shouldTryPlaybackModeFallback("AUDIO_DECODER_UNAVAILABLE", 10004))
        assertTrue(shouldTryPlaybackModeFallback("IJKPlayerError", -1010))
    }
}

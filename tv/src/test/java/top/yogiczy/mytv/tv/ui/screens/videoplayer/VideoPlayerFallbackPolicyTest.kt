package top.yogiczy.mytv.tv.ui.screens.videoplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackMode

class VideoPlayerFallbackPolicyTest {
    @Test
    fun `network and timeout failures move to next transport`() {
        assertFalse(shouldTryPlaybackModeFallback("LOAD_TIMEOUT", 10003))
        assertFalse(shouldTryPlaybackModeFallback("ERROR_CODE_IO_UNSPECIFIED", 2001))
        assertFalse(shouldTryPlaybackModeFallback("IJKPlayerError", -1004))
    }

    @Test
    fun `4K Media3 failure always gives the same route one IJK attempt`() {
        assertTrue(
            shouldTryPlaybackModeFallbackForRoute(
                quality = ChannelQuality.UHD_4K,
                currentMode = IptvPlaybackMode.MEDIA3,
                errorCodeName = "ERROR_CODE_IO_UNSPECIFIED",
                errorCode = 2001,
            ),
        )
        assertFalse(
            shouldTryPlaybackModeFallbackForRoute(
                quality = ChannelQuality.UHD_4K,
                currentMode = IptvPlaybackMode.IJK,
                errorCodeName = "ERROR_CODE_IO_UNSPECIFIED",
                errorCode = 2001,
            ),
        )
    }

    @Test
    fun `decoder and unsupported errors try a compatible player`() {
        assertTrue(shouldTryPlaybackModeFallback("ERROR_CODE_DECODING_FAILED", 4003))
        assertTrue(shouldTryPlaybackModeFallback("AUDIO_DECODER_UNAVAILABLE", 10004))
        assertTrue(shouldTryPlaybackModeFallback("IJKPlayerError", -1010))
    }

    @Test
    fun `unknown 4K starts Media3 then falls back once to hardware IJK`() {
        assertEquals(
            IptvPlaybackMode.MEDIA3,
            selectPlaybackModeForRoute(
                quality = ChannelQuality.UHD_4K,
                preferredMode = null,
                configuredType = Configs.VideoPlayerType.IJK,
                requiresTvbHlsSession = false,
                sdkInt = 33,
            ),
        )
        assertEquals(
            listOf(IptvPlaybackMode.IJK),
            playbackModeFallbackCandidates(ChannelQuality.UHD_4K, IptvPlaybackMode.MEDIA3),
        )
        assertTrue(
            playbackModeFallbackCandidates(ChannelQuality.UHD_4K, IptvPlaybackMode.IJK)
                .isEmpty(),
        )
    }

    @Test
    fun `4K route that previously rendered with IJK reuses IJK immediately`() {
        assertEquals(
            IptvPlaybackMode.IJK,
            selectPlaybackModeForRoute(
                quality = ChannelQuality.UHD_4K,
                preferredMode = IptvPlaybackMode.IJK,
                configuredType = Configs.VideoPlayerType.MEDIA3,
                requiresTvbHlsSession = false,
                sdkInt = 33,
            ),
        )
    }

    @Test
    fun `software IJK is never selected for 4K`() {
        assertEquals(
            IptvPlaybackMode.MEDIA3,
            selectPlaybackModeForRoute(
                quality = ChannelQuality.UHD_4K,
                preferredMode = IptvPlaybackMode.IJK_SOFTWARE,
                configuredType = Configs.VideoPlayerType.IJK,
                requiresTvbHlsSession = false,
                sdkInt = 33,
            ),
        )
    }
}

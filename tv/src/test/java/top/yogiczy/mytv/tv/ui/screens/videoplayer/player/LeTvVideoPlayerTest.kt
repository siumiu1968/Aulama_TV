package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LeTvVideoPlayerTest {
    @Test
    fun `legacy HDR host uses its compatible HTTP endpoint`() {
        assertEquals(
            "http://o11.163189.xyz/stream/tvb/fct4k/",
            leTvLegacyHdrPlaybackUrl("https://o11.163189.xyz/stream/tvb/fct4k/"),
        )
    }

    @Test
    fun `unverified hosts keep HTTPS`() {
        assertEquals(
            "https://example.com/live/4k.m3u8",
            leTvLegacyHdrPlaybackUrl("https://example.com/live/4k.m3u8"),
        )
    }
}

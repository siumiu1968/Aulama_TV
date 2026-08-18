package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvStreamUrlPolicyTest {
    @Test
    fun `standard and known extensionless live playlist urls start as HLS`() {
        assertTrue(isLikelyHlsStreamUrl("https://example.com/live/channel.m3u8?token=hidden"))
        assertTrue(isLikelyHlsStreamUrl("https://cdn3.indevs.in/stream/tvb/channel/"))
        assertTrue(isLikelyHlsStreamUrl("https://10.fast.hidns.vip/stream/tvb/channel/"))
        assertTrue(isLikelyHlsStreamUrl("https://prd-vcache.edge-global.akamai.tvb.com/live"))
    }

    @Test
    fun `ordinary extensionless pages and transport urls keep inferred handling`() {
        assertFalse(isLikelyHlsStreamUrl("https://example.com/"))
        assertFalse(isLikelyHlsStreamUrl("https://example.com/stream/tvb/channel/"))
        assertFalse(isLikelyHlsStreamUrl("https://example.com/video/live"))
        assertFalse(isLikelyHlsStreamUrl("rtsp://example.com/stream/tvb/channel/"))
    }
}

package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality

class M3uIptvParserTest {
    @Test
    fun `merges routes into logical channels and prefers 4K`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="368366" tvg-name="翡翠台（備用1）" group-title="香港｜1080p・實測",翡翠台 81（1080p 備用1）
            https://example.com/jade-1080.m3u8
            #EXTINF:-1 tvg-id="368366" tvg-name="翡翠台 4K" group-title="香港｜4K・實測",翡翠台 81（4K 主線）
            https://example.com/jade-4k.m3u8
            #EXTINF:-1 tvg-name="Now 新聞台" group-title="香港｜1080p・實測",Now 新聞台 332（1080p）
            #EXTVLCOPT:http-referrer=https://news.now.com/home/live
            https://example.com/now-main.m3u8
            #EXTINF:-1 tvg-name="Now 新聞台（備用1）" group-title="香港｜1080p・實測",Now 新聞台 332（1080p 備用1）
            https://example.com/now-backup.m3u8
        """.trimIndent()

        val result = M3uIptvParser().parse(playlist)

        assertEquals(1, result.size)
        assertEquals("香港", result.first().name)
        assertEquals(2, result.first().channelList.size)

        val jade = result.first().channelList.first()
        assertEquals("翡翠台 81", jade.name)
        assertEquals(2, jade.routes.size)
        assertEquals(ChannelQuality.UHD_4K, jade.routes.first().quality)
        assertEquals("https://example.com/jade-4k.m3u8", jade.routes.first().url)

        val now = result.first().channelList.last()
        assertEquals("Now 新聞台 332", now.name)
        assertEquals(2, now.routes.size)
        assertEquals("https://news.now.com/home/live", now.routes.first().referrer)
        assertTrue(now.routes.first().requestHeaders.containsKey("Referer"))
    }
}

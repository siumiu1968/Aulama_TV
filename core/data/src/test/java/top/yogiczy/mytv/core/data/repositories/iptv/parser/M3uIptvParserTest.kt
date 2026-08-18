package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.CaptionIdentifiers
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality

class M3uIptvParserTest {
    @Test
    fun `matches web caption catalogue identifiers and keeps M3U language metadata`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.en" tvg-language="en-US" tvg-name="News" group-title="美國｜新聞",News 1080p
            HTTPS://MEDIA.EXAMPLE.COM:443/live.m3u8
        """.trimIndent()

        val channel = M3uIptvParser().parse(playlist).first().channelList.first()

        assertEquals("news.en", channel.tvgId)
        assertEquals("en-US", channel.tvgLanguage)
        assertEquals("international-channel-upvjru", channel.captionChannelId)
        assertEquals("news.en", channel.routes.single().tvgId)
        assertEquals("en-US", channel.routes.single().tvgLanguage)
        assertEquals("route-7fioaj", channel.routes.single().captionRouteId)
        assertEquals(
            "https://media.example.com/live.m3u8",
            CaptionIdentifiers.canonicalStreamUrl(channel.routes.single().url),
        )
    }

    @Test
    fun `merges routes into logical channels and preserves curated order`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="368366" tvg-name="翡翠台 4K" group-title="香港｜4K・實測",翡翠台 81（4K 主線）
            https://example.com/jade-4k.m3u8
            #EXTINF:-1 tvg-id="368366" tvg-name="翡翠台（備用1）" group-title="香港｜1080p・實測",翡翠台 81（1080p 備用1）
            https://example.com/jade-1080.m3u8
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
        assertEquals("https://example.com/jade-1080.m3u8", jade.routes.last().url)

        val now = result.first().channelList.last()
        assertEquals("Now 新聞台 332", now.name)
        assertEquals(2, now.routes.size)
        assertEquals("https://news.now.com/home/live", now.routes.first().referrer)
        assertTrue(now.routes.first().requestHeaders.containsKey("Referer"))
    }

    @Test
    fun `uses the same curated Hong Kong logos as the web app`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="368361" tvg-name="TVB Plus" tvg-logo="https://old.example/tvb-plus.png" group-title="香港",TVB Plus 82（1080p）
            https://example.com/tvb-plus.m3u8
            #EXTINF:-1 tvg-id="3493" tvg-name="TVB星河" tvg-logo="https://old.example/xinghe.jpg" group-title="香港",TVB星河（1080p）
            https://example.com/xinghe.m3u8
            #EXTINF:-1 tvg-id="HongKongInternationalBusinessChannel.hk" tvg-name="HOY 76" tvg-logo="https://old.example/hoy-76.png" group-title="香港",HOY 76（1080p）
            https://example.com/hoy-76.m3u8
            #EXTINF:-1 tvg-id="HOYTV.hk" tvg-name="HOY 77" tvg-logo="https://old.example/hoy-77.png" group-title="香港",HOY 77（1080p）
            https://example.com/hoy-77.m3u8
            #EXTINF:-1 tvg-id="HOYInfotainment.hk" tvg-name="HOY 78" tvg-logo="https://old.example/hoy-78.png" group-title="香港",HOY 78（1080p）
            https://example.com/hoy-78.m3u8
            #EXTINF:-1 tvg-name="鳳凰衛視香港台" tvg-logo="https://old.example/phoenix.png" group-title="香港",鳳凰衛視香港台（1080p）
            https://example.com/phoenix.m3u8
        """.trimIndent()

        val channels = M3uIptvParser().parse(playlist).first().channelList

        assertEquals("https://aulama.org/iptv/channel-logos/tvb-plus.png", channels[0].logo)
        assertEquals("https://aulama.org/iptv/channel-logos/tvb-xinghe.png", channels[1].logo)
        assertEquals(
            "https://aulama.org/iptv/channel-logos/hoy-76.png?v=20260802-transparent",
            channels[2].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/hoy-77.png?v=20260802-transparent",
            channels[3].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/hoy-78.png?v=20260802-transparent",
            channels[4].logo,
        )
        assertEquals("https://aulama.org/iptv/channel-logos/phoenix-hk.png", channels[5].logo)
    }

    @Test
    fun `keeps the playlist logo for channels without a curated override`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CNA.sg" tvg-name="CNA" tvg-logo="https://example.com/cna.svg" group-title="新加坡｜新聞",CNA（1080p）
            https://example.com/cna.m3u8
        """.trimIndent()

        val channel = M3uIptvParser().parse(playlist).first().channelList.first()

        assertEquals("https://example.com/cna.svg", channel.logo)
    }

    @Test
    fun `fills missing China logos from the shared Aulama catalogue`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ZhejiangChildren.cn@HD" tvg-name="浙江少兒" group-title="中國",浙江少兒（1080p）
            https://example.com/zhejiang-children.m3u8
            #EXTINF:-1 tvg-id="Suzhou4K.cn@HD" tvg-name="蘇州 4K" group-title="中國",蘇州 4K（4K）
            https://example.com/suzhou-4k.m3u8
            #EXTINF:-1 tvg-id="CGTNFrench.cn@SD" tvg-name="CGTN Français" group-title="中國",CGTN Français（1080p）
            https://example.com/cgtn-fr.m3u8
        """.trimIndent()

        val channels = M3uIptvParser().parse(playlist).first().channelList

        assertEquals(
            "https://aulama.org/iptv/channel-logos/cn-zhejiang-children.png?v=20260814",
            channels[0].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/cn-suzhou-4k.png?v=20260814",
            channels[1].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/cn-cgtn-french.png?v=20260814",
            channels[2].logo,
        )
    }

    @Test
    fun `replaces rate limited and SVG international logos with shared PNG artwork`() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="BloombergTV.us@Plus" tvg-name="Bloomberg TV+" tvg-logo="https://i.imgur.com/old.png" group-title="美國｜財經",Bloomberg TV+
            https://example.com/bloomberg.m3u8
            #EXTINF:-1 tvg-id="YahooFinance.us@SD" tvg-name="Yahoo Finance" tvg-logo="https://upload.example/yahoo.svg" group-title="美國｜財經",Yahoo Finance
            https://example.com/yahoo.m3u8
            #EXTINF:-1 tvg-id="CNAOriginals.sg@SD" tvg-name="CNA Originals" tvg-logo="https://upload.example/cna.svg" group-title="新加坡｜紀錄",CNA Originals
            https://example.com/cna-originals.m3u8
        """.trimIndent()

        val channels = M3uIptvParser().parse(playlist).flatMap { it.channelList }

        assertEquals(
            "https://aulama.org/iptv/channel-logos/intl-bloomberg.png?v=20260814",
            channels[0].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/intl-yahoo-finance.png?v=20260814",
            channels[1].logo,
        )
        assertEquals(
            "https://aulama.org/iptv/channel-logos/intl-cna.png?v=20260814",
            channels[2].logo,
        )
    }
}

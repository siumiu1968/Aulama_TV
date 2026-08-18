package top.yogiczy.mytv.tv.ui.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvRouteProbeTest {
    @Test
    fun `probe keeps hls session cookie and route headers through child playlist`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/master" -> MockResponse()
                    .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                    .setHeader("Set-Cookie", "tvb-session=ok; Path=/")
                    .setBody(
                        "#EXTM3U\n" +
                            "#EXT-X-STREAM-INF:BANDWIDTH=4500000\n" +
                            "child.m3u8\n",
                    )
                "/child.m3u8" -> if (request.getHeader("Cookie") == "tvb-session=ok") {
                    MockResponse().setBody("#EXTM3U\n#EXTINF:6.0,\nsegment.ts\n")
                } else {
                    MockResponse().setResponseCode(403)
                }
                "/segment.ts" -> MockResponse().setBody("media-packet")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val route = ChannelRoute(
                url = server.url("/master").toString(),
                referrer = "https://example.test/guide",
                userAgent = "Aulama-Probe-Test",
            )

            val result = IptvRouteProbe.probeAll(listOf(route)).single()

            assertEquals(IptvRouteProbeStatus.AVAILABLE, result.status)
            repeat(3) {
                val request = server.takeRequest()
                assertEquals("https://example.test/guide", request.getHeader("Referer"))
                assertEquals("Aulama-Probe-Test", request.getHeader("User-Agent"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `master playlist probes the highest bandwidth variant`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
            1080/index.m3u8
        """.trimIndent()

        assertEquals(
            HlsProbeTarget("1080/index.m3u8", isPlaylist = true),
            hlsProbeTarget(playlist),
        )
    }

    @Test
    fun `live media playlist probes one segment behind live edge`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF:6.0,
            100.ts
            #EXTINF:6.0,
            101.ts
        """.trimIndent()

        assertEquals(
            HlsProbeTarget("100.ts", isPlaylist = false),
            hlsProbeTarget(playlist),
        )
    }

    @Test
    fun `hard recovery skips routes already confirmed unavailable`() {
        assertEquals(
            listOf(2, 3),
            remainingProbeViableRoutes(
                attemptOrder = listOf(0, 1, 2, 3),
                currentCursor = 0,
                results = listOf(
                    result(1, IptvRouteProbeStatus.UNAVAILABLE, 100),
                    result(2, IptvRouteProbeStatus.AVAILABLE, 200),
                    result(3, IptvRouteProbeStatus.INCONCLUSIVE, 3_000),
                ),
            ),
        )
    }

    @Test
    fun `soft revisit returns from 720p to an available 1080p route`() {
        val routes = listOf(
            route("recovered-1080", ChannelQuality.FULL_HD),
            route("current-720", ChannelQuality.HD),
            route("dead-1080", ChannelQuality.FULL_HD),
        )

        assertEquals(
            0,
            selectSoftRevisitRoute(
                routes = routes,
                currentRouteIndex = 1,
                results = listOf(
                    result(0, IptvRouteProbeStatus.AVAILABLE, 700),
                    result(1, IptvRouteProbeStatus.AVAILABLE, 100),
                    result(2, IptvRouteProbeStatus.UNAVAILABLE, 50),
                ),
                learnedScores = mapOf(0 to 35.0, 1 to 80.0, 2 to 90.0),
            ),
        )
    }

    @Test
    fun `soft revisit keeps current route when same quality gain is below margin`() {
        val routes = listOf(
            route("candidate", ChannelQuality.FULL_HD),
            route("current", ChannelQuality.FULL_HD),
        )

        assertEquals(
            null,
            selectSoftRevisitRoute(
                routes = routes,
                currentRouteIndex = 1,
                results = listOf(result(0, IptvRouteProbeStatus.AVAILABLE, 100)),
                learnedScores = mapOf(0 to 65.0, 1 to 50.0),
            ),
        )
    }

    @Test
    fun `probe only reorders unattempted routes and prefers available quality then speed`() {
        val routes = listOf(
            route("current", ChannelQuality.FULL_HD),
            route("fast-720", ChannelQuality.HD),
            route("slow-1080", ChannelQuality.FULL_HD),
            route("dead-4k", ChannelQuality.UHD_4K),
        )
        val results = listOf(
            result(0, IptvRouteProbeStatus.AVAILABLE, 500),
            result(1, IptvRouteProbeStatus.AVAILABLE, 300),
            result(2, IptvRouteProbeStatus.AVAILABLE, 900),
            result(3, IptvRouteProbeStatus.UNAVAILABLE, 100),
        )

        assertEquals(
            listOf(0, 2, 1, 3),
            reorderUnattemptedRoutesByProbe(routes, listOf(0, 1, 2, 3), 0, results),
        )
    }

    @Test
    fun `inconclusive routes stay ahead of definitively unavailable routes`() {
        val routes = listOf(
            route("current", ChannelQuality.FULL_HD),
            route("dead", ChannelQuality.FULL_HD),
            route("unknown", ChannelQuality.HD),
        )

        assertEquals(
            listOf(0, 2, 1),
            reorderUnattemptedRoutesByProbe(
                routes,
                listOf(0, 1, 2),
                0,
                listOf(
                    result(1, IptvRouteProbeStatus.UNAVAILABLE, 200),
                    result(2, IptvRouteProbeStatus.INCONCLUSIVE, 4_000),
                ),
            ),
        )
    }

    @Test
    fun `same quality keeps learned stability ahead of one fast probe sample`() {
        val routes = listOf(
            route("current", ChannelQuality.FULL_HD),
            route("instant-fast", ChannelQuality.FULL_HD),
            route("learned-stable", ChannelQuality.FULL_HD),
        )
        val results = listOf(
            result(1, IptvRouteProbeStatus.AVAILABLE, 100),
            result(2, IptvRouteProbeStatus.AVAILABLE, 900),
        )

        assertEquals(
            listOf(0, 2, 1),
            reorderUnattemptedRoutesByProbe(
                routes = routes,
                attemptOrder = listOf(0, 1, 2),
                currentCursor = 0,
                results = results,
                learnedScores = mapOf(1 to 45.0, 2 to 80.0),
            ),
        )
    }

    private fun route(url: String, quality: ChannelQuality) = ChannelRoute(
        url = "https://example.test/$url",
        quality = quality,
    )

    private fun result(index: Int, status: IptvRouteProbeStatus, elapsedMs: Long) =
        IptvRouteProbeResult(index, status, elapsedMs)
}

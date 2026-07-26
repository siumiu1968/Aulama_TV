package top.yogiczy.mytv.tv.account

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AulamaSyncProtocolTest {
    @Test
    fun `relay plan forbidden falls back to direct without invalidating account`() {
        assertTrue(AulamaRelayPlanHttpPolicy.shouldUseDirectFallback(403))
        assertTrue(AulamaRelayPlanHttpPolicy.shouldUseDirectFallback(404))
        assertFalse(AulamaRelayPlanHttpPolicy.shouldUseDirectFallback(401))
        assertFalse(AulamaRelayPlanHttpPolicy.shouldUseDirectFallback(500))
    }

    @Test
    fun `sync payload round trips through server document shape`() {
        val payload = AulamaSyncPayload(
            favorites = listOf("Jade", "ViuTV"),
            customSources = listOf(
                AulamaCustomSource(
                    id = "home",
                    name = "Home",
                    url = "https://example.com/home.m3u",
                )
            ),
            routePriorities = mapOf(
                "Jade" to listOf("https://example.com/jade.m3u8"),
            ),
        )
        val document = JsonObject().apply {
            addProperty("revision", 7)
            add("sync", AulamaSyncProtocol.toJson(payload))
        }

        assertEquals(AulamaSyncDocument(7, payload), AulamaSyncProtocol.parseDocument(document.toString()))
    }

    @Test
    fun `relay plan parser follows final candidates contract and rejects unsafe bearer targets`() {
        val routes = AulamaSyncProtocol.parseRelayPlan(
            """
                {"candidates":[
                  {"id":"hk_relay","region":"HK","url":"https://hk-edge.example/signed/live.m3u8?token=short-lived","authorization":"none"},
                  {"id":"jp_relay","region":"JP","url":"https://aulama.org/hermes-api/iptv/hls?relay=jp","authorization":"bearer"},
                  {"id":"direct","region":"direct","url":"http://origin.example/live.m3u8","authorization":"none"},
                  {"id":"unsafe","region":"香港","url":"https://evil.example/stream","authorization":"bearer"}
                ]}
            """.trimIndent()
        )

        assertEquals(listOf("hk_relay", "jp_relay", "direct"), routes.map { it.id })
        assertEquals(AulamaCandidateAuthorization.NONE, routes.first().authorization)
        assertEquals(AulamaCandidateAuthorization.BEARER, routes[1].authorization)
        assertEquals(AulamaPlaybackTransport.DIRECT, routes.last().transport)
        assertTrue(AulamaSyncProtocol.isTrustedBearerTarget(routes[1].url))
    }

    @Test
    fun `relay plan accepts dynamic candidate list when hk is omitted`() {
        val routes = AulamaSyncProtocol.parseRelayPlan(
            """
                {"candidates":[
                  {"id":"jp_relay","region":"JP","url":"https://aulama.org/hermes-api/iptv/hls?relay=jp","authorization":"bearer"},
                  {"id":"direct","region":"direct","url":"https://origin.example/live.m3u8","authorization":"none"}
                ]}
            """.trimIndent()
        )

        assertEquals(listOf("jp_relay", "direct"), routes.map { it.id })
    }

    @Test
    fun `bearer authorization is never bound to direct or non aulama urls`() {
        AulamaPlaybackAuthorization.clear()
        val direct = AulamaPlanCandidate(
            id = "direct",
            region = "direct",
            url = "https://origin.example/live.m3u8",
            authorization = AulamaCandidateAuthorization.BEARER,
            transport = AulamaPlaybackTransport.DIRECT,
        )
        val relay = AulamaPlanCandidate(
            id = "jp_relay",
            region = "JP",
            url = "https://aulama.org/hermes-api/iptv/hls?relay=jp",
            authorization = AulamaCandidateAuthorization.BEARER,
            transport = AulamaPlaybackTransport.RELAY,
        )
        val signedHk = AulamaPlanCandidate(
            id = "hk_relay",
            region = "HK",
            url = "https://hk-edge.example/signed/live.m3u8?token=short-lived",
            authorization = AulamaCandidateAuthorization.NONE,
            transport = AulamaPlaybackTransport.RELAY,
        )
        AulamaPlaybackAuthorization.bind(direct, "secret")
        AulamaPlaybackAuthorization.bind(signedHk, "secret")
        AulamaPlaybackAuthorization.bind(relay, "secret")

        assertEquals(emptyMap<String, String>(), AulamaPlaybackAuthorization.headersFor(direct.url))
        assertEquals(emptyMap<String, String>(), AulamaPlaybackAuthorization.headersFor(signedHk.url))
        assertEquals(
            mapOf("Authorization" to "Bearer secret"),
            AulamaPlaybackAuthorization.headersFor(relay.url),
        )
        AulamaPlaybackAuthorization.clearForUrl(relay.url)
        assertEquals(emptyMap<String, String>(), AulamaPlaybackAuthorization.headersFor(relay.url))
        AulamaPlaybackAuthorization.clear()
    }
}

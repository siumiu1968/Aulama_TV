package org.aulama.iptv.mobile.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.aulama.iptv.mobile.data.playback.RelayPlanCandidate

class AulamaAuthResponseParserTest {
    @Test
    fun parsesBackendCamelCasePublicKeyContract() {
        val parsed = AulamaAuthResponseParser.parsePasskeyRequest(
            """{"challenge_id":"challenge-1","publicKey":{"challenge":"abc","rpId":"aulama.org"}}"""
        )

        assertEquals("challenge-1", parsed?.requestId)
        assertEquals(
            """{"challenge":"abc","rpId":"aulama.org"}""",
            parsed?.requestJson,
        )
    }

    @Test
    fun rejectsResponseWithoutChallengeIdOrOptions() {
        assertNull(AulamaAuthResponseParser.parsePasskeyRequest("{\"publicKey\":{}}"))
        assertNull(AulamaAuthResponseParser.parsePasskeyRequest("{\"challenge_id\":\"id\"}"))
    }

    @Test
    fun parsesDynamicRelayPlanWithoutAddingBearerToSignedHongKongUrl() {
        val parsed = AulamaAuthResponseParser.parseRelayPlan(
            """{"candidates":[
                {"id":"hk_relay","url":"https://hk.example/signed","authorization":"none"},
                {"id":"jp_relay","url":"https://aulama.org/hermes-api/iptv/hls","authorization":"bearer"},
                {"id":"direct","url":"https://upstream.example/live.m3u8","authorization":"none"}
            ]}""".trimIndent()
        )

        assertEquals(
            listOf(
                RelayPlanCandidate("hk_relay", "https://hk.example/signed", false),
                RelayPlanCandidate("jp_relay", "https://aulama.org/hermes-api/iptv/hls", true),
                RelayPlanCandidate("direct", "https://upstream.example/live.m3u8", false),
            ),
            parsed,
        )
    }

    @Test
    fun relayPlanForbiddenFallsBackWithoutTreatingSessionAsExpired() {
        assertEquals(true, AulamaHttpStatusPolicy.relayPlanFallsBackToDirect(403))
        assertEquals(false, AulamaHttpStatusPolicy.relayPlanFallsBackToDirect(401))
    }

    @Test
    fun relayPlanRequestIncludesBoundedPlaybackMetadata() {
        val url = AulamaRelayPlanRequest.url(
            routeUrl = "https://upstream.example/live.m3u8",
            referrer = " https://referrer.example/${"r".repeat(3_000)} ",
            userAgent = "u".repeat(700),
        )

        assertEquals("hls", url.queryParameter("kind"))
        assertEquals("https://upstream.example/live.m3u8", url.queryParameter("url"))
        assertEquals(
            AulamaRelayPlanRequest.MAX_REFERRER_LENGTH,
            url.queryParameter("referrer")?.length,
        )
        assertEquals(
            AulamaRelayPlanRequest.MAX_USER_AGENT_LENGTH,
            url.queryParameter("user_agent")?.length,
        )
    }
}

package top.yogiczy.mytv.tv.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AulamaSyncMergePolicyTest {
    @Test
    fun `independent additions and deletions merge without resurrecting base values`() {
        val base = AulamaSyncPayload(favorites = listOf("Jade", "News"))
        val local = AulamaSyncPayload(favorites = listOf("Jade", "ViuTV"))
        val remote = AulamaSyncPayload(favorites = listOf("Jade", "News", "HOY"))

        assertEquals(
            listOf("Jade", "ViuTV", "HOY"),
            AulamaSyncMergePolicy.merge(base, local, remote).favorites,
        )
    }

    @Test
    fun `simultaneous priority edits preserve local order then remote alternatives`() {
        val base = AulamaSyncPayload(
            routePriorities = mapOf("Jade" to listOf("route-1")),
        )
        val local = AulamaSyncPayload(
            routePriorities = mapOf("Jade" to listOf("route-2", "route-1")),
        )
        val remote = AulamaSyncPayload(
            routePriorities = mapOf("Jade" to listOf("route-3", "route-1")),
        )

        assertEquals(
            listOf("route-2", "route-1", "route-3"),
            AulamaSyncMergePolicy.merge(base, local, remote).routePriorities["Jade"],
        )
    }

    @Test
    fun `unchanged local value accepts remote deletion`() {
        val source = AulamaCustomSource("home", "Home", "https://example.com/home.m3u")
        val base = AulamaSyncPayload(customSources = listOf(source))
        val local = AulamaSyncPayload(customSources = listOf(source))

        assertEquals(
            emptyList<AulamaCustomSource>(),
            AulamaSyncMergePolicy.merge(base, local, AulamaSyncPayload()).customSources,
        )
    }

    @Test
    fun `super admin tries server relays before direct while regular users stay direct`() {
        val plan = listOf(
            AulamaPlanCandidate(
                id = "hk_relay",
                region = "HK",
                url = "https://hk-edge.example/signed/live.m3u8?token=short-lived",
                authorization = AulamaCandidateAuthorization.NONE,
                transport = AulamaPlaybackTransport.RELAY,
            ),
            AulamaPlanCandidate(
                id = "jp_relay",
                region = "JP",
                url = "https://aulama.org/hermes-api/iptv/hls?relay=jp",
                authorization = AulamaCandidateAuthorization.BEARER,
                transport = AulamaPlaybackTransport.RELAY,
            ),
            AulamaPlanCandidate(
                id = "direct",
                region = "direct",
                url = "https://origin.example/stream",
                authorization = AulamaCandidateAuthorization.NONE,
                transport = AulamaPlaybackTransport.DIRECT,
            ),
        )
        val admin = AulamaPlaybackPolicy.candidates(
            directUrl = "https://origin.example/stream",
            isSuperAdmin = true,
            plan = plan,
        )
        val regular = AulamaPlaybackPolicy.candidates(
            directUrl = "https://origin.example/stream",
            isSuperAdmin = false,
            plan = plan,
        )

        assertEquals(
            listOf(
                AulamaPlaybackTransport.RELAY,
                AulamaPlaybackTransport.RELAY,
                AulamaPlaybackTransport.DIRECT,
            ),
            admin.map { it.transport },
        )
        assertEquals(listOf(AulamaPlaybackTransport.DIRECT), regular.map { it.transport })
    }
}

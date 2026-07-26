package org.aulama.iptv.mobile.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class RouteHealthTest {
    @Test
    fun hardOrderingIsManualRankThenTransportThenLocalHealth() {
        val manualOne = route("manual-1", 1)
        val manualTwo = route("manual-2", 2)
        val healthy = route("healthy", 3)
        val weak = route("weak", 4)
        val health = healthMap(
            healthy to strongRecord(),
            weak to weakRecord(),
            manualOne to weakRecord(),
            manualTwo to strongRecord(),
        )

        val ranked = PlaybackPlanPolicy.rank(
            routes = listOf(healthy, weak, manualTwo, manualOne),
            manualPriorityUrls = listOf(manualOne.url, manualTwo.url),
            relayPlans = listOf(healthy, weak, manualOne, manualTwo).associate {
                it.url to relayPlan(it.url)
            },
            superAdmin = true,
            accessToken = "access",
            fourKCapable = true,
            health = { health[it] ?: RouteHealthRecord() },
            nowMs = NOW,
        )

        assertEquals(
            listOf(
                "HK_RELAY|${manualOne.url}",
                "JP_RELAY|${manualOne.url}",
                "DIRECT|${manualOne.url}",
                "HK_RELAY|${manualTwo.url}",
                "JP_RELAY|${manualTwo.url}",
                "DIRECT|${manualTwo.url}",
                "HK_RELAY|${healthy.url}",
                "HK_RELAY|${weak.url}",
                "JP_RELAY|${healthy.url}",
                "JP_RELAY|${weak.url}",
                "DIRECT|${healthy.url}",
                "DIRECT|${weak.url}",
            ),
            ranked.map(PlaybackCandidate::key),
        )
        assertTrue(ranked.filter { it.transport == PlaybackTransport.HK_RELAY }.all {
            it.authorization == null && it.route.url.startsWith("https://hk-relay.example/")
        })
        assertTrue(ranked.filter { it.transport == PlaybackTransport.JP_RELAY }.all {
            it.authorization == "Bearer access" && it.route.url.startsWith("https://aulama.org/")
        })
    }

    @Test
    fun nonAdminReceivesDirectCandidatesOnly() {
        val route = route("one", 1)
        val candidates = PlaybackPlanPolicy.rank(
            routes = listOf(route),
            manualPriorityUrls = emptyList(),
            relayPlans = mapOf(route.url to relayPlan(route.url)),
            superAdmin = false,
            accessToken = null,
            fourKCapable = true,
            health = { RouteHealthRecord() },
            nowMs = NOW,
        )

        assertEquals(listOf(PlaybackTransport.DIRECT), candidates.map(PlaybackCandidate::transport))
        assertNull(candidates.single().authorization)
        assertEquals(route.url, candidates.single().route.url)
    }

    @Test
    fun bearerCandidateOutsideAulamaIsRejected() {
        val route = route("one", 1)
        val candidates = PlaybackPlanPolicy.rank(
            routes = listOf(route),
            manualPriorityUrls = emptyList(),
            relayPlans = mapOf(
                route.url to listOf(
                    RelayPlanCandidate("hk_relay", "https://evil.example/relay", true),
                    RelayPlanCandidate("direct", route.url, false),
                )
            ),
            superAdmin = true,
            accessToken = "secret",
            fourKCapable = true,
            health = { RouteHealthRecord() },
            nowMs = NOW,
        )

        assertEquals(listOf(PlaybackTransport.DIRECT), candidates.map(PlaybackCandidate::transport))
        assertTrue(candidates.none { it.authorization != null })
    }

    @Test
    fun missingHongKongCandidateNaturallyFallsBackToJapanThenDirect() {
        val route = route("one", 1).copy(
            referrer = "https://upstream.example/",
            userAgent = "Upstream Player",
        )
        val candidates = PlaybackPlanPolicy.rank(
            routes = listOf(route),
            manualPriorityUrls = emptyList(),
            relayPlans = mapOf(
                route.url to listOf(
                    RelayPlanCandidate("jp_relay", "https://aulama.org/hermes-api/iptv/hls", true),
                    RelayPlanCandidate("direct", route.url, false),
                )
            ),
            superAdmin = true,
            accessToken = "access",
            fourKCapable = true,
            health = { RouteHealthRecord() },
            nowMs = NOW,
        )

        assertEquals(
            listOf(PlaybackTransport.JP_RELAY, PlaybackTransport.DIRECT),
            candidates.map(PlaybackCandidate::transport),
        )
        assertEquals("Bearer access", candidates.first().authorization)
        assertNull(candidates.last().authorization)
        assertTrue(candidates.first().route.requestHeaders.isEmpty())
        assertEquals(route.requestHeaders, candidates.last().route.requestHeaders)
    }

    @Test
    fun degradationThresholdsDoNotSwitchEarly() {
        assertEquals(
            PlaybackHealthDecision.Starting,
            PlaybackDegradationPolicy.evaluate(11_999, false, 0, 1.0, false),
        )
        assertEquals(
            PlaybackHealthDecision.Degraded(DegradationReason.FIRST_FRAME_TIMEOUT),
            PlaybackDegradationPolicy.evaluate(12_000, false, 0, 1.0, false),
        )
        assertEquals(
            PlaybackHealthDecision.Healthy,
            PlaybackDegradationPolicy.evaluate(44_000, true, 2, 0.40, false),
        )
        assertEquals(
            PlaybackHealthDecision.Degraded(DegradationReason.REPEATED_STALLS),
            PlaybackDegradationPolicy.evaluate(44_000, true, 3, 0.05, false),
        )
        assertEquals(
            PlaybackHealthDecision.Healthy,
            PlaybackDegradationPolicy.evaluate(59_999, true, 0, 0.80, false),
        )
        assertEquals(
            PlaybackHealthDecision.Healthy,
            PlaybackDegradationPolicy.evaluate(60_000, true, 0, 0.15, false),
        )
        assertEquals(
            PlaybackHealthDecision.Degraded(DegradationReason.HIGH_BUFFER_RATIO),
            PlaybackDegradationPolicy.evaluate(60_000, true, 0, 0.151, false),
        )
    }

    @Test
    fun fatalCooldownStartsAtTwoMinutesAndCapsAtThirty() {
        assertEquals(2 * 60_000L, RouteCooldownPolicy.durationMs(1))
        assertEquals(4 * 60_000L, RouteCooldownPolicy.durationMs(2))
        assertEquals(16 * 60_000L, RouteCooldownPolicy.durationMs(4))
        assertEquals(30 * 60_000L, RouteCooldownPolicy.durationMs(5))
        assertEquals(30 * 60_000L, RouteCooldownPolicy.durationMs(20))

        val failed = RouteHealthRecord().updated(failureSample("DIRECT|route"))
        assertTrue(failed.isCoolingDown(NOW + 60_000L))
        assertFalse(failed.isCoolingDown(NOW + 2 * 60_000L))
    }

    @Test
    fun automaticFallbackRequiresTwentyPointImprovementAndSkipsCooldown() {
        val current = candidate("current")
        val insufficient = candidate("insufficient")
        val better = candidate("better")
        val cooling = candidate("cooling")
        val records = mapOf(
            current.key to scoreRecord(success = 0.1),
            insufficient.key to scoreRecord(success = 0.25),
            better.key to strongRecord(),
            cooling.key to strongRecord().copy(cooldownUntilMs = NOW + 10_000),
        )

        val selected = AutomaticFallbackPolicy.choose(
            current = current,
            candidates = listOf(cooling, insufficient, better),
            health = { records.getValue(it) },
            nowMs = NOW,
            fourKCapable = false,
        )

        assertEquals(better, selected)
        assertNull(
            AutomaticFallbackPolicy.choose(
                current = current,
                candidates = listOf(insufficient),
                health = { records.getValue(it) },
                nowMs = NOW,
                fourKCapable = false,
            )
        )
    }

    @Test
    fun recordedFatalSampleImmediatelyMakesNeutralFallbackEligible() {
        val current = candidate("current")
        val fallback = candidate("fallback")
        val records = mutableMapOf<String, RouteHealthRecord>()
        records[current.key] = RouteHealthRecord().updated(failureSample(current.key))
        records[fallback.key] = RouteHealthRecord()

        val selected = AutomaticFallbackPolicy.choose(
            current = current,
            candidates = listOf(current, fallback),
            health = { records.getValue(it) },
            nowMs = NOW,
            fourKCapable = false,
        )

        assertEquals(fallback, selected)
        assertTrue(records.getValue(current.key).isCoolingDown(NOW))
    }

    @Test
    fun fourKBonusAppliesOnlyWhenDeviceCapabilityIsConfirmed() {
        val record = RouteHealthRecord()
        assertEquals(
            record.score(NOW, false, ChannelQuality.FULL_HD),
            record.score(NOW, false, ChannelQuality.UHD_4K),
            0.001,
        )
        assertTrue(
            record.score(NOW, true, ChannelQuality.UHD_4K) >
                record.score(NOW, true, ChannelQuality.FULL_HD)
        )
    }

    @Test
    fun stableWatchingScoresAboveFatalOrEarlyManualExit() {
        val stable = RouteHealthRecord().updated(
            PlaybackHealthSample(
                candidateKey = "stable",
                startupMs = 700,
                bufferingRatio = 0.01,
                fatalError = false,
                degraded = false,
                stableWatchMs = 10 * 60_000L,
                manualEarlyExit = false,
                observedAtMs = NOW,
            )
        )
        val fatal = RouteHealthRecord().updated(failureSample("fatal"))
        val earlyExit = RouteHealthRecord().updated(
            PlaybackHealthSample(
                candidateKey = "early",
                startupMs = 700,
                bufferingRatio = 0.01,
                fatalError = false,
                degraded = false,
                stableWatchMs = 5_000,
                manualEarlyExit = true,
                observedAtMs = NOW,
            )
        )

        assertTrue(stable.score(NOW) > fatal.score(NOW))
        assertTrue(stable.score(NOW) > earlyExit.score(NOW))
    }

    private fun relayPlan(sourceUrl: String) = listOf(
        RelayPlanCandidate("hk_relay", "https://hk-relay.example/signed?url=$sourceUrl", false),
        RelayPlanCandidate("jp_relay", "https://aulama.org/jp?url=$sourceUrl", true),
        RelayPlanCandidate("direct", sourceUrl, false),
    )

    private fun route(name: String, order: Int) = ChannelRoute(
        url = "https://stream.example/$name.m3u8",
        sourceOrder = order,
    )

    private fun candidate(name: String) = PlaybackCandidate(
        route = route(name, 0),
        sourceUrl = "https://stream.example/$name.m3u8",
        transport = PlaybackTransport.DIRECT,
    )

    private fun healthMap(vararg values: Pair<ChannelRoute, RouteHealthRecord>): Map<String, RouteHealthRecord> =
        buildMap {
            values.forEach { (route, record) ->
                PlaybackTransport.entries.forEach { transport ->
                    put("${transport.name}|${route.url}", record)
                }
            }
        }

    private fun strongRecord() = scoreRecord(success = 1.0)

    private fun weakRecord() = scoreRecord(success = 0.0).copy(
        startupMsEwma = 10_000.0,
        bufferingRatioEwma = 0.5,
        failureEwma = 0.8,
        manualEarlyExitEwma = 0.7,
    )

    private fun scoreRecord(success: Double) = RouteHealthRecord(
        samples = 8,
        successEwma = success,
        startupMsEwma = 700.0,
        bufferingRatioEwma = 0.01,
        failureEwma = 0.0,
        stableWatchMinutesEwma = 8.0,
        manualEarlyExitEwma = 0.0,
        lastObservedAtMs = NOW,
    )

    private fun failureSample(key: String) = PlaybackHealthSample(
        candidateKey = key,
        startupMs = 12_000,
        bufferingRatio = 1.0,
        fatalError = true,
        degraded = false,
        stableWatchMs = 0,
        manualEarlyExit = false,
        observedAtMs = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

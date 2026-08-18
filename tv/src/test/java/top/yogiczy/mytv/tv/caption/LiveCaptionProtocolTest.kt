package top.yogiczy.mytv.tv.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.CaptionIdentifiers
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class LiveCaptionProtocolTest {
    @Test
    fun `FNV identifiers and handshake match web caption catalogue`() {
        val url = "https://media.example.com/live.m3u8"
        val channel = Channel(
            tvgId = "news.en",
            tvgLanguage = "en-US",
            captionChannelId = CaptionIdentifiers.channelId("news.en"),
        )
        val route = ChannelRoute(
            url = url,
            tvgId = "news.en",
            tvgLanguage = "en-US",
            captionRouteId = CaptionIdentifiers.routeId(url),
        )

        val subscription = LiveCaptionSubscription.from(channel, route)!!

        assertEquals("international-channel-upvjru", subscription.channelId)
        assertEquals("route-7fioaj", subscription.routeId)
        assertEquals(
            "{\"channel_id\":\"international-channel-upvjru\",\"tvg_id\":\"news.en\",\"route_id\":\"route-7fioaj\",\"source_url\":\"https://media.example.com/live.m3u8\"}",
            subscription.toJson(),
        )
    }

    @Test
    fun `handshake fails closed for non English or missing managed metadata`() {
        assertNull(
            LiveCaptionSubscription.from(
                Channel(tvgId = "news.zh", tvgLanguage = "zh-Hant"),
                ChannelRoute("https://example.com/live.m3u8"),
            )
        )
        assertNull(
            LiveCaptionSubscription.from(
                Channel(tvgLanguage = "en"),
                ChannelRoute("https://example.com/live.m3u8"),
            )
        )
    }

    @Test
    fun `cue reducer rejects stale revisions and keeps final sticky`() {
        val partial = cue(revision = 2, english = "Breaking news")
        val first = reduceLiveCaptionCue(LiveCaptionCueState(), partial)
        val stale = reduceLiveCaptionCue(first, partial.copy(revision = 1, english = "Old"))
        assertSame(first, stale)

        val final = reduceLiveCaptionCue(
            first,
            partial.copy(revision = 3, english = "Breaking news now", final = true),
        )
        val translation = reduceLiveCaptionCue(
            final,
            partial.copy(revision = 4, english = "", zhHant = "最新消息", final = false),
        )

        assertEquals("Breaking news now", translation.current?.english)
        assertEquals("最新消息", translation.current?.zhHant)
        assertTrue(translation.current?.final == true)
    }

    @Test
    fun `bilingual selection keeps previous translated cue during next English partial`() {
        val previous = cue(
            cueId = "one.en:1",
            seq = 1,
            revision = 4,
            english = "First",
            zhHant = "第一則",
            final = true,
        )
        val nextPartial = cue(
            cueId = "one.en:2",
            seq = 2,
            revision = 1,
            english = "Second",
        )

        assertEquals(
            previous,
            selectLiveCaptionCue(
                listOf(previous, nextPartial),
                LiveCaptionMode.BILINGUAL,
                previous,
            ),
        )
        assertEquals(
            nextPartial,
            selectLiveCaptionCue(listOf(previous, nextPartial), LiveCaptionMode.ENGLISH),
        )
    }

    @Test
    fun `pending presentation replaces same cue and only evicts superseded partials`() {
        val oldFinal = cue(cueId = "one.en:1", seq = 1, final = true)
        val oldPartial = cue(cueId = "one.en:2", seq = 2, english = "Old partial")
        val sameCueUpdate = oldPartial.copy(revision = 2, english = "Updated partial")
        val nextPartial = cue(cueId = "one.en:3", seq = 3, english = "Next partial")

        val updated = reducePendingLiveCaptionPresentations(
            linkedMapOf(
                liveCaptionCueKey(oldFinal) to oldFinal,
                liveCaptionCueKey(oldPartial) to oldPartial,
            ),
            sameCueUpdate,
        )
        assertEquals(2, updated.size)
        assertEquals("Updated partial", updated[liveCaptionCueKey(oldPartial)]?.english)

        val advanced = reducePendingLiveCaptionPresentations(updated, nextPartial)
        assertEquals(setOf("one.en:1", "one.en:3"), advanced.keys)
        assertTrue(advanced["one.en:1"]?.final == true)
    }

    @Test
    fun `English language tags accept BCP47 underscore and comma variants`() {
        assertTrue(isEnglishLanguageTag("en-US"))
        assertTrue(isEnglishLanguageTag("zh-Hant,en_GB"))
        assertFalse(isEnglishLanguageTag("zh-Hant,ja-JP"))
    }

    @Test
    fun `wall clock presentation uses server anchor and target delay`() {
        val receivedAt = 2_000_000L
        val anchored = cue(
            audioStartMs = 1_000_000L,
            serverNowMs = 1_005_000L,
            receivedAtMs = receivedAt,
        )

        assertEquals(
            2_000L,
            liveCaptionPresentationDelayMs(
                anchored,
                LiveCaptionMode.ENGLISH,
                nowMs = receivedAt,
            ),
        )
        assertEquals(
            5_000L,
            liveCaptionPresentationDelayMs(
                anchored,
                LiveCaptionMode.BILINGUAL,
                nowMs = receivedAt,
            ),
        )
        assertEquals(
            14_000L,
            liveCaptionPresentationDelayMs(
                anchored.copy(audioStartMs = 1_030_000L),
                LiveCaptionMode.BILINGUAL,
                nowMs = receivedAt,
            ),
        )
    }

    @Test
    fun `Chinese display modes share one playback delay contract`() {
        assertEquals(
            liveCaptionTargetDelayMs(LiveCaptionMode.BILINGUAL),
            liveCaptionTargetDelayMs(LiveCaptionMode.TRADITIONAL_CHINESE),
        )
        assertEquals(10_000L, liveCaptionTargetDelayMs(LiveCaptionMode.BILINGUAL))
    }

    @Test
    fun `expiry is readable and bounded`() {
        val short = cue(english = "News", suggestedHoldMs = 0)
        val long = cue(english = "x".repeat(500), suggestedHoldMs = 40_000)

        assertEquals(2_200L, liveCaptionExpiryDelayMs(short, LiveCaptionMode.ENGLISH, 1_000L))
        assertEquals(12_000L, liveCaptionExpiryDelayMs(long, LiveCaptionMode.ENGLISH, 1_000L))
    }

    @Test
    fun `reconnect is exponential and bounded to five attempts`() {
        assertEquals(listOf(800L, 1_600L, 3_200L, 6_400L, 8_000L),
            (1..5).map(LiveCaptionReconnectPolicy::delayMs))
        assertNull(LiveCaptionReconnectPolicy.delayMs(6))
    }

    @Test
    fun `pause invalidates offset proof and resume requires a fresh generation verification`() {
        val initial = LiveCaptionLifecycleSyncState(foreground = false)
        val firstResume = reduceLiveCaptionLifecycle(
            initial,
            LiveCaptionLifecycleEvent.RESUME,
        )
        assertTrue(firstResume.foreground)
        assertFalse(firstResume.synchronized)
        assertEquals(1L, firstResume.resumeGeneration)

        val firstVerified = firstResume.withVerifiedOffset(
            verified = true,
            expectedResumeGeneration = 1L,
        )
        assertTrue(firstVerified.synchronized)

        val paused = reduceLiveCaptionLifecycle(
            firstVerified,
            LiveCaptionLifecycleEvent.PAUSE_OR_STOP,
        )
        assertFalse(paused.foreground)
        assertFalse(paused.synchronized)
        assertFalse(
            paused.withVerifiedOffset(
                verified = true,
                expectedResumeGeneration = 1L,
            ).synchronized,
        )

        val secondResume = reduceLiveCaptionLifecycle(
            paused,
            LiveCaptionLifecycleEvent.RESUME,
        )
        assertEquals(2L, secondResume.resumeGeneration)
        assertFalse(secondResume.synchronized)
        assertFalse(
            secondResume.withVerifiedOffset(
                verified = true,
                expectedResumeGeneration = 1L,
            ).synchronized,
        )
        assertTrue(
            secondResume.withVerifiedOffset(
                verified = true,
                expectedResumeGeneration = 2L,
            ).synchronized,
        )
    }

    @Test
    fun `websocket request sends bearer and production origin without token in URL`() {
        val request = liveCaptionWebSocketRequest("secret-token")

        assertEquals("Bearer secret-token", request.header("Authorization"))
        assertEquals("https://aulama.org", request.header("Origin"))
        assertFalse(request.url.toString().contains("secret-token"))
        assertEquals("/hermes-auth/iptv/captions/ws", request.url.encodedPath)
    }

    private fun cue(
        cueId: String = "one.en:1",
        seq: Long = 1,
        revision: Long = 1,
        english: String = "Breaking news",
        zhHant: String = "",
        final: Boolean = false,
        audioStartMs: Long? = null,
        serverNowMs: Long? = null,
        receivedAtMs: Long? = null,
        suggestedHoldMs: Long? = null,
    ) = LiveCaptionCue(
        cueId = cueId,
        seq = seq,
        revision = revision,
        sourceId = "one.en:route-test",
        english = english,
        zhHant = zhHant,
        final = final,
        stability = if (final) "final" else "partial",
        audioStartMs = audioStartMs,
        serverNowMs = serverNowMs,
        receivedAtMs = receivedAtMs,
        suggestedHoldMs = suggestedHoldMs,
    )
}

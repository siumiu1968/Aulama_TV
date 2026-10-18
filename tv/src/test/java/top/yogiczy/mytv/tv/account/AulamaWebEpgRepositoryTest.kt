package top.yogiczy.mytv.tv.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import java.time.OffsetDateTime

class AulamaWebEpgRepositoryTest {
    private val now = OffsetDateTime.parse("2026-10-18T13:10:00+08:00").toInstant().toEpochMilli()
    private val channel = Channel(name = "無綫新聞台", epgName = "無綫新聞台 (免費)", tvgId = "368363")
    private val programmes = """[
        {"title":"下一節新聞","start":"2026-10-18T13:30:00+08:00","end":"2026-10-18T14:00:00+08:00"},
        {"title":"新聞報道","start":"2026-10-18T13:00:00+08:00","end":"2026-10-18T13:30:00+08:00"}
    ]"""
    private fun snapshot(programmesJson: String = programmes) =
        """{"channels":{"無綫新聞台":$programmesJson}}"""
    private fun live(channelName: String = "無綫新聞台") =
        """{"available":true,"channel":"$channelName","now":null,"next":$programmes}"""

    @Test
    fun `web programme offsets map to the correct Hong Kong instant and playlist aliases`() {
        val guide = AulamaWebEpgParser.parseChannel(live(), "無綫新聞台", now)!!
        assertEquals("無綫新聞台 (免費)", guide.guideId)
        assertEquals("新聞報道", guide.programmeList.first().title)
        assertEquals(now - 10 * 60_000L, guide.programmeList.first().startAt)
        val list = AulamaWebEpgParser.parseSnapshot(snapshot(), now)
        assertEquals(guide.programmeList, list.match(channel)?.programmeList)
    }

    @Test
    fun `stale public snapshot triggers the same live API fallback as the website`() = runBlocking {
        val requested = mutableListOf<String>()
        val stale = programmes.replace("2026-10-18", "2026-08-10")
        val repository = AulamaWebEpgRepository(
            fetchSnapshot = { snapshot(stale) },
            fetchChannel = { requested.add(it); live() },
            nowMs = { now },
        )
        val published = mutableListOf<Int>()
        val result = repository.getEpgList(listOf(channel)) { published.add(it.size) }
        assertEquals(listOf("無綫新聞台"), requested)
        assertEquals("新聞報道", result.match(channel)?.programmeList?.first()?.title)
        assertTrue(published.contains(1))
    }

    @Test
    fun `current public snapshot avoids unnecessary API requests`() = runBlocking {
        var calls = 0
        val repository = AulamaWebEpgRepository(
            fetchSnapshot = { snapshot() },
            fetchChannel = { calls++; error("Should use the public snapshot") },
            nowMs = { now },
        )
        assertEquals(1, repository.getEpgList(listOf(channel)).size)
        assertEquals(0, calls)
    }

    @Test
    fun `live responses cannot assign another channels schedule or expired data`() {
        assertNull(AulamaWebEpgParser.parseChannel(live("翡翠台"), "無綫新聞台", now))
        assertNull(AulamaWebEpgParser.parseChannel(live().replace("2026-10-18", "2026-10-17"), "無綫新聞台", now))
        assertNull(AulamaWebEpgParser.parseChannel("""{"available":false}""", "無綫新聞台", now))
    }

    @Test
    fun `invalid and reversed programme times are rejected without losing valid entries`() {
        val invalid = """{"title":"錯誤","start":"invalid","end":"2026-10-18T14:00:00+08:00"},
            {"title":"倒序","start":"2026-10-18T14:00:00+08:00","end":"2026-10-18T13:00:00+08:00"},"""
        val result = AulamaWebEpgParser.parseChannel(live().replace("\"next\":[", "\"next\":[$invalid"), "無綫新聞台", now)!!
        assertEquals(2, result.programmeList.size)
    }

    @Test
    fun `recent results are reused and a transient failure preserves the last usable schedule`() = runBlocking {
        var clock = now
        var calls = 0
        val repository = AulamaWebEpgRepository(
            fetchSnapshot = { error("Offline public source") },
            fetchChannel = { calls++; if (calls > 1) error("Temporarily offline") else live() },
            nowMs = { clock },
        )
        assertEquals(1, repository.getEpgList(listOf(channel)).size)
        assertEquals(1, repository.getEpgList(listOf(channel)).size)
        assertEquals(1, calls)
        clock += AulamaWebEpgRepository.CHANNEL_TTL_MS + 1
        assertEquals(1, repository.getEpgList(listOf(channel)).size)
        assertEquals(2, calls)
    }

    @Test
    fun `known Hong Kong names use canonical web names without trusting numeric EPG ids`() {
        assertEquals("翡翠台", AulamaWebEpgParser.webChannelName("翡翠台 (免費)"))
        assertEquals("Viu 頻道", AulamaWebEpgParser.webChannelName("ViuTV"))
        assertEquals("Now新聞台", AulamaWebEpgParser.webChannelName("now新聞"))
        assertEquals("港台電視31", AulamaWebEpgParser.webChannelName("香港電台31"))
        assertEquals("HOY77", AulamaWebEpgParser.webChannelName("HOY 77"))
        assertNull(AulamaWebEpgParser.webChannelName("368363"))
        assertNull(AulamaWebEpgParser.webChannelName("CCTV-1"))
    }

    @Test
    fun `cancellation is propagated rather than reported as a source failure`() = runBlocking {
        val repository = AulamaWebEpgRepository(
            fetchSnapshot = { throw CancellationException("cancelled") },
            fetchChannel = { live() },
            nowMs = { now },
        )
        try {
            repository.getEpgList(listOf(channel))
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }
    }
}

package top.yogiczy.mytv.core.data.repositories.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EpgChannelMatcherTest {
    @Test
    fun `maps Hong Kong playlist labels to verified guide ids`() {
        val cases = mapOf(
            "翡翠台 4K" to "翡翠台 (免費)",
            "TVB Plus（備用1）" to "TVB Plus (免費)",
            "無綫新聞台" to "無綫新聞台 (免費)",
            "明珠台 84" to "明珠台 (免費)",
            "ViuTV 99" to "ViuTV",
            "Now 新聞台 332" to "now新聞",
            "港台電視31" to "香港電台31",
            "港台電視35" to "香港電台35",
            "HOY 78" to "HOY 78",
        )

        cases.forEach { (input, expected) ->
            assertEquals(input, expected, EpgChannelMatcher.preferredGuideId(input))
        }
    }

    @Test
    fun `maps mainland channel labels without trusting old numeric ids`() {
        assertEquals("cctv1", EpgChannelMatcher.preferredGuideId("CCTV-1 綜合"))
        assertEquals("cctv5+", EpgChannelMatcher.preferredGuideId("CCTV-5+ 體育賽事"))
        assertEquals("cctv4亞洲", EpgChannelMatcher.preferredGuideId("CCTV-4 中文國際"))
        assertEquals("CCTV-4美洲頻道", EpgChannelMatcher.preferredGuideId("CCTV-4 美洲"))
        assertEquals("cgtn", EpgChannelMatcher.preferredGuideId("CGTN English"))
        assertEquals("cgtn阿拉伯語", EpgChannelMatcher.preferredGuideId("CGTN Arabic"))
        assertEquals("cgtn英文記錄片", EpgChannelMatcher.preferredGuideId("CGTN Documentary"))
    }

    @Test
    fun `normalization treats traditional and simplified guide ids as equal`() {
        assertEquals(
            EpgChannelMatcher.normalize("無綫新聞台 (免費)"),
            EpgChannelMatcher.normalize("无线新闻台 (免费)"),
        )
        assertEquals(
            EpgChannelMatcher.normalize("香港電台31"),
            EpgChannelMatcher.normalize("香港电台31"),
        )
    }

    @Test
    fun `parses XMLTV offsets and minute precision in Hong Kong time`() {
        val expected = Calendar.getInstance(TimeZone.getTimeZone("Asia/Hong_Kong")).apply {
            clear()
            set(2026, Calendar.JULY, 27, 16, 20, 0)
        }.timeInMillis

        assertEquals(expected, XmlTvTimeParser.parse("20260727162000 +0800"))
        assertEquals(expected, XmlTvTimeParser.parse("202607271620 +0800"))
        assertTrue(XmlTvTimeParser.parse("invalid") == 0L)
    }
}

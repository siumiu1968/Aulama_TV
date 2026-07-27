package top.yogiczy.mytv.core.data.repositories.epg

import top.yogiczy.mytv.core.data.entities.channel.Channel

/**
 * Maps playlist labels to the stable channel ids used by Aulama's preferred XMLTV guides.
 * Numeric epg.pw ids are deliberately ignored because several Hong Kong ids point to
 * similarly named overseas channels.
 */
object EpgChannelMatcher {
    private val translations = listOf(
        "无线" to "無綫",
        "新闻" to "新聞",
        "电视" to "電視",
        "电台" to "電台",
        "频道" to "頻道",
        "免费" to "免費",
        "亚洲" to "亞洲",
        "综合" to "綜合",
        "财经" to "財經",
        "电视剧" to "電視劇",
        "体育" to "體育",
        "电影" to "電影",
        "纪录" to "紀錄",
        "記錄" to "紀錄",
        "奥林匹克" to "奧林匹克",
        "国际" to "國際",
    )

    fun normalize(value: String): String {
        var normalized = value
            .trim()
            .lowercase()
            .replace('（', '(')
            .replace('）', ')')
        translations.forEach { (from, to) -> normalized = normalized.replace(from, to) }
        return normalized
            .replace(Regex("\\([^)]*(?:備用|主線|線路|自動|原生|瀏覽器|\\d{3,4}p|4k|uhd|fhd)[^)]*\\)"), "")
            .replace(Regex("(?:備用|主線|線路)\\s*\\d*$"), "")
            .replace(Regex("[^a-z0-9+\\p{L}]"), "")
    }

    fun preferredGuideId(value: String): String {
        val key = normalize(value)
        return when {
            key.contains("黃金翡翠台") -> "黃金翡翠台 (免費)"
            key.contains("tvbplus") -> "TVB Plus (免費)"
            key.contains("無綫新聞台") -> "無綫新聞台 (免費)"
            key.contains("明珠台") && !key.contains("劇集") -> "明珠台 (免費)"
            key.contains("翡翠台") && !key.contains("劇集") && !key.contains("娛樂") -> "翡翠台 (免費)"
            key.startsWith("viutv") || key == "viu99" -> "ViuTV"
            key.contains("now新聞") || key.contains("now新聞台") -> "now新聞"
            Regex("hoy(?:tv)?76").containsMatchIn(key) -> "HOY 76"
            Regex("hoy(?:tv)?77").containsMatchIn(key) -> "HOY 77"
            Regex("hoy(?:tv)?78").containsMatchIn(key) -> "HOY 78"
            (key.contains("港台電視") || key.contains("香港電台")) && key.contains("31") -> "香港電台31"
            (key.contains("港台電視") || key.contains("香港電台")) && key.contains("32") -> "香港電台32"
            (key.contains("港台電視") || key.contains("香港電台")) && key.contains("33") -> "香港電台33"
            (key.contains("港台電視") || key.contains("香港電台")) && key.contains("34") -> "香港電台34"
            (key.contains("港台電視") || key.contains("香港電台")) && key.contains("35") -> "香港電台35"
            key.contains("鳳凰") && key.contains("資訊") -> "鳳凰資訊"
            key.contains("鳳凰") && key.contains("中文") -> "鳳凰中文"
            key.contains("鳳凰") && key.contains("香港") -> "鳳凰香港"
            key == "cgtnenglish" || key == "cgtn" -> "cgtn"
            key == "cgtnarabic" -> "cgtn阿拉伯語"
            key == "cgtnespañol" || key == "cgtnespanol" -> "cgtn西班牙語"
            key == "cgtndocumentary" -> "cgtn英文記錄片"
            key == "cnasingapore" -> "CNA"
            key == "france24français" || key == "france24francais" -> "France 24 (French)"
            else -> cctvGuideId(key) ?: value.trim()
        }
    }

    fun lookupKey(channel: Channel): String = normalize(
        preferredGuideId(channel.epgName.ifBlank { channel.name })
    )

    fun filterSignature(values: Collection<String>): String = values
        .map(::preferredGuideId)
        .map(::normalize)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .joinToString("|")
        .hashCode()
        .toUInt()
        .toString(16)

    private fun cctvGuideId(key: String): String? {
        val match = Regex("cctv(\\d{1,2})(\\+|plus)?").find(key) ?: return null
        val number = match.groupValues[1]
        val plus = match.groupValues[2].isNotBlank()
        return when {
            number == "4" && (key.contains("美洲") || key.contains("america")) -> "CCTV-4美洲頻道"
            number == "4" && (key.contains("歐洲") || key.contains("europe")) -> "CCTV-4歐洲頻道"
            number == "4" -> "cctv4亞洲"
            plus -> "cctv$number+"
            else -> "cctv$number"
        }
    }
}

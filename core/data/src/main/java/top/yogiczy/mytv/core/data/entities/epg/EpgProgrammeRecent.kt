package top.yogiczy.mytv.core.data.entities.epg

/**
 * 頻道當前節目/下一個節目
 */
data class EpgProgrammeRecent(
    /**
     * 當前正在播放
     */
    val now: EpgProgramme? = null,

    /**
     * 稍後播放
     */
    val next: EpgProgramme? = null,
) {
    companion object {
        val EXAMPLE = EpgProgrammeRecent(
            now = EpgProgramme(
                title = "2023/2024賽季中國男子籃球職業聯賽季後賽12進8第五場",
                startAt = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                endAt = System.currentTimeMillis() + 1000 * 60 * 60 * 2,
            ),
            next = null,
        )
    }
}
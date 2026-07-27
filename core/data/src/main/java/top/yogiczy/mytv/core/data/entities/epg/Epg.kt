package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import java.util.Calendar

/**
 * 頻道節目單
 */
@Serializable
data class Epg(
    /** Preferred XMLTV channel id used for deterministic matching. */
    val guideId: String = "",

    /**
     * 頻道名稱
     */
    val channel: String = "",

    /** XMLTV id and display-name aliases observed in the source. */
    val aliases: List<String> = emptyList(),

    /**
     * 節目列表
     */
    val programmeList: List<EpgProgramme> = EpgProgrammeList(),
) {
    companion object {
        private val log = Logger.create(javaClass.simpleName)

        fun Epg.recentProgramme(): EpgProgrammeRecent {
            val currentTime = System.currentTimeMillis()

            // 確保 programmeList 按 startAt 排序
            if (programmeList.isEmpty()) {
                log.d("No programmes available for channel=${channel}")
                return EpgProgrammeRecent()
            }

            // 確保 programmeList 按 startAt 排序
            val sortedProgrammeList = programmeList.sortedBy { it.startAt }

            val liveProgramIndex = sortedProgrammeList.binarySearch {
                when {
                    currentTime < it.startAt -> 1
                    currentTime >= it.endAt -> -1
                    else -> 0
                }
            }

//            log.d("channel=${channel},liveProgramIndex=$liveProgramIndex")

            return if (liveProgramIndex >= 0) {
                EpgProgrammeRecent(
                    now = sortedProgrammeList[liveProgramIndex],
                    next = sortedProgrammeList.getOrNull(liveProgramIndex + 1)
                )
            } else {
                EpgProgrammeRecent()
            }
        }

        fun example(channel: Channel): Epg {
            return Epg(
                guideId = channel.epgName,
                channel = channel.epgName,
                programmeList = EpgProgrammeList(
                    List(100) { index ->
                        val startAt =
                            System.currentTimeMillis() - 3500 * 1000 * 24 * 2 + index * 3600 * 1000
                        EpgProgramme(
                            title = "${channel.epgName}節目${index + 1}",
                            startAt = startAt,
                            endAt = startAt + 3600 * 1000
                        )
                    }
                )
            )
        }

        fun empty(channel: Channel): Epg {
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            return Epg(
                guideId = channel.epgName,
                channel = channel.epgName,
                programmeList = EpgProgrammeList(
                    listOf(
                        EpgProgramme(
                            title = "暫無節目",
                            startAt = dayStart.timeInMillis,
                            endAt = dayEnd.timeInMillis,
                        )
                    )
                )
            )
        }
    }
}

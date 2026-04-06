package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel

/**
 * 節目預約
 */
@Serializable
data class EpgProgrammeReserve(
    /**
     * 頻道名稱
     */
    val channel: String = "",

    /**
     * 節目名稱
     */
    val programme: String = "",

    /**
     * 開始時間（時間戳）
     */
    val startAt: Long = 0,

    /**
     * 結束時間（時間戳）
     */
    val endAt: Long = 0,
) {
    fun test(channel: Channel, programme: EpgProgramme): Boolean {
        return this.channel == channel.name
                && this.programme == programme.title
                && this.startAt == programme.startAt
                && this.endAt == programme.endAt
    }
}
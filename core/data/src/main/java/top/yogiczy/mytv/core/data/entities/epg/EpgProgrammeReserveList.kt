package top.yogiczy.mytv.core.data.entities.epg

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 節目預約列表
 */
@Serializable
@Immutable
data class EpgProgrammeReserveList(
    val value: List<EpgProgrammeReserve> = emptyList(),
) : List<EpgProgrammeReserve> by value

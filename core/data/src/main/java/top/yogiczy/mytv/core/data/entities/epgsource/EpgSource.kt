package top.yogiczy.mytv.core.data.entities.epgsource

import kotlinx.serialization.Serializable

/**
 * 節目單來源
 */
@Serializable
data class EpgSource(
    /**
     * 名稱
     */
    val name: String = "",

    /**
     * 鏈接
     */
    val url: String = "",
)
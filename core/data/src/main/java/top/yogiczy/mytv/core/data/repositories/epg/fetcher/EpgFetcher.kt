package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import okhttp3.Response

/**
 * 節目單數據獲取接口
 */
interface EpgFetcher {
    /**
     * 是否支持該格式
     */
    fun isSupport(url: String): Boolean

    /**
     * 獲取節目單
     */
    suspend fun fetch(response: Response): String

    companion object {
        val instances = listOf(
            XmlEpgFetcher(),
            XmlGzEpgFetcher(),
            DefaultEpgFetcher(),
        )
    }
}
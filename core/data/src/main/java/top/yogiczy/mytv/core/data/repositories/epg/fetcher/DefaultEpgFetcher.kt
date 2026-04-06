package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import okhttp3.Response

/**
 * 缺省節目單數據獲取
 */
class DefaultEpgFetcher : EpgFetcher {
    override fun isSupport(url: String): Boolean {
        return true
    }

    override suspend fun fetch(response: Response): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    }
}
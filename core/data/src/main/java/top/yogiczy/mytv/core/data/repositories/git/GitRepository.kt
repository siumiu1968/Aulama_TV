package top.yogiczy.mytv.core.data.repositories.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.git.parser.GitReleaseParser
import top.yogiczy.mytv.core.data.utils.Loggable

/**
 * git數據獲取
 */
class GitRepository : Loggable() {

    /**
     * 獲取最新發行版
     */
    suspend fun latestRelease(
        url: String,
        includePrerelease: Boolean = false,
    ): GitRelease {
        log.d("獲取最新發行版: $url")

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            val parser = GitReleaseParser.instances.first { it.isSupport(url) }
            return withContext(Dispatchers.IO) {
                parser.parse(
                    data = response.body!!.string(),
                    includePrerelease = includePrerelease,
                )
            }
        } catch (ex: Exception) {
            log.e("獲取最新發行版失敗", ex)
            throw Exception("獲取最新發行版失敗，請檢查網絡連接", ex)
        }
    }
}

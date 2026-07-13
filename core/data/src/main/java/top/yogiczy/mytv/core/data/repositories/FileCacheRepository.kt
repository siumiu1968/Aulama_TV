package top.yogiczy.mytv.core.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File

/**
 * 用於將數據緩存至本地
 */
abstract class FileCacheRepository(
    private val fileName: String,
    private val isFullPath: Boolean = false,
) {
    private var onDataChanged:(() -> Unit)? = null

    public fun setDataChanged(onDataChanged: () ->Unit) {
        this.onDataChanged = onDataChanged
    }
    private fun getCacheFile() =
        if (isFullPath) File(fileName) else File(Globals.cacheDir, fileName)

    private suspend fun getCacheData(): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.readText()
        else null
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        file.writeText(data)
    }

    protected suspend fun getOrRefresh(cacheTime: Long, refreshOp: suspend () -> String): String {
        return getOrRefresh(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    protected suspend fun getOrRefresh(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String {
        val cachedData = getCacheData()
        val shouldRefresh = cachedData.isNullOrBlank() ||
            isExpired(getCacheFile().lastModified(), cachedData)

        if (!shouldRefresh) return cachedData

        return try {
            val refreshedData = refreshOp()
            if (refreshedData.isBlank()) error("刷新資料為空")
            if (refreshedData != cachedData) onDataChanged?.invoke()
            setCacheData(refreshedData)
            refreshedData
        } catch (ex: Exception) {
            // 父母隔一段時間再開機時，即使網絡暫時不通，仍保留上次可用頻道。
            cachedData?.takeIf { it.isNotBlank() } ?: throw ex
        }
    }

    open suspend fun clearCache() {
        try {
            getCacheFile().delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

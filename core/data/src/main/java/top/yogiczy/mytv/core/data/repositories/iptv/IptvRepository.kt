package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.core.data.utils.Logger


class IdGenerator {
    private var currentId = 0

    fun nextId(): Int {
        return ++currentId
    }

    fun reset() {
        currentId = 0
    }
}
/**
 * 直播源數據獲取
 */
class IptvRepository(
    private val source: IptvSource,
) : FileCacheRepository(
    if (source.isLocal) source.url
    else "iptv-${source.url.hashCode().toUInt().toString(16)}.txt",
    source.isLocal,
) {
    private val log = Logger.create(javaClass.simpleName)

    private val idGenerator = IdGenerator()

    /**
     * 獲取直播源數據
     */
    private suspend fun fetchSource(sourceUrl: String): String {
        log.d("獲取遠程直播源: $source")

        val client = OkHttp.client
        val request = Request.Builder().url(sourceUrl).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            return withContext(Dispatchers.IO) {
                response.body?.string() ?: ""
            }
        } catch (ex: Exception) {
            log.e("獲取直播源失敗", ex)
            throw Exception("獲取直播源失敗，請檢查網絡連接", ex)
        }
    }

    /**
     * 獲取直播源分組列表
     */
    suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        try {
            val sourceData = getOrRefresh(if (source.isLocal) Long.MAX_VALUE else cacheTime) {
                fetchSource(source.url)
            }

            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            val startTime = System.currentTimeMillis()
            val groupList = parser.parse(sourceData)

            // 在獲取到頻道列表後，統一生成頻道id
            idGenerator.reset()
            val groupListWithIds = ChannelGroupList(groupList.map { group ->
                group.copy(channelList = ChannelList(group.channelList.map { channel ->
                    channel.copy(id = idGenerator.nextId().toString())
                }))
            })

            log.i(
                listOf(
                    "解析直播源（${source.name}）完成：${groupList.size}個分組",
                    "${groupList.sumOf { it.channelList.size }}個頻道",
                    "${groupList.sumOf { it.channelList.sumOf { channel -> channel.urlList.size } }}條線路",
                    "耗時：${System.currentTimeMillis() - startTime}ms",
                ).joinToString()
            )

            return groupListWithIds
        } catch (ex: Exception) {
            log.e("獲取直播源失敗", ex)
            throw Exception(ex)
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}
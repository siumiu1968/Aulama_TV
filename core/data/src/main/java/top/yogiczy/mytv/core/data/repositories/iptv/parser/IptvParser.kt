package top.yogiczy.mytv.core.data.repositories.iptv.parser

import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList

/**
 * 直播源數據解析接口
 */
interface IptvParser {
    /**
     * 是否支持該直播源格式
     */
    fun isSupport(url: String, data: String): Boolean

    /**
     * 解析直播源數據
     */
    suspend fun parse(data: String): ChannelGroupList

    companion object {
        val instances = listOf(
            M3uIptvParser(),
            TxtIptvParser(),
            DefaultIptvParser(),
        )
    }
}
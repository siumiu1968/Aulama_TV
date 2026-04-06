package top.yogiczy.mytv.core.data.entities.channel

/**
 * 頻道分組
 */
data class ChannelGroup(
    /**
     * 分組名稱
     */
    val name: String = "",

    /**
     * 頻道列表
     */
    val channelList: ChannelList = ChannelList(),
) {
    companion object {
        val EXAMPLE = ChannelGroup(
            name = "頻道分組",
            channelList = ChannelList.EXAMPLE,
        )
    }
}
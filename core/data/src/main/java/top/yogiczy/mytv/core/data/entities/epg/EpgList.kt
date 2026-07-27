package top.yogiczy.mytv.core.data.entities.epg

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.recentProgramme
import top.yogiczy.mytv.core.data.repositories.epg.EpgChannelMatcher

/**
 * 頻道節目單列表
 */
@Serializable
@Immutable
data class EpgList(
    val value: List<Epg> = emptyList(),
    val source: String = "",
    val updatedAt: Long = 0L,
    val timezone: String = "Asia/Hong_Kong",
    val filterSignature: String = "",
) : List<Epg> by value {
    companion object {
        private val matchCache =
            object : LinkedHashMap<String, Epg?>(128, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Epg?>?): Boolean {
                    return size > 1024
                }
            }

        fun EpgList.recentProgramme(channel: Channel): EpgProgrammeRecent? {
            if (isEmpty()) return null

            return match(channel)?.recentProgramme()
        }

        fun EpgList.match(channel: Channel): Epg? {
            if (isEmpty()) return null

            val key = EpgChannelMatcher.lookupKey(channel)
            val cacheKey = "${System.identityHashCode(this)}|$key"
            return matchCache.getOrPut(cacheKey) {
                firstOrNull { epg ->
                    sequenceOf(epg.guideId, epg.channel)
                        .plus(epg.aliases.asSequence())
                        .any { EpgChannelMatcher.normalize(it) == key }
                }
            }
        }

        fun merge(vararg lists: EpgList): EpgList {
            val available = lists.filter { it.isNotEmpty() }
            if (available.isEmpty()) return EpgList()

            val merged = LinkedHashMap<String, Epg>()
            available.forEach { list ->
                list.forEach { epg ->
                    val key = EpgChannelMatcher.normalize(epg.guideId.ifBlank { epg.channel })
                    if (key.isNotBlank()) merged.putIfAbsent(key, epg)
                }
            }
            clearCache()
            return EpgList(
                value = merged.values.toList(),
                source = available.map(EpgList::source).filter(String::isNotBlank).distinct().joinToString(" + "),
                updatedAt = available.map(EpgList::updatedAt).filter { it > 0L }.minOrNull() ?: 0L,
                filterSignature = available.map(EpgList::filterSignature).joinToString("+"),
            )
        }

        fun clearCache() {
            matchCache.clear()
        }

        fun example(channelList: ChannelList): EpgList {
            return EpgList(channelList.map(Epg.Companion::example))
        }
    }
}

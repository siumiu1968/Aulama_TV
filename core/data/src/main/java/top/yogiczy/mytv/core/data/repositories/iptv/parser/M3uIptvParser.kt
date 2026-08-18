package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.CaptionIdentifiers
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

/**
 * m3u直播源解析
 */
class M3uIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String): ChannelGroupList = withContext(Dispatchers.Default) {
        val lines = data.lineSequence().map(String::trim).toList()
        val iptvList = mutableListOf<IptvResponseItem>()
        var pending: PendingItem? = null
        var referrer: String? = null
        var userAgent: String? = null

        lines.forEach { line ->
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                    referrer = null
                    userAgent = null
                }

                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) -> {
                    referrer = line.substringAfter("=", "").trim().ifBlank { null }
                }

                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> {
                    userAgent = line.substringAfter("=", "").trim().ifBlank { null }
                }

                line.isNotBlank() && !line.startsWith("#") && pending != null -> {
                    val item = pending!!
                    val quality = ChannelQuality.detect(item.name, item.channelName, item.groupName)
                    iptvList += IptvResponseItem(
                        tvgId = item.tvgId,
                        tvgLanguage = item.tvgLanguage,
                        logicalKey = item.tvgId?.takeIf(String::isNotBlank)
                            ?: normalizeChannelName(item.name).lowercase(),
                        name = normalizeChannelName(item.name),
                        channelName = normalizeChannelName(item.channelName),
                        groupName = normalizeGroupName(item.groupName),
                        route = ChannelRoute(
                            url = line,
                            quality = quality,
                            label = routeLabel(item.name, quality),
                            referrer = referrer,
                            userAgent = userAgent,
                            sourceOrder = iptvList.size,
                            tvgId = item.tvgId.orEmpty(),
                            tvgLanguage = item.tvgLanguage.orEmpty(),
                            captionRouteId = CaptionIdentifiers.routeId(line),
                        ),
                        logo = item.logo,
                    )
                    pending = null
                    referrer = null
                    userAgent = null
                }
            }
        }

        val channels = iptvList
            .groupBy(IptvResponseItem::logicalKey)
            .values
            .map { items ->
                val first = items.first()
                val routes = items
                    .map(IptvResponseItem::route)
                    .distinctBy(ChannelRoute::url)
                    .sortedBy(ChannelRoute::sourceOrder)
                ParsedChannel(first.groupName, first.route.sourceOrder, Channel(
                    name = first.name,
                    tvgId = first.tvgId.orEmpty(),
                    tvgLanguage = items.firstNotNullOfOrNull {
                        it.tvgLanguage?.takeIf(String::isNotBlank)
                    }.orEmpty(),
                    captionChannelId = first.tvgId
                        ?.let(CaptionIdentifiers::channelId)
                        .orEmpty(),
                    epgName = first.channelName,
                    routes = routes,
                    logo = AulamaChannelLogoResolver.resolve(
                        tvgId = first.tvgId,
                        name = first.name,
                        epgName = first.channelName,
                        fallback = items.firstNotNullOfOrNull(IptvResponseItem::logo),
                    ),
                ))
            }
            .sortedBy(ParsedChannel::sourceOrder)

        return@withContext ChannelGroupList(channels.groupBy(ParsedChannel::groupName).map { groupEntry ->
            ChannelGroup(
                name = groupEntry.key,
                channelList = ChannelList(groupEntry.value.map(ParsedChannel::channel))
            )
        })
    }

    private fun parseExtInf(line: String): PendingItem {
        fun attribute(name: String) = Regex("$name=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1)?.trim()

        val name = line.substringAfterLast(',').trim()
        return PendingItem(
            tvgId = attribute("tvg-id"),
            tvgLanguage = attribute("tvg-language"),
            name = name,
            channelName = attribute("tvg-name").orEmpty().ifBlank { name },
            groupName = attribute("group-title").orEmpty().ifBlank { "其他" },
            logo = attribute("tvg-logo"),
        )
    }

    private fun normalizeGroupName(value: String): String = value
        .substringBefore('｜')
        .trim()
        .ifBlank { "其他" }

    private fun normalizeChannelName(value: String): String = value
        .replace(
            Regex("\\s*[（(][^）)]*(?:4K|2160|1080|720|主線|備用|線路)[^）)]*[）)]\\s*$", RegexOption.IGNORE_CASE),
            "",
        )
        .replace(
            Regex("\\s+(?:4K|UHD|FHD|1080P?|720P?|主線|備用\\s*\\d*)\\s*$", RegexOption.IGNORE_CASE),
            "",
        )
        .trim()

    private fun routeLabel(name: String, quality: ChannelQuality): String {
        val suffix = Regex("[（(]([^）)]+)[）)]\\s*$").find(name)?.groupValues?.getOrNull(1)
        return suffix?.trim().orEmpty().ifBlank { quality.label }
    }

    private data class PendingItem(
        val tvgId: String?,
        val tvgLanguage: String?,
        val name: String,
        val channelName: String,
        val groupName: String,
        val logo: String?,
    )

    private data class ParsedChannel(
        val groupName: String,
        val sourceOrder: Int,
        val channel: Channel,
    )

    private data class IptvResponseItem(
        val tvgId: String?,
        val tvgLanguage: String?,
        val logicalKey: String,
        val name: String,
        val channelName: String,
        val groupName: String,
        val route: ChannelRoute,
        val logo: String?,
    )
}

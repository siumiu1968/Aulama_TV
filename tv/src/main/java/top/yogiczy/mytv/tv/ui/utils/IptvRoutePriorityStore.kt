package top.yogiczy.mytv.tv.ui.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.account.AulamaTvSync

object IptvRoutePriorityStore {
    private const val key = "IPTV_ROUTE_PRIORITIES_V1"
    private const val maxChannels = 200
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, MutableList<String>>>() {}.type

    @Synchronized
    fun priorities(channelName: String): List<String> = read()[channelName]
        ?.distinct()
        .orEmpty()

    @Synchronized
    fun toggle(channelName: String, url: String): List<String> {
        val all = read()
        val current = all[channelName]
            ?.filterNot { it == url }
            ?.distinct()
            ?.toMutableList()
            ?: mutableListOf()

        if (url !in all[channelName].orEmpty()) current += url

        if (current.isEmpty()) {
            all.remove(channelName)
        } else {
            all[channelName] = current
        }
        write(all.entries.toList().takeLast(maxChannels).associate { it.toPair() })
        AulamaTvSync.notifyLocalChange()
        return current
    }

    @Synchronized
    fun snapshot(): Map<String, List<String>> = read()
        .mapValues { (_, urls) -> urls.distinct().take(32) }

    @Synchronized
    fun replaceAll(value: Map<String, List<String>>, notifySync: Boolean = true) {
        val normalized = value.entries
            .filter { it.key.isNotBlank() }
            .takeLast(maxChannels)
            .associate { (channel, urls) -> channel to urls.distinct().take(32) }
            .filterValues { it.isNotEmpty() }
        write(normalized)
        if (notifySync) AulamaTvSync.notifyLocalChange()
    }

    private fun read(): LinkedHashMap<String, MutableList<String>> = try {
        val value = gson.fromJson<MutableMap<String, MutableList<String>>>(
            SP.getString(key, "{}"),
            mapType,
        ) ?: mutableMapOf()
        LinkedHashMap(value)
    } catch (_: Exception) {
        linkedMapOf()
    }

    private fun write(value: Map<String, List<String>>) {
        SP.putString(key, gson.toJson(value))
    }
}

internal fun mergeRouteAttemptOrder(
    routes: List<ChannelRoute>,
    priorityUrls: List<String>,
    automaticIndices: List<Int>,
    requestedIndex: Int? = null,
): List<Int> {
    val priorityIndices = priorityUrls.mapNotNull { priorityUrl ->
        routes.indexOfFirst { it.url == priorityUrl }.takeIf { it >= 0 }
    }

    return buildList {
        requestedIndex?.takeIf(routes.indices::contains)?.let(::add)
        addAll(priorityIndices)
        addAll(automaticIndices.filter(routes.indices::contains))
        addAll(routes.indices)
    }.distinct()
}

internal fun keepManualFourKFallbacksTogether(
    routes: List<ChannelRoute>,
    attemptOrder: List<Int>,
    requestedIndex: Int?,
): List<Int> {
    val requested = requestedIndex?.takeIf(routes.indices::contains) ?: return attemptOrder
    if (routes[requested].quality != ChannelQuality.UHD_4K) return attemptOrder

    val validOrder = (attemptOrder + routes.indices).filter(routes.indices::contains).distinct()
    return buildList {
        add(requested)
        addAll(validOrder.filter { index ->
            index != requested && routes[index].quality == ChannelQuality.UHD_4K
        })
        addAll(validOrder.filter { index ->
            index != requested && routes[index].quality != ChannelQuality.UHD_4K
        })
    }
}

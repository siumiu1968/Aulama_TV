package top.yogiczy.mytv.core.data.entities.channel

import java.net.URI

/** Identifiers shared with Aulama Web's managed live-caption catalogue. */
object CaptionIdentifiers {
    fun channelId(tvgId: String): String = tvgId.trim()
        .takeIf(String::isNotEmpty)
        ?.let { "international-channel-${fnv1a32Base36("tvg:$it")}" }
        .orEmpty()

    fun routeId(url: String): String = canonicalStreamUrl(url)
        .takeIf(String::isNotEmpty)
        ?.let { "route-${fnv1a32Base36(it)}" }
        .orEmpty()

    fun canonicalStreamUrl(rawUrl: String): String = runCatching {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https") || host.isEmpty()) return@runCatching ""
        val renderedHost = if (':' in host) "[$host]" else host
        val port = uri.port.takeUnless {
            it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80)
        }
        buildString {
            append(scheme).append("://").append(renderedHost)
            port?.let { append(':').append(it) }
            append(uri.rawPath?.ifEmpty { "/" } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
    }.getOrDefault("")

    fun fnv1a32Base36(value: String): String {
        var hash = 0x811C9DC5.toInt()
        value.forEach { character ->
            hash = hash xor character.code
            hash *= 0x01000193
        }
        return Integer.toUnsignedString(hash, 36)
    }
}

package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import java.net.URI

private const val TVB_AKAMAI_HOST = "prd-vcache.edge-global.akamai.tvb.com"
private val EXTENSIONLESS_HLS_HOSTS = setOf(
    TVB_AKAMAI_HOST,
    "10.fast.hidns.vip",
    "cdn3.indevs.in",
    "cdn.qd.je",
)

internal fun isLikelyHlsStreamUrl(url: String): Boolean {
    val cleanUrl = url.substringBefore('#').substringBefore('?')
    if (cleanUrl.endsWith(".m3u8", ignoreCase = true)) return true

    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    return uri.host?.lowercase() in EXTENSIONLESS_HLS_HOSTS
}

internal fun isTvbHlsSessionUrl(url: String): Boolean =
    runCatching { URI(url).host }
        .getOrNull()
        .equals(TVB_AKAMAI_HOST, ignoreCase = true)

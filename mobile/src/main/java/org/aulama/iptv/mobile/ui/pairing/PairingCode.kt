package org.aulama.iptv.mobile.ui.pairing

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

@JvmInline
value class PairingCode(val value: String)

object PairingCodeParser {
    fun fromQr(rawValue: String): PairingCode? {
        return runCatching { parseQr(rawValue) }.getOrNull()
    }

    private fun parseQr(rawValue: String): PairingCode? {
        val uri = runCatching { URI(rawValue.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals(PAIRING_HOST, ignoreCase = true)) return null
        if (uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        if (uri.fragment != null) return null
        if (uri.path != PAIRING_PATH && uri.path != PAIRING_PATH.removeSuffix("/")) return null

        val code = uri.rawQuery
            ?.split("&")
            ?.mapNotNull(::parseQueryEntry)
            ?.firstOrNull { it.first == "code" }
            ?.second
            ?: return null
        return normalize(code)
    }

    fun fromManual(rawValue: String): PairingCode? {
        return if (rawValue.contains("://")) fromQr(rawValue) else normalize(rawValue)
    }

    private fun normalize(value: String): PairingCode? {
        val compact = value
            .trim()
            .uppercase(Locale.ROOT)
            .filterNot { it == '-' || it.isWhitespace() }
        if (!PAIRING_CODE_REGEX.matches(compact)) return null
        return PairingCode("${compact.take(4)}-${compact.drop(4)}")
    }

    private fun parseQueryEntry(entry: String): Pair<String, String>? {
        val separator = entry.indexOf('=')
        if (separator <= 0) return null
        val key = decode(entry.substring(0, separator))
        val value = decode(entry.substring(separator + 1))
        return key to value
    }

    private fun decode(value: String): String = URLDecoder.decode(
        value,
        StandardCharsets.UTF_8.name(),
    )

    private val PAIRING_CODE_REGEX = Regex("^[A-Z0-9]{8}$")
    private const val PAIRING_HOST = "aulama.org"
    private const val PAIRING_PATH = "/iptv/pair/"
}

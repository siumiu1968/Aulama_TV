package top.yogiczy.mytv.tv.account

import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

internal object AulamaPlaybackAuthorization {
    private const val maxEntries = 32
    private val bearerByUrl = linkedMapOf<String, String>()

    @Synchronized
    fun bind(candidate: AulamaPlanCandidate, accessToken: String) {
        if (candidate.transport == AulamaPlaybackTransport.DIRECT ||
            candidate.authorization != AulamaCandidateAuthorization.BEARER ||
            !AulamaSyncProtocol.isTrustedBearerTarget(candidate.url)
        ) {
            bearerByUrl.remove(candidate.url)
            return
        }
        bearerByUrl[candidate.url] = accessToken
        while (bearerByUrl.size > maxEntries) {
            bearerByUrl.remove(bearerByUrl.keys.first())
        }
    }

    @Synchronized
    fun headersFor(url: String): Map<String, String> {
        if (!AulamaSyncProtocol.isTrustedBearerTarget(url)) return emptyMap()
        val token = bearerByUrl[url] ?: return emptyMap()
        return mapOf("Authorization" to "Bearer $token")
    }

    @Synchronized
    fun clearForUrl(url: String) {
        bearerByUrl.remove(url)
    }

    @Synchronized
    fun clear() {
        bearerByUrl.clear()
    }
}

internal fun ChannelRoute.aulamaRequestHeaders(): Map<String, String> =
    requestHeaders + AulamaPlaybackAuthorization.headersFor(url)

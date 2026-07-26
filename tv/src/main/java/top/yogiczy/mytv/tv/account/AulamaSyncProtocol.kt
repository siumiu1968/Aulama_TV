package top.yogiczy.mytv.tv.account

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

internal data class AulamaCustomSource(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long? = null,
    val deletedAt: Long? = null,
)

internal data class AulamaSyncPayload(
    val favorites: List<String> = emptyList(),
    val customSources: List<AulamaCustomSource> = emptyList(),
    val routePriorities: Map<String, List<String>> = emptyMap(),
)

internal data class AulamaSyncDocument(
    val revision: Long,
    val payload: AulamaSyncPayload,
)

internal enum class AulamaCandidateAuthorization {
    NONE,
    BEARER,
}

internal data class AulamaPlanCandidate(
    val id: String,
    val region: String,
    val url: String,
    val authorization: AulamaCandidateAuthorization,
    val transport: AulamaPlaybackTransport,
)

internal class AulamaSyncConflict(val currentRevision: Long) : Exception()

internal object AulamaSyncProtocol {
    fun parseDocument(body: String): AulamaSyncDocument? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        val revision = root.requiredLong("revision").takeIf { it >= 0 } ?: return null
        val sync = root.getAsJsonObject("sync") ?: return null
        AulamaSyncDocument(
            revision = revision,
            payload = AulamaSyncPayload(
                favorites = sync.stringArray("favorites", 2_000, 512),
                customSources = sync.customSources(),
                routePriorities = sync.routePriorities(),
            ),
        )
    }.getOrNull()

    fun parseConflictRevision(body: String): Long? = runCatching {
        JsonParser.parseString(body).asJsonObject.requiredLong("current_revision")
            .takeIf { it >= 0 }
    }.getOrNull()

    fun parseRelayPlan(body: String): List<AulamaPlanCandidate> = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        val candidates = root.getAsJsonArray("candidates") ?: return emptyList()
        candidates.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = item.optionalString("id")
                ?.takeIf { it.length <= 80 }
                ?: return@mapNotNull null
            val region = item.optionalString("region")
                ?.takeIf { it.length <= 80 }
                ?: id
            val url = item.optionalString("url")
                ?.takeIf(::isSafePlanUrl)
                ?: return@mapNotNull null
            val authorization = when (item.optionalString("authorization")?.lowercase()) {
                null, "none" -> AulamaCandidateAuthorization.NONE
                "bearer" -> AulamaCandidateAuthorization.BEARER
                else -> return@mapNotNull null
            }
            val isDirect = id.equals("direct", true) || region.equals("direct", true)
            if (authorization == AulamaCandidateAuthorization.BEARER &&
                (isDirect || !isTrustedBearerTarget(url))
            ) {
                return@mapNotNull null
            }
            AulamaPlanCandidate(
                id = id,
                region = region,
                url = url,
                authorization = authorization,
                transport = if (isDirect) {
                    AulamaPlaybackTransport.DIRECT
                } else {
                    AulamaPlaybackTransport.RELAY
                },
            )
        }.distinctBy { it.url }.take(4)
    }.getOrDefault(emptyList())

    fun toJson(payload: AulamaSyncPayload): JsonObject = JsonObject().apply {
        add("favorites", JsonArray().also { values ->
            payload.favorites.distinct().take(2_000).forEach(values::add)
        })
        add("custom_sources", JsonArray().also { values ->
            payload.customSources.distinctBy { it.id }.take(100).forEach { source ->
                values.add(JsonObject().apply {
                    addProperty("id", source.id)
                    addProperty("name", source.name)
                    addProperty("url", source.url)
                    source.updatedAt?.let { addProperty("updated_at", it) }
                    source.deletedAt?.let { addProperty("deleted_at", it) }
                })
            }
        })
        add("route_priorities", JsonObject().also { priorities ->
            payload.routePriorities.entries.take(2_000).forEach { (channel, urls) ->
                priorities.add(channel, JsonArray().also { values ->
                    urls.distinct().take(32).forEach(values::add)
                })
            }
        })
    }

    fun isSafePlanUrl(value: String): Boolean = runCatching {
        if (value.length > 8_192) return false
        val uri = URI(value)
        uri.scheme.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)

    fun isTrustedBearerTarget(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("aulama.org", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null
    }.getOrDefault(false)

    private fun JsonObject.customSources(): List<AulamaCustomSource> {
        val values = getAsJsonArray("custom_sources") ?: return emptyList()
        if (values.size() > 100) error("Too many custom sources")
        return values.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = item.optionalString("id")?.takeIf { it.length <= 256 }
                ?: return@mapNotNull null
            val name = item.optionalString("name")?.takeIf { it.length <= 256 }
                ?: return@mapNotNull null
            val url = item.optionalString("url")
                ?.takeIf { it.length <= 4_096 && it.isSafeHttpUrl() }
                ?: return@mapNotNull null
            AulamaCustomSource(
                id = id,
                name = name,
                url = url,
                updatedAt = item.optionalLong("updated_at"),
                deletedAt = item.optionalLong("deleted_at"),
            )
        }.distinctBy { it.id }
    }

    private fun JsonObject.routePriorities(): Map<String, List<String>> {
        val values = getAsJsonObject("route_priorities") ?: return emptyMap()
        if (values.size() > 2_000) error("Too many route priorities")
        return buildMap {
            values.entrySet().forEach { (channel, element) ->
                if (channel.isBlank() || channel.length > 512 || !element.isJsonArray) return@forEach
                put(
                    channel,
                    element.asJsonArray.mapNotNull { value ->
                        runCatching { value.asString.trim() }.getOrNull()
                            ?.takeIf { it.isNotEmpty() && it.length <= 4_096 }
                    }.distinct().take(32),
                )
            }
        }
    }

    private fun JsonObject.stringArray(name: String, limit: Int, maxLength: Int): List<String> {
        val values = getAsJsonArray(name) ?: return emptyList()
        if (values.size() > limit) error("Too many values")
        return values.mapNotNull { value ->
            runCatching { value.asString.trim() }.getOrNull()
                ?.takeIf { it.isNotEmpty() && it.length <= maxLength }
        }.distinct()
    }

    private fun JsonObject.requiredLong(name: String): Long = get(name).asLong

    private fun JsonObject.optionalLong(name: String): Long? = runCatching {
        get(name)?.takeUnless { it.isJsonNull }?.asLong
    }.getOrNull()?.takeIf { it >= 0 }

    private fun JsonObject.optionalString(name: String): String? = runCatching {
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun String.isSafeHttpUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)
}

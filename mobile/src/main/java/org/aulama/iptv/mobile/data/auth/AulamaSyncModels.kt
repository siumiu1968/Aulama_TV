package org.aulama.iptv.mobile.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class AulamaCustomSource(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
)

data class SyncPayload(
    val favorites: List<String> = emptyList(),
    val customSources: List<AulamaCustomSource> = emptyList(),
    val routePriorities: Map<String, List<String>> = emptyMap(),
)

data class SyncDocument(
    val revision: Long,
    val payload: SyncPayload,
)

internal object SyncJsonCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseDocument(value: String): SyncDocument? = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        val revision = root["revision"]?.jsonPrimitive?.longOrNull ?: return null
        val payload = parsePayload(root["sync"]?.jsonObject ?: return null) ?: return null
        SyncDocument(revision, payload)
    }.getOrNull()

    fun parsePayload(value: String): SyncPayload? = runCatching {
        parsePayload(json.parseToJsonElement(value).jsonObject)
    }.getOrNull()

    fun toJson(payload: SyncPayload): JsonObject = buildJsonObject {
        put("favorites", buildJsonArray {
            payload.favorites.distinct().forEach { add(JsonPrimitive(it)) }
        })
        put("custom_sources", buildJsonArray {
            payload.customSources.distinctBy(AulamaCustomSource::id).forEach { source ->
                add(buildJsonObject {
                    put("id", source.id)
                    put("name", source.name)
                    put("url", source.url)
                    source.updatedAt?.let { put("updated_at", timestampElement(it)) }
                    source.deletedAt?.let { put("deleted_at", timestampElement(it)) }
                })
            }
        })
        put("route_priorities", buildJsonObject {
            payload.routePriorities.forEach { (channel, routes) ->
                put(channel, buildJsonArray {
                    routes.distinct().forEach { add(JsonPrimitive(it)) }
                })
            }
        })
    }

    private fun parsePayload(json: JsonObject): SyncPayload? {
        val favorites = json["favorites"].stringsOrNull() ?: return null
        val customSources = json["custom_sources"]?.jsonArray?.map { element ->
            val source = element.jsonObject
            AulamaCustomSource(
                id = source.text("id") ?: return null,
                name = source.text("name") ?: return null,
                url = source.text("url") ?: return null,
                updatedAt = source.timestamp("updated_at"),
                deletedAt = source.timestamp("deleted_at"),
            )
        } ?: return null
        val routePriorities = json["route_priorities"]?.jsonObject?.mapValues { (_, value) ->
            value.stringsOrNull() ?: return null
        } ?: return null
        return SyncPayload(
            favorites = favorites.distinct(),
            customSources = customSources.distinctBy(AulamaCustomSource::id),
            routePriorities = routePriorities.mapValues { it.value.distinct() },
        )
    }

    private fun JsonElement?.stringsOrNull(): List<String>? = runCatching {
        this?.jsonArray?.map { it.jsonPrimitive.content.trim() }
            ?.takeIf { values -> values.none(String::isEmpty) }
    }.getOrNull()

    private fun JsonObject.text(name: String): String? = this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun JsonObject.timestamp(name: String): String? {
        val value = this[name] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun timestampElement(value: String): JsonPrimitive =
        value.toLongOrNull()?.takeIf { it >= 0 }?.let(::JsonPrimitive) ?: JsonPrimitive(value)
}

object SyncConflictMerger {
    fun merge(local: SyncPayload, remote: SyncPayload): SyncPayload = SyncPayload(
        favorites = (remote.favorites + local.favorites).distinct(),
        customSources = mergeSources(local.customSources, remote.customSources),
        routePriorities = mergePriorities(local.routePriorities, remote.routePriorities),
    )

    private fun mergeSources(
        local: List<AulamaCustomSource>,
        remote: List<AulamaCustomSource>,
    ): List<AulamaCustomSource> {
        val localById = local.associateBy(AulamaCustomSource::id)
        val remoteById = remote.associateBy(AulamaCustomSource::id)
        return (remote.map(AulamaCustomSource::id) + local.map(AulamaCustomSource::id))
            .distinct()
            .mapNotNull { id ->
                val localSource = localById[id]
                val remoteSource = remoteById[id]
                when {
                    localSource == null -> remoteSource
                    remoteSource == null -> localSource
                    isStrictlyNewer(localSource.updatedAt, remoteSource.updatedAt) -> localSource
                    else -> remoteSource
                }
            }
    }

    private fun mergePriorities(
        local: Map<String, List<String>>,
        remote: Map<String, List<String>>,
    ): Map<String, List<String>> = (remote.keys + local.keys).distinct().associateWith { channel ->
        (local[channel].orEmpty() + remote[channel].orEmpty()).distinct().take(MAX_ROUTES_PER_CHANNEL)
    }

    private fun isStrictlyNewer(local: String?, remote: String?): Boolean {
        if (local == null) return false
        if (remote == null) return true
        val localLong = local.toLongOrNull()
        val remoteLong = remote.toLongOrNull()
        return if (localLong != null && remoteLong != null) localLong > remoteLong else local > remote
    }

    private const val MAX_ROUTES_PER_CHANNEL = 32
}

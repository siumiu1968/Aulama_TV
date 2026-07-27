package top.yogiczy.mytv.tv.account

internal object AulamaSyncMergePolicy {
    fun merge(
        base: AulamaSyncPayload,
        local: AulamaSyncPayload,
        remote: AulamaSyncPayload,
    ): AulamaSyncPayload = AulamaSyncPayload(
        favorites = mergeSet(base.favorites, local.favorites, remote.favorites),
        customSources = mergeSources(base.customSources, local.customSources, remote.customSources),
        routePriorities = mergePriorities(
            base.routePriorities,
            local.routePriorities,
            remote.routePriorities,
        ),
    )

    private fun mergeSet(
        base: List<String>,
        local: List<String>,
        remote: List<String>,
    ): List<String> {
        val baseSet = base.toSet()
        val localSet = local.toSet()
        val remoteSet = remote.toSet()
        val retainedBase = base.filter { it in localSet && it in remoteSet }
        val additions = (local + remote).filter { it !in baseSet }
        return (retainedBase + additions).distinct()
    }

    private fun mergeSources(
        base: List<AulamaCustomSource>,
        local: List<AulamaCustomSource>,
        remote: List<AulamaCustomSource>,
    ): List<AulamaCustomSource> {
        val baseMap = base.associateBy { it.id }
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val orderedIds = (local.map { it.id } + remote.map { it.id } + base.map { it.id }).distinct()
        return orderedIds.mapNotNull { id ->
            chooseValue(baseMap[id], localMap[id], remoteMap[id])
        }.filter { it.deletedAt == null }
    }

    private fun mergePriorities(
        base: Map<String, List<String>>,
        local: Map<String, List<String>>,
        remote: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val channels = (local.keys + remote.keys + base.keys).distinct()
        return buildMap {
            channels.forEach { channel ->
                val baseValue = base[channel]
                val localValue = local[channel]
                val remoteValue = remote[channel]
                val value = when {
                    localValue == remoteValue -> localValue
                    localValue == baseValue -> remoteValue
                    remoteValue == baseValue -> localValue
                    localValue == null -> remoteValue
                    remoteValue == null -> localValue
                    else -> (localValue + remoteValue).distinct().take(32)
                }
                value?.takeIf { it.isNotEmpty() }?.let { put(channel, it) }
            }
        }
    }

    private fun <T> chooseValue(base: T?, local: T?, remote: T?): T? = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        local == null -> remote
        remote == null -> local
        else -> local
    }
}

internal enum class AulamaPlaybackTransport {
    RELAY,
    DIRECT,
}

internal data class AulamaPlaybackCandidate(
    val id: String,
    val transport: AulamaPlaybackTransport,
    val url: String,
    val label: String,
    val authorization: AulamaCandidateAuthorization = AulamaCandidateAuthorization.NONE,
)

internal object AulamaPlaybackPolicy {
    const val AUTO_PREFERENCE_ID = "auto"

    fun candidates(
        directUrl: String,
        isSuperAdmin: Boolean,
        plan: List<AulamaPlanCandidate>,
    ): List<AulamaPlaybackCandidate> = buildList {
        if (isSuperAdmin) {
            plan.distinctBy { it.url }.forEach { candidate ->
                add(
                    AulamaPlaybackCandidate(
                        id = candidate.id,
                        transport = candidate.transport,
                        url = candidate.url,
                        label = if (candidate.transport == AulamaPlaybackTransport.DIRECT) {
                            "直接連線"
                        } else {
                            relayLabel(candidate)
                        },
                        authorization = candidate.authorization,
                    )
                )
            }
        }
        if (none { it.transport == AulamaPlaybackTransport.DIRECT }) add(
            AulamaPlaybackCandidate(
                id = "direct",
                transport = AulamaPlaybackTransport.DIRECT,
                url = directUrl,
                label = "直接連線",
            )
        )
    }.distinctBy { it.url }

    fun prioritize(
        candidates: List<AulamaPlaybackCandidate>,
        preferenceId: String,
    ): List<AulamaPlaybackCandidate> {
        if (preferenceId == AUTO_PREFERENCE_ID) return candidates
        val preferred = candidates.firstOrNull { it.id == preferenceId } ?: return candidates
        return listOf(preferred) + candidates.filterNot { it.url == preferred.url }
    }

    fun preferenceLabel(preferenceId: String): String = when (preferenceId) {
        "hk_relay" -> "香港中轉優先"
        "jp_relay" -> "日本中轉優先"
        "direct" -> "直接連線優先"
        else -> "自動：香港 → 日本 → 直接"
    }

    private fun relayLabel(candidate: AulamaPlanCandidate): String = when {
        candidate.id.equals("hk_relay", ignoreCase = true) ||
            candidate.region.equals("hk", ignoreCase = true) -> "香港中轉"

        candidate.id.equals("jp_relay", ignoreCase = true) ||
            candidate.region.equals("jp", ignoreCase = true) -> "日本中轉"

        else -> "${candidate.region}中轉"
    }
}

package top.yogiczy.mytv.tv.caption

import com.google.gson.JsonObject
import top.yogiczy.mytv.core.data.entities.channel.CaptionIdentifiers
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

enum class LiveCaptionMode(
    val storageValue: String,
    val label: String,
) {
    OFF("off", "關閉字幕"),
    ENGLISH("en", "英文原文"),
    BILINGUAL("bilingual", "中英雙語"),
    TRADITIONAL_CHINESE("zh", "繁中翻譯");

    companion object {
        fun fromStorageValue(value: String?): LiveCaptionMode = entries.firstOrNull {
            it.storageValue == value?.trim()?.lowercase()
        } ?: OFF
    }
}

internal enum class LiveCaptionStatus {
    OFF,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    LOGIN_REQUIRED,
    PREMIUM_REQUIRED,
    QUOTA_EXHAUSTED,
    UNSUPPORTED,
    UNAVAILABLE,
}

internal enum class LiveCaptionLifecycleEvent {
    RESUME,
    PAUSE_OR_STOP,
}

internal data class LiveCaptionLifecycleSyncState(
    val foreground: Boolean,
    val synchronized: Boolean = false,
    val resumeGeneration: Long = 0L,
)

internal fun reduceLiveCaptionLifecycle(
    state: LiveCaptionLifecycleSyncState,
    event: LiveCaptionLifecycleEvent,
): LiveCaptionLifecycleSyncState = when (event) {
    LiveCaptionLifecycleEvent.RESUME -> state.copy(
        foreground = true,
        synchronized = false,
        resumeGeneration = state.resumeGeneration + 1,
    )
    LiveCaptionLifecycleEvent.PAUSE_OR_STOP -> state.copy(
        foreground = false,
        synchronized = false,
    )
}

internal fun LiveCaptionLifecycleSyncState.withVerifiedOffset(
    verified: Boolean,
    expectedResumeGeneration: Long,
): LiveCaptionLifecycleSyncState = if (
    foreground && resumeGeneration == expectedResumeGeneration
) {
    copy(synchronized = verified)
} else {
    this
}

internal data class LiveCaptionQuota(
    val limited: Boolean,
    val limitMs: Long?,
    val usedMs: Long,
    val remainingMs: Long?,
    val resetAt: String,
    val exhausted: Boolean,
)

internal data class LiveCaptionCue(
    val cueId: String,
    val seq: Long?,
    val revision: Long,
    val sourceId: String,
    val english: String,
    val zhHant: String,
    val final: Boolean,
    val stability: String,
    val audioStartMs: Long? = null,
    val audioEndMs: Long? = null,
    val emittedAtMs: Long? = null,
    val serverNowMs: Long? = null,
    val receivedAtMs: Long? = null,
    val processingMs: Long? = null,
    val delayMs: Long? = null,
    val suggestedHoldMs: Long? = null,
) {
    fun isUsable(mode: LiveCaptionMode): Boolean = when (mode) {
        LiveCaptionMode.OFF -> false
        LiveCaptionMode.ENGLISH -> english.isNotBlank()
        LiveCaptionMode.BILINGUAL -> english.isNotBlank() && zhHant.isNotBlank()
        LiveCaptionMode.TRADITIONAL_CHINESE -> zhHant.isNotBlank()
    }
}

internal data class LiveCaptionCueState(
    val current: LiveCaptionCue? = null,
    val highestSequence: Long? = null,
    val history: Map<String, LiveCaptionCue> = emptyMap(),
)

internal fun reduceLiveCaptionCue(
    state: LiveCaptionCueState,
    incoming: LiveCaptionCue,
): LiveCaptionCueState {
    if (incoming.english.isBlank() && incoming.zhHant.isBlank()) return state
    val key = incoming.cueId.ifBlank {
        listOfNotNull(incoming.sourceId.takeIf(String::isNotBlank), incoming.seq?.toString())
            .joinToString(":")
    }
    if (key.isBlank()) return state

    val previous = state.history[key]
    if (previous == null && incoming.seq != null &&
        state.highestSequence != null && incoming.seq < state.highestSequence
    ) return state
    if (previous != null && incoming.revision < previous.revision) return state

    val merged = mergeLiveCaptionCue(previous, incoming.copy(cueId = key))
    if (previous == merged) return state

    val nextHistory = LinkedHashMap(state.history)
    nextHistory[key] = merged
    while (nextHistory.size > 32) nextHistory.remove(nextHistory.keys.first())
    return LiveCaptionCueState(
        current = merged,
        highestSequence = listOfNotNull(state.highestSequence, incoming.seq).maxOrNull(),
        history = nextHistory,
    )
}

internal fun liveCaptionCueKey(cue: LiveCaptionCue): String = cue.cueId.ifBlank {
    listOfNotNull(cue.sourceId.takeIf(String::isNotBlank), cue.seq?.toString()).joinToString(":")
}

internal fun mergeLiveCaptionCue(
    previous: LiveCaptionCue?,
    incoming: LiveCaptionCue,
): LiveCaptionCue {
    if (previous == null || liveCaptionCueKey(previous) != liveCaptionCueKey(incoming)) {
        return incoming
    }
    if (incoming.revision < previous.revision) return previous
    return incoming.copy(
        cueId = liveCaptionCueKey(incoming),
        revision = maxOf(previous.revision, incoming.revision),
        sourceId = incoming.sourceId.ifBlank { previous.sourceId },
        english = incoming.english.ifBlank { previous.english },
        zhHant = incoming.zhHant.ifBlank { previous.zhHant },
        final = previous.final || incoming.final || incoming.stability == "final",
        stability = incoming.stability.ifBlank { previous.stability },
        audioStartMs = incoming.audioStartMs ?: previous.audioStartMs,
        audioEndMs = incoming.audioEndMs ?: previous.audioEndMs,
        emittedAtMs = incoming.emittedAtMs ?: previous.emittedAtMs,
        serverNowMs = incoming.serverNowMs ?: previous.serverNowMs,
        receivedAtMs = incoming.receivedAtMs ?: previous.receivedAtMs,
        processingMs = incoming.processingMs ?: previous.processingMs,
        delayMs = incoming.delayMs ?: previous.delayMs,
        suggestedHoldMs = incoming.suggestedHoldMs ?: previous.suggestedHoldMs,
    )
}

internal fun reducePendingLiveCaptionPresentations(
    pending: Map<String, LiveCaptionCue>,
    incoming: LiveCaptionCue,
): Map<String, LiveCaptionCue> {
    val incomingKey = liveCaptionCueKey(incoming)
    if (incomingKey.isBlank()) return pending
    val next = LinkedHashMap(pending)
    next.entries.removeAll { (key, queued) ->
        key != incomingKey && !queued.final && isSupersededPartial(queued, incoming)
    }
    val merged = mergeLiveCaptionCue(next[incomingKey], incoming.copy(cueId = incomingKey))
    if (next[incomingKey] == merged && next.size == pending.size) return pending
    next[incomingKey] = merged
    while (next.size > 32) next.remove(next.keys.first())
    return next
}

private fun isSupersededPartial(pending: LiveCaptionCue, incoming: LiveCaptionCue): Boolean =
    if (pending.audioStartMs != null && incoming.audioStartMs != null) {
        pending.audioStartMs <= incoming.audioStartMs
    } else {
        pending.seq != null && incoming.seq != null && pending.seq <= incoming.seq
    }

internal fun selectLiveCaptionCue(
    cues: Collection<LiveCaptionCue>,
    mode: LiveCaptionMode,
    previousVisible: LiveCaptionCue? = null,
): LiveCaptionCue? = cues.asSequence()
    .filter { it.isUsable(mode) }
    .sortedWith(
        compareByDescending<LiveCaptionCue> { it.audioStartMs ?: Long.MIN_VALUE }
            .thenByDescending { it.seq ?: Long.MIN_VALUE }
            .thenByDescending(LiveCaptionCue::revision)
    )
    .firstOrNull()
    ?: previousVisible?.takeIf { it.isUsable(mode) }

internal data class LiveCaptionSubscription(
    val channelId: String,
    val tvgId: String,
    val routeId: String,
    val sourceUrl: String,
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("channel_id", channelId)
        addProperty("tvg_id", tvgId)
        addProperty("route_id", routeId)
        addProperty("source_url", sourceUrl)
    }.toString()

    companion object {
        fun from(channel: Channel, route: ChannelRoute): LiveCaptionSubscription? {
            val tvgId = route.tvgId.ifBlank { channel.tvgId }.trim()
            val language = route.tvgLanguage.ifBlank { channel.tvgLanguage }
            if (tvgId.isBlank() || !isEnglishLanguageTag(language)) return null
            val sourceUrl = CaptionIdentifiers.canonicalStreamUrl(route.url)
            if (sourceUrl.isBlank()) return null
            return LiveCaptionSubscription(
                channelId = channel.captionChannelId.ifBlank {
                    CaptionIdentifiers.channelId(tvgId)
                },
                tvgId = tvgId,
                routeId = route.captionRouteId.ifBlank {
                    CaptionIdentifiers.routeId(sourceUrl)
                },
                sourceUrl = sourceUrl,
            ).takeIf {
                it.channelId.isNotBlank() && it.routeId.isNotBlank()
            }
        }
    }
}

internal fun isEnglishLanguageTag(value: String): Boolean = value.split(',').any { tag ->
    tag.trim().lowercase().replace('_', '-').substringBefore('-') == "en"
}

internal data class LiveCaptionError(
    val status: LiveCaptionStatus,
    val code: String,
    val message: String,
)

internal fun mapLiveCaptionError(code: String?, serverMessage: String?): LiveCaptionError {
    val normalized = code.orEmpty().trim()
    val knownMessage = when (normalized) {
        "caption_worker_busy" -> "另一個英文頻道正產生字幕，請稍後再試"
        "caption_connection_limit" -> "字幕已喺其他裝置開啟，請先關閉其中一個"
        "caption_premium_required" -> "即時字幕只限高級會員或以上"
        "caption_quota_exhausted" -> "今日 120 分鐘即時字幕額度已用完"
        "caption_quota_unavailable" -> "字幕額度服務暫時未能使用，請稍後再試"
        "managed_english_source_not_found" -> "此來源暫未支援即時字幕"
        "unsafe_source_destination" -> "此來源未通過字幕安全檢查"
        "catalog_unavailable" -> "字幕頻道資料暫時未能更新"
        else -> serverMessage.orEmpty().trim().ifBlank { "即時字幕暫時未能提供。" }
    }
    val status = when (normalized) {
        "caption_premium_required" -> LiveCaptionStatus.PREMIUM_REQUIRED
        "caption_quota_exhausted" -> LiveCaptionStatus.QUOTA_EXHAUSTED
        else -> LiveCaptionStatus.UNAVAILABLE
    }
    return LiveCaptionError(status, normalized, knownMessage)
}

internal object LiveCaptionReconnectPolicy {
    private const val MAX_ATTEMPTS = 5

    fun delayMs(attempt: Int): Long? {
        if (attempt !in 1..MAX_ATTEMPTS) return null
        return minOf(8_000L, 800L shl (attempt - 1))
    }
}

internal data class LiveCaptionClockAnchor(
    val serverNowMs: Long,
    val receivedAtMs: Long,
) {
    fun serverNowAt(clientNowMs: Long): Long =
        serverNowMs + (clientNowMs - receivedAtMs).coerceAtLeast(0)
}

internal fun liveCaptionTargetDelayMs(mode: LiveCaptionMode): Long = when (mode) {
    LiveCaptionMode.ENGLISH -> 7_000L
    LiveCaptionMode.BILINGUAL,
    LiveCaptionMode.TRADITIONAL_CHINESE,
    -> 10_000L
    LiveCaptionMode.OFF -> 0L
}

internal fun liveCaptionPresentationDelayMs(
    cue: LiveCaptionCue,
    mode: LiveCaptionMode,
    nowMs: Long,
): Long {
    val emittedAtMs = cue.emittedAtMs
    val inferredProcessingStartMs = if (emittedAtMs != null && cue.processingMs != null) {
        emittedAtMs - cue.processingMs.coerceAtLeast(0)
    } else {
        null
    }
    val serverAudioStartMs = cue.audioStartMs
        ?: if (cue.audioEndMs != null && inferredProcessingStartMs != null) {
            minOf(cue.audioEndMs, inferredProcessingStartMs)
        } else {
            cue.audioEndMs ?: inferredProcessingStartMs
        }
        ?: return 0L
    val localAudioStartMs = localCaptionTimestampMs(cue, serverAudioStartMs) ?: return 0L
    return (localAudioStartMs + liveCaptionTargetDelayMs(mode) - nowMs)
        .coerceIn(0L, 14_000L)
}

internal fun liveCaptionExpiryDelayMs(
    cue: LiveCaptionCue,
    mode: LiveCaptionMode,
    nowMs: Long,
): Long {
    val textLength = maxOf(cue.english.length, cue.zhHant.length)
    val readableHoldMs = (textLength * 90L).coerceIn(2_200L, 8_000L)
    val holdMs = maxOf(readableHoldMs, cue.suggestedHoldMs ?: 0L).coerceAtMost(12_000L)
    val localAudioEndMs = cue.audioEndMs?.let { localCaptionTimestampMs(cue, it) }
    val clearAtMs = maxOf(
        nowMs + holdMs,
        localAudioEndMs?.plus(liveCaptionTargetDelayMs(mode))?.plus(350L) ?: 0L,
    )
    return (clearAtMs - nowMs).coerceIn(350L, 16_000L)
}

private fun localCaptionTimestampMs(cue: LiveCaptionCue, serverTimestampMs: Long): Long? {
    val receivedAtMs = cue.receivedAtMs ?: return serverTimestampMs
    val serverReferenceMs = cue.serverNowMs ?: cue.emittedAtMs ?: return serverTimestampMs
    return serverTimestampMs + (receivedAtMs - serverReferenceMs)
}

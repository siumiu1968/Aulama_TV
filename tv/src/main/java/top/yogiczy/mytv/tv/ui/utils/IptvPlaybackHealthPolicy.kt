package top.yogiczy.mytv.tv.ui.utils

import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality

internal data class IptvPlaybackHealthWindow(
    val attemptStartedAtMs: Long,
    val firstFrameAtMs: Long? = null,
    val stallStartedAtMs: Long? = null,
    val completedBufferMs: Long = 0L,
    val stallTimestampsMs: List<Long> = emptyList(),
)

internal sealed interface IptvDegradationReason {
    data object FirstFrameTimeout : IptvDegradationReason
    data object LongRebuffer : IptvDegradationReason
    data object RepeatedStalls : IptvDegradationReason
    data class ExcessiveBuffering(val ratio: Double) : IptvDegradationReason
}

internal object IptvPlaybackHealthPolicy {
    const val firstFrameTimeoutMs = 12_000L
    const val fourKFirstFrameTimeoutMs = 15_000L
    const val relayFirstFrameTimeoutMs = 30_000L
    const val longRebufferTimeoutMs = 12_000L
    const val stallWindowMs = 45_000L
    const val stallThreshold = 3
    const val bufferRatioWindowMs = 60_000L
    const val bufferRatioThreshold = 0.15

    fun firstFrameTimeoutMsFor(
        quality: ChannelQuality,
        isRelay: Boolean,
    ): Long = when {
        isRelay -> relayFirstFrameTimeoutMs
        quality == ChannelQuality.UHD_4K -> fourKFirstFrameTimeoutMs
        else -> firstFrameTimeoutMs
    }

    fun start(nowMs: Long): IptvPlaybackHealthWindow =
        IptvPlaybackHealthWindow(attemptStartedAtMs = nowMs)

    fun onFirstFrame(
        state: IptvPlaybackHealthWindow,
        nowMs: Long,
    ): IptvPlaybackHealthWindow = state.copy(
        firstFrameAtMs = nowMs,
        stallStartedAtMs = null,
        completedBufferMs = 0L,
        stallTimestampsMs = emptyList(),
    )

    fun onBuffering(
        state: IptvPlaybackHealthWindow,
        buffering: Boolean,
        nowMs: Long,
    ): IptvPlaybackHealthWindow {
        if (state.firstFrameAtMs == null) return state
        val recentStalls = state.stallTimestampsMs.filter { nowMs - it <= stallWindowMs }
        return if (buffering && state.stallStartedAtMs == null) {
            state.copy(
                stallStartedAtMs = nowMs,
                stallTimestampsMs = recentStalls + nowMs,
            )
        } else if (!buffering && state.stallStartedAtMs != null) {
            state.copy(
                stallStartedAtMs = null,
                completedBufferMs = state.completedBufferMs +
                    (nowMs - state.stallStartedAtMs).coerceAtLeast(0L),
                stallTimestampsMs = recentStalls,
            )
        } else {
            state.copy(stallTimestampsMs = recentStalls)
        }
    }

    fun onExternalStall(
        state: IptvPlaybackHealthWindow,
        nowMs: Long,
    ): IptvPlaybackHealthWindow = if (state.firstFrameAtMs == null) {
        state
    } else {
        state.copy(
            stallTimestampsMs = (
                state.stallTimestampsMs.filter { nowMs - it <= stallWindowMs } + nowMs
                ).distinct(),
        )
    }

    fun evaluate(
        state: IptvPlaybackHealthWindow,
        nowMs: Long,
        firstFrameDeadlineMs: Long = firstFrameTimeoutMs,
    ): IptvDegradationReason? {
        val firstFrameAt = state.firstFrameAtMs
        if (firstFrameAt == null) {
            return IptvDegradationReason.FirstFrameTimeout.takeIf {
                nowMs - state.attemptStartedAtMs >= firstFrameDeadlineMs
            }
        }
        val activeBufferMs = state.stallStartedAtMs
            ?.let { (nowMs - it).coerceAtLeast(0L) }
            ?: 0L
        if (activeBufferMs >= longRebufferTimeoutMs) {
            return IptvDegradationReason.LongRebuffer
        }
        val stalls = state.stallTimestampsMs.count { nowMs - it <= stallWindowMs }
        if (stalls >= stallThreshold) return IptvDegradationReason.RepeatedStalls

        val playbackMs = (nowMs - firstFrameAt).coerceAtLeast(0L)
        if (playbackMs < bufferRatioWindowMs) return null
        val ratio = (state.completedBufferMs + activeBufferMs).toDouble() / playbackMs
        return IptvDegradationReason.ExcessiveBuffering(ratio).takeIf {
            ratio > bufferRatioThreshold
        }
    }

    fun reasonCode(reason: IptvDegradationReason): String = when (reason) {
        IptvDegradationReason.FirstFrameTimeout -> "first-frame-timeout"
        IptvDegradationReason.LongRebuffer -> "long-rebuffer"
        IptvDegradationReason.RepeatedStalls -> "stall-threshold"
        is IptvDegradationReason.ExcessiveBuffering ->
            "buffer-ratio:${"%.3f".format(java.util.Locale.US, reason.ratio)}"
    }
}

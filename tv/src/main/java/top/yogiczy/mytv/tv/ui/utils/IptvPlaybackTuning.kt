package top.yogiczy.mytv.tv.ui.utils

enum class IptvStabilityProfile {
    FAST_START,
    STABLE_LIVE,
}

internal data class Media3BufferTuning(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
)

internal data class IjkBufferTuning(
    val bufferSizeBytes: Long,
    val infiniteBuffer: Int,
    val packetBuffering: Int,
)

// 線路狀況會隨時間改變；近期卡頓先暫用穩定檔，連續穩播後自動回復快開。
internal const val BUFFER_ISSUE_PROFILE_EXPIRY_MS = 30 * 60 * 1000L
internal const val BUFFER_ISSUE_RECOVERY_WATCH_MS = 2 * 60 * 1000L

internal fun media3BufferTuning(profile: IptvStabilityProfile): Media3BufferTuning = when (profile) {
    IptvStabilityProfile.FAST_START -> Media3BufferTuning(8_000, 35_000, 1_200, 3_000)
    IptvStabilityProfile.STABLE_LIVE -> Media3BufferTuning(12_000, 45_000, 2_500, 6_000)
}

internal fun ijkBufferTuning(profile: IptvStabilityProfile): IjkBufferTuning = when (profile) {
    IptvStabilityProfile.FAST_START -> IjkBufferTuning(256 * 1024L, 1, 0)
    IptvStabilityProfile.STABLE_LIVE -> IjkBufferTuning(256 * 1024L, 0, 1)
}

internal fun isBufferingIssue(reason: String): Boolean = reason == "long-rebuffer" ||
    reason == "stall-threshold" ||
    reason == "audio-underrun" ||
    reason == "ijk-playback-stalled" ||
    reason.startsWith("buffer-ratio:")

internal fun selectIptvStabilityProfile(
    health: IptvRouteHealth?,
    mode: IptvPlaybackMode,
    now: Long = System.currentTimeMillis(),
): IptvStabilityProfile {
    val issueAt = health?.lastBufferIssueAt ?: return IptvStabilityProfile.FAST_START
    val sameMode = health.lastBufferIssueMode == mode.name
    val isRecent = now >= issueAt && now - issueAt <= BUFFER_ISSUE_PROFILE_EXPIRY_MS
    val recovered = health.stableWatchSinceBufferIssueMs >= BUFFER_ISSUE_RECOVERY_WATCH_MS
    return if (sameMode && isRecent && !recovered) {
        IptvStabilityProfile.STABLE_LIVE
    } else {
        IptvStabilityProfile.FAST_START
    }
}

internal fun profileAfterPlaybackModeFallback(
    profile: IptvStabilityProfile,
    previousMode: IptvPlaybackMode,
    nextMode: IptvPlaybackMode,
): IptvStabilityProfile = if (previousMode == nextMode) profile else IptvStabilityProfile.FAST_START

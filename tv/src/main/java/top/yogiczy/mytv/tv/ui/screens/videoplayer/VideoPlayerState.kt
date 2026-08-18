package top.yogiczy.mytv.tv.ui.screens.videoplayer

import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.IJKVideoPlayer
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.LeTvVideoPlayer
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.Media3VideoPlayer
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.IptvDegradationReason
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackMode
import top.yogiczy.mytv.tv.ui.utils.IptvRouteHealth
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackHealthPolicy
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackHealthWindow
import top.yogiczy.mytv.tv.ui.utils.IptvStabilityProfile
import top.yogiczy.mytv.tv.ui.utils.profileAfterPlaybackModeFallback
import top.yogiczy.mytv.tv.ui.utils.selectIptvStabilityProfile

internal fun shouldTryPlaybackModeFallback(errorCodeName: String, errorCode: Int): Boolean {
    val normalized = errorCodeName.uppercase()
    return errorCode == -1010 || listOf(
        "DECODER",
        "DECODING",
        "UNSUPPORTED",
        "PARSING_CONTAINER",
    ).any(normalized::contains)
}

internal fun shouldTryPlaybackModeFallbackForRoute(
    quality: ChannelQuality,
    currentMode: IptvPlaybackMode,
    errorCodeName: String,
    errorCode: Int,
): Boolean =
    (quality == ChannelQuality.UHD_4K && currentMode == IptvPlaybackMode.MEDIA3) ||
        shouldTryPlaybackModeFallback(errorCodeName, errorCode)

internal fun selectPlaybackModeForRoute(
    quality: ChannelQuality,
    preferredMode: IptvPlaybackMode?,
    configuredType: Configs.VideoPlayerType,
    requiresTvbHlsSession: Boolean,
    sdkInt: Int,
): IptvPlaybackMode = when {
    quality == ChannelQuality.UHD_4K && preferredMode == IptvPlaybackMode.IJK ->
        IptvPlaybackMode.IJK

    quality == ChannelQuality.UHD_4K && sdkInt <= Build.VERSION_CODES.M ->
        IptvPlaybackMode.IJK

    quality == ChannelQuality.UHD_4K -> IptvPlaybackMode.MEDIA3
    requiresTvbHlsSession -> IptvPlaybackMode.MEDIA3
    preferredMode != null -> preferredMode
    configuredType == Configs.VideoPlayerType.IJK -> IptvPlaybackMode.IJK
    else -> IptvPlaybackMode.MEDIA3
}

internal fun playbackModeFallbackCandidates(
    quality: ChannelQuality,
    currentMode: IptvPlaybackMode,
    sdkInt: Int = Build.VERSION.SDK_INT,
): List<IptvPlaybackMode> = if (quality == ChannelQuality.UHD_4K) {
    when (currentMode) {
        IptvPlaybackMode.MEDIA3 -> listOf(IptvPlaybackMode.IJK)
        IptvPlaybackMode.IJK -> if (sdkInt > Build.VERSION_CODES.M) {
            listOf(IptvPlaybackMode.MEDIA3)
        } else {
            emptyList()
        }
        IptvPlaybackMode.IJK_SOFTWARE -> emptyList()
    }
} else {
    when (currentMode) {
        IptvPlaybackMode.IJK -> listOf(
            IptvPlaybackMode.MEDIA3,
            IptvPlaybackMode.IJK_SOFTWARE,
        )

        IptvPlaybackMode.IJK_SOFTWARE -> listOf(
            IptvPlaybackMode.MEDIA3,
            IptvPlaybackMode.IJK,
        )

        IptvPlaybackMode.MEDIA3 -> listOf(
            IptvPlaybackMode.IJK,
            IptvPlaybackMode.IJK_SOFTWARE,
        )
    }
}

internal fun shouldTryPlaybackModeFallbackForDegradation(reason: String): Boolean = reason !in setOf(
    "ijk-slow-rendering",
    "ijk-av-sync-drift",
    "slow-rendering",
    "dropped-frames",
)

internal fun updatedContinuousHealthStartMs(
    previousStartMs: Long,
    nowMs: Long,
    healthy: Boolean,
): Long = when {
    !healthy -> 0L
    previousStartMs > 0L -> previousStartMs
    else -> nowMs
}

internal fun continuousHealthyDurationMs(
    healthyStartMs: Long,
    nowMs: Long,
    healthy: Boolean,
): Long = if (healthy && healthyStartMs > 0L) {
    (nowMs - healthyStartMs).coerceAtLeast(0L)
} else {
    0L
}

internal fun engineFirstFrameTimeoutMs(healthDeadlineMs: Long): Long =
    healthDeadlineMs.coerceAtLeast(1_000L) + 5_000L

/** A callback may mutate state only for the current physical player session. */
internal fun acceptsPlayerSessionCallback(
    sourceSession: Long,
    activeSession: Long,
    hasTerminalRetry: Boolean,
): Boolean = sourceSession == activeSession && !hasTerminalRetry

internal fun acceptsPendingPlayerPrepare(
    pendingSession: Long?,
    activeSession: Long,
): Boolean = pendingSession == activeSession

internal fun acceptsVideoOutputGeneration(
    sourceGeneration: Int,
    activeGeneration: Int,
): Boolean = sourceGeneration == activeGeneration

internal data class LiveCaptionMediaItemKey(
    val routeUrl: String,
    val targetOffsetMs: Long?,
)

internal enum class LiveCaptionOffsetAction {
    VERIFY_CURRENT_MEDIA_ITEM,
    REPREPARE_MEDIA3,
    ALREADY_CLEAR,
    REJECT,
}

internal fun liveCaptionOffsetAction(
    desired: LiveCaptionMediaItemKey,
    currentMode: IptvPlaybackMode,
    preparedMedia3Item: LiveCaptionMediaItemKey?,
    lastMedia3Reprepare: LiveCaptionMediaItemKey?,
): LiveCaptionOffsetAction = when {
    currentMode != IptvPlaybackMode.MEDIA3 && desired.targetOffsetMs == null ->
        LiveCaptionOffsetAction.ALREADY_CLEAR
    currentMode == IptvPlaybackMode.MEDIA3 && preparedMedia3Item == desired ->
        LiveCaptionOffsetAction.VERIFY_CURRENT_MEDIA_ITEM
    lastMedia3Reprepare == desired -> LiveCaptionOffsetAction.REJECT
    else -> LiveCaptionOffsetAction.REPREPARE_MEDIA3
}

@Stable
class VideoPlayerState(
    private var instance: VideoPlayer,
    private val settingsViewModel: SettingsViewModel,
    private val context: android.content.Context,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private var defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
) {
    private var currentRoute: ChannelRoute? = null
    private var pendingPrepareRoute: ChannelRoute? = null
    private var pendingPrepareSession: Long? = null
    private var currentSurface: SurfaceView? = null
    private var currentTexture: TextureView? = null
    private var activePlayerType = when (instance) {
        is Media3VideoPlayer -> Configs.VideoPlayerType.MEDIA3
        else -> Configs.VideoPlayerType.IJK
    }
    private var activeIJKSoftwareDecode = false
    private var activeLeTvVendorPlayer = instance is LeTvVideoPlayer
    private var activeStabilityProfile = IptvStabilityProfile.FAST_START
    private var liveOffsetTargetMs: Long? = null
    private var preparedMedia3LiveOffsetItem: LiveCaptionMediaItemKey? = null
    private var lastCaptionMedia3Reprepare: LiveCaptionMediaItemKey? = null
    private var initialized = false
    private var playbackGeneration = 0
    private var activePlayerSession = 0L
    private var fourKFallbackRouteUrl: String? = null
    private val attemptedPlaybackModes = mutableSetOf<IptvPlaybackMode>()
    private var bufferingHealthJob: Job? = null
    private var firstFrameHealthJob: Job? = null
    private var ratioHealthJob: Job? = null
    private var playbackHealthWindow: IptvPlaybackHealthWindow =
        IptvPlaybackHealthPolicy.start(0L)
    private var currentFirstFrameTimeoutMs = IptvPlaybackHealthPolicy.firstFrameTimeoutMs
    private var degradedReported = false
    private var appInForeground by mutableStateOf(false)
    private var terminalRetryAction: (() -> Unit)? = null
    private var continuousHealthStartMs = 0L
    val isPlaybackForeground: Boolean
        get() = appInForeground
    /** 顯示模式 */
    var displayMode by mutableStateOf(defaultDisplayModeProvider())

    /** 視頻寬高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 錯誤 */
    var error by mutableStateOf<String?>(null)
        private set

    /** 播放器仍在自動切換引擎或後備線路，未到最終失敗。 */
    var retryMessage by mutableStateOf<String?>(null)
        private set

    var hasTerminalRetry by mutableStateOf(false)
        private set

    /** 正在緩衝 */
    var isBuffering by mutableStateOf(false)

    /** 正在播放 */
    var isPlaying by mutableStateOf(false)

    /** 新線路真正渲染第一格畫面後先為 true。 */
    var hasRenderedFirstFrame by mutableStateOf(false)
        private set

    /** 4K/HDR 輸出必須使用 SurfaceView，避免 TextureView 將 HDR 轉成 SDR。 */
    var requiresSurfaceView by mutableStateOf(false)
        private set

    /** 播放器引擎轉換時重建輸出 View，避免新舊解碼器同時佔用同一 Surface。 */
    var videoOutputGeneration by mutableIntStateOf(0)
        private set

    /** 總時長 */
    var duration by mutableLongStateOf(0L)

    /** 當前播放位置 */
    var currentPosition by mutableLongStateOf(0L)

    /** 元數據 */
    var metadata by mutableStateOf(VideoPlayer.Metadata())

    val playbackMode: IptvPlaybackMode
        get() = when (activePlayerType) {
            Configs.VideoPlayerType.MEDIA3 -> IptvPlaybackMode.MEDIA3
            Configs.VideoPlayerType.IJK -> if (
                activeIJKSoftwareDecode || (
                    Configs.videoPlayerForceSoftDecode &&
                        currentRoute?.quality != ChannelQuality.UHD_4K
                    )
            ) {
                IptvPlaybackMode.IJK_SOFTWARE
            } else {
                IptvPlaybackMode.IJK
            }
        }

    fun prepare(
        route: ChannelRoute,
        retrying: Boolean = false,
        preferredPlaybackMode: IptvPlaybackMode? = null,
        learnedHealth: IptvRouteHealth? = null,
        firstFrameTimeoutMs: Long = IptvPlaybackHealthPolicy.firstFrameTimeoutMs,
    ) {
        if (!initialized) initialize()
        // Reject a callback posted by the previous attempt before terminal state is cleared.
        activePlayerSession += 1
        clearTerminalRetry()
        if (currentRoute?.url != route.url) {
            fourKFallbackRouteUrl = null
            preparedMedia3LiveOffsetItem = null
            lastCaptionMedia3Reprepare = null
        }
        if (
            route.quality == ChannelQuality.UHD_4K &&
            preferredPlaybackMode == IptvPlaybackMode.IJK
        ) {
            // A previous first frame proved that standard IJK works for this 4K URL.
            // Reuse it instead of probing Media3 or a vendor decoder again.
            fourKFallbackRouteUrl = route.url
        }
        attemptedPlaybackModes.clear()
        playbackGeneration += 1
        currentFirstFrameTimeoutMs = firstFrameTimeoutMs.coerceAtLeast(1_000L)
        resetPlaybackHealthWindow()
        degradedReported = false
        currentRoute = route
        val mode = selectPlaybackMode(route, preferredPlaybackMode)
        activeStabilityProfile = selectIptvStabilityProfile(learnedHealth, mode)
        requiresSurfaceView = route.quality == ChannelQuality.UHD_4K ||
            mode == IptvPlaybackMode.MEDIA3
        if (requiresSurfaceView) currentTexture = null
        error = null
        retryMessage = if (retrying) "自動重試中" else null
        hasRenderedFirstFrame = false
        isBuffering = true
        continuousHealthStartMs = 0L
        attemptedPlaybackModes += mode
        prepareWithMode(route, mode)
    }

    fun play() {
        if (appInForeground && !hasTerminalRetry) instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun setAppForeground(foreground: Boolean) {
        appInForeground = foreground
        instance.setPlaybackAllowed(foreground)
        if (foreground && currentRoute != null && !hasTerminalRetry) {
            instance.play()
            if (isBuffering && hasRenderedFirstFrame) scheduleLongRebufferEvaluation()
        } else {
            bufferingHealthJob?.cancel()
        }
        updateContinuousHealth()
    }

    fun seekTo(position: Long) {
        instance.seekTo(position)
    }

    /**
     * 即時字幕需要一個可量度嘅 live edge。Media3 可以精準調整；如果目前係
     * IJK，啟用字幕時只重建同一條實際播放線路一次，唔會消耗或重排路由。
     */
    fun setLiveCaptionOffsetTargetMs(targetMs: Long?): Boolean {
        val targetChanged = liveOffsetTargetMs != targetMs
        liveOffsetTargetMs = targetMs
        val route = currentRoute ?: return false
        if (targetChanged) lastCaptionMedia3Reprepare = null
        val desired = LiveCaptionMediaItemKey(route.url, targetMs)
        return when (
            liveCaptionOffsetAction(
                desired = desired,
                currentMode = playbackMode,
                preparedMedia3Item = preparedMedia3LiveOffsetItem,
                lastMedia3Reprepare = lastCaptionMedia3Reprepare,
            )
        ) {
            LiveCaptionOffsetAction.VERIFY_CURRENT_MEDIA_ITEM ->
                instance.setLiveOffsetTargetMs(targetMs)
            LiveCaptionOffsetAction.ALREADY_CLEAR -> true
            LiveCaptionOffsetAction.REJECT -> false
            LiveCaptionOffsetAction.REPREPARE_MEDIA3 -> {
                lastCaptionMedia3Reprepare = desired
                prepare(
                    route = route,
                    retrying = true,
                    preferredPlaybackMode = IptvPlaybackMode.MEDIA3,
                    firstFrameTimeoutMs = currentFirstFrameTimeoutMs,
                )
                // A rebuild was scheduled, but synchronization is not proven until the
                // new MediaItem renders a frame and this method verifies its live offset.
                false
            }
        }
    }

    fun stop() {
        stopPlayback(clearTerminalRetry = true)
    }

    private fun stopPlayback(clearTerminalRetry: Boolean) {
        // A stopped player may still dispatch native callbacks after terminal retry is cleared.
        activePlayerSession += 1
        playbackGeneration += 1
        bufferingHealthJob?.cancel()
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        hasRenderedFirstFrame = false
        isBuffering = false
        continuousHealthStartMs = 0L
        error = null
        retryMessage = null
        if (clearTerminalRetry) clearTerminalRetry()
        pendingPrepareRoute = null
        pendingPrepareSession = null
        instance.stop()
    }

    fun showRetryNotice(message: String) {
        clearTerminalRetry()
        error = null
        retryMessage = message
        isBuffering = true
        continuousHealthStartMs = 0L
    }

    fun keepCurrentRoute() {
        clearTerminalRetry()
        retryMessage = null
        error = null
        if (!hasRenderedFirstFrame) return

        degradedReported = false
        val now = System.currentTimeMillis()
        continuousHealthStartMs = updatedContinuousHealthStartMs(
            previousStartMs = 0L,
            nowMs = now,
            healthy = hasRenderedFirstFrame && isPlaying && !isBuffering,
        )
        playbackHealthWindow = IptvPlaybackHealthPolicy.onFirstFrame(
            IptvPlaybackHealthPolicy.start(now),
            now,
        )
        if (isBuffering) {
            playbackHealthWindow = IptvPlaybackHealthPolicy.onBuffering(
                playbackHealthWindow,
                buffering = true,
                nowMs = now,
            )
            scheduleLongRebufferEvaluation()
        }
        scheduleBufferRatioEvaluation()
        instance.restartPlaybackHealthMonitoring()
    }

    fun showTerminalError(message: String, onRetry: () -> Unit) {
        terminalRetryAction = onRetry
        hasTerminalRetry = true
        stopPlayback(clearTerminalRetry = false)
        error = message
        retryMessage = null
        isPlaying = false
        isBuffering = false
        continuousHealthStartMs = 0L
    }

    fun retryTerminalError() {
        val retry = terminalRetryAction ?: return
        clearTerminalRetry()
        error = null
        retry()
    }

    private fun clearTerminalRetry() {
        terminalRetryAction = null
        hasTerminalRetry = false
    }

    fun continuousHealthyPlaybackMs(nowMs: Long = System.currentTimeMillis()): Long =
        continuousHealthyDurationMs(
            healthyStartMs = continuousHealthStartMs,
            nowMs = nowMs,
            healthy = hasRenderedFirstFrame && isPlaying && !isBuffering &&
                !degradedReported && !hasTerminalRetry,
        )

    private fun updateContinuousHealth(nowMs: Long = System.currentTimeMillis()) {
        continuousHealthStartMs = updatedContinuousHealthStartMs(
            previousStartMs = continuousHealthStartMs,
            nowMs = nowMs,
            healthy = hasRenderedFirstFrame && isPlaying && !isBuffering &&
                !degradedReported && !hasTerminalRetry,
        )
    }

    fun setVideoSurfaceView(
        surfaceView: SurfaceView,
        outputGeneration: Int = videoOutputGeneration,
    ) {
        if (!acceptsVideoOutputGeneration(outputGeneration, videoOutputGeneration)) return
        currentSurface = surfaceView
        currentTexture = null
        instance.setVideoSurfaceView(surfaceView)
        preparePendingRoute()
    }

    fun setVideoTextureView(
        textureView: TextureView,
        outputGeneration: Int = videoOutputGeneration,
    ) {
        if (requiresSurfaceView || !acceptsVideoOutputGeneration(outputGeneration, videoOutputGeneration)) return
        currentTexture = textureView
        currentSurface = null
        instance.setVideoTextureView(textureView)
        preparePendingRoute()
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<(String) -> Boolean>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()
    private val onPlaybackIssueObservedListeners = mutableListOf<(String) -> Unit>()
    private val onPlaybackDegradedListeners = mutableListOf<(String) -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onFirstFrame(listener: () -> Unit) {
        onFirstFrameListeners.add(listener)
    }

    fun onError(listener: (String) -> Boolean) {
        onErrorListeners.add(listener)
    }

    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    fun onPlaybackIssueObserved(listener: (String) -> Unit) {
        onPlaybackIssueObservedListeners.add(listener)
    }

    fun onPlaybackDegraded(listener: (String) -> Unit) {
        onPlaybackDegradedListeners.add(listener)
    }

    private fun reportPlaybackDegraded(
        reason: String,
        allowPlaybackModeFallback: Boolean = false,
    ) {
        if (degradedReported) return
        // Telemetry must be captured before an engine fallback consumes the event. It lets the
        // next attempt of the same route + decoder choose the stable buffer profile.
        onPlaybackIssueObservedListeners.forEach { it(reason) }
        if (
            allowPlaybackModeFallback &&
            attemptedPlaybackModes.size == 1 &&
            tryPlaybackModeFallback()
        ) return
        degradedReported = true
        continuousHealthStartMs = 0L
        bufferingHealthJob?.cancel()
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        error = null
        retryMessage = "自動切換線路中"
        onPlaybackDegradedListeners.forEach { it(reason) }
    }

    private fun reportHealthDegradation(reason: IptvDegradationReason) {
        reportPlaybackDegraded(
            reason = IptvPlaybackHealthPolicy.reasonCode(reason),
            allowPlaybackModeFallback = reason == IptvDegradationReason.FirstFrameTimeout ||
                reason == IptvDegradationReason.LongRebuffer ||
                currentRoute?.quality == ChannelQuality.UHD_4K,
        )
    }

    private fun resetPlaybackHealthWindow() {
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        val generation = playbackGeneration
        playbackHealthWindow = IptvPlaybackHealthPolicy.start(System.currentTimeMillis())
        firstFrameHealthJob = coroutineScope.launch {
            delay(currentFirstFrameTimeoutMs)
            if (generation != playbackGeneration || hasRenderedFirstFrame) return@launch
            IptvPlaybackHealthPolicy.evaluate(
                playbackHealthWindow,
                System.currentTimeMillis(),
                currentFirstFrameTimeoutMs,
            )?.let(::reportHealthDegradation)
        }
    }

    private fun scheduleBufferRatioEvaluation() {
        ratioHealthJob?.cancel()
        val generation = playbackGeneration
        ratioHealthJob = coroutineScope.launch {
            delay(IptvPlaybackHealthPolicy.bufferRatioWindowMs)
            while (generation == playbackGeneration && !degradedReported) {
                IptvPlaybackHealthPolicy.evaluate(
                    playbackHealthWindow,
                    System.currentTimeMillis(),
                    currentFirstFrameTimeoutMs,
                )?.let(::reportHealthDegradation)
                delay(5_000L)
            }
        }
    }

    private fun scheduleLongRebufferEvaluation() {
        bufferingHealthJob?.cancel()
        if (!appInForeground || !isBuffering || !hasRenderedFirstFrame || degradedReported) return

        val generation = playbackGeneration
        bufferingHealthJob = coroutineScope.launch {
            delay(IptvPlaybackHealthPolicy.longRebufferTimeoutMs)
            if (
                generation != playbackGeneration ||
                !appInForeground ||
                !isBuffering ||
                !hasRenderedFirstFrame ||
                degradedReported
            ) return@launch

            IptvPlaybackHealthPolicy.evaluate(
                playbackHealthWindow,
                System.currentTimeMillis(),
                currentFirstFrameTimeoutMs,
            )?.let(::reportHealthDegradation)
        }
    }

    private fun tryPlaybackModeFallback(): Boolean {
        val route = currentRoute ?: return false
        val candidates = playbackModeFallbackCandidates(route.quality, playbackMode)
        val nextMode = candidates.firstOrNull { it !in attemptedPlaybackModes } ?: return false
        val previousMode = playbackMode
        attemptedPlaybackModes += nextMode
        if (route.quality == ChannelQuality.UHD_4K && nextMode == IptvPlaybackMode.IJK) {
            // Media3 failed on this exact 4K route. Use the standard IJK path once before
            // considering another 4K route; do not detour through a vendor/software decoder.
            fourKFallbackRouteUrl = route.url
        }

        playbackGeneration += 1
        val fallbackGeneration = playbackGeneration
        // The replacement runs on the next main-loop turn. Invalidate the source now so a
        // second queued native callback cannot consume another mode or jump to terminal state.
        activePlayerSession += 1
        bufferingHealthJob?.cancel()
        resetPlaybackHealthWindow()
        degradedReported = false
        hasRenderedFirstFrame = false
        isBuffering = true
        continuousHealthStartMs = 0L
        error = null
        retryMessage = if (nextMode == IptvPlaybackMode.IJK_SOFTWARE) {
            "正在啟用兼容解碼"
        } else {
            "正在調整播放方式"
        }
        coroutineScope.launch {
            if (
                fallbackGeneration == playbackGeneration &&
                !hasTerminalRetry &&
                currentRoute?.url == route.url &&
                playbackMode == previousMode
            ) {
                activeStabilityProfile = profileAfterPlaybackModeFallback(
                    activeStabilityProfile,
                    previousMode,
                    nextMode,
                )
                prepareWithMode(route, nextMode)
            }
        }
        return true
    }


    private fun selectPlaybackMode(
        route: ChannelRoute,
        preferredMode: IptvPlaybackMode? = null,
        configuredType: Configs.VideoPlayerType = Configs.videoPlayerType,
    ): IptvPlaybackMode = selectPlaybackModeForRoute(
        quality = route.quality,
        preferredMode = preferredMode,
        configuredType = configuredType,
        requiresTvbHlsSession = Media3VideoPlayer.requiresTvbHlsSession(route),
        sdkInt = Build.VERSION.SDK_INT,
    )

    private fun shouldUseLeTvVendorPlayer(mode: IptvPlaybackMode): Boolean =
        mode == IptvPlaybackMode.IJK &&
            currentRoute?.quality == ChannelQuality.UHD_4K &&
            currentRoute?.url != fourKFallbackRouteUrl &&
            LeTvVideoPlayer.isAvailable(context)

    private fun createPlayer(
        mode: IptvPlaybackMode,
        stabilityProfile: IptvStabilityProfile,
    ): VideoPlayer = when (mode) {
        IptvPlaybackMode.IJK -> if (shouldUseLeTvVendorPlayer(mode)) {
            LeTvVideoPlayer(context, coroutineScope)
        } else {
            IJKVideoPlayer(context, coroutineScope, stabilityProfile = stabilityProfile)
        }
        IptvPlaybackMode.IJK_SOFTWARE -> IJKVideoPlayer(
            context,
            coroutineScope,
            forceSoftwareDecode = true,
            stabilityProfile = stabilityProfile,
        )
        IptvPlaybackMode.MEDIA3 -> Media3VideoPlayer(context, coroutineScope, stabilityProfile)
    }

    private fun prepareWithMode(route: ChannelRoute, mode: IptvPlaybackMode) {
        requiresSurfaceView = route.quality == ChannelQuality.UHD_4K ||
            mode == IptvPlaybackMode.MEDIA3
        replacePlayerForSession(mode)
        preparedMedia3LiveOffsetItem = if (mode == IptvPlaybackMode.MEDIA3) {
            LiveCaptionMediaItemKey(route.url, liveOffsetTargetMs)
        } else {
            null
        }
        // VideoPlayerState owns route-aware engine fallback. Keep the lower-level
        // player watchdog as a delayed safety net so both timers cannot fire in
        // the same frame and race terminal state against a new engine.
        instance.setFirstFrameTimeoutMs(engineFirstFrameTimeoutMs(currentFirstFrameTimeoutMs))
        pendingPrepareRoute = route
        pendingPrepareSession = activePlayerSession
    }

    private fun preparePendingRoute() {
        val route = pendingPrepareRoute ?: return
        if (
            currentRoute?.url != route.url ||
            !acceptsPendingPlayerPrepare(pendingPrepareSession, activePlayerSession)
        ) {
            pendingPrepareRoute = null
            pendingPrepareSession = null
            return
        }
        pendingPrepareRoute = null
        pendingPrepareSession = null
        instance.prepare(route)
    }

    private fun replacePlayerForSession(mode: IptvPlaybackMode) {
        val type = when (mode) {
            IptvPlaybackMode.IJK,
            IptvPlaybackMode.IJK_SOFTWARE -> Configs.VideoPlayerType.IJK
            IptvPlaybackMode.MEDIA3 -> Configs.VideoPlayerType.MEDIA3
        }
        val softwareDecode = mode == IptvPlaybackMode.IJK_SOFTWARE
        val useLeTvVendorPlayer = shouldUseLeTvVendorPlayer(mode)
        // Invalidate callbacks before teardown: native players can post a final event here.
        activePlayerSession += 1
        instance.release()
        currentSurface = null
        currentTexture = null
        videoOutputGeneration += 1
        instance = createPlayer(mode, activeStabilityProfile)
        activePlayerType = type
        activeIJKSoftwareDecode = softwareDecode
        activeLeTvVendorPlayer = useLeTvVendorPlayer
        instance.setPlaybackAllowed(appInForeground)
        instance.setLiveOffsetTargetMs(liveOffsetTargetMs)
        if (initialized) configureInstance()
    }

    private fun configureInstance() {
        val source = instance
        val sourceSession = activePlayerSession
        fun acceptsSourceCallback(): Boolean = source === instance &&
            acceptsPlayerSessionCallback(sourceSession, activePlayerSession, hasTerminalRetry)

        source.initialize()
        source.onResolution { width, height ->
            if (!acceptsSourceCallback()) return@onResolution
            if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
        }
        source.onError playerError@ { ex ->
            if (!acceptsSourceCallback()) return@playerError
            val route = currentRoute
            if (
                ex != null &&
                route?.quality == ChannelQuality.UHD_4K &&
                activeLeTvVendorPlayer &&
                fourKFallbackRouteUrl != route.url
            ) {
                playbackGeneration += 1
                val vendorFallbackGeneration = playbackGeneration
                activePlayerSession += 1
                bufferingHealthJob?.cancel()
                firstFrameHealthJob?.cancel()
                ratioHealthJob?.cancel()
                fourKFallbackRouteUrl = route.url
                hasRenderedFirstFrame = false
                isBuffering = true
                continuousHealthStartMs = 0L
                error = null
                retryMessage = "正在切換兼容 4K 播放"
                coroutineScope.launch {
                    if (
                        vendorFallbackGeneration == playbackGeneration &&
                        !hasTerminalRetry &&
                        currentRoute?.url == route.url &&
                        activeLeTvVendorPlayer
                    ) {
                        activeStabilityProfile = IptvStabilityProfile.FAST_START
                        prepareWithMode(route, IptvPlaybackMode.IJK)
                    }
                }
                return@playerError
            }

            if (
                ex != null &&
                route != null &&
                shouldTryPlaybackModeFallbackForRoute(
                    quality = route.quality,
                    currentMode = playbackMode,
                    errorCodeName = ex.errorCodeName,
                    errorCode = ex.errorCode,
                ) &&
                tryPlaybackModeFallback()
            ) {
                return@playerError
            }

            hasRenderedFirstFrame = false
            continuousHealthStartMs = 0L
            val message = ex?.let { "${it.errorCodeName}(${it.errorCode})" }
            error = null
            retryMessage = message?.let { "自動重試中" }
            val willRetry = message != null && onErrorListeners.any { it(message) }
            if (!willRetry) {
                retryMessage = null
                error = message
            }

        }
        source.onReady {
            if (!acceptsSourceCallback()) return@onReady
            onReadyListeners.forEach { it.invoke() }
            error = null
            displayMode = defaultDisplayModeProvider()
        }
        source.onBuffering {
            if (!acceptsSourceCallback()) return@onBuffering
            isBuffering = it
            updateContinuousHealth()
            if (it) error = null
            if (!it) bufferingHealthJob?.cancel()
            val now = System.currentTimeMillis()
            playbackHealthWindow = IptvPlaybackHealthPolicy.onBuffering(
                playbackHealthWindow,
                buffering = it,
                nowMs = now,
            )
            IptvPlaybackHealthPolicy.evaluate(
                playbackHealthWindow,
                now,
                currentFirstFrameTimeoutMs,
            )
                ?.let(::reportHealthDegradation)
            if (it && hasRenderedFirstFrame && !degradedReported) {
                scheduleLongRebufferEvaluation()
            }
        }
        source.onPrepared {
            if (!acceptsSourceCallback()) return@onPrepared
        }
        source.onFirstFrame {
            if (!acceptsSourceCallback()) return@onFirstFrame
            bufferingHealthJob?.cancel()
            firstFrameHealthJob?.cancel()
            playbackHealthWindow = IptvPlaybackHealthPolicy.onFirstFrame(
                playbackHealthWindow,
                System.currentTimeMillis(),
            )
            scheduleBufferRatioEvaluation()
            hasRenderedFirstFrame = true
            isBuffering = false
            updateContinuousHealth()
            error = null
            retryMessage = null
            onFirstFrameListeners.forEach { it.invoke() }
        }
        source.onIsPlayingChanged { playing ->
            if (!acceptsSourceCallback()) return@onIsPlayingChanged
            if (playing && !appInForeground) source.pause()
            isPlaying = playing && appInForeground
            updateContinuousHealth()
        }
        source.onDurationChanged {
            if (acceptsSourceCallback()) duration = it
        }
        source.onCurrentPositionChanged {
            if (acceptsSourceCallback()) currentPosition = it
        }
        source.onMetadata {
            if (acceptsSourceCallback()) metadata = it
        }
        source.onInterrupt {
            if (acceptsSourceCallback()) onInterruptListeners.forEach { it.invoke() }
        }
        source.onPlaybackDegraded { reason ->
            if (!acceptsSourceCallback()) return@onPlaybackDegraded
            reportPlaybackDegraded(
                reason,
                allowPlaybackModeFallback = shouldTryPlaybackModeFallbackForDegradation(reason),
            )
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        settingsViewModel.videoPlayerTypeValue = Configs.videoPlayerType
        settingsViewModel.onVideoPlayerTypeChanged = { _ ->
            currentRoute?.let { route ->
                val wasPlaying = isPlaying
                val position = currentPosition
                prepare(route)
                seekTo(position)
                if (wasPlaying) play()
            }
        }
        configureInstance()
    }

    fun release() {
        initialized = false
        activePlayerSession += 1
        settingsViewModel.onVideoPlayerTypeChanged = null
        onReadyListeners.clear()
        onFirstFrameListeners.clear()
        onErrorListeners.clear()
        onInterruptListeners.clear()
        onPlaybackIssueObservedListeners.clear()
        onPlaybackDegradedListeners.clear()
        bufferingHealthJob?.cancel()
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        pendingPrepareRoute = null
        pendingPrepareSession = null
        clearTerminalRetry()
        instance.release()
    }
}

@Composable
fun rememberVideoPlayerState(
    defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
    settingsViewModel: SettingsViewModel = viewModel(),
): VideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        val player = when (Configs.videoPlayerType) {
            Configs.VideoPlayerType.IJK -> IJKVideoPlayer(context, coroutineScope)
            Configs.VideoPlayerType.MEDIA3 -> Media3VideoPlayer(context, coroutineScope)
        }
        VideoPlayerState(
            player,
            settingsViewModel,
            context,
            coroutineScope,
            defaultDisplayModeProvider,
        )
    }

    DisposableEffect(Unit) {
        state.initialize()
        onDispose { state.release() }
    }

    DisposableEffect(lifecycleOwner) {
        state.setAppForeground(lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.setAppForeground(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> state.setAppForeground(false)
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}

enum class VideoPlayerDisplayMode(
    val label: String,
    val value: Int,
) {
    /** 原始 */
    ORIGINAL("原始", 0),

    /** 填充 */
    FILL("填充", 1),

    /** 裁剪 */
    CROP("裁剪", 2),

    /** 4:3 */
    FOUR_THREE("4:3", 3),

    /** 16:9 */
    SIXTEEN_NINE("16:9", 4),

    /** 2.35:1 */
    WIDE("2.35:1", 5);

    companion object {
        fun fromValue(value: Int): VideoPlayerDisplayMode {
            return entries.firstOrNull { it.value == value } ?: ORIGINAL
        }
    }
}

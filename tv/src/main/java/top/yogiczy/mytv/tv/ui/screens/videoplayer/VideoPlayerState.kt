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
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackHealthPolicy
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackHealthWindow

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
): List<IptvPlaybackMode> = if (quality == ChannelQuality.UHD_4K) {
    if (currentMode == IptvPlaybackMode.MEDIA3) listOf(IptvPlaybackMode.IJK) else emptyList()
} else {
    when (currentMode) {
        IptvPlaybackMode.IJK -> listOf(
            IptvPlaybackMode.IJK_SOFTWARE,
            IptvPlaybackMode.MEDIA3,
        )

        IptvPlaybackMode.IJK_SOFTWARE -> listOf(
            IptvPlaybackMode.IJK,
            IptvPlaybackMode.MEDIA3,
        )

        IptvPlaybackMode.MEDIA3 -> listOf(
            IptvPlaybackMode.IJK,
            IptvPlaybackMode.IJK_SOFTWARE,
        )
    }
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
    private var currentSurface: SurfaceView? = null
    private var currentTexture: TextureView? = null
    private var activePlayerType = when (instance) {
        is Media3VideoPlayer -> Configs.VideoPlayerType.MEDIA3
        else -> Configs.VideoPlayerType.IJK
    }
    private var activeIJKSoftwareDecode = false
    private var activeLeTvVendorPlayer = instance is LeTvVideoPlayer
    private var initialized = false
    private var playbackGeneration = 0
    private var fourKFallbackRouteUrl: String? = null
    private val attemptedPlaybackModes = mutableSetOf<IptvPlaybackMode>()
    private var bufferingHealthJob: Job? = null
    private var firstFrameHealthJob: Job? = null
    private var ratioHealthJob: Job? = null
    private var playbackHealthWindow: IptvPlaybackHealthWindow =
        IptvPlaybackHealthPolicy.start(0L)
    private var currentFirstFrameTimeoutMs = IptvPlaybackHealthPolicy.firstFrameTimeoutMs
    private var degradedReported = false
    private var isAppForeground = false
    private val recentRebuffers = mutableListOf<Long>()
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
        firstFrameTimeoutMs: Long = IptvPlaybackHealthPolicy.firstFrameTimeoutMs,
    ) {
        if (!initialized) initialize()
        if (currentRoute?.url != route.url) {
            fourKFallbackRouteUrl = null
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
        recentRebuffers.clear()
        degradedReported = false
        currentRoute = route
        val mode = selectPlaybackMode(route, preferredPlaybackMode)
        requiresSurfaceView = route.quality == ChannelQuality.UHD_4K ||
            mode == IptvPlaybackMode.MEDIA3
        if (requiresSurfaceView) currentTexture = null
        error = null
        retryMessage = if (retrying) "自動重試中" else null
        hasRenderedFirstFrame = false
        isBuffering = true
        attemptedPlaybackModes += mode
        prepareWithMode(route, mode)
    }

    fun play() {
        if (isAppForeground) instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        instance.setPlaybackAllowed(foreground)
        if (foreground && currentRoute != null) instance.play()
    }

    fun seekTo(position: Long) {
        instance.seekTo(position)
    }

    fun stop() {
        playbackGeneration += 1
        bufferingHealthJob?.cancel()
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        recentRebuffers.clear()
        hasRenderedFirstFrame = false
        isBuffering = false
        error = null
        retryMessage = null
        pendingPrepareRoute = null
        instance.stop()
    }

    fun showRetryNotice(message: String) {
        error = null
        retryMessage = message
        isBuffering = true
    }

    fun keepCurrentRoute() {
        retryMessage = null
        error = null
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        currentSurface = surfaceView
        currentTexture = null
        instance.setVideoSurfaceView(surfaceView)
        preparePendingRoute()
    }

    fun setVideoTextureView(textureView: TextureView) {
        if (requiresSurfaceView) return
        currentTexture = textureView
        currentSurface = null
        instance.setVideoTextureView(textureView)
        preparePendingRoute()
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<(String) -> Boolean>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()
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

    fun onPlaybackDegraded(listener: (String) -> Unit) {
        onPlaybackDegradedListeners.add(listener)
    }

    private fun reportPlaybackDegraded(
        reason: String,
        allowPlaybackModeFallback: Boolean = false,
    ) {
        if (degradedReported) return
        if (
            allowPlaybackModeFallback &&
            attemptedPlaybackModes.size == 1 &&
            tryPlaybackModeFallback()
        ) return
        degradedReported = true
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
            allowPlaybackModeFallback = currentRoute?.quality == ChannelQuality.UHD_4K,
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
        bufferingHealthJob?.cancel()
        resetPlaybackHealthWindow()
        recentRebuffers.clear()
        degradedReported = false
        hasRenderedFirstFrame = false
        isBuffering = true
        error = null
        retryMessage = if (nextMode == IptvPlaybackMode.IJK_SOFTWARE) {
            "正在啟用兼容解碼"
        } else {
            "正在調整播放方式"
        }
        coroutineScope.launch {
            if (
                currentRoute?.url == route.url &&
                playbackMode == previousMode
            ) {
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

    private fun createPlayer(mode: IptvPlaybackMode): VideoPlayer = when (mode) {
        IptvPlaybackMode.IJK -> if (shouldUseLeTvVendorPlayer(mode)) {
            LeTvVideoPlayer(context, coroutineScope)
        } else {
            IJKVideoPlayer(context, coroutineScope)
        }
        IptvPlaybackMode.IJK_SOFTWARE -> IJKVideoPlayer(
            context,
            coroutineScope,
            forceSoftwareDecode = true,
        )
        IptvPlaybackMode.MEDIA3 -> Media3VideoPlayer(context, coroutineScope)
    }

    private fun prepareWithMode(route: ChannelRoute, mode: IptvPlaybackMode) {
        requiresSurfaceView = route.quality == ChannelQuality.UHD_4K ||
            mode == IptvPlaybackMode.MEDIA3
        val switchedPlayer = switchPlayerIfNeeded(mode)
        instance.setFirstFrameTimeoutMs(currentFirstFrameTimeoutMs)
        if (switchedPlayer) {
            pendingPrepareRoute = route
        } else {
            pendingPrepareRoute = null
            instance.prepare(route)
        }
    }

    private fun preparePendingRoute() {
        val route = pendingPrepareRoute ?: return
        if (currentRoute?.url != route.url) {
            pendingPrepareRoute = null
            return
        }
        pendingPrepareRoute = null
        instance.prepare(route)
    }

    private fun switchPlayerIfNeeded(mode: IptvPlaybackMode): Boolean {
        val type = when (mode) {
            IptvPlaybackMode.IJK,
            IptvPlaybackMode.IJK_SOFTWARE -> Configs.VideoPlayerType.IJK
            IptvPlaybackMode.MEDIA3 -> Configs.VideoPlayerType.MEDIA3
        }
        val softwareDecode = mode == IptvPlaybackMode.IJK_SOFTWARE
        val useLeTvVendorPlayer = shouldUseLeTvVendorPlayer(mode)
        if (
            activePlayerType == type &&
            (
                type != Configs.VideoPlayerType.IJK ||
                    (
                        activeIJKSoftwareDecode == softwareDecode &&
                            activeLeTvVendorPlayer == useLeTvVendorPlayer
                        )
                )
        ) return false

        instance.release()
        currentSurface = null
        currentTexture = null
        videoOutputGeneration += 1
        instance = createPlayer(mode)
        activePlayerType = type
        activeIJKSoftwareDecode = softwareDecode
        activeLeTvVendorPlayer = useLeTvVendorPlayer
        instance.setPlaybackAllowed(isAppForeground)
        if (initialized) configureInstance()
        return true
    }

    private fun configureInstance() {
        instance.initialize()
        instance.onResolution { width, height ->
            if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
        }
        instance.onError playerError@ { ex ->
            val route = currentRoute
            if (
                ex != null &&
                route?.quality == ChannelQuality.UHD_4K &&
                activeLeTvVendorPlayer &&
                fourKFallbackRouteUrl != route.url
            ) {
                fourKFallbackRouteUrl = route.url
                hasRenderedFirstFrame = false
                isBuffering = true
                error = null
                retryMessage = "正在切換兼容 4K 播放"
                coroutineScope.launch {
                    if (
                        currentRoute?.url == route.url &&
                        activeLeTvVendorPlayer
                    ) {
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
            val message = ex?.let { "${it.errorCodeName}(${it.errorCode})" }
            error = null
            retryMessage = message?.let { "自動重試中" }
            val willRetry = message != null && onErrorListeners.any { it(message) }
            if (!willRetry) {
                retryMessage = null
                error = message
            }

        }
        instance.onReady {
            onReadyListeners.forEach { it.invoke() }
            error = null
            displayMode = defaultDisplayModeProvider()
        }
        instance.onBuffering {
            isBuffering = it
            if (it) error = null
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
        }
        instance.onPrepared { }
        instance.onFirstFrame {
            firstFrameHealthJob?.cancel()
            playbackHealthWindow = IptvPlaybackHealthPolicy.onFirstFrame(
                playbackHealthWindow,
                System.currentTimeMillis(),
            )
            scheduleBufferRatioEvaluation()
            hasRenderedFirstFrame = true
            isBuffering = false
            error = null
            retryMessage = null
            onFirstFrameListeners.forEach { it.invoke() }
        }
        instance.onIsPlayingChanged { playing ->
            if (playing && !isAppForeground) instance.pause()
            isPlaying = playing && isAppForeground
        }
        instance.onDurationChanged { duration = it }
        instance.onCurrentPositionChanged { currentPosition = it }
        instance.onMetadata { metadata = it }
        instance.onInterrupt { onInterruptListeners.forEach { it.invoke() } }
        instance.onPlaybackDegraded { reason ->
            reportPlaybackDegraded(reason, allowPlaybackModeFallback = true)
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
        settingsViewModel.onVideoPlayerTypeChanged = null
        onReadyListeners.clear()
        onFirstFrameListeners.clear()
        onErrorListeners.clear()
        onInterruptListeners.clear()
        onPlaybackDegradedListeners.clear()
        bufferingHealthJob?.cancel()
        firstFrameHealthJob?.cancel()
        ratioHealthJob?.cancel()
        pendingPrepareRoute = null
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

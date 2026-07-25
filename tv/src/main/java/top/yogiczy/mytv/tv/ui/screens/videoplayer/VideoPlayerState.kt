package top.yogiczy.mytv.tv.ui.screens.videoplayer

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
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.Media3VideoPlayer
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.utils.Configs


@Stable
class VideoPlayerState(
    private var instance: VideoPlayer,
    private val settingsViewModel: SettingsViewModel,
    private val context: android.content.Context,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private var defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
) {
    private var currentRoute: ChannelRoute? = null
    private var currentSurface: SurfaceView? = null
    private var currentTexture: TextureView? = null
    private var activePlayerType = when (instance) {
        is Media3VideoPlayer -> Configs.VideoPlayerType.MEDIA3
        else -> Configs.VideoPlayerType.IJK
    }
    private var initialized = false
    private var playbackGeneration = 0
    private var fourKFallbackRouteUrl: String? = null
    private var bufferingHealthJob: Job? = null
    private var degradedReported = false
    private var isAppForeground = false
    private val recentRebuffers = mutableListOf<Long>()
    /** 顯示模式 */
    var displayMode by mutableStateOf(defaultDisplayModeProvider())

    /** 視頻寬高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 錯誤 */
    var error by mutableStateOf<String?>(null)

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

    fun prepare(route: ChannelRoute) {
        if (!initialized) initialize()
        if (currentRoute?.url != route.url) fourKFallbackRouteUrl = null
        playbackGeneration += 1
        bufferingHealthJob?.cancel()
        recentRebuffers.clear()
        degradedReported = false
        currentRoute = route
        requiresSurfaceView = route.quality == ChannelQuality.UHD_4K
        if (requiresSurfaceView) currentTexture = null
        error = null
        hasRenderedFirstFrame = false
        isBuffering = true
        switchPlayerIfNeeded(preferredPlayerType(route))
        instance.prepare(route)
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
        recentRebuffers.clear()
        hasRenderedFirstFrame = false
        isBuffering = false
        instance.stop()
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        currentSurface = surfaceView
        currentTexture = null
        instance.setVideoSurfaceView(surfaceView)
    }

    fun setVideoTextureView(textureView: TextureView) {
        if (requiresSurfaceView) return
        currentTexture = textureView
        currentSurface = null
        instance.setVideoTextureView(textureView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()
    private val onPlaybackDegradedListeners = mutableListOf<(String) -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onFirstFrame(listener: () -> Unit) {
        onFirstFrameListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    fun onPlaybackDegraded(listener: (String) -> Unit) {
        onPlaybackDegradedListeners.add(listener)
    }

    private fun reportPlaybackDegraded(reason: String) {
        if (degradedReported || !hasRenderedFirstFrame) return
        degradedReported = true
        bufferingHealthJob?.cancel()
        onPlaybackDegradedListeners.forEach { it(reason) }
    }


    private fun preferredPlayerType(
        route: ChannelRoute,
        configuredType: Configs.VideoPlayerType = Configs.videoPlayerType,
    ): Configs.VideoPlayerType =
        if (
            route.quality == ChannelQuality.UHD_4K ||
            Media3VideoPlayer.requiresTvbHlsSession(route)
        ) {
            Configs.VideoPlayerType.MEDIA3
        } else {
            configuredType
        }

    private fun createPlayer(type: Configs.VideoPlayerType): VideoPlayer = when (type) {
        Configs.VideoPlayerType.IJK -> IJKVideoPlayer(context, coroutineScope)
        Configs.VideoPlayerType.MEDIA3 -> Media3VideoPlayer(context, coroutineScope)
    }

    private fun switchPlayerIfNeeded(type: Configs.VideoPlayerType) {
        if (activePlayerType == type) return

        instance.release()
        currentSurface = null
        currentTexture = null
        videoOutputGeneration += 1
        instance = createPlayer(type)
        activePlayerType = type
        instance.setPlaybackAllowed(isAppForeground)
        if (initialized) configureInstance()
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
                activePlayerType == Configs.VideoPlayerType.MEDIA3 &&
                fourKFallbackRouteUrl != route.url &&
                ex.errorCodeName.contains("DECODER", ignoreCase = true)
            ) {
                fourKFallbackRouteUrl = route.url
                hasRenderedFirstFrame = false
                isBuffering = true
                coroutineScope.launch {
                    if (
                        currentRoute?.url == route.url &&
                        activePlayerType == Configs.VideoPlayerType.MEDIA3
                    ) {
                        switchPlayerIfNeeded(Configs.VideoPlayerType.IJK)
                        instance.prepare(route)
                    }
                }
                return@playerError
            }

            hasRenderedFirstFrame = false
            error = ex?.let { "${it.errorCodeName}(${it.errorCode})" }
                ?.apply { onErrorListeners.forEach { it.invoke() } }

        }
        instance.onReady {
            onReadyListeners.forEach { it.invoke() }
            error = null
            displayMode = defaultDisplayModeProvider()
        }
        instance.onBuffering {
            isBuffering = it
            if (it) error = null
            bufferingHealthJob?.cancel()
            if (it && hasRenderedFirstFrame && !degradedReported) {
                val now = System.currentTimeMillis()
                recentRebuffers.removeAll { sample -> now - sample > 25_000L }
                recentRebuffers += now
                if (recentRebuffers.size >= 2) {
                    reportPlaybackDegraded("repeated-rebuffer")
                } else {
                    val generation = playbackGeneration
                    bufferingHealthJob = coroutineScope.launch {
                        delay(3_200L)
                        if (generation == playbackGeneration && isBuffering) {
                            reportPlaybackDegraded("long-rebuffer")
                        }
                    }
                }
            }
        }
        instance.onPrepared { }
        instance.onFirstFrame {
            hasRenderedFirstFrame = true
            isBuffering = false
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
        instance.onPlaybackDegraded(::reportPlaybackDegraded)
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        settingsViewModel.videoPlayerTypeValue = Configs.videoPlayerType
        settingsViewModel.onVideoPlayerTypeChanged = { type ->
            currentRoute?.let { route ->
                val targetType = preferredPlayerType(route, type)
                if (targetType == activePlayerType) return@let

                val wasPlaying = isPlaying
                val position = currentPosition
                switchPlayerIfNeeded(targetType)
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

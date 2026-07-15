package org.aulama.iptv.mobile.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.Constants

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
}

@OptIn(UnstableApi::class)
class DirectPlayerState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .forceEnableMediaCodecAsynchronousQueueing()

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(6_000, 35_000, 1_000, 2_500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setLoadControl(loadControl)
        .build()
        .apply { playWhenReady = false }

    var status by mutableStateOf(PlaybackStatus.IDLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var onPlaybackFailure: (String) -> Unit = {}

    private var currentRouteUrl: String? = null
    private var appForeground = false
    private var failureReported = false
    private var bufferingTimeoutJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        status = PlaybackStatus.BUFFERING
                        startBufferingTimeout()
                    }

                    Player.STATE_READY -> {
                        bufferingTimeoutJob?.cancel()
                        status = if (player.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    }

                    Player.STATE_ENDED -> status = PlaybackStatus.PAUSED
                    Player.STATE_IDLE -> if (currentRouteUrl == null) status = PlaybackStatus.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !appForeground) {
                    player.pause()
                    return
                }
                if (player.playbackState == Player.STATE_READY) {
                    status = if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                }
            }

            override fun onRenderedFirstFrame() {
                bufferingTimeoutJob?.cancel()
                failureReported = false
                status = PlaybackStatus.PLAYING
                errorMessage = null
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                status = PlaybackStatus.ERROR
                errorMessage = "目前線路未能播放"
                reportFailure("player-${error.errorCode}")
            }
        })
    }

    fun prepare(route: ChannelRoute) {
        if (currentRouteUrl == route.url) return

        currentRouteUrl = route.url
        failureReported = false
        errorMessage = null
        status = PlaybackStatus.BUFFERING
        bufferingTimeoutJob?.cancel()

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(createMediaSource(route))
        player.prepare()
        player.playWhenReady = appForeground
        if (appForeground) player.play()
        startBufferingTimeout()
    }

    fun setForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground && currentRouteUrl != null) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    fun stop() {
        currentRouteUrl = null
        bufferingTimeoutJob?.cancel()
        player.stop()
        player.clearMediaItems()
        status = PlaybackStatus.IDLE
    }

    fun release() {
        bufferingTimeoutJob?.cancel()
        player.playWhenReady = false
        player.stop()
        player.release()
    }

    private fun reportFailure(reason: String) {
        if (failureReported) return
        failureReported = true
        bufferingTimeoutJob?.cancel()
        onPlaybackFailure(reason)
    }

    private fun startBufferingTimeout() {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = scope.launch {
            delay(BUFFERING_TIMEOUT_MS)
            if (status == PlaybackStatus.BUFFERING) {
                errorMessage = "載入時間較長，正在切換後備線路"
                reportFailure("buffering-timeout")
            }
        }
    }

    private fun createMediaSource(route: ChannelRoute): MediaSource {
        val uri = Uri.parse(if (route.url.endsWith("?")) "${route.url}t" else route.url)
        val mediaItem = MediaItem.fromUri(uri)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(route.userAgent ?: Constants.VIDEO_PLAYER_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setKeepPostFor302Redirects(true)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (route.requestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(route.requestHeaders)
                }
            }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        if (route.url.startsWith("rtsp://", true) || route.url.startsWith("rtp://", true)) {
            return RtspMediaSource.Factory().createMediaSource(mediaItem)
        }

        return when (Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            C.CONTENT_TYPE_RTSP -> RtspMediaSource.Factory().createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    private companion object {
        const val BUFFERING_TIMEOUT_MS = 15_000L
    }
}

@Composable
fun rememberDirectPlayerState(
    onPlaybackFailure: (String) -> Unit,
): DirectPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentFailureHandler by rememberUpdatedState(onPlaybackFailure)
    val state = remember { DirectPlayerState(context.applicationContext, scope) }

    SideEffect {
        state.onPlaybackFailure = { currentFailureHandler(it) }
    }

    DisposableEffect(lifecycleOwner) {
        state.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.setForeground(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> state.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { state.release() }
    }

    return state
}

@OptIn(UnstableApi::class)
@Composable
fun DirectVideoPlayer(
    state: DirectPlayerState,
    route: ChannelRoute?,
    channelName: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(route?.url) {
        if (route == null) state.stop() else state.prepare(route)
    }

    Box(
        modifier = modifier
            .clip(if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        if (route != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = state.player
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        controllerShowTimeoutMs = 3_500
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        contentDescription = channelName
                    }
                },
                update = {
                    it.player = state.player
                    it.keepScreenOn = state.status == PlaybackStatus.PLAYING ||
                        state.status == PlaybackStatus.BUFFERING
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.LiveTv,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxSize(0.18f),
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
        ) {
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (fullscreen) "離開全螢幕" else "全螢幕",
                )
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

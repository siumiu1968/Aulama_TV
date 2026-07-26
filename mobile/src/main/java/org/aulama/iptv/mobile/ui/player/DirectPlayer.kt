package org.aulama.iptv.mobile.ui.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
import org.aulama.iptv.mobile.data.playback.PlaybackCandidate
import org.aulama.iptv.mobile.data.playback.PlaybackDegradationPolicy
import org.aulama.iptv.mobile.data.playback.PlaybackHealthSample
import org.aulama.iptv.mobile.data.playback.PlaybackHealthDecision
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    private val playbackHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val safeRequest = if (
                request.header("Authorization") != null &&
                !request.url.host.equals("aulama.org", ignoreCase = true)
            ) {
                request.newBuilder().removeHeader("Authorization").build()
            } else {
                request
            }
            chain.proceed(safeRequest)
        }
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
    var onHealthSample: (PlaybackHealthSample) -> Unit = {}

    private var currentCandidate: PlaybackCandidate? = null
    private var appForeground = false
    private var failureReported = false
    private var healthMonitorJob: Job? = null
    private var sessionStartedAtMs = 0L
    private var firstFrameAtMs = 0L
    private var bufferingStartedAtMs = 0L
    private var bufferedMs = 0L
    private val stallTimestampsMs = ArrayDeque<Long>()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        status = PlaybackStatus.BUFFERING
                        if (bufferingStartedAtMs == 0L) {
                            bufferingStartedAtMs = SystemClock.elapsedRealtime()
                            if (firstFrameAtMs > 0L) {
                                stallTimestampsMs.addLast(bufferingStartedAtMs)
                                pruneStalls(bufferingStartedAtMs)
                                evaluateDegradation()
                            }
                        }
                    }

                    Player.STATE_READY -> {
                        finishBufferingPeriod()
                        status = if (player.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    }

                    Player.STATE_ENDED -> status = PlaybackStatus.PAUSED
                    Player.STATE_IDLE -> if (currentCandidate == null) status = PlaybackStatus.IDLE
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
                if (firstFrameAtMs == 0L) firstFrameAtMs = SystemClock.elapsedRealtime()
                if (!failureReported) {
                    status = PlaybackStatus.PLAYING
                    errorMessage = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                status = PlaybackStatus.ERROR
                errorMessage = "播放受阻，正在自動重試或切換"
                reportFailure("player-${error.errorCode}", fatal = true)
            }
        })
    }

    fun prepare(candidate: PlaybackCandidate) {
        if (currentCandidate?.route?.url == candidate.route.url) return

        finishSession(fatalError = false, degraded = false, manualChange = true)
        currentCandidate = candidate
        sessionStartedAtMs = SystemClock.elapsedRealtime()
        firstFrameAtMs = 0L
        bufferingStartedAtMs = 0L
        bufferedMs = 0L
        stallTimestampsMs.clear()
        failureReported = false
        errorMessage = null
        status = PlaybackStatus.BUFFERING
        healthMonitorJob?.cancel()

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(createMediaSource(candidate))
        player.prepare()
        player.playWhenReady = appForeground
        if (appForeground) player.play()
        startHealthMonitor()
    }

    fun setForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground && currentCandidate != null) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    fun stop() {
        finishSession(fatalError = false, degraded = false, manualChange = false)
        currentCandidate = null
        healthMonitorJob?.cancel()
        player.stop()
        player.clearMediaItems()
        status = PlaybackStatus.IDLE
    }

    fun release() {
        finishSession(fatalError = false, degraded = false, manualChange = false)
        healthMonitorJob?.cancel()
        player.playWhenReady = false
        player.stop()
        player.release()
    }

    private fun reportFailure(reason: String, fatal: Boolean = false) {
        if (failureReported) return
        failureReported = true
        healthMonitorJob?.cancel()
        errorMessage = "線路不穩，正在自動重試或切換"
        finishSession(fatalError = fatal, degraded = !fatal, manualChange = false)
        scope.launch {
            delay(AUTO_SWITCH_NOTICE_MS)
            onPlaybackFailure(reason)
        }
    }

    private fun startHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = scope.launch {
            while (currentCandidate != null && !failureReported) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                evaluateDegradation()
            }
        }
    }

    private fun evaluateDegradation() {
        if (failureReported || sessionStartedAtMs <= 0L) return
        val now = SystemClock.elapsedRealtime()
        pruneStalls(now)
        val elapsed = (now - sessionStartedAtMs).coerceAtLeast(1L)
        val currentBuffered = bufferedMs + if (bufferingStartedAtMs > 0L) {
            (now - bufferingStartedAtMs).coerceAtLeast(0L)
        } else 0L
        when (
            val decision = PlaybackDegradationPolicy.evaluate(
                elapsedMs = elapsed,
                firstFrameRendered = firstFrameAtMs > 0L,
                stallsInLast45Seconds = stallTimestampsMs.size,
                bufferingRatio = currentBuffered.toDouble() / elapsed,
                fatalError = false,
            )
        ) {
            is PlaybackHealthDecision.Degraded -> reportFailure(
                reason = "degraded-${decision.reason.name.lowercase()}",
                fatal = false,
            )
            PlaybackHealthDecision.Healthy,
            PlaybackHealthDecision.Starting -> Unit
        }
    }

    private fun pruneStalls(nowMs: Long) {
        while (
            stallTimestampsMs.firstOrNull()?.let {
                nowMs - it > PlaybackDegradationPolicy.STALL_WINDOW_MS
            } == true
        ) {
            stallTimestampsMs.removeFirst()
        }
    }

    private fun finishBufferingPeriod(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (bufferingStartedAtMs > 0L) {
            bufferedMs += (nowMs - bufferingStartedAtMs).coerceAtLeast(0L)
            bufferingStartedAtMs = 0L
        }
    }

    private fun finishSession(
        fatalError: Boolean,
        degraded: Boolean,
        manualChange: Boolean,
    ) {
        val candidate = currentCandidate ?: return
        if (sessionStartedAtMs <= 0L) return
        val nowElapsed = SystemClock.elapsedRealtime()
        finishBufferingPeriod(nowElapsed)
        val durationMs = (nowElapsed - sessionStartedAtMs).coerceAtLeast(1L)
        val startupMs = if (firstFrameAtMs > 0L) {
            (firstFrameAtMs - sessionStartedAtMs).coerceAtLeast(0L)
        } else {
            durationMs
        }
        val stableWatchMs = if (firstFrameAtMs > 0L) {
            (nowElapsed - firstFrameAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        onHealthSample(
            PlaybackHealthSample(
                candidateKey = candidate.key,
                startupMs = startupMs,
                bufferingRatio = bufferedMs.toDouble() / durationMs,
                fatalError = fatalError,
                degraded = degraded,
                stableWatchMs = stableWatchMs,
                manualEarlyExit = manualChange && stableWatchMs in 1 until MANUAL_EARLY_EXIT_MS,
                observedAtMs = System.currentTimeMillis(),
            )
        )
        sessionStartedAtMs = 0L
        firstFrameAtMs = 0L
        bufferingStartedAtMs = 0L
        bufferedMs = 0L
        stallTimestampsMs.clear()
    }

    private fun createMediaSource(candidate: PlaybackCandidate): MediaSource {
        val route = candidate.route
        val uri = Uri.parse(if (route.url.endsWith("?")) "${route.url}t" else route.url)
        val mediaItem = MediaItem.fromUri(uri)
        val httpFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent(route.userAgent ?: Constants.VIDEO_PLAYER_USER_AGENT)
            .apply {
                if (route.requestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(route.requestHeaders)
                }
                candidate.authorization?.let { token ->
                    setDefaultRequestProperties(route.requestHeaders + ("Authorization" to token))
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
        const val HEALTH_CHECK_INTERVAL_MS = 1_000L
        const val AUTO_SWITCH_NOTICE_MS = 450L
        const val MANUAL_EARLY_EXIT_MS = 30_000L
    }
}

@Composable
fun rememberDirectPlayerState(
    onPlaybackFailure: (String) -> Unit,
    onHealthSample: (PlaybackHealthSample) -> Unit,
): DirectPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentFailureHandler by rememberUpdatedState(onPlaybackFailure)
    val currentHealthHandler by rememberUpdatedState(onHealthSample)
    val state = remember { DirectPlayerState(context.applicationContext, scope) }

    SideEffect {
        state.onPlaybackFailure = { currentFailureHandler(it) }
        state.onHealthSample = { currentHealthHandler(it) }
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
    candidate: PlaybackCandidate?,
    channelName: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(candidate?.route?.url) {
        if (candidate == null) state.stop() else state.prepare(candidate)
    }

    Box(
        modifier = modifier
            .clip(if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        if (candidate != null) {
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

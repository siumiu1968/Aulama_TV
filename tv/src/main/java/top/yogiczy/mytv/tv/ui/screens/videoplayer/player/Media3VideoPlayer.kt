package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.media.MediaFormat
import android.os.Build
import android.net.Uri
import android.view.Display
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.rtsp.RtspMediaSource

import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.account.aulamaRequestHeaders
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class Media3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : VideoPlayer(coroutineScope) {
    private val log = Logger.create(javaClass.simpleName)

    private val toneMappingCodecAdapterFactory = object : MediaCodecAdapter.Factory {
        private val delegate = DefaultMediaCodecAdapterFactory(context).let { factory ->
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                factory.forceDisableAsynchronous()
            } else {
                factory
            }
        }

        override fun createAdapter(
            configuration: MediaCodecAdapter.Configuration,
        ): MediaCodecAdapter {
            val decoderSupportsFormat = runCatching {
                configuration.codecInfo.isFormatSupported(configuration.format)
            }.getOrDefault(true)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                configuration.surface != null &&
                decoderSupportsFormat &&
                !supportsHdrOutput(configuration.format)
            ) {
                configuration.mediaFormat.setInteger(
                    MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                )
                log.i("Requesting decoder HDR-to-SDR tone mapping for this display")
                return runCatching {
                    delegate.createAdapter(configuration)
                }.getOrElse {
                    configuration.mediaFormat.removeKey(MediaFormat.KEY_COLOR_TRANSFER_REQUEST)
                    log.w("Decoder rejected HDR-to-SDR tone mapping; retrying native output")
                    delegate.createAdapter(configuration)
                }
            }
            return delegate.createAdapter(configuration)
        }
    }

    private val mediaCookies = mutableListOf<Cookie>()
    private val mediaCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(mediaCookies) {
                val now = System.currentTimeMillis()
                mediaCookies.removeAll { existing ->
                    existing.expiresAt < now || cookies.any { incoming ->
                        existing.name == incoming.name &&
                            existing.domain == incoming.domain &&
                            existing.path == incoming.path
                    }
                }
                mediaCookies += cookies.filter { it.expiresAt >= now }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(mediaCookies) {
                val now = System.currentTimeMillis()
                mediaCookies.removeAll { it.expiresAt < now }
                mediaCookies.filter { it.matches(url) }
            }
    }

    private val mediaHttpClient by lazy {
        OkHttp.client.newBuilder()
            .cookieJar(mediaCookieJar)
            .connectTimeout(Configs.videoPlayerLoadTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(Configs.videoPlayerLoadTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(Configs.videoPlayerLoadTimeout, TimeUnit.MILLISECONDS)
            .build()
    }

    private val videoPlayer by lazy {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory =
                toneMappingCodecAdapterFactory
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (Configs.videoPlayerForceSoftDecode)
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 35_000, 1_200, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply { playWhenReady = false }
    }
    private fun dataSourceFactory(route: ChannelRoute) =
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(mediaHttpClient).apply {
                setUserAgent(Configs.videoPlayerUserAgent)
                val requestHeaders = route.aulamaRequestHeaders()
                if (requestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(requestHeaders)
                }
            },
        )

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private val resilientExtractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
        )
    private var updatePositionJob: Job? = null
    private var playbackHealthJob: Job? = null
    private var playbackHealthReported = false
    private val recentAudioUnderruns = ArrayDeque<Long>()
    private var recoverableErrorRetries = 0
    private var currentRoute: ChannelRoute? = null
    private var currentSurfaceView: SurfaceView? = null
    private var currentTextureView: TextureView? = null

    private fun supportsHdrOutput(format: Format): Boolean {
        val requiredHdrTypes = when {
            format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ->
                intArrayOf(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG ->
                intArrayOf(Display.HdrCapabilities.HDR_TYPE_HLG)
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 ->
                intArrayOf(
                    Display.HdrCapabilities.HDR_TYPE_HDR10,
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
                )
            else -> return true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return false
        val supportedTypes = display.hdrCapabilities.supportedHdrTypes
        return requiredHdrTypes.any(supportedTypes::contains)
    }


    private fun getMediaSource(
        uri: Uri,
        route: ChannelRoute,
        contentType: Int? = null,
    ): MediaSource? {
        val mediaItem = MediaItem.fromUri(uri)
        val dataSourceFactory = dataSourceFactory(route)

        if (uri.toString().startsWith("rtp://")) {
            return RtspMediaSource.Factory().createMediaSource(mediaItem)
        }

        return when (val type = contentType ?: Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().setDebugLoggingEnabled(true).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory, resilientExtractorsFactory)
                    .createMediaSource(mediaItem)
            }

            else -> {
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                    )
                )
                null
            }
        }
    }

    private fun prepareInternal(route: ChannelRoute, contentType: Int? = null) {
        playbackHealthJob?.cancel()
        playbackHealthReported = false
        recentAudioUnderruns.clear()
        currentRoute = route
        val uri = Uri.parse(route.url.let { if (it.endsWith("?")) "${it}t" else it })
        val mediaSource = getMediaSource(uri, route, contentType)

        if (mediaSource != null) {
            contentTypeAttempts[contentType ?: Util.inferContentType(uri)] = true
            videoPlayer.clearVideoSurface()
            clearVideoOutput()
            videoPlayer.stop()
            videoPlayer.clearMediaItems()
            videoPlayer.setMediaSource(mediaSource)
            currentSurfaceView?.let(videoPlayer::setVideoSurfaceView)
            currentTextureView?.let(videoPlayer::setVideoTextureView)
            videoPlayer.prepare()
            if (canStartPlayback) videoPlayer.play()
            triggerPrepared()
        }
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }


        override fun onPlayerError(ex: androidx.media3.common.PlaybackException) {
            log.e("onPlayerError", ex)

            when (ex.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    if (recoverableErrorRetries < MAX_RECOVERABLE_ERROR_RETRIES) {
                        recoverableErrorRetries += 1
                        videoPlayer.seekToDefaultPosition()
                        videoPlayer.prepare()
                    } else {
                        triggerError(PlaybackException(ex.errorCodeName, ex.errorCode))
                    }
                }

                // 當解析容器不支持時，嘗試使用其他解析容器
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    videoPlayer.currentMediaItem?.localConfiguration?.uri?.let {
                        if (contentTypeAttempts[C.CONTENT_TYPE_HLS] != true) {
                            currentRoute?.let { route -> prepareInternal(route, C.CONTENT_TYPE_HLS) }
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_RTSP] != true) {
                            currentRoute?.let { route -> prepareInternal(route, C.CONTENT_TYPE_RTSP) }
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                            currentRoute?.let { route -> prepareInternal(route, C.CONTENT_TYPE_OTHER) }
                        } else {
                            val type = Util.inferContentType(it)
                            triggerError(
                                PlaybackException.UNSUPPORTED_TYPE.copy(
                                    errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                                )
                            )
                        }
                    }
                }

                else -> {
                    triggerError(PlaybackException(ex.errorCodeName, ex.errorCode))
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerError(null)
                triggerBuffering(true)
            } else if (playbackState == Player.STATE_READY) {
                triggerReady()

                updatePositionJob?.cancel()
                updatePositionJob = coroutineScope.launch {

                    while (true) {
                        val livePosition =
                            System.currentTimeMillis() - videoPlayer.currentLiveOffset

                        triggerCurrentPosition(if (livePosition > 0) livePosition else videoPlayer.currentPosition)
                        delay(1000)
                    }
                }

                triggerDuration(videoPlayer.duration)
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            triggerIsPlayingChanged(isPlaying)
        }

        override fun onRenderedFirstFrame() {
            triggerFirstFrame()
            startPlaybackHealthMonitor()
        }
    }

    private fun startPlaybackHealthMonitor() {
        playbackHealthJob?.cancel()
        playbackHealthJob = coroutineScope.launch {
            delay(5_000L)
            val counters = videoPlayer.videoDecoderCounters ?: return@launch
            counters.ensureUpdated()
            var previousRendered = counters.renderedOutputBufferCount
            var previousDropped = counters.droppedBufferCount
            var previousPosition = videoPlayer.currentPosition
            var badSamples = 0

            while (!playbackHealthReported) {
                delay(3_000L)
                if (!videoPlayer.isPlaying || videoPlayer.playbackState != Player.STATE_READY) {
                    badSamples = 0
                    continue
                }
                counters.ensureUpdated()
                val renderedDelta = (counters.renderedOutputBufferCount - previousRendered).coerceAtLeast(0)
                val droppedDelta = (counters.droppedBufferCount - previousDropped).coerceAtLeast(0)
                val frameDelta = renderedDelta + droppedDelta
                val droppedRatio = if (frameDelta > 0) droppedDelta.toFloat() / frameDelta else 0f
                val progressDelta = (videoPlayer.currentPosition - previousPosition).coerceAtLeast(0L)
                val unhealthy = (frameDelta >= 20 && droppedRatio >= 0.18f) ||
                    (renderedDelta < 8 && progressDelta < 1_500L)

                badSamples = if (unhealthy) badSamples + 1 else 0
                previousRendered = counters.renderedOutputBufferCount
                previousDropped = counters.droppedBufferCount
                previousPosition = videoPlayer.currentPosition

                if (badSamples >= 2) {
                    playbackHealthReported = true
                    triggerPlaybackDegraded(
                        if (droppedRatio >= 0.18f) "dropped-frames" else "slow-rendering"
                    )
                }
            }
        }
    }

    private val metadataListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 幀率、比特率目前是從tag中獲取，有的返回空，後續需要實時計算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            if (
                playbackHealthReported ||
                !videoPlayer.isPlaying ||
                videoPlayer.playbackState != Player.STATE_READY
            ) return
            val now = System.currentTimeMillis()
            recentAudioUnderruns.removeAll { now - it > AUDIO_UNDERRUN_WINDOW_MS }
            recentAudioUnderruns += now
            log.w(
                "Audio underrun: bufferSize=$bufferSize, bufferSizeMs=$bufferSizeMs, " +
                    "elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs, " +
                    "samples=${recentAudioUnderruns.size}",
            )
            if (recentAudioUnderruns.size >= AUDIO_UNDERRUN_LIMIT) {
                playbackHealthReported = true
                triggerPlaybackDegraded("audio-underrun")
            }
        }
    }

    private val eventLogger = EventLogger()

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
    }

    override fun release() {
        playbackHealthJob?.cancel()
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.release()
        super.release()
    }

    override fun prepare(route: ChannelRoute) {
        contentTypeAttempts.clear()
        recoverableErrorRetries = 0
        prepareInternal(route)
    }

    override fun play() {
        if (canStartPlayback) videoPlayer.play()
    }

    override fun pause() {
        videoPlayer.pause()
    }

    override fun seekTo(position: Long) {
        videoPlayer.seekTo(position)
    }

    override fun stop() {
        playbackHealthJob?.cancel()
        recoverableErrorRetries = 0
        videoPlayer.stop()
        updatePositionJob?.cancel()
        super.stop()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        currentSurfaceView = surfaceView
        currentTextureView = null
        videoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        currentTextureView = textureView
        currentSurfaceView = null
        videoPlayer.setVideoTextureView(textureView)
    }

    private fun clearVideoOutput() {
        currentSurfaceView?.holder?.surface?.takeIf { it.isValid }?.let { surface ->
            val canvas = runCatching { surface.lockCanvas(null) }.getOrNull() ?: return@let
            try {
                canvas.drawColor(AndroidColor.BLACK, PorterDuff.Mode.SRC)
            } finally {
                runCatching { surface.unlockCanvasAndPost(canvas) }
            }
        }

        currentTextureView?.takeIf(TextureView::isAvailable)?.let { textureView ->
            val canvas = runCatching { textureView.lockCanvas() }.getOrNull() ?: return@let
            try {
                canvas.drawColor(AndroidColor.BLACK, PorterDuff.Mode.SRC)
            } finally {
                runCatching { textureView.unlockCanvasAndPost(canvas) }
            }
        }
    }

    companion object {
        private const val MAX_RECOVERABLE_ERROR_RETRIES = 1
        private const val AUDIO_UNDERRUN_LIMIT = 3
        private const val AUDIO_UNDERRUN_WINDOW_MS = 15_000L
        private const val TVB_AKAMAI_HOST = "prd-vcache.edge-global.akamai.tvb.com"

        internal fun requiresTvbHlsSession(route: ChannelRoute): Boolean =
            Uri.parse(route.url).host.equals(TVB_AKAMAI_HOST, ignoreCase = true)
    }
}

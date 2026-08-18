package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.account.aulamaRequestHeaders

private const val PLAYBACK_HEALTH_MIN_PROGRESS_MS = 1_500L
private const val PLAYBACK_HEALTH_BAD_SAMPLE_LIMIT = 3
private const val PLAYBACK_HEALTH_MAX_AV_DRIFT_SECONDS = 0.75f

internal enum class IJKPlaybackHealthIssue(val reasonCode: String) {
    STALLED("ijk-playback-stalled"),
    SLOW_RENDERING("ijk-slow-rendering"),
    DECODE_STALLED("ijk-decode-stalled"),
    AV_SYNC_DRIFT("ijk-av-sync-drift"),
}

internal fun playbackHealthIssue(
    outputFps: Float,
    decodeFps: Float,
    progressDelta: Long,
    minimumFps: Float,
    hasObservedOutputFps: Boolean,
    hasObservedDecodeFps: Boolean,
    avDifferenceSeconds: Float = Float.NaN,
    playbackExpectedButStopped: Boolean = false,
): IJKPlaybackHealthIssue? {
    if (playbackExpectedButStopped) return IJKPlaybackHealthIssue.STALLED

    val outputFpsAvailableNow = outputFps.isFinite() && outputFps > 0f
    val decodeFpsAvailableNow = decodeFps.isFinite() && decodeFps > 0f
    val hasHealthyFpsEvidence =
        (outputFpsAvailableNow && outputFps >= minimumFps) ||
            (decodeFpsAvailableNow && decodeFps >= minimumFps)
    val positionStalled = progressDelta < PLAYBACK_HEALTH_MIN_PROGRESS_MS
    if (
        positionStalled &&
        !hasHealthyFpsEvidence &&
        !outputFpsAvailableNow &&
        !decodeFpsAvailableNow
    ) {
        return IJKPlaybackHealthIssue.STALLED
    }

    val outputStopped = hasObservedOutputFps && outputFps.isFinite() && outputFps <= 0f
    if (outputStopped) return IJKPlaybackHealthIssue.STALLED

    val outputTooLow = when {
        outputFpsAvailableNow -> outputFps < minimumFps
        else -> false
    }
    if (outputTooLow) return IJKPlaybackHealthIssue.SLOW_RENDERING

    val decodeTooLow = when {
        decodeFpsAvailableNow -> decodeFps < minimumFps
        hasObservedDecodeFps && decodeFps.isFinite() -> decodeFps <= 0f
        else -> false
    }
    if (decodeTooLow) return IJKPlaybackHealthIssue.DECODE_STALLED

    val avSyncDrifted = avDifferenceSeconds.isFinite() &&
        kotlin.math.abs(avDifferenceSeconds) >= PLAYBACK_HEALTH_MAX_AV_DRIFT_SECONDS
    return IJKPlaybackHealthIssue.AV_SYNC_DRIFT.takeIf { avSyncDrifted }
}

internal fun isPlaybackHealthUnhealthy(
    outputFps: Float,
    decodeFps: Float,
    progressDelta: Long,
    minimumFps: Float,
    hasObservedOutputFps: Boolean,
    hasObservedDecodeFps: Boolean,
    avDifferenceSeconds: Float = Float.NaN,
    playbackExpectedButStopped: Boolean = false,
): Boolean {
    return playbackHealthIssue(
        outputFps = outputFps,
        decodeFps = decodeFps,
        progressDelta = progressDelta,
        minimumFps = minimumFps,
        hasObservedOutputFps = hasObservedOutputFps,
        hasObservedDecodeFps = hasObservedDecodeFps,
        avDifferenceSeconds = avDifferenceSeconds,
        playbackExpectedButStopped = playbackExpectedButStopped,
    ) != null
}

class IJKVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val forceSoftwareDecode: Boolean = false,
) : VideoPlayer(coroutineScope) {
    private val log = Logger.create(javaClass.simpleName)

    private var currentRoute: ChannelRoute? = null
    private var retryCount = 0
    private val maxRetryCount = 1

    private val ijkPlayer by lazy {
        IjkMediaPlayer()
    }

    private val useSoftwareDecode: Boolean
        get() = forceSoftwareDecode || (
            Configs.videoPlayerForceSoftDecode &&
                currentRoute?.quality != ChannelQuality.UHD_4K
            )

    private fun setOption() {
        ijkPlayer.apply {
            val softwareDecode = useSoftwareDecode
            val isHls = currentRoute?.url?.let(::isLikelyHlsStreamUrl) == true
            val timeoutUs = Configs.videoPlayerLoadTimeout * 1_000L
            val analyzeDurationUs = if (softwareDecode) 6_000_000L else 2_000_000L
            val probeSize = if (softwareDecode) 4 * 1024 * 1024L else 1024 * 1024L
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1)
            if (!isHls) {
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "seekable", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1)
            }
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", timeoutUs)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", timeoutUs)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", analyzeDurationUs)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", analyzeDurationUs)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", probeSize)
            setOption(
                IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "fflags",
                if (softwareDecode) "genpts+discardcorrupt" else "fastseek",
            )
            if (softwareDecode) {
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "use_wallclock_as_timestamps", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "correct_ts_overflow", 1)
            }
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
            if (softwareDecode)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
            else{
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
            }
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "crypto,file,http,https,tcp,tls,udp,rtmp,rtsp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5)
            // IJK defaults to 31fps and discards non-reference frames above it.
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 60)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            // 頻道全屬直播；精準 seek 及等候 A/V 同步會被異常 PTS 誘發，播一兩秒後黑畫面。
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-av-start", 0)

            // rtsp設置 https://ffmpeg.org/ffmpeg-protocols.html#rtsp
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp")
            // 直播串流要持續消耗封包，否則播放器會累積延遲，甚至出畫後再次黑屏。
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 256 * 1024L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)

            //https://www.cnblogs.com/Fitz/p/18537127
            // setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter",0) //丟棄一些“無用”的數據包，例如AVI格式中的零大小數據包
            // setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 0) //不跳幀，解碼所有幀
        }
    }
    
    private var updatePositionJob: Job? = null
    private var playbackHealthJob: Job? = null
    private var retryJob: Job? = null
    private var playbackAttemptGeneration = 0L
    private var playbackHealthReported = false
    private var desiredFrameRate = 0f
    private var currentSurface: Surface? = null
    private var ownsCurrentSurface = false
    private var canRequestFrameRate = false
    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var surfaceHolderCallback: SurfaceHolder.Callback? = null
    private var surfaceTextureListener: TextureView.SurfaceTextureListener? = null

    private fun updateFrameRateHint() {
        val stream = runCatching { ijkPlayer.mediaInfo?.mMeta?.mVideoStream }.getOrNull()
        val frameRate = when {
            stream != null && stream.mFpsNum > 0 && stream.mFpsDen > 0 ->
                stream.mFpsNum.toFloat() / stream.mFpsDen
            ijkPlayer.videoDecodeFramesPerSecond.isFinite() ->
                ijkPlayer.videoDecodeFramesPerSecond
            else -> 0f
        }

        if (frameRate in 1f..121f) {
            desiredFrameRate = frameRate
            applyFrameRateHint(currentSurface, frameRate)
        }
    }

    private fun applyFrameRateHint(surface: Surface?, frameRate: Float = desiredFrameRate) {
        if (
            !canRequestFrameRate ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            surface?.isValid != true
        ) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                surface.setFrameRate(
                    frameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS,
                )
            } else {
                surface.setFrameRate(
                    frameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                )
            }
        }.onFailure { log.w("Unable to apply ${frameRate}fps surface hint: ${it.message}") }
    }

    private fun startPlaybackHealthMonitor() {
        playbackHealthJob?.cancel()
        playbackHealthReported = false
        playbackHealthJob = coroutineScope.launch {
            delay(5_000L)
            var previousPosition = ijkPlayer.currentPosition
            var badSamples = 0
            var previousIssue: IJKPlaybackHealthIssue? = null
            var hasObservedOutputFps = false
            var hasObservedDecodeFps = false
            while (!playbackHealthReported) {
                delay(3_000L)
                if (!canStartPlayback) {
                    badSamples = 0
                    previousIssue = null
                    previousPosition = ijkPlayer.currentPosition
                    continue
                }
                val isPlaying = ijkPlayer.isPlaying
                val outputFps = ijkPlayer.videoOutputFramesPerSecond
                val decodeFps = ijkPlayer.videoDecodeFramesPerSecond
                val avDifference = ijkPlayer.avDifference
                val position = ijkPlayer.currentPosition
                val progressDelta = (position - previousPosition).coerceAtLeast(0L)
                val minimumFps = if (currentRoute?.quality == ChannelQuality.UHD_4K) 16f else 8f
                val issue = playbackHealthIssue(
                    outputFps = outputFps,
                    decodeFps = decodeFps,
                    progressDelta = progressDelta,
                    minimumFps = minimumFps,
                    hasObservedOutputFps = hasObservedOutputFps,
                    hasObservedDecodeFps = hasObservedDecodeFps,
                    avDifferenceSeconds = avDifference,
                    playbackExpectedButStopped = !isPlaying,
                )
                hasObservedOutputFps = hasObservedOutputFps ||
                    (outputFps.isFinite() && outputFps > 0f)
                hasObservedDecodeFps = hasObservedDecodeFps ||
                    (decodeFps.isFinite() && decodeFps > 0f)
                badSamples = if (issue != null && issue == previousIssue) badSamples + 1 else {
                    if (issue == null) 0 else 1
                }
                previousIssue = issue
                if (issue != null) {
                    log.w(
                        "Playback health low (${issue.reasonCode}): outputFps=$outputFps, " +
                        "decodeFps=$decodeFps, progressDelta=$progressDelta, " +
                            "avDifference=$avDifference, " +
                            "isPlaying=$isPlaying, " +
                            "outputTelemetry=$hasObservedOutputFps, " +
                            "decodeTelemetry=$hasObservedDecodeFps, samples=$badSamples",
                    )
                }
                previousPosition = position
                if (badSamples >= PLAYBACK_HEALTH_BAD_SAMPLE_LIMIT) {
                    playbackHealthReported = true
                    triggerPlaybackDegraded(issue?.reasonCode ?: "ijk-playback-stalled")
                }
            }
        }
    }

    override fun restartPlaybackHealthMonitoring() {
        startPlaybackHealthMonitor()
    }
    
    private val playerListener = object : IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener,
        IMediaPlayer.OnCompletionListener {
        
        override fun onPrepared(mp: IMediaPlayer?) {
            val mediaInfo = runCatching { ijkPlayer.mediaInfo }.getOrNull()
            val audioStream = mediaInfo?.mMeta?.mAudioStream
            if (audioStream != null && mediaInfo.mAudioDecoder.isNullOrBlank()) {
                val codec = audioStream.mCodecName ?: "unknown"
                log.w("IJK audio decoder unavailable for $codec; trying compatible player")
                triggerError(PlaybackException("AUDIO_DECODER_UNAVAILABLE", 10004))
                return
            }
            updateFrameRateHint()
            triggerReady()
            if (canStartPlayback) play()
            
            updatePositionJob?.cancel()
            updatePositionJob = coroutineScope.launch {
                while (true) {
                    triggerCurrentPosition(
                        position = ijkPlayer.currentPosition,
                        monitorTimelineStall = false,
                    )
                    triggerDuration(ijkPlayer.duration)
                    delay(1000)
                }
            }
        }
        
        override fun onInfo(mp: IMediaPlayer?, what: Int, extra: Int): Boolean {
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> triggerBuffering(true)
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> triggerBuffering(false)
                IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    retryCount = 0
                    updateFrameRateHint()
                    triggerFirstFrame()
                    triggerIsPlayingChanged(true)
                    startPlaybackHealthMonitor()
                }
            }
            return true
        }
        
        override fun onError(mp: IMediaPlayer?, what: Int, extra: Int): Boolean {
            log.e("onError what=$what extra=$extra")
            // -110 ETIMEDOUT  -138 ENOSYS  均做二次重試
            if ((what == -110 || what == -138) && retryCount < maxRetryCount) {
                retryCount++
                scheduleRetry(750L)
                return true   // 自己消化掉，不拋到 UI 層
            }
            triggerError(PlaybackException("IJKPlayerError", what))
            return true
        }
        
        override fun onVideoSizeChanged(
            mp: IMediaPlayer?,
            width: Int,
            height: Int,
            sarNum: Int,
            sarDen: Int
        ) {
            triggerResolution(width, height)
        }
        
        override fun onCompletion(mp: IMediaPlayer?) {
            triggerIsPlayingChanged(false)
        }
    }

    override fun initialize() {
        super.initialize()
        ijkPlayer.apply {
            setOnPreparedListener(playerListener)
            setOnInfoListener(playerListener)
            setOnErrorListener(playerListener)
            setOnVideoSizeChangedListener(playerListener)
            setOnCompletionListener(playerListener)
        }
    }

    override fun release() {
        playbackAttemptGeneration += 1
        retryJob?.cancel()
        retryJob = null
        updatePositionJob?.cancel()
        playbackHealthJob?.cancel()
        unbindVideoOutput()
        ijkPlayer.reset()
        ijkPlayer.release()
        super.release()
    }

    override fun prepare(route: ChannelRoute) {
        prepareRoute(route, resetRetryBudget = true)
    }

    private fun prepareRoute(route: ChannelRoute, resetRetryBudget: Boolean) {
        playbackAttemptGeneration += 1
        retryJob?.cancel()
        retryJob = null
        playbackHealthJob?.cancel()
        playbackHealthReported = false
        applyFrameRateHint(currentSurface, 0f)
        desiredFrameRate = 0f
        currentRoute = route
        if (resetRetryBudget) retryCount = 0
        try {
            ijkPlayer.reset()
            setOption()
            currentSurface?.takeIf(Surface::isValid)?.let(ijkPlayer::setSurface)
            ijkPlayer.setDataSource(context, Uri.parse(route.url), route.aulamaRequestHeaders())
            ijkPlayer.prepareAsync()
            triggerPrepared()
        } catch (e: Exception) {
            handleError(e)
        }
    }
    // 添加錯誤處理方法
    private fun handleError(e: Exception) {
        log.e("playback error", e)
        if (retryCount < maxRetryCount) {
            retryCount++
            scheduleRetry(1_000L * retryCount)
        } else {
            triggerError(PlaybackException("PlaybackError", -1))
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        val expectedGeneration = playbackAttemptGeneration
        retryJob?.cancel()
        retryJob = coroutineScope.launch {
            delay(delayMs)
            if (expectedGeneration != playbackAttemptGeneration) return@launch
            retryJob = null
            currentRoute?.let { prepareRoute(it, resetRetryBudget = false) }
        }
    }

    override fun play() {
        if (canStartPlayback && !ijkPlayer.isPlaying) {
            ijkPlayer.start()
            triggerIsPlayingChanged(true)
        }
    }

    override fun pause() {
        if (ijkPlayer.isPlaying) {
            ijkPlayer.pause()
            triggerIsPlayingChanged(false)
        }
    }

    override fun seekTo(position: Long) {
        // 部分 MPEG-TS 直播會將 PTS 誤報成 duration；對它 seek 會造成無限重試及黑畫面。
        log.w("Seek is ignored for live channel: position=$position")
    }

    override fun stop() {
        playbackAttemptGeneration += 1
        retryJob?.cancel()
        retryJob = null
        updatePositionJob?.cancel()
        playbackHealthJob?.cancel()
        ijkPlayer.stop()
        super.stop()
    }

    private fun bindSurface(surface: Surface?, ownsSurface: Boolean, supportsFrameRate: Boolean) {
        if (currentSurface === surface) return

        ijkPlayer.setSurface(null)
        if (ownsCurrentSurface) {
            currentSurface?.release()
        }

        currentSurface = surface
        ownsCurrentSurface = ownsSurface
        canRequestFrameRate = supportsFrameRate
        applyFrameRateHint(surface)
        ijkPlayer.setSurface(surface)
        if (surface != null && canStartPlayback) play()
    }

    private fun unbindVideoOutput() {
        surfaceHolderCallback?.let { callback ->
            boundSurfaceView?.holder?.removeCallback(callback)
        }
        if (boundTextureView?.surfaceTextureListener === surfaceTextureListener) {
            boundTextureView?.surfaceTextureListener = null
        }
        surfaceHolderCallback = null
        surfaceTextureListener = null
        boundSurfaceView = null
        boundTextureView = null
        bindSurface(null, ownsSurface = false, supportsFrameRate = false)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (boundSurfaceView === surfaceView) return
        unbindVideoOutput()
        boundSurfaceView = surfaceView

        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (boundSurfaceView === surfaceView) {
                    bindSurface(holder.surface, ownsSurface = false, supportsFrameRate = true)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (boundSurfaceView === surfaceView) {
                    pause()
                    bindSurface(null, ownsSurface = false, supportsFrameRate = false)
                }
            }
        }
        surfaceHolderCallback = callback
        surfaceView.holder.addCallback(callback)
        surfaceView.holder.surface
            ?.takeIf(Surface::isValid)
            ?.let { bindSurface(it, ownsSurface = false, supportsFrameRate = true) }
    }

    override fun setVideoTextureView(textureView: TextureView) {
        if (boundTextureView === textureView) return
        unbindVideoOutput()
        boundTextureView = textureView

        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (boundTextureView === textureView) {
                    bindSurface(
                        Surface(surface),
                        ownsSurface = true,
                        supportsFrameRate = false,
                    )
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                if (boundTextureView === textureView) {
                    pause()
                    bindSurface(null, ownsSurface = false, supportsFrameRate = false)
                }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        surfaceTextureListener = listener
        textureView.surfaceTextureListener = listener

        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let {
                bindSurface(Surface(it), ownsSurface = true, supportsFrameRate = false)
            }
        }
    }
}

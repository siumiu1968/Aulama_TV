package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.Color as AndroidColor
import android.graphics.PorterDuff
import android.net.Uri
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

class IJKVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : VideoPlayer(coroutineScope) {
    private val log = Logger.create(javaClass.simpleName)

    private var currentRoute: ChannelRoute? = null
    private var retryCount = 0
    private val maxRetryCount = 1

    private val ijkPlayer by lazy {
        IjkMediaPlayer().apply {
            //            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_INFO)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 2)
            setOption(
                IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "timeout",
                Configs.videoPlayerLoadTimeout
            )
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 100L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 10)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek")
        }
    }

    private fun setOption() {
        ijkPlayer.apply {
            val highBandwidth = currentRoute?.quality == ChannelQuality.UHD_4K
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
            if (Configs.videoPlayerForceSoftDecode)
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
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)

            // rtsp設置 https://ffmpeg.org/ffmpeg-protocols.html#rtsp
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 1316)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", if (highBandwidth) 0 else 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L)

            // 高碼率 4K 要保留短緩衝吸收網絡抖動；一般直播維持低延遲。
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", if (highBandwidth) 1 else 0)
            if (highBandwidth) {
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 8_000)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 120)
            }

            //https://www.cnblogs.com/Fitz/p/18537127
            // setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter",0) //丟棄一些“無用”的數據包，例如AVI格式中的零大小數據包
            // setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 0) //不跳幀，解碼所有幀
        }
    }
    
    private var updatePositionJob: Job? = null
    private var playbackHealthJob: Job? = null
    private var playbackHealthReported = false

    private fun startPlaybackHealthMonitor() {
        playbackHealthJob?.cancel()
        playbackHealthReported = false
        playbackHealthJob = coroutineScope.launch {
            delay(5_000L)
            var previousPosition = ijkPlayer.currentPosition
            var badSamples = 0
            while (!playbackHealthReported) {
                delay(3_000L)
                if (!ijkPlayer.isPlaying) {
                    badSamples = 0
                    continue
                }
                val outputFps = ijkPlayer.videoOutputFramesPerSecond
                val decodeFps = ijkPlayer.videoDecodeFramesPerSecond
                val position = ijkPlayer.currentPosition
                val progressDelta = (position - previousPosition).coerceAtLeast(0L)
                val minimumFps = if (currentRoute?.quality == ChannelQuality.UHD_4K) 16f else 8f
                val unhealthy = (outputFps.isFinite() && outputFps > 0f && outputFps < minimumFps) ||
                    (decodeFps.isFinite() && decodeFps > 0f && decodeFps < minimumFps && progressDelta < 1_500L)
                badSamples = if (unhealthy) badSamples + 1 else 0
                previousPosition = position
                if (badSamples >= 2) {
                    playbackHealthReported = true
                    triggerPlaybackDegraded("low-output-fps")
                }
            }
        }
    }
    
    private val playerListener = object : IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener,
        IMediaPlayer.OnCompletionListener {
        
        override fun onPrepared(mp: IMediaPlayer?) {
            triggerReady()
            
            updatePositionJob?.cancel()
            updatePositionJob = coroutineScope.launch {
                while (true) {
                    triggerCurrentPosition(ijkPlayer.currentPosition)
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
                coroutineScope.launch {
                    delay(750)
                    currentRoute?.let { prepare(it) }
                }
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
        updatePositionJob?.cancel()
        playbackHealthJob?.cancel()
        ijkPlayer.reset()
        ijkPlayer.release()
        super.release()
    }

    override fun prepare(route: ChannelRoute) {
        playbackHealthJob?.cancel()
        playbackHealthReported = false
        val isNewRoute = currentRoute?.url != route.url
        currentRoute = route
        if (isNewRoute) retryCount = 0
        try {
            clearCurrentSurface()
            ijkPlayer.reset()
            // 在設置數據源前確保Surface有效
            if (currentSurface != null) {
                ijkPlayer.setSurface(currentSurface)
            }
            ijkPlayer.setDataSource(context, Uri.parse(route.url), route.requestHeaders)
            setOption()
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
            coroutineScope.launch {
                delay(1000 * retryCount.toLong()) // 指數退避
                currentRoute?.let { prepare(it) }
            }
        } else {
            triggerError(PlaybackException("PlaybackError", -1))
        }
    }

    override fun play() {
        if (!ijkPlayer.isPlaying) {
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
//        ijkPlayer.seekTo(position)
        // 對於直播流（duration <= 0），seek操作不僅無效，還可能導致播放器狀態異常。
        // 增加保護，只對點播視頻執行seek。
        if (ijkPlayer.duration > 0) {
            log.d("Seeking to $position")
            ijkPlayer.seekTo(position)
        } else {
            log.w("Seek is ignored for live streams.")
        }
    }

    override fun stop() {
        updatePositionJob?.cancel()
        playbackHealthJob?.cancel()
        ijkPlayer.stop()
        super.stop()
    }

    // 添加成員變量保存當前Surface
    private var currentSurface: Surface? = null

    private fun clearCurrentSurface() {
        val surface = currentSurface?.takeIf(Surface::isValid) ?: return
        val canvas = runCatching { surface.lockCanvas(null) }.getOrNull() ?: return
        try {
            canvas.drawColor(AndroidColor.BLACK, PorterDuff.Mode.SRC)
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
        }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                currentSurface = holder.surface
                ijkPlayer.setDisplay(holder)
                // Resume playback when the surface is available again
                play()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Pause playback when the surface is destroyed
                pause()
                ijkPlayer.setSurface(null)
                currentSurface?.release()
                currentSurface = null
            }
        })
        ijkPlayer.setDisplay(surfaceView.holder)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                currentSurface = Surface(surface)
                ijkPlayer.setSurface(currentSurface)
                play()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                pause()
                ijkPlayer.setSurface(null)
                currentSurface?.release()
                currentSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        if (textureView.isAvailable) {
            val newSurface = Surface(textureView.surfaceTexture)
            currentSurface = newSurface
            ijkPlayer.setSurface(newSurface)
        }
    }
}

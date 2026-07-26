package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
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
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.account.aulamaRequestHeaders
import java.net.URI

private val LETV_LEGACY_HTTP_HDR_HOSTS = setOf(
    "o11.163189.xyz",
)

internal fun leTvLegacyHdrPlaybackUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host?.lowercase() !in LETV_LEGACY_HTTP_HDR_HOSTS
    ) {
        return url
    }

    // LeTV's Android 6 vendor player supports HDR10, but its bundled TLS stack
    // cannot negotiate with this endpoint. The same HLS origin serves HTTP.
    return url.replaceFirst("https://", "http://", ignoreCase = true)
}

/**
 * Uses LeTV's bundled Jungle/MStar player on legacy EUI firmware.
 *
 * This is the only player path on these Android 6 televisions that forwards
 * HDR10 metadata into the vendor PQ pipeline.
 */
class LeTvVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : VideoPlayer(coroutineScope) {
    private val log = Logger.create(javaClass.simpleName)

    private var mediaPlayer: MediaPlayer? = null
    private var currentRoute: ChannelRoute? = null
    private var updatePositionJob: Job? = null
    private var firstFrameReported = false
    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var surfaceHolderCallback: SurfaceHolder.Callback? = null
    private var surfaceTextureListener: TextureView.SurfaceTextureListener? = null
    private var textureSurface: Surface? = null

    private fun createVendorPlayer(): MediaPlayer {
        val classLoader = context.classLoader
        val playerClass = Class.forName(MEDIA_PLAYER_CLASS, true, classLoader)
        val playerTypeClass = Class.forName(PLAYER_TYPE_CLASS, true, classLoader)
        val extPlayer = playerTypeClass.getField("EXT_PLAYER").get(null)
        val constructor = playerClass.getConstructor(playerTypeClass, String::class.java)
        return constructor.newInstance(extPlayer, MSTAR_PLAYER_CLASS) as MediaPlayer
    }

    private fun ensurePlayer(): MediaPlayer {
        mediaPlayer?.let { return it }
        return createVendorPlayer().also { player ->
            mediaPlayer = player
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setScreenOnWhilePlaying(true)
            player.setOnPreparedListener {
                triggerResolution(it.videoWidth, it.videoHeight)
                metadata = metadata.copy(
                    videoMimeType = "video/hevc",
                    videoWidth = it.videoWidth,
                    videoHeight = it.videoHeight,
                    videoColor = "BT.2020 / PQ (LeTV HDR)",
                    videoFrameRate = 50f,
                    videoDecoder = "LeTV Jungle / OMX.MS.HEVC.Decoder",
                )
                triggerMetadata(metadata)
                triggerReady()
                startPositionUpdates()
                if (canStartPlayback) play()
            }
            player.setOnVideoSizeChangedListener { _, width, height ->
                triggerResolution(width, height)
            }
            player.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> triggerBuffering(true)
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> triggerBuffering(false)
                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        triggerBuffering(false)
                        reportFirstFrame()
                    }
                }
                true
            }
            player.setOnErrorListener { _, what, extra ->
                log.e("Vendor player error what=$what extra=$extra")
                triggerError(
                    PlaybackException(
                        errorCodeName = "LETV_VENDOR_PLAYER_ERROR_$extra",
                        errorCode = what,
                    ),
                )
                true
            }
            player.setOnCompletionListener {
                triggerIsPlayingChanged(false)
            }
            boundSurfaceView?.holder?.let(player::setDisplay)
            textureSurface?.let(player::setSurface)
        }
    }

    private fun reportFirstFrame() {
        if (firstFrameReported) return
        firstFrameReported = true
        triggerFirstFrame()
        triggerIsPlayingChanged(true)
    }

    private fun startPositionUpdates() {
        updatePositionJob?.cancel()
        updatePositionJob = coroutineScope.launch {
            while (true) {
                val player = mediaPlayer ?: break
                runCatching {
                    triggerCurrentPosition(player.currentPosition.toLong())
                    triggerDuration(player.duration.toLong())
                }
                delay(1_000L)
            }
        }
    }

    override fun initialize() {
        super.initialize()
    }

    override fun prepare(route: ChannelRoute) {
        currentRoute = route
        firstFrameReported = false
        updatePositionJob?.cancel()
        try {
            val player = ensurePlayer()
            player.reset()
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setScreenOnWhilePlaying(true)
            boundSurfaceView?.holder?.let(player::setDisplay)
            textureSurface?.let(player::setSurface)
            val headers = HashMap(route.aulamaRequestHeaders()).apply {
                putIfAbsent("User-Agent", Configs.videoPlayerUserAgent)
            }
            val playbackUrl = leTvLegacyHdrPlaybackUrl(route.url)
            player.setDataSource(context, Uri.parse(playbackUrl), headers)
            player.prepareAsync()
            triggerBuffering(true)
            triggerPrepared()
            log.i(
                "Preparing UHD route through LeTV Jungle HDR pipeline" +
                    if (playbackUrl != route.url) " with legacy HTTP compatibility" else "",
            )
        } catch (error: Exception) {
            log.e("Unable to start LeTV vendor player", error)
            triggerError(PlaybackException("LETV_VENDOR_PLAYER_UNAVAILABLE", -1))
        }
    }

    override fun play() {
        val player = mediaPlayer ?: return
        if (!canStartPlayback) return
        runCatching {
            if (!player.isPlaying) player.start()
            triggerIsPlayingChanged(true)
        }.onFailure {
            log.e("Unable to start LeTV vendor player", it)
            triggerError(PlaybackException("LETV_VENDOR_PLAYER_START_FAILED", -2))
        }
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) player.pause()
            triggerIsPlayingChanged(false)
        }
    }

    override fun seekTo(position: Long) {
        log.w("Seek is ignored for live channel: position=$position")
    }

    override fun stop() {
        updatePositionJob?.cancel()
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
        }
        firstFrameReported = false
        triggerIsPlayingChanged(false)
        super.stop()
    }

    override fun release() {
        updatePositionJob?.cancel()
        unbindVideoOutput()
        mediaPlayer?.let { player ->
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        super.release()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (boundSurfaceView === surfaceView) return
        unbindVideoOutput()
        boundSurfaceView = surfaceView
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(holder)
                if (canStartPlayback) play()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pause()
                mediaPlayer?.setDisplay(null)
            }
        }
        surfaceHolderCallback = callback
        surfaceView.holder.addCallback(callback)
        mediaPlayer?.setDisplay(surfaceView.holder)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        if (boundTextureView === textureView) return
        unbindVideoOutput()
        boundTextureView = textureView
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                textureSurface = Surface(surface)
                mediaPlayer?.setSurface(textureSurface)
                if (canStartPlayback) play()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                pause()
                mediaPlayer?.setSurface(null)
                textureSurface?.release()
                textureSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        surfaceTextureListener = listener
        textureView.surfaceTextureListener = listener
        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let {
                textureSurface = Surface(it)
                mediaPlayer?.setSurface(textureSurface)
            }
        }
    }

    private fun unbindVideoOutput() {
        surfaceHolderCallback?.let { callback ->
            boundSurfaceView?.holder?.removeCallback(callback)
        }
        if (boundTextureView?.surfaceTextureListener === surfaceTextureListener) {
            boundTextureView?.surfaceTextureListener = null
        }
        mediaPlayer?.setDisplay(null)
        mediaPlayer?.setSurface(null)
        textureSurface?.release()
        textureSurface = null
        surfaceHolderCallback = null
        surfaceTextureListener = null
        boundSurfaceView = null
        boundTextureView = null
    }

    companion object {
        private const val MEDIA_PLAYER_CLASS = "com.letv.spo.jungle.MediaPlayerExt"
        private const val PLAYER_TYPE_CLASS = "com.letv.spo.jungle.MediaPlayerExt\$PlayerType"
        private const val MSTAR_PLAYER_CLASS = "com.mstar.android.media.MMediaPlayer"

        fun isAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) return false
            return runCatching {
                Class.forName(MEDIA_PLAYER_CLASS, false, context.classLoader)
                Class.forName(PLAYER_TYPE_CLASS, false, context.classLoader)
            }.isSuccess
        }
    }
}

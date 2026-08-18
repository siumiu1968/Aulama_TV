package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

import top.yogiczy.mytv.tv.ui.utils.Configs

abstract class VideoPlayer(
    private val coroutineScope: CoroutineScope,
) {
    protected var metadata = Metadata()
    @Volatile
    protected var canStartPlayback = false

    open fun initialize() {
        clearAllListeners()
    }

    open fun release() {
        loadTimeoutJob?.cancel()
        interruptJob?.cancel()
        loadTimeoutJob = null
        interruptJob = null
        clearAllListeners()
    }

    abstract fun prepare(route: ChannelRoute)

    abstract fun play()

    abstract fun pause()

    open fun restartPlaybackHealthMonitoring() = Unit

    open fun setPlaybackAllowed(allowed: Boolean) {
        canStartPlayback = allowed
        if (!allowed) pause()
    }

    abstract fun seekTo(position: Long)

    /**
     * 將直播播放點移到 live edge 後指定毫秒數；傳入 null 會返回 live edge。
     * 只有能可靠取得 live edge 嘅播放器先覆寫並回傳 true。
     */
    open fun setLiveOffsetTargetMs(targetMs: Long?): Boolean = false

    open fun stop() {
        loadTimeoutJob?.cancel()
        interruptJob?.cancel()
        currentPosition = 0L
    }

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView)
    abstract fun setVideoTextureView(textureView: TextureView)

    private var loadTimeoutJob: Job? = null
    private var interruptJob: Job? = null
    private var currentPosition = 0L
    private var firstFrameTimeoutMs: Long? = null

    fun setFirstFrameTimeoutMs(timeoutMs: Long?) {
        firstFrameTimeoutMs = timeoutMs?.coerceAtLeast(1_000L)
    }

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>()
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>()
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>()
    private val onPreparedListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<() -> Unit>()

    private val onIsPlayingChanged = mutableListOf<(isPlaying: Boolean) -> Unit>()
    private val onDurationChanged = mutableListOf<(duration: Long) -> Unit>()
    private val onCurrentPositionChanged = mutableListOf<(position: Long) -> Unit>()
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()
    private val onPlaybackDegradedListeners = mutableListOf<(reason: String) -> Unit>()

    private fun clearAllListeners() {
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onFirstFrameListeners.clear()
        onIsPlayingChanged.clear()
        onDurationChanged.clear()
        onCurrentPositionChanged.clear()
        onMetadataListeners.clear()
        onInterruptListeners.clear()
        onPlaybackDegradedListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) {
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) {
        onErrorListeners.forEach { it(error) }

        if (error != PlaybackException.LOAD_TIMEOUT) {
            loadTimeoutJob?.cancel()
            loadTimeoutJob = null
        }
    }

    protected fun triggerReady() {
        onReadyListeners.forEach { it() }
    }

    protected fun triggerFirstFrame() {
        onFirstFrameListeners.forEach { it() }
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
    }

    protected fun triggerBuffering(buffering: Boolean) {
        onBufferingListeners.forEach { it(buffering) }
    }

    protected fun triggerPrepared() {
        onPreparedListeners.forEach { it() }
        loadTimeoutJob?.cancel()
        loadTimeoutJob = coroutineScope.launch {
            delay(effectiveFirstFrameTimeoutMs(Configs.videoPlayerLoadTimeout, firstFrameTimeoutMs))
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
        interruptJob?.cancel()
        interruptJob = null
        metadata = Metadata()
    }

    protected fun triggerIsPlayingChanged(isPlaying: Boolean) {
        onIsPlayingChanged.forEach { it(isPlaying) }
    }

    protected fun triggerDuration(duration: Long) {
        onDurationChanged.forEach { it(duration) }
    }

    protected fun triggerMetadata(metadata: Metadata) {
        onMetadataListeners.forEach { it(metadata) }
    }

    protected fun triggerCurrentPosition(
        position: Long,
        monitorTimelineStall: Boolean = true,
    ) {
        if (!monitorTimelineStall) {
            // IJK live playback has decode/output FPS telemetry. Its HLS timeline can pause
            // or jump even while frames render normally, so the richer health monitor owns
            // stall detection and this position-only watchdog must stay disarmed.
            interruptJob?.cancel()
            interruptJob = null
        } else if (shouldArmTimelineStallWatchdog(monitorTimelineStall, currentPosition, position)) {
            interruptJob?.cancel()
            interruptJob = coroutineScope.launch {
                delay(Configs.videoPlayerLoadTimeout)
                onInterruptListeners.forEach { it() }
            }
        }
        currentPosition = position
        onCurrentPositionChanged.forEach { it(position) }
    }

    protected fun triggerPlaybackDegraded(reason: String) {
        onPlaybackDegradedListeners.forEach { it(reason) }
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) {
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) {
        onPreparedListeners.add(listener)
    }

    fun onFirstFrame(listener: () -> Unit) {
        onFirstFrameListeners.add(listener)
    }

    fun onIsPlayingChanged(listener: (isPlaying: Boolean) -> Unit) {
        onIsPlayingChanged.add(listener)
    }

    fun onDurationChanged(listener: (duration: Long) -> Unit) {
        onDurationChanged.add(listener)
    }

    fun onCurrentPositionChanged(listener: (position: Long) -> Unit) {
        onCurrentPositionChanged.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) {
        onMetadataListeners.add(listener)
    }


    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    fun onPlaybackDegraded(listener: (reason: String) -> Unit) {
        onPlaybackDegradedListeners.add(listener)
    }

    data class PlaybackException(val errorCodeName: String, val errorCode: Int) :
        Exception(errorCodeName) {
        companion object {
            val UNSUPPORTED_TYPE = PlaybackException("UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT = PlaybackException("LOAD_TIMEOUT", 10003)
        }
    }

    /** 元數據 */
    data class Metadata(
        /** 視頻編碼 */
        val videoMimeType: String = "",
        /** 視頻寬度 */
        val videoWidth: Int = 0,
        /** 視頻高度 */
        val videoHeight: Int = 0,
        /** 視頻顏色 */
        val videoColor: String = "",
        /** 視頻幀率 */
        val videoFrameRate: Float = 0f,
        /** 視頻比特率 */
        val videoBitrate: Int = 0,
        /** 視頻解碼器 */
        val videoDecoder: String = "",

        /** 音頻編碼 */
        val audioMimeType: String = "",
        /** 音頻通道 */
        val audioChannels: Int = 0,
        /** 音頻採樣率 */
        val audioSampleRate: Int = 0,
        /** 音頻解碼器 */
        val audioDecoder: String = "",
    )
}

internal fun effectiveFirstFrameTimeoutMs(
    configuredTimeoutMs: Long,
    requestedTimeoutMs: Long?,
): Long = maxOf(configuredTimeoutMs, requestedTimeoutMs ?: 0L)

internal fun shouldArmTimelineStallWatchdog(
    enabled: Boolean,
    previousPosition: Long,
    currentPosition: Long,
): Boolean = enabled && previousPosition != currentPosition

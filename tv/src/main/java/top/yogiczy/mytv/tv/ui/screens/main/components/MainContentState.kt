package top.yogiczy.mytv.tv.ui.screens.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine   // 1. 新增
import androidx.compose.ui.platform.LocalContext          // 2. 新增
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.videoplayer.VideoPlayerState
import top.yogiczy.mytv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import top.yogiczy.mytv.tv.ui.utils.IptvDisplayCapabilities
import top.yogiczy.mytv.tv.ui.utils.IptvDegradationReason
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackHealthPolicy
import top.yogiczy.mytv.tv.ui.utils.IptvPlaybackMode
import top.yogiczy.mytv.tv.ui.utils.IptvNetworkScope
import top.yogiczy.mytv.tv.ui.utils.IptvRouteHealthStore
import top.yogiczy.mytv.tv.ui.utils.IptvRoutePriorityStore
import top.yogiczy.mytv.tv.ui.utils.keepManualFourKFallbacksTogether
import top.yogiczy.mytv.tv.ui.utils.mergeRouteAttemptOrder
import top.yogiczy.mytv.tv.ui.utils.orderRoutesForDisplay
import top.yogiczy.mytv.tv.ui.utils.currentIptvNetworkScope
import top.yogiczy.mytv.tv.ui.utils.isIptvNetworkHandover
import top.yogiczy.mytv.tv.account.AulamaAccount
import top.yogiczy.mytv.tv.account.AulamaPlaybackCandidate
import top.yogiczy.mytv.tv.account.AulamaPlaybackAuthorization
import top.yogiczy.mytv.tv.account.AulamaPlaybackPolicy
import top.yogiczy.mytv.tv.account.AulamaPlaybackTransport
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val STABLE_WATCH_SAMPLE_MS = 60_000L
private const val STABLE_WATCH_POLL_MS = 5_000L
private const val STABLE_WATCH_FINAL_CREDIT_MS = 45_000L
private const val QUICK_ROUTE_EXIT_MS = 25_000L
private const val RELAY_RESOLUTION_TIMEOUT_MS = 4_000L
private const val SAME_CANDIDATE_RELOAD_DELAY_MS = 260L
private val SUPER_ADMIN_TRANSPORT_IDS = listOf("hk_relay", "jp_relay", "direct")

private enum class NetworkHandoverAction {
    NONE,
    RESTARTED,
    SUPPRESS_HEALTH,
}

internal fun requiresImmediatePlaybackRecovery(reason: String): Boolean = when (reason) {
    IptvPlaybackHealthPolicy.reasonCode(IptvDegradationReason.FirstFrameTimeout),
    IptvPlaybackHealthPolicy.reasonCode(IptvDegradationReason.LongRebuffer),
    "audio-underrun",
    "ijk-playback-stalled",
    "ijk-decode-stalled" -> true
    else -> false
}

internal fun shouldReloadCurrentCandidate(reason: String): Boolean =
    requiresImmediatePlaybackRecovery(reason) &&
        reason != IptvPlaybackHealthPolicy.reasonCode(IptvDegradationReason.FirstFrameTimeout)

internal class PlaybackRecoveryBudget {
    private val reloadedCandidates = mutableSetOf<String>()

    fun tryUseCandidateReload(healthKey: String): Boolean =
        healthKey.isNotBlank() && reloadedCandidates.add(healthKey)

    fun restoreCandidateReload(healthKey: String) {
        reloadedCandidates.remove(healthKey)
    }

    fun reset() {
        reloadedCandidates.clear()
    }
}

internal class SoftDegradationNoticeBudget {
    private val notifiedCandidates = mutableSetOf<String>()

    fun tryNotify(healthKey: String): Boolean =
        healthKey.isNotBlank() && notifiedCandidates.add(healthKey)

    fun restore(healthKey: String) {
        notifiedCandidates.remove(healthKey)
    }

    fun reset() {
        notifiedCandidates.clear()
    }
}

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val settingsViewModel: SettingsViewModel,
    private val context: Context,
) : Loggable() {
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelUrlIdx by mutableIntStateOf(0)
    val currentChannelUrlIdx get() = _currentChannelUrlIdx

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var routeAttemptOrder = emptyList<Int>()
    private var routeAttemptCursor = 0
    private var transportAttempts = emptyList<AulamaPlaybackCandidate>()
    private var transportAttemptCursor = 0
    private var transportResolutionGeneration = 0L
    private var _playbackTransportPreferenceId by mutableStateOf(
        AulamaPlaybackPolicy.AUTO_PREFERENCE_ID
    )
    val playbackTransportPreferenceId get() = _playbackTransportPreferenceId
    private var routeStartedAt = 0L
    /** 每個 attempt 固定網絡 scope；真實換網故障會重新排序而不懲罰舊網絡。 */
    private var activeNetworkScope = IptvNetworkScope.UNKNOWN
    private var routeFirstFrameAt = 0L
    private var routeWatchCreditedMs = 0L
    private var routeSuccessRecorded = false
    private var stableWatchLearningJob: Job? = null
    private var lastFailureHandledKey: String? = null
    private val recoveryBudget = PlaybackRecoveryBudget()
    private val softDegradationNoticeBudget = SoftDegradationNoticeBudget()
    private var recoveryGeneration = 0L
    private var networkHandoverRestartUsed = false
    private val supportsHdrOutput by lazy {
        IptvDisplayCapabilities.supportsHdrOutput(context)
    }
    private val hdrFallbackNotifiedChannels = mutableSetOf<String>()

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isSettingsScreenVisible by mutableStateOf(false)
    var isSettingsScreenVisible
        get() = _isSettingsScreenVisible
        set(value) {
            _isSettingsScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelUrlScreenVisible by mutableStateOf(false)
    var isChannelUrlScreenVisible
        get() = _isChannelUrlScreenVisible
        set(value) {
            _isChannelUrlScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    init {
        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(channelGroupList.channelList.getOrElse(settingsViewModel.iptvLastChannelIdx) {
            channelGroupList.channelList.firstOrNull() ?: Channel()
        })

        /* 新增：監聽 RESTART_PLAY 廣播，收到後重新 prepare 當前頻道 */
        coroutineScope.launch {
            suspendCancellableCoroutine<Unit> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == "top.yogiczy.mytv.tv.RESTART_PLAY") {
                            finishCurrentWatchSession()
                            resetPlaybackRecoveryBudget()
                            prepareCurrentRoute()
                        }
                    }
                }
                val filter = IntentFilter("top.yogiczy.mytv.tv.RESTART_PLAY")
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                cont.invokeOnCancellation { context.unregisterReceiver(receiver) }
                // 永久掛起，直到作用域被取消
            }
        }

        videoPlayerState.onFirstFrame {
            val route = currentRouteOrNull() ?: return@onFirstFrame
            val healthKey = currentPlaybackHealthKey()
            val legacyHealthKey = currentPlaybackLegacyHealthKey()
            if (!routeSuccessRecorded) {
                val now = System.currentTimeMillis()
                routeSuccessRecorded = true
                routeFirstFrameAt = now
                IptvRouteHealthStore.markSuccess(
                    healthKey,
                    now - routeStartedAt,
                    playbackMode = videoPlayerState.playbackMode,
                    legacyKey = legacyHealthKey,
                )
                startStableWatchLearning(healthKey, legacyHealthKey)
            }
            if (currentPlaybackTransport() == AulamaPlaybackTransport.DIRECT) {
                settingsViewModel.iptvPlayableHostList += getUrlHost(route.url)
            }
            coroutineScope.launch {
                val name = _currentChannel.name
                val urlIdx = _currentChannelUrlIdx
                delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                if (name == _currentChannel.name && urlIdx == _currentChannelUrlIdx) {
                    _isTempChannelScreenVisible = false
                    settingsViewModel.setIptvChannelUrlIdx(_currentChannel.name, _currentChannelUrlIdx)
//                    settingsViewModel.iptvChannelUrlIdx[_currentChannel.name]= _currentChannelUrlIdx
                }
            }
        }

        videoPlayerState.onPlaybackIssueObserved { reason ->
            val latestScope = currentIptvNetworkScope(context)
            if (latestScope != activeNetworkScope) {
                return@onPlaybackIssueObserved
            }
            IptvRouteHealthStore.markBufferingIssue(
                currentPlaybackHealthKey(),
                reason,
                playbackMode = videoPlayerState.playbackMode,
                legacyKey = currentPlaybackLegacyHealthKey(),
            )
        }

        videoPlayerState.onPlaybackDegraded { reason ->
            val failedRoute = currentRouteOrNull() ?: return@onPlaybackDegraded
            val handoverAction = handleNetworkHandoverFailure()
            if (handoverAction == NetworkHandoverAction.RESTARTED) return@onPlaybackDegraded
            val shouldRecordHealth = handoverAction != NetworkHandoverAction.SUPPRESS_HEALTH
            val failedHealthKey = currentPlaybackHealthKey()
            val failedLegacyHealthKey = currentPlaybackLegacyHealthKey()
            if (lastFailureHandledKey == failedHealthKey) return@onPlaybackDegraded
            val mustRecover = requiresImmediatePlaybackRecovery(reason)
            if (!mustRecover && !softDegradationNoticeBudget.tryNotify(failedHealthKey)) {
                videoPlayerState.keepCurrentRoute()
                startStableWatchLearning(failedHealthKey, failedLegacyHealthKey)
                return@onPlaybackDegraded
            }
            lastFailureHandledKey = failedHealthKey
            finishCurrentWatchSession()
            val shouldReload = shouldReloadCurrentCandidate(reason)
            if (shouldReload && scheduleSameCandidateReload(failedHealthKey)) {
                Snackbar.show("播放停頓，原線重試中")
                return@onPlaybackDegraded
            }

            if (shouldRecordHealth) {
                IptvRouteHealthStore.markDegraded(
                    failedHealthKey,
                    reason,
                    playbackMode = videoPlayerState.playbackMode,
                    legacyKey = failedLegacyHealthKey,
                )
            }
            if (
                shouldRecordHealth &&
                currentPlaybackTransport() == AulamaPlaybackTransport.DIRECT
            ) {
                settingsViewModel.iptvPlayableHostList -= getUrlHost(failedRoute.url)
            }

            if (playNextRoute(forceSwitch = mustRecover)) {
                val nextRoute = currentRouteOrNull()
                val target = nextRoute?.quality?.label ?: "後備"
                Snackbar.show("畫面播放唔順，已自動切換${target}線路")
                log.w("線路播放質素下降（$reason），自動切換：${failedRoute.url}")
            } else if (mustRecover) {
                showPlaybackExhausted(reason)
            } else {
                lastFailureHandledKey = null
                videoPlayerState.keepCurrentRoute()
                startStableWatchLearning(failedHealthKey, failedLegacyHealthKey)
                Snackbar.show("未有明顯更佳線路，暫時保留目前播放")
                log.w("線路播放質素下降（$reason），後備線路未高出 20 分：${failedRoute.url}")
            }
        }

        videoPlayerState.onError { error ->
            val failedRoute = currentRouteOrNull() ?: return@onError false
            val handoverAction = handleNetworkHandoverFailure()
            if (handoverAction == NetworkHandoverAction.RESTARTED) return@onError true
            val shouldRecordHealth = handoverAction != NetworkHandoverAction.SUPPRESS_HEALTH
            val failedHealthKey = currentPlaybackHealthKey()
            val failedLegacyHealthKey = currentPlaybackLegacyHealthKey()
            if (lastFailureHandledKey == failedHealthKey) return@onError true
            lastFailureHandledKey = failedHealthKey
            finishCurrentWatchSession()

            if (shouldRecordHealth) {
                IptvRouteHealthStore.markFailure(
                    failedHealthKey,
                    legacyKey = failedLegacyHealthKey,
                )
            }
            if (
                shouldRecordHealth &&
                currentPlaybackTransport() == AulamaPlaybackTransport.DIRECT
            ) {
                settingsViewModel.iptvPlayableHostList -= getUrlHost(failedRoute.url)
            }

            if (_currentPlaybackEpgProgramme != null) {
                // 回放播放錯誤時先返回同一頻道直播，再按線路健康度回退。
                changeCurrentChannel(_currentChannel, _currentChannelUrlIdx, null, retrying = true)
                return@onError true
            }

            if (playNextRoute(forceSwitch = true)) {
                true
            } else {
                showPlaybackExhausted(error)
                true
            }
        }

        videoPlayerState.onInterrupt {
            val handoverAction = handleNetworkHandoverFailure()
            if (handoverAction == NetworkHandoverAction.RESTARTED) return@onInterrupt
            val shouldRecordHealth = handoverAction != NetworkHandoverAction.SUPPRESS_HEALTH
            currentRouteOrNull()?.let { route ->
                val failedHealthKey = currentPlaybackHealthKey()
                val failedLegacyHealthKey = currentPlaybackLegacyHealthKey()
                if (lastFailureHandledKey == failedHealthKey) return@onInterrupt
                lastFailureHandledKey = failedHealthKey
                finishCurrentWatchSession()
                if (scheduleSameCandidateReload(failedHealthKey)) return@onInterrupt

                if (shouldRecordHealth) {
                    IptvRouteHealthStore.markFailure(
                        failedHealthKey,
                        legacyKey = failedLegacyHealthKey,
                    )
                }
                if (
                    shouldRecordHealth &&
                    currentPlaybackTransport() == AulamaPlaybackTransport.DIRECT
                ) {
                    settingsViewModel.iptvPlayableHostList -= getUrlHost(route.url)
                }
            }
            if (!playNextRoute(forceSwitch = true)) showPlaybackExhausted("playback-interrupted")
        }
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()

        val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
        val favoriteChannelList =
            channelGroupList.channelList.filter { it.name in favoriteChannelNameList }

        return if (_currentChannel in favoriteChannelList && _currentChannel != favoriteChannelList.first()) {
            val currentIdx = favoriteChannelList.indexOf(_currentChannel)
            favoriteChannelList[currentIdx - 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupList.channelList.lastOrNull()
        } else {
            favoriteChannelList.lastOrNull()
        }

    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()

        val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
        val favoriteChannelList =
            channelGroupList.channelList.filter { it.name in favoriteChannelNameList }

        return if (_currentChannel in favoriteChannelList && _currentChannel != favoriteChannelList.last()) {
            val currentIdx = favoriteChannelList.indexOf(_currentChannel)
            favoriteChannelList[currentIdx + 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupList.channelList.firstOrNull()
        } else {
            favoriteChannelList.firstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx - 1) {
                channelGroupList.channelList.lastOrNull() ?: Channel()
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx + 1) {
                channelGroupList.channelList.firstOrNull() ?: Channel()
            }
        }
    }

    private fun getUrlIdx(urlList: List<String>, urlIdx: Int? = null): Int {
        if (urlList.isEmpty()) return 0
        val idx = if (urlIdx == null) settingsViewModel.getIptvChannelUrlIdx(_currentChannel.name)
//            urlList.indexOfFirst {
//            settingsViewModel.iptvPlayableHostList.contains(getUrlHost(it))
//        }
        else (urlIdx + urlList.size) % urlList.size

        return max(0, min(idx, urlList.size - 1))
    }

    private fun currentRouteOrNull(): ChannelRoute? =
        _currentChannel.routes.getOrNull(_currentChannelUrlIdx)

    private fun currentPlaybackTransport(): AulamaPlaybackTransport =
        transportAttempts.getOrNull(transportAttemptCursor)?.transport
            ?: AulamaPlaybackTransport.DIRECT

    private fun currentPlaybackCandidate(): AulamaPlaybackCandidate? =
        transportAttempts.getOrNull(transportAttemptCursor)

    private fun currentPlaybackHealthKey(): String = IptvRouteHealthStore.scopedCandidateKey(
        routeUrl = currentRouteOrNull()?.url.orEmpty(),
        transportId = currentPlaybackCandidate()?.id.orEmpty(),
        networkScope = activeNetworkScope,
    )

    private fun currentPlaybackLegacyHealthKey(): String = IptvRouteHealthStore.candidateKey(
        routeUrl = currentRouteOrNull()?.url.orEmpty(),
        transportId = currentPlaybackCandidate()?.id.orEmpty(),
    )

    /**
     * Android 換 default network 時會終止舊 socket。第一次相關故障只按新網絡重新排序，
     * 不會把 handover 當成舊線路本身失敗。穩播 60 秒前只特別重啟一次；其後網絡
     * 再抖動只略過錯誤樣本，仍沿用原有有界恢復，避免繞過 terminal 狀態。
     */
    private fun handleNetworkHandoverFailure(): NetworkHandoverAction {
        val latestScope = currentIptvNetworkScope(context)
        if (latestScope == activeNetworkScope) return NetworkHandoverAction.NONE
        if (
            activeNetworkScope == IptvNetworkScope.UNKNOWN ||
            latestScope == IptvNetworkScope.UNKNOWN
        ) {
            // Offline/indeterminate state still follows normal bounded recovery, but it is not
            // evidence that the route itself is bad.
            return NetworkHandoverAction.SUPPRESS_HEALTH
        }
        if (!isIptvNetworkHandover(activeNetworkScope, latestScope)) {
            return NetworkHandoverAction.NONE
        }
        if (networkHandoverRestartUsed) return NetworkHandoverAction.SUPPRESS_HEALTH

        val previousScope = activeNetworkScope
        networkHandoverRestartUsed = true
        finishCurrentWatchSession()
        // Relay resolution can take several seconds. Invalidate the old physical player now so
        // its queued native callbacks cannot race a new transport attempt during that window.
        videoPlayerState.stop()
        resetPlaybackRecoveryBudget(resetNetworkHandoverGuard = false)
        routeAttemptOrder = buildRouteAttemptOrder(_currentChannel, requestedIndex = null)
        routeAttemptCursor = 0
        _currentChannelUrlIdx = routeAttemptOrder.firstOrNull()
            ?: return NetworkHandoverAction.SUPPRESS_HEALTH
        Snackbar.show("網絡已切換，正在重新選擇線路")
        log.i("網絡由 $previousScope 切換至 $latestScope，重新排序播放候選")
        prepareCurrentRoute(retrying = true)
        return NetworkHandoverAction.RESTARTED
    }

    private fun scheduleSameCandidateReload(healthKey: String): Boolean {
        // A 4K attempt already gets one complete Media3 -> IJK pass inside
        // VideoPlayerState. Repeating the same candidate would delay the other 4K
        // backups and contradict the manual fallback order.
        if (currentRouteOrNull()?.quality == ChannelQuality.UHD_4K) return false

        if (!recoveryBudget.tryUseCandidateReload(healthKey)) return false

        val channelName = _currentChannel.name
        val routeUrl = currentRouteOrNull()?.url ?: return false
        val attemptCursor = transportAttemptCursor
        val expectedRecoveryGeneration = recoveryGeneration
        videoPlayerState.showRetryNotice("播放有波動，原線重試中")
        coroutineScope.launch {
            delay(SAME_CANDIDATE_RELOAD_DELAY_MS)
            val baseRoute = currentRouteOrNull() ?: return@launch
            val stillCurrentAttempt =
                recoveryGeneration == expectedRecoveryGeneration &&
                videoPlayerState.isPlaybackForeground &&
                _currentChannel.name == channelName &&
                baseRoute.url == routeUrl &&
                transportAttemptCursor == attemptCursor &&
                currentPlaybackHealthKey() == healthKey
            if (!stillCurrentAttempt) {
                if (recoveryGeneration == expectedRecoveryGeneration) {
                    recoveryBudget.restoreCandidateReload(healthKey)
                }
                return@launch
            }

            lastFailureHandledKey = null
            prepareTransportAttempt(
                baseRoute = baseRoute,
                directRoute = withPlaybackProgramme(baseRoute),
                retrying = true,
            )
        }
        return true
    }

    private fun startStableWatchLearning(healthKey: String, legacyHealthKey: String) {
        stableWatchLearningJob?.cancel()
        stableWatchLearningJob = coroutineScope.launch {
            var creditedContinuousMs = 0L
            while (currentPlaybackHealthKey() == healthKey) {
                delay(STABLE_WATCH_POLL_MS)
                val continuousHealthyMs = videoPlayerState.continuousHealthyPlaybackMs()
                if (continuousHealthyMs < creditedContinuousMs) creditedContinuousMs = 0L
                if (continuousHealthyMs >= creditedContinuousMs + STABLE_WATCH_SAMPLE_MS) {
                    IptvRouteHealthStore.markStableWatch(
                        healthKey,
                        STABLE_WATCH_SAMPLE_MS,
                        playbackMode = videoPlayerState.playbackMode,
                        legacyKey = legacyHealthKey,
                    )
                    routeWatchCreditedMs += STABLE_WATCH_SAMPLE_MS
                    creditedContinuousMs += STABLE_WATCH_SAMPLE_MS
                    networkHandoverRestartUsed = false
                    recoveryBudget.restoreCandidateReload(healthKey)
                    softDegradationNoticeBudget.restore(healthKey)
                }
            }
        }
    }

    private fun finishCurrentWatchSession(quickExit: Boolean = false) {
        stableWatchLearningJob?.cancel()
        stableWatchLearningJob = null
        val route = currentRouteOrNull()
        if (route != null && routeFirstFrameAt > 0L) {
            val healthKey = currentPlaybackHealthKey()
            val legacyHealthKey = currentPlaybackLegacyHealthKey()
            val watchedMs = (System.currentTimeMillis() - routeFirstFrameAt).coerceAtLeast(0L)
            val uncreditedMs = (watchedMs - routeWatchCreditedMs).coerceAtLeast(0L)
            if (uncreditedMs >= STABLE_WATCH_FINAL_CREDIT_MS) {
                IptvRouteHealthStore.markStableWatch(
                    healthKey,
                    uncreditedMs,
                    playbackMode = videoPlayerState.playbackMode,
                    legacyKey = legacyHealthKey,
                )
            }
            if (quickExit && watchedMs in 1 until QUICK_ROUTE_EXIT_MS) {
                IptvRouteHealthStore.markQuickExit(healthKey, legacyKey = legacyHealthKey)
            }
        }
        routeFirstFrameAt = 0L
        routeWatchCreditedMs = 0L
        routeSuccessRecorded = false
    }

    private fun resetPlaybackRecoveryBudget(resetNetworkHandoverGuard: Boolean = true) {
        recoveryGeneration += 1
        recoveryBudget.reset()
        softDegradationNoticeBudget.reset()
        lastFailureHandledKey = null
        if (resetNetworkHandoverGuard) networkHandoverRestartUsed = false
    }

    private fun showPlaybackExhausted(reason: String) {
        val route = currentRouteOrNull()
        log.w("所有播放候選已用盡（$reason）：host=${getUrlHost(route?.url.orEmpty())}")
        videoPlayerState.showTerminalError(
            message = "所有可用線路暫時無法播放，請重新嘗試或切換頻道",
            onRetry = ::retryPlaybackSession,
        )
    }

    private fun retryPlaybackSession() {
        resetPlaybackRecoveryBudget()
        finishCurrentWatchSession()
        routeAttemptOrder = buildRouteAttemptOrder(_currentChannel, requestedIndex = null)
        routeAttemptCursor = 0
        _currentChannelUrlIdx = routeAttemptOrder.firstOrNull() ?: return
        prepareCurrentRoute(retrying = true)
    }

    private fun buildRouteAttemptOrder(
        channel: Channel,
        requestedIndex: Int?,
    ): List<Int> {
        if (channel.routes.isEmpty()) return emptyList()
        val requested = requestedIndex?.let { getUrlIdx(channel.urlList, it) }
        val ranked = IptvRouteHealthStore.rankedIndices(
            routes = channel.routes,
            supports4k = supportsHdrOutput,
            transportIds = if (AulamaAccount.manager.isSuperAdmin()) {
                SUPER_ADMIN_TRANSPORT_IDS
            } else {
                listOf("direct")
            },
            networkScope = currentIptvNetworkScope(context),
        )
        val automaticOrder = orderRoutesForDisplay(
            routes = channel.routes,
            rankedIndices = ranked,
            requestedIndex = null,
            supportsHdrOutput = supportsHdrOutput,
        )

        val mergedOrder = mergeRouteAttemptOrder(
            routes = channel.routes,
            priorityUrls = IptvRoutePriorityStore.priorities(channel.name),
            automaticIndices = automaticOrder,
            requestedIndex = requested,
        )
        return keepManualFourKFallbacksTogether(
            routes = channel.routes,
            attemptOrder = mergedOrder,
            requestedIndex = requested,
        )
    }

    private fun notifyHdrRouteDecision(channel: Channel, requestedIndex: Int?) {
        if (supportsHdrOutput) return

        val manuallyRequested4k = requestedIndex != null &&
            channel.routes.getOrNull(getUrlIdx(channel.urlList, requestedIndex))
                ?.quality == ChannelQuality.UHD_4K
        val manuallyPrioritized4k = requestedIndex == null &&
            IptvRoutePriorityStore.priorities(channel.name)
                .firstNotNullOfOrNull { url -> channel.routes.firstOrNull { it.url == url } }
                ?.quality == ChannelQuality.UHD_4K
        if (manuallyRequested4k || manuallyPrioritized4k) {
            Snackbar.show("呢部電視未支援 HDR，4K 顏色可能偏淡")
            return
        }

        val selectedRoute = currentRouteOrNull() ?: return
        if (
            requestedIndex == null &&
            selectedRoute.quality != ChannelQuality.UHD_4K &&
            channel.routes.any { it.quality == ChannelQuality.UHD_4K } &&
            hdrFallbackNotifiedChannels.add(channel.name)
        ) {
            Snackbar.show(
                "呢部電視未支援 HDR，已自動選用${selectedRoute.quality.label}正常色彩線路"
            )
        }
    }

    private fun prepareCurrentRoute(retrying: Boolean = false) {
        val baseRoute = currentRouteOrNull() ?: return
        stableWatchLearningJob?.cancel()
        stableWatchLearningJob = null
        routeFirstFrameAt = 0L
        routeWatchCreditedMs = 0L
        routeSuccessRecorded = false
        val directRoute = withPlaybackProgramme(baseRoute)

        _isTempChannelScreenVisible = true
        routeStartedAt = System.currentTimeMillis()
        lastFailureHandledKey = null
        val priorityRank = IptvRoutePriorityStore.priorities(_currentChannel.name)
            .indexOf(baseRoute.url)
            .takeIf { it >= 0 }
            ?.plus(1)
        val selectionLabel = priorityRank?.let { "優先$it" } ?: "自動線路"
        log.d(
            "播放${_currentChannel.name}（${baseRoute.quality.label}，$selectionLabel，" +
                "線路${_currentChannelUrlIdx + 1}/${_currentChannel.routes.size}）: ${directRoute.url}"
        )

        if (ChannelUtil.isHybridWebViewUrl(directRoute.url)) {
            transportAttempts = listOf(
                AulamaPlaybackPolicy.candidates(directRoute.url, false, emptyList()).single()
            )
            transportAttemptCursor = 0
            videoPlayerState.stop()
            return
        }

        val generation = ++transportResolutionGeneration
        if (!AulamaAccount.manager.isSuperAdmin()) {
            transportAttempts = AulamaPlaybackPolicy.candidates(
                directRoute.url,
                isSuperAdmin = false,
                plan = emptyList(),
            )
            transportAttemptCursor = 0
            prepareTransportAttempt(baseRoute, directRoute, retrying)
            return
        }

        videoPlayerState.showRetryNotice(if (retrying) "自動切換線路中" else "正在選擇最佳線路")
        coroutineScope.launch {
            val resolved = withTimeoutOrNull(RELAY_RESOLUTION_TIMEOUT_MS) {
                AulamaAccount.manager.playbackCandidates(
                    url = directRoute.url,
                    referrer = directRoute.referrer,
                    userAgent = directRoute.userAgent,
                )
            } ?: AulamaPlaybackPolicy.candidates(directRoute.url, false, emptyList())
            if (generation != transportResolutionGeneration || currentRouteOrNull()?.url != baseRoute.url) {
                return@launch
            }
            transportAttempts = rankTransportAttempts(baseRoute, resolved)
            transportAttemptCursor = 0
            prepareTransportAttempt(baseRoute, directRoute, retrying)
        }
    }

    private fun rankTransportAttempts(
        baseRoute: ChannelRoute,
        candidates: List<AulamaPlaybackCandidate>,
    ): List<AulamaPlaybackCandidate> {
        val prioritized = AulamaPlaybackPolicy.prioritize(
            candidates = candidates,
            preferenceId = _playbackTransportPreferenceId,
        )
        val rankedIds = IptvRouteHealthStore.rankedTransportIds(
            routeUrl = baseRoute.url,
            orderedTransportIds = prioritized.map { it.id },
            quality = baseRoute.quality,
            supports4k = supportsHdrOutput,
            networkScope = currentIptvNetworkScope(context),
        )
        val learnedOrder = rankedIds.flatMap { id -> prioritized.filter { it.id == id } } +
            prioritized.filter { it.id !in rankedIds }
        if (_playbackTransportPreferenceId == AulamaPlaybackPolicy.AUTO_PREFERENCE_ID) {
            return learnedOrder
        }

        val preferred = prioritized.firstOrNull {
            it.id == _playbackTransportPreferenceId
        } ?: return learnedOrder
        return listOf(preferred) + learnedOrder.filterNot { it.url == preferred.url }
    }

    private fun prepareTransportAttempt(
        baseRoute: ChannelRoute,
        directRoute: ChannelRoute,
        retrying: Boolean,
    ) {
        val attempt = transportAttempts.getOrNull(transportAttemptCursor)
            ?: AulamaPlaybackPolicy.candidates(directRoute.url, false, emptyList()).single()
        val route = if (attempt.transport == AulamaPlaybackTransport.RELAY) {
            directRoute.copy(
                url = attempt.url,
                label = attempt.label,
                referrer = null,
                userAgent = null,
            )
        } else {
            AulamaPlaybackAuthorization.clearForUrl(directRoute.url)
            directRoute
        }
        activeNetworkScope = currentIptvNetworkScope(context)
        lastFailureHandledKey = null
        routeStartedAt = System.currentTimeMillis()
        videoPlayerState.prepare(
            route,
            retrying = retrying,
            preferredPlaybackMode = if (attempt.transport == AulamaPlaybackTransport.RELAY) {
                // Media3's shared OkHttp factory keeps the bearer header on every HLS segment.
                IptvPlaybackMode.MEDIA3
            } else {
                IptvRouteHealthStore.preferredPlaybackMode(
                    baseRoute.url,
                    attempt.id,
                    activeNetworkScope,
                )
            },
            learnedHealth = IptvRouteHealthStore.healthFor(
                baseRoute.url,
                attempt.id,
                activeNetworkScope,
            ),
            firstFrameTimeoutMs = IptvPlaybackHealthPolicy.firstFrameTimeoutMsFor(
                quality = baseRoute.quality,
                isRelay = attempt.transport == AulamaPlaybackTransport.RELAY,
            ),
        )
    }

    private fun playNextRoute(forceSwitch: Boolean = false): Boolean {
        if (transportAttemptCursor + 1 < transportAttempts.size) {
            val baseRoute = currentRouteOrNull() ?: return false
            transportAttemptCursor += 1
            val directRoute = withPlaybackProgramme(baseRoute)
            prepareTransportAttempt(baseRoute, directRoute, retrying = true)
            return true
        }
        if (routeAttemptCursor + 1 >= routeAttemptOrder.size) return false
        val currentRoute = currentRouteOrNull() ?: return false
        val nextRoute = _currentChannel.routes.getOrNull(routeAttemptOrder[routeAttemptCursor + 1])
            ?: return false
        if (!forceSwitch) {
            val now = System.currentTimeMillis()
            val currentTransportId = currentPlaybackCandidate()?.id.orEmpty()
            val currentScore = IptvRouteHealthStore.performanceScore(
                IptvRouteHealthStore.healthFor(
                    currentRoute.url,
                    currentTransportId,
                    activeNetworkScope,
                ),
                now,
                currentRoute.quality,
                supportsHdrOutput,
            )
            val nextTransportIds = if (AulamaAccount.manager.isSuperAdmin()) {
                SUPER_ADMIN_TRANSPORT_IDS
            } else {
                listOf("direct")
            }
            val candidateScore = IptvRouteHealthStore.performanceScore(
                nextTransportIds.mapNotNull { transportId ->
                    IptvRouteHealthStore.healthFor(
                        nextRoute.url,
                        transportId,
                        activeNetworkScope,
                    )
                }.maxByOrNull { health ->
                    IptvRouteHealthStore.performanceScore(
                        health,
                        now,
                        nextRoute.quality,
                        supportsHdrOutput,
                    )
                },
                now,
                nextRoute.quality,
                supportsHdrOutput,
            )
            if (!IptvRouteHealthStore.shouldAutoSwitch(currentScore, candidateScore)) return false
        }
        routeAttemptCursor += 1
        _currentChannelUrlIdx = routeAttemptOrder[routeAttemptCursor]
        prepareCurrentRoute(retrying = true)
        return true
    }

    private fun withPlaybackProgramme(baseRoute: ChannelRoute): ChannelRoute {
        val programme = _currentPlaybackEpgProgramme ?: return baseRoute
        val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val query = "playseek=${timeFormat.format(programme.startAt)}-${timeFormat.format(programme.endAt)}"
        val playbackUrl = if (URI(baseRoute.url).query.isNullOrBlank()) {
            "${baseRoute.url}?$query"
        } else {
            "${baseRoute.url}&$query"
        }
        return baseRoute.copy(url = playbackUrl)
    }

    fun changeCurrentChannel(
        channel: Channel,
        urlIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
        retrying: Boolean = false,
    ) {
        if (channel.routes.isEmpty()) return
        if (
            channel == _currentChannel &&
            urlIdx != null &&
            getUrlIdx(channel.urlList, urlIdx) == _currentChannelUrlIdx &&
            playbackEpgProgramme == _currentPlaybackEpgProgramme
        ) return

        if (!retrying) resetPlaybackRecoveryBudget()

        val isManualRouteChange = !retrying &&
            channel.name == _currentChannel.name &&
            urlIdx != null &&
            getUrlIdx(channel.urlList, urlIdx) != _currentChannelUrlIdx
        finishCurrentWatchSession(quickExit = isManualRouteChange)

        _currentChannel = channel
        settingsViewModel.iptvLastChannelIdx =
            channelGroupListProvider().channelIdx(_currentChannel)
        _currentPlaybackEpgProgramme = playbackEpgProgramme
        routeAttemptOrder = buildRouteAttemptOrder(channel, urlIdx)
        routeAttemptCursor = 0
        _currentChannelUrlIdx = routeAttemptOrder.firstOrNull() ?: return
        notifyHdrRouteDecision(channel, urlIdx)
        prepareCurrentRoute(retrying = retrying)
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun changeCurrentChannelToNextRoute(): Boolean {
        val routeCount = _currentChannel.routes.size
        if (routeCount <= 1) return false

        val nextRouteIdx = (_currentChannelUrlIdx + 1) % routeCount
        changeCurrentChannel(
            channel = _currentChannel,
            urlIdx = nextRouteIdx,
            playbackEpgProgramme = _currentPlaybackEpgProgramme,
        )
        return true
    }

    fun refreshCurrentChannel() {
        if (videoPlayerState.hasTerminalRetry) return
        finishCurrentWatchSession()
        prepareCurrentRoute()
    }

    fun changePlaybackTransportPreference(preferenceId: String) {
        if (!AulamaAccount.manager.isSuperAdmin()) return
        val allowed = setOf(
            AulamaPlaybackPolicy.AUTO_PREFERENCE_ID,
            "hk_relay",
            "jp_relay",
            "direct",
        )
        _playbackTransportPreferenceId = preferenceId.takeIf { it in allowed }
            ?: AulamaPlaybackPolicy.AUTO_PREFERENCE_ID
        Snackbar.show(AulamaPlaybackPolicy.preferenceLabel(_playbackTransportPreferenceId))
        finishCurrentWatchSession()
        resetPlaybackRecoveryBudget()
        prepareCurrentRoute(retrying = true)
    }

    fun favoriteChannelOrNot(channel: Channel) {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return

        if (settingsViewModel.iptvChannelFavoriteList.contains(channel.name)) {
            settingsViewModel.iptvChannelFavoriteList -= channel.name
            Snackbar.show("取消收藏：${channel.name}")
        } else {
            settingsViewModel.iptvChannelFavoriteList += channel.name
            Snackbar.show("已收藏：${channel.name}")
        }
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消預約：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已預約：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        urlIdx: Int? = _currentChannelUrlIdx,
    ): Boolean {
        val currentUrlIdx = getUrlIdx(channel.urlList, urlIdx)
        return ChannelUtil.urlSupportPlayback(channel.urlList[currentUrlIdx])
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = viewModel(),
):MainContentState {
    val context = LocalContext.current.applicationContext  // 先在Composable體內獲取context
    return remember {
        MainContentState(
            coroutineScope = coroutineScope,
            videoPlayerState = videoPlayerState,
            channelGroupListProvider = channelGroupListProvider,
            settingsViewModel = settingsViewModel,
            context = context,  // 傳遞context
        )
    }
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}

package top.yogiczy.mytv.tv.ui.screens.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.tv.account.AulamaAccount
import top.yogiczy.mytv.tv.account.AulamaAccountState
import top.yogiczy.mytv.tv.caption.LiveCaptionMode
import top.yogiczy.mytv.tv.caption.LiveCaptionLifecycleEvent
import top.yogiczy.mytv.tv.caption.LiveCaptionLifecycleSyncState
import top.yogiczy.mytv.tv.caption.LiveCaptionSession
import top.yogiczy.mytv.tv.caption.isEnglishLanguageTag
import top.yogiczy.mytv.tv.caption.liveCaptionTargetDelayMs
import top.yogiczy.mytv.tv.caption.reduceLiveCaptionLifecycle
import top.yogiczy.mytv.tv.caption.withVerifiedOffset
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.Visible
import top.yogiczy.mytv.tv.ui.material.popupable
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelNumberSelectScreen
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelScreen
import top.yogiczy.mytv.tv.ui.screens.channel.ChannelTempScreen
import top.yogiczy.mytv.tv.ui.screens.channel.rememberChannelNumberSelectState
import top.yogiczy.mytv.tv.ui.screens.channelurl.ChannelUrlScreen
import top.yogiczy.mytv.tv.ui.screens.classicchannel.ClassicChannelScreen
import top.yogiczy.mytv.tv.ui.screens.datetime.DatetimeScreen
import top.yogiczy.mytv.tv.ui.screens.epg.EpgScreen
import top.yogiczy.mytv.tv.ui.screens.epgreverse.EpgReverseScreen
import top.yogiczy.mytv.tv.ui.screens.livecaption.LiveCaptionModeScreen
import top.yogiczy.mytv.tv.ui.screens.livecaption.LiveCaptionOverlay
import top.yogiczy.mytv.tv.ui.screens.monitor.MonitorScreen
import top.yogiczy.mytv.tv.ui.screens.quickop.QuickOpScreen
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsScreen
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.UpdateScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayer.VideoPlayerScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import top.yogiczy.mytv.tv.ui.screens.videoplayercontroller.VideoPlayerControllerScreen
import top.yogiczy.mytv.tv.ui.screens.videoplayerdiaplaymode.VideoPlayerDisplayModeScreen
import top.yogiczy.mytv.tv.ui.screens.webview.WebViewScreen
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.handleDragGestures
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

private const val SELECT_DOUBLE_PRESS_WINDOW_MS = 400L
private const val LIVE_CAPTION_SYNC_RETRY_COUNT = 5
private const val LIVE_CAPTION_SYNC_RETRY_DELAY_MS = 300L

private fun isLiveCaptionChannel(channel: Channel): Boolean {
    return isEnglishLanguageTag(channel.tvgLanguage) && channel.captionChannelId.isNotBlank()
}

private fun liveCaptionAccessMessage(state: AulamaAccountState): String = when (state) {
    is AulamaAccountState.SignedIn -> if (
        state.profile.isSuperAdmin || state.profile.role in setOf("premium", "admin", "super_admin")
    ) "" else "即時字幕只限高級會員或以上"
    AulamaAccountState.Restoring,
    AulamaAccountState.StartingPairing,
    is AulamaAccountState.Pairing -> "正在核對 Aulama ID…"
    else -> "請先登入 Aulama ID 使用即時字幕"
}

private fun liveCaptionTargetOffsetMs(mode: LiveCaptionMode): Long? = when (mode) {
    LiveCaptionMode.OFF -> null
    else -> liveCaptionTargetDelayMs(mode)
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    filteredChannelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()

    val videoPlayerState =
        rememberVideoPlayerState(defaultDisplayModeProvider = { settingsViewModel.videoPlayerDisplayMode })
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = filteredChannelGroupListProvider,
    )
    val channelNumberSelectState = rememberChannelNumberSelectState {
        val idx = it.toInt() - 1
        filteredChannelGroupListProvider().channelList.getOrNull(idx)?.let { channel ->
            mainContentState.changeCurrentChannel(channel)
        }
    }
    val pendingSelectJob = remember { mutableStateOf<Job?>(null) }
    val liveCaptionSession = remember { LiveCaptionSession() }
    val liveCaptionSessionState by liveCaptionSession.state.collectAsState()
    val accountState by AulamaAccount.manager.state.collectAsState()
    val selectedLiveCaptionMode = remember { mutableStateOf(Configs.liveCaptionMode) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveCaptionLifecycle = remember(lifecycleOwner) {
        mutableStateOf(
            LiveCaptionLifecycleSyncState(
                foreground = lifecycleOwner.lifecycle.currentState
                    .isAtLeast(Lifecycle.State.RESUMED),
            )
        )
    }

    val captionChannel = mainContentState.currentChannel
    val captionRoute = captionChannel.routes.getOrNull(mainContentState.currentChannelUrlIdx)
    val liveCaptionAvailable = isLiveCaptionChannel(captionChannel) &&
        captionRoute?.captionRouteId?.isNotBlank() == true
    val captionAccessMessage = liveCaptionAccessMessage(accountState)
    val effectiveCaptionMode = selectedLiveCaptionMode.value.takeIf {
        liveCaptionAvailable && captionAccessMessage.isBlank()
    } ?: LiveCaptionMode.OFF
    val captionTargetOffsetMs = liveCaptionTargetOffsetMs(effectiveCaptionMode)

    LaunchedEffect(effectiveCaptionMode) {
        // Language-only changes at the same playback delay stay on the existing socket.
        liveCaptionSession.setMode(effectiveCaptionMode)
    }

    LaunchedEffect(
        captionTargetOffsetMs,
        captionChannel.captionChannelId,
        captionRoute?.captionRouteId,
        videoPlayerState.hasRenderedFirstFrame,
        liveCaptionLifecycle.value.foreground,
        liveCaptionLifecycle.value.resumeGeneration,
    ) {
        val targetOffsetMs = captionTargetOffsetMs
        val resumeGeneration = liveCaptionLifecycle.value.resumeGeneration
        liveCaptionLifecycle.value = liveCaptionLifecycle.value.copy(synchronized = false)
        if (targetOffsetMs == null) {
            videoPlayerState.setLiveCaptionOffsetTargetMs(null)
            return@LaunchedEffect
        }
        if (!liveCaptionLifecycle.value.foreground) return@LaunchedEffect
        if (!videoPlayerState.hasRenderedFirstFrame) {
            return@LaunchedEffect
        }
        var synchronized = videoPlayerState.setLiveCaptionOffsetTargetMs(targetOffsetMs)
        if (videoPlayerState.hasRenderedFirstFrame) {
            for (attempt in 1..LIVE_CAPTION_SYNC_RETRY_COUNT) {
                if (synchronized) break
                delay(LIVE_CAPTION_SYNC_RETRY_DELAY_MS)
                if (!videoPlayerState.hasRenderedFirstFrame) return@LaunchedEffect
                synchronized = videoPlayerState.setLiveCaptionOffsetTargetMs(targetOffsetMs)
            }
        }
        liveCaptionLifecycle.value = liveCaptionLifecycle.value.withVerifiedOffset(
            verified = synchronized,
            expectedResumeGeneration = resumeGeneration,
        )
        if (
            videoPlayerState.hasRenderedFirstFrame &&
            !synchronized
        ) {
            liveCaptionSession.stop()
            selectedLiveCaptionMode.value = LiveCaptionMode.OFF
            Configs.liveCaptionMode = LiveCaptionMode.OFF
            Snackbar.show("目前播放方式未能同步即時字幕")
        }
    }

    LaunchedEffect(
        captionChannel.captionChannelId,
        captionRoute?.captionRouteId,
        videoPlayerState.hasRenderedFirstFrame,
        effectiveCaptionMode != LiveCaptionMode.OFF,
        liveCaptionLifecycle.value.synchronized,
        liveCaptionLifecycle.value.foreground,
    ) {
        if (
            effectiveCaptionMode == LiveCaptionMode.OFF ||
            captionRoute == null ||
            !videoPlayerState.hasRenderedFirstFrame ||
            !liveCaptionLifecycle.value.synchronized ||
            !liveCaptionLifecycle.value.foreground
        ) {
            liveCaptionSession.stop()
        } else {
            liveCaptionSession.start(captionChannel, captionRoute, effectiveCaptionMode)
        }
    }

    LaunchedEffect(liveCaptionSessionState.errorCode) {
        if (liveCaptionSessionState.errorCode == "caption_quota_exhausted") {
            selectedLiveCaptionMode.value = LiveCaptionMode.OFF
            Configs.liveCaptionMode = LiveCaptionMode.OFF
            Snackbar.show("今日 120 分鐘即時字幕額度已用完")
        }
    }

    DisposableEffect(liveCaptionSession) {
        onDispose {
            liveCaptionSession.close()
        }
    }

    val hasBlockingOverlay = mainContentState.isChannelScreenVisible ||
        mainContentState.isSettingsScreenVisible ||
        mainContentState.isVideoPlayerControllerScreenVisible ||
        mainContentState.isQuickOpScreenVisible ||
        mainContentState.isEpgScreenVisible ||
        mainContentState.isChannelUrlScreenVisible ||
        mainContentState.isVideoPlayerDisplayModeScreenVisible ||
        mainContentState.isLiveCaptionModeScreenVisible ||
        videoPlayerState.hasTerminalRetry

    // 監聽生命週期：從後台回到前台時立即刷新當前頻道
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mainContentState.refreshCurrentChannel()
                Lifecycle.Event.ON_RESUME -> {
                    liveCaptionSession.stop()
                    liveCaptionLifecycle.value = reduceLiveCaptionLifecycle(
                        liveCaptionLifecycle.value,
                        LiveCaptionLifecycleEvent.RESUME,
                    )
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    // Stop synchronously at the lifecycle boundary; the next RESUME must
                    // re-verify Media3's actual offset before opening a new WebSocket.
                    liveCaptionSession.stop()
                    liveCaptionLifecycle.value = reduceLiveCaptionLifecycle(
                        liveCaptionLifecycle.value,
                        LiveCaptionLifecycleEvent.PAUSE_OR_STOP,
                    )
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingSelectJob.value?.cancel()
        }
    }

    val globalInputModifier = if (hasBlockingOverlay) {
        Modifier
    } else {
        Modifier
            .captureBackKey {
                if(mainContentState.currentPlaybackEpgProgramme!=null){
                    mainContentState.changeCurrentChannel(mainContentState.currentChannel)
                }
                else onBackPressed()
            }
            .handleKeyEvents(
                onUp = {
                    if (!mainContentState.isChannelUrlScreenVisible) {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                        else mainContentState.changeCurrentChannelToPrev()
                    }
                },
                onDown = {
                    if (!mainContentState.isChannelUrlScreenVisible) {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                        else mainContentState.changeCurrentChannelToNext()
                    }
                },
                onSelect = {
                    if (!mainContentState.isChannelUrlScreenVisible) {
                        val pendingJob = pendingSelectJob.value
                        if (pendingJob?.isActive == true) {
                            pendingJob.cancel()
                            pendingSelectJob.value = null
                            if (!mainContentState.changeCurrentChannelToNextRoute()) {
                                mainContentState.isChannelScreenVisible = true
                            }
                        } else {
                            pendingSelectJob.value = coroutineScope.launch {
                                delay(SELECT_DOUBLE_PRESS_WINDOW_MS)
                                if (!mainContentState.isChannelUrlScreenVisible) {
                                    mainContentState.isChannelScreenVisible = true
                                }
                                pendingSelectJob.value = null
                            }
                        }
                    }
                },
                onLongSelect = {
                    pendingSelectJob.value?.cancel()
                    pendingSelectJob.value = null
                    mainContentState.isQuickOpScreenVisible = true
                },
                onSettings = { mainContentState.isQuickOpScreenVisible = true },
                onLongLeft = { mainContentState.isEpgScreenVisible = true },
                onLongRight = { mainContentState.isChannelUrlScreenVisible = true },
                onLongDown = { mainContentState.isVideoPlayerControllerScreenVisible = true },
                onNumber = { channelNumberSelectState.input(it) },
            )
            .handleDragGestures(
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
            )
    }

    Box(
        modifier = modifier
            .popupable()
            .then(globalInputModifier),
    ) {
        VideoPlayerScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        )

        LiveCaptionOverlay(
            modeProvider = { effectiveCaptionMode },
            englishProvider = { liveCaptionSessionState.visibleCue?.english.orEmpty() },
            traditionalChineseProvider = { liveCaptionSessionState.visibleCue?.zhHant.orEmpty() },
            stateMessageProvider = { liveCaptionSessionState.message.orEmpty() },
        )

        Visible({ ChannelUtil.isHybridWebViewUrl(mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx]) }) {
            WebViewScreen(
                urlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
                onVideoResolutionChanged = { width, height ->
                    videoPlayerState.metadata = videoPlayerState.metadata.copy(
                        videoWidth = width,
                        videoHeight = height,
                    )
                    mainContentState.isTempChannelScreenVisible = false
                },
            )
        }
    }

    Visible({
        !mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        DatetimeScreen(showModeProvider = { settingsViewModel.uiTimeShowMode })
    }

    ChannelNumberSelectScreen(channelNumberProvider = { channelNumberSelectState.channelNumber })

    Visible({
        mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        ChannelTempScreen(
            channelProvider = { mainContentState.currentChannel },
            channelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            channelNumberProvider = { filteredChannelGroupListProvider().channelIdx(mainContentState.currentChannel) + 1 },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            recentEpgProgrammeProvider = {
                epgListProvider().recentProgramme(mainContentState.currentChannel)
            },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isEpgScreenVisible },
        onDismissRequest = { mainContentState.isEpgScreenVisible = false },
    ) {
        EpgScreen(
            epgProvider = {
                epgListProvider().match(mainContentState.currentChannel)
                    ?: Epg.empty(mainContentState.currentChannel)
            },
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList.filter {
                    it.channel == mainContentState.currentChannel.name
                })
            },
            supportPlaybackProvider = { mainContentState.supportPlayback() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = {
                mainContentState.isEpgScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannelUrlIdx,
                    it,
                )
            },
            onEpgProgrammeReserve = { programme ->
                mainContentState.reverseEpgProgrammeOrNot(
                    mainContentState.currentChannel,
                    programme
                )
            },
            onClose = { mainContentState.isEpgScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelUrlScreenVisible },
        onDismissRequest = { mainContentState.isChannelUrlScreenVisible = false },
    ) {
        ChannelUrlScreen(
            channelProvider = { mainContentState.currentChannel },
            currentUrlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
            isSuperAdminProvider = { AulamaAccount.manager.isSuperAdmin() },
            transportPreferenceIdProvider = {
                mainContentState.playbackTransportPreferenceId
            },
            onTransportPreferenceSelected = {
                mainContentState.isChannelUrlScreenVisible = false
                mainContentState.changePlaybackTransportPreference(it)
            },
            onUrlSelected = {
                mainContentState.isChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.urlList.indexOf(it),
                )
            },
            onClose = { mainContentState.isChannelUrlScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerControllerScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerControllerScreenVisible = false },
    ) {
        val threshold = 1000L * 60 * 60 * 24 * 365
        val hour0 = -28800000L

        VideoPlayerControllerScreen(
            isVideoPlayerPlayingProvider = { videoPlayerState.isPlaying },
            isVideoPlayerBufferingProvider = { videoPlayerState.isBuffering },
            videoPlayerCurrentPositionProvider = {
                if (videoPlayerState.currentPosition >= threshold) videoPlayerState.currentPosition
                else hour0 + videoPlayerState.currentPosition
            },
            videoPlayerDurationProvider = {
                if (videoPlayerState.currentPosition >= threshold) {
                    val playback = mainContentState.currentPlaybackEpgProgramme

                    if (playback != null) {
                        playback.startAt to playback.endAt
                    } else {
                        val programme =
                            epgListProvider().recentProgramme(mainContentState.currentChannel)?.now
                        (programme?.startAt ?: hour0) to (programme?.endAt ?: hour0)
                    }
                } else {
                    hour0 to (hour0 + videoPlayerState.duration)
                }
            },
            onVideoPlayerPlay = { videoPlayerState.play() },
            onVideoPlayerPause = { videoPlayerState.pause() },
            onVideoPlayerSeekTo = { videoPlayerState.seekTo(it) },
            onClose = { mainContentState.isVideoPlayerControllerScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerDisplayModeScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
    ) {
        VideoPlayerDisplayModeScreen(
            currentDisplayModeProvider = { videoPlayerState.displayMode },
            onDisplayModeChanged = { videoPlayerState.displayMode = it },
            onApplyToGlobal = {
                mainContentState.isVideoPlayerDisplayModeScreenVisible = false
                settingsViewModel.videoPlayerDisplayMode = videoPlayerState.displayMode
                Snackbar.show("已應用到全局")
            },
            onClose = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isQuickOpScreenVisible },
        onDismissRequest = { mainContentState.isQuickOpScreenVisible = false },
    ) {
        QuickOpScreen(
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            currentChannelNumberProvider = {
                (filteredChannelGroupListProvider().channelList.indexOf(mainContentState.currentChannel) + 1).toString()
            },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            epgListProvider = epgListProvider,
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onShowEpg = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isEpgScreenVisible = true
            },
            onShowChannelUrl = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isChannelUrlScreenVisible = true
            },
            onShowVideoPlayerController = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerControllerScreenVisible = true
            },
            onShowVideoPlayerDisplayMode = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerDisplayModeScreenVisible = true
            },
            showLiveCaptionProvider = { liveCaptionAvailable },
            onShowLiveCaption = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isLiveCaptionModeScreenVisible = true
            },
            onShowMoreSettings = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isSettingsScreenVisible = true
            },
            onClearCache = {
                settingsViewModel.iptvPlayableHostList = emptySet()
                coroutineScope.launch {
                    IptvRepository(settingsViewModel.iptvSourceCurrent).clearCache()
                    EpgRepository(settingsViewModel.epgSourceCurrent).clearCache()
                    Snackbar.show("緩存已清除，請重啓應用")
                }
            },
            onClose = { mainContentState.isQuickOpScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isLiveCaptionModeScreenVisible },
        onDismissRequest = { mainContentState.isLiveCaptionModeScreenVisible = false },
    ) {
        LiveCaptionModeScreen(
            currentModeProvider = { selectedLiveCaptionMode.value },
            statusMessageProvider = { liveCaptionSessionState.message.orEmpty() },
            accessMessageProvider = { captionAccessMessage },
            onModeSelected = { mode ->
                if (mode != LiveCaptionMode.OFF && captionAccessMessage.isNotBlank()) {
                    Snackbar.show(captionAccessMessage)
                } else {
                    selectedLiveCaptionMode.value = mode
                    Configs.liveCaptionMode = mode
                    mainContentState.isLiveCaptionModeScreenVisible = false
                }
            },
            onClose = { mainContentState.isLiveCaptionModeScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && !settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ClassicChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            favoriteChannelListProvider = {
                val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
                ChannelList(filteredChannelGroupListProvider().channelList
                    .filter { favoriteChannelNameList.contains(it.name) })
            },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList)
            },
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            supportPlaybackProvider = { mainContentState.supportPlayback(it, null) },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = { channel, programme ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(channel, null, programme)
            },
            onEpgProgrammeReserve = { channel, programme ->
                mainContentState.reverseEpgProgrammeOrNot(channel, programme)
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isSettingsScreenVisible },
        onDismissRequest = { mainContentState.isSettingsScreenVisible = false },
    ) {
        SettingsScreen(
            channelGroupListProvider = channelGroupListProvider,
            onClose = { mainContentState.isSettingsScreenVisible = false },
        )
    }

    EpgReverseScreen(
        epgProgrammeReserveListProvider = { settingsViewModel.epgChannelReserveList },
        onConfirmReserve = { reserve ->
            filteredChannelGroupListProvider().channelList.firstOrNull { it.name == reserve.channel }
                ?.let {
                    mainContentState.changeCurrentChannel(it)
                }
        },
        onDeleteReserve = { reserve ->
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reserve)
        },
    )

    UpdateScreen()

    Visible({ settingsViewModel.debugShowFps }) { MonitorScreen() }
}

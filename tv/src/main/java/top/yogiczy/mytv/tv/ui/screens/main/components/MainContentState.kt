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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine   // 1. 新增
import androidx.compose.ui.platform.LocalContext          // 2. 新增
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
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
import top.yogiczy.mytv.tv.ui.utils.IptvRouteHealthStore
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
    private var routeStartedAt = 0L
    private var lastFailureHandledUrl: String? = null

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
            IptvRouteHealthStore.markSuccess(
                route.url,
                System.currentTimeMillis() - routeStartedAt,
            )
            settingsViewModel.iptvPlayableHostList += getUrlHost(route.url)
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

        videoPlayerState.onError {
            val failedRoute = currentRouteOrNull() ?: return@onError
            if (lastFailureHandledUrl == failedRoute.url) return@onError
            lastFailureHandledUrl = failedRoute.url
            IptvRouteHealthStore.markFailure(failedRoute.url)
            settingsViewModel.iptvPlayableHostList -= getUrlHost(failedRoute.url)

            if (_currentPlaybackEpgProgramme != null) {
                // 回放播放錯誤時先返回同一頻道直播，再按線路健康度回退。
                changeCurrentChannel(_currentChannel, _currentChannelUrlIdx, null)
                return@onError
            }

            playNextRoute()
        }

        videoPlayerState.onInterrupt {
            currentRouteOrNull()?.let {
                IptvRouteHealthStore.markFailure(it.url)
                settingsViewModel.iptvPlayableHostList -= getUrlHost(it.url)
            }
            if (!playNextRoute()) prepareCurrentRoute()
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

    private fun buildRouteAttemptOrder(
        channel: Channel,
        requestedIndex: Int?,
    ): List<Int> {
        if (channel.routes.isEmpty()) return emptyList()
        val remembered = getUrlIdx(channel.urlList, requestedIndex)
        val ranked = IptvRouteHealthStore.rankedIndices(channel.routes, remembered)

        // 明確手動揀線時先尊重該線；自動換台則固定畫質優先（4K > 1080p）。
        return if (requestedIndex != null) {
            listOf(remembered) + ranked.filterNot { it == remembered }
        } else {
            ranked
        }
    }

    private fun prepareCurrentRoute() {
        val baseRoute = currentRouteOrNull() ?: return
        var route = baseRoute

        _currentPlaybackEpgProgramme?.let { programme ->
            val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val query = "playseek=${timeFormat.format(programme.startAt)}-${timeFormat.format(programme.endAt)}"
            val playbackUrl = if (URI(baseRoute.url).query.isNullOrBlank()) {
                "${baseRoute.url}?$query"
            } else {
                "${baseRoute.url}&$query"
            }
            route = baseRoute.copy(url = playbackUrl)
        }

        _isTempChannelScreenVisible = true
        routeStartedAt = System.currentTimeMillis()
        lastFailureHandledUrl = null
        log.d(
            "播放${_currentChannel.name}（${baseRoute.quality.label}，自動線路${_currentChannelUrlIdx + 1}/${_currentChannel.routes.size}）: ${route.url}"
        )

        if (ChannelUtil.isHybridWebViewUrl(route.url)) {
            videoPlayerState.stop()
        } else {
            videoPlayerState.prepare(route)
        }
    }

    private fun playNextRoute(): Boolean {
        if (routeAttemptCursor + 1 >= routeAttemptOrder.size) return false
        routeAttemptCursor += 1
        _currentChannelUrlIdx = routeAttemptOrder[routeAttemptCursor]
        prepareCurrentRoute()
        return true
    }

    fun changeCurrentChannel(
        channel: Channel,
        urlIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        if (channel.routes.isEmpty()) return
        if (
            channel == _currentChannel &&
            urlIdx != null &&
            getUrlIdx(channel.urlList, urlIdx) == _currentChannelUrlIdx &&
            playbackEpgProgramme == _currentPlaybackEpgProgramme
        ) return

        _currentChannel = channel
        settingsViewModel.iptvLastChannelIdx =
            channelGroupListProvider().channelIdx(_currentChannel)
        _currentPlaybackEpgProgramme = playbackEpgProgramme
        routeAttemptOrder = buildRouteAttemptOrder(channel, urlIdx)
        routeAttemptCursor = 0
        _currentChannelUrlIdx = routeAttemptOrder.firstOrNull() ?: return
        prepareCurrentRoute()
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun refreshCurrentChannel() {
        prepareCurrentRoute()
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

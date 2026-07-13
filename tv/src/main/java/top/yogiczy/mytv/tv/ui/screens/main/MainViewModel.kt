package top.yogiczy.mytv.tv.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Add property to store last EPG update date
    private var lastEpgUpdateDate: LocalDate? = null

    init {
        init()
    }

    fun init() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading()
            ChannelUtil.loadHybridWebViewUrlFromRemote(Constants.WEBVIEW_CHANNELS_URL)
            refreshChannel()
            refreshEpg()
        }
    }

    // Add method to handle app resume
    fun onAppResume() {
        viewModelScope.launch {
            val currentDate = LocalDate.now()
            if (lastEpgUpdateDate != null && lastEpgUpdateDate != currentDate) {
                // Date changed, refresh EPG
                refreshEpg()
            }
        }
    }
    private fun onChannelChanged() {
        viewModelScope.launch {
            Configs.iptvChannelUrlIdx= emptyMap()
        }
    }

    private suspend fun refreshChannel() {
        flow {
            emit(loadChannelGroupList())
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    MainUiState.Loading("獲取遠程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                _uiState.value = MainUiState.Error(it.message)
            }
            .map { hybridChannel(it) }
            .map {
                _uiState.value = MainUiState.Ready(channelGroupList = it)
                it
            }
            .collect()
    }

    private suspend fun loadChannelGroupList(): ChannelGroupList {
        val currentSource = Configs.iptvSourceCurrent
        if (!Constants.isAulamaManagedSource(currentSource)) {
            return loadSource(currentSource)
        }

        val regionResults = supervisorScope {
            Constants.AULAMA_REGION_SOURCE_LIST.map { (region, source) ->
                async { runCatching { region to loadSource(source) } }
            }.awaitAll()
        }
        val loadedRegions = regionResults.mapNotNull { it.getOrNull() }
        if (loadedRegions.isEmpty()) {
            throw regionResults.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("暫時未能取得頻道清單")
        }

        var channelId = 0
        return ChannelGroupList(loadedRegions.mapNotNull { (region, groupList) ->
            val channels = groupList.channelList
            if (channels.isEmpty()) return@mapNotNull null
            ChannelGroup(
                name = region,
                channelList = ChannelList(channels.map { channel ->
                    channel.copy(id = (++channelId).toString())
                }),
            )
        })
    }

    private suspend fun loadSource(source: IptvSource): ChannelGroupList {
        val repository = IptvRepository(source)
        repository.setDataChanged { onChannelChanged() }
        return repository.getChannelGroupList(cacheTime = Configs.iptvSourceCacheTime)
    }

    private suspend fun hybridChannel(channelGroupList: ChannelGroupList) =
        withContext(Dispatchers.Default) {
            val hybridMode = Configs.iptvHybridMode
            return@withContext when (hybridMode) {
                Configs.IptvHybridMode.DISABLE -> channelGroupList
                Configs.IptvHybridMode.IPTV_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                routes = channel.routes.plus(
                                    (ChannelUtil.getHybridWebViewUrl(channel.name) ?: emptyList())
                                        .mapIndexed { index, url -> ChannelRoute(url, sourceOrder = channel.routes.size + index) }
                                )
                            )
                        }))
                    })
                }

                Configs.IptvHybridMode.HYBRID_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                routes = (ChannelUtil.getHybridWebViewUrl(channel.name) ?: emptyList())
                                    .mapIndexed { index, url -> ChannelRoute(url, sourceOrder = index) }
                                    .plus(channel.routes)
                            )
                        }))
                    })
                }
            }
        }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return

        if (_uiState.value is MainUiState.Ready) {
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList

            flow {
                emit(
                    EpgRepository(Configs.epgSourceCurrent).getEpgList(
                        filteredChannels = channelGroupList.channelList.map { it.epgName },
                        refreshTimeThreshold = Configs.epgRefreshTimeThreshold,
                    )
                )
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    Snackbar.show("節目單獲取失敗，請檢查網絡連接", type = SnackbarType.ERROR)
                }
                .map { epgList ->
                    // Record current date when EPG is successfully updated
                    lastEpgUpdateDate = LocalDate.now()
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }
}

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList(),
    ) : MainUiState
}

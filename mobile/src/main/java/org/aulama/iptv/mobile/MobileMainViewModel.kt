package org.aulama.iptv.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants

data class RegionChannels(
    val name: String,
    val channels: List<Channel>,
)

sealed interface MobileUiState {
    data object Loading : MobileUiState
    data class Ready(val regions: List<RegionChannels>) : MobileUiState
    data class Error(val message: String) : MobileUiState
}

data class SelectedChannel(
    val region: String,
    val channel: Channel,
)

class MobileMainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("aulama_tv_mobile", 0)

    private val _uiState = MutableStateFlow<MobileUiState>(MobileUiState.Loading)
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    private val _selectedRegion = MutableStateFlow(
        preferences.getString(KEY_LAST_REGION, "香港") ?: "香港"
    )
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _selectedChannel = MutableStateFlow<SelectedChannel?>(null)
    val selectedChannel: StateFlow<SelectedChannel?> = _selectedChannel.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _favorites = MutableStateFlow(
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _darkTheme = MutableStateFlow(preferences.getBoolean(KEY_DARK_THEME, true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MobileUiState.Loading

            val results = supervisorScope {
                Constants.AULAMA_REGION_SOURCE_LIST.map { (region, source) ->
                    async {
                        runCatching {
                            val channels = IptvRepository(source)
                                .getChannelGroupList(cacheTime = 0L)
                                .channelList
                                .mapIndexed { index, channel ->
                                    channel.copy(id = "$region-${index + 1}")
                                }
                            RegionChannels(region, channels)
                        }
                    }
                }.awaitAll()
            }

            val loaded = results.mapNotNull(Result<RegionChannels>::getOrNull)
            if (loaded.isEmpty()) {
                _uiState.value = MobileUiState.Error(
                    results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                        ?: "暫時未能取得頻道清單"
                )
                return@launch
            }

            _uiState.value = MobileUiState.Ready(loaded)
            restoreSelection(loaded)
        }
    }

    private fun restoreSelection(regions: List<RegionChannels>) {
        val savedRegion = _selectedRegion.value
        val region = regions.firstOrNull { it.name == savedRegion } ?: regions.first()
        val savedChannel = preferences.getString(KEY_LAST_CHANNEL, null)
        val channel = region.channels.firstOrNull { it.name == savedChannel }
            ?: region.channels.firstOrNull()

        _selectedRegion.value = region.name
        channel?.let { selectChannel(region.name, it) }
    }

    fun selectRegion(region: String) {
        _selectedRegion.value = region
        preferences.edit().putString(KEY_LAST_REGION, region).apply()

        val current = _selectedChannel.value
        if (current?.region == region) return

        val ready = _uiState.value as? MobileUiState.Ready ?: return
        ready.regions.firstOrNull { it.name == region }
            ?.channels
            ?.firstOrNull()
            ?.let { selectChannel(region, it) }
    }

    fun selectChannel(region: String, channel: Channel) {
        _selectedRegion.value = region
        _selectedChannel.value = SelectedChannel(region, channel)
        _selectedRouteIndex.value = 0
        preferences.edit()
            .putString(KEY_LAST_REGION, region)
            .putString(KEY_LAST_CHANNEL, channel.name)
            .apply()
    }

    fun selectRoute(index: Int) {
        val routeCount = _selectedChannel.value?.channel?.routes?.size ?: return
        if (index in 0 until routeCount) _selectedRouteIndex.value = index
    }

    fun tryNextRoute(): Boolean {
        val routeCount = _selectedChannel.value?.channel?.routes?.size ?: return false
        val next = _selectedRouteIndex.value + 1
        if (next >= routeCount) return false
        _selectedRouteIndex.value = next
        return true
    }

    fun updateSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    fun favoriteKey(region: String, channel: Channel) = "$region|${channel.name}"

    fun toggleFavorite(region: String, channel: Channel) {
        val key = favoriteKey(region, channel)
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        _favorites.value = updated
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun toggleTheme() {
        val updated = !_darkTheme.value
        _darkTheme.value = updated
        preferences.edit().putBoolean(KEY_DARK_THEME, updated).apply()
    }

    companion object {
        private const val KEY_LAST_REGION = "last_region"
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}

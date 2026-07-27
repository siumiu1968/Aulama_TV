package org.aulama.iptv.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.aulama.iptv.mobile.data.auth.AulamaAccountManager
import org.aulama.iptv.mobile.data.auth.AulamaAccountState
import org.aulama.iptv.mobile.data.auth.AulamaApiException
import org.aulama.iptv.mobile.data.auth.AulamaAuthApi
import org.aulama.iptv.mobile.data.auth.AulamaSyncManager
import org.aulama.iptv.mobile.data.auth.CredentialAttempt
import org.aulama.iptv.mobile.data.auth.CredentialManagerAuthClient
import org.aulama.iptv.mobile.data.auth.FileLocalSyncStore
import org.aulama.iptv.mobile.data.auth.RefreshTokenStoreFactory
import org.aulama.iptv.mobile.data.auth.SyncPayload
import org.aulama.iptv.mobile.data.playback.PlaybackCandidate
import org.aulama.iptv.mobile.data.playback.PlaybackHealthSample
import org.aulama.iptv.mobile.data.playback.AutomaticFallbackPolicy
import org.aulama.iptv.mobile.data.playback.DevicePlaybackCapabilities
import org.aulama.iptv.mobile.data.playback.PlaybackPlanPolicy
import org.aulama.iptv.mobile.data.playback.RouteHealthStore
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants
import java.io.IOException

data class RegionChannels(
    val name: String,
    val channels: List<Channel>,
)

sealed interface MobileUiState {
    data object Loading : MobileUiState
    data class Ready(val regions: List<RegionChannels>) : MobileUiState
    data class Error(val message: String) : MobileUiState
}

data class MobileEpgState(
    val loading: Boolean = false,
    val error: String? = null,
)

data class SelectedChannel(
    val region: String,
    val channel: Channel,
)

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Syncing : SyncUiState
    data class Synced(val revision: Long) : SyncUiState
    data class Unavailable(val message: String) : SyncUiState
}

sealed interface PairingApprovalState {
    data object Idle : PairingApprovalState
    data object Approving : PairingApprovalState
    data object Approved : PairingApprovalState
    data class Failed(val message: String, val configurationMissing: Boolean = false) : PairingApprovalState
}

class MobileMainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("aulama_tv_mobile", 0)
    private val accountManager = AulamaAccountManager(
        gateway = AulamaAuthApi(),
        refreshTokenStore = RefreshTokenStoreFactory.create(application),
        appVersion = BuildConfig.VERSION_NAME,
    )
    private val localSyncStore = FileLocalSyncStore(application)
    private val syncManager = AulamaSyncManager(accountManager, localSyncStore)
    private val healthStore = RouteHealthStore(application)

    val accountState: StateFlow<AulamaAccountState> = accountManager.state

    private val _uiState = MutableStateFlow<MobileUiState>(MobileUiState.Loading)
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    private val _epgList = MutableStateFlow(EpgList())
    val epgList: StateFlow<EpgList> = _epgList.asStateFlow()

    private val _epgState = MutableStateFlow(MobileEpgState())
    val epgState: StateFlow<MobileEpgState> = _epgState.asStateFlow()

    private val _selectedRegion = MutableStateFlow(
        preferences.getString(KEY_LAST_REGION, "香港") ?: "香港"
    )
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _selectedChannel = MutableStateFlow<SelectedChannel?>(null)
    val selectedChannel: StateFlow<SelectedChannel?> = _selectedChannel.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    private val _playbackCandidates = MutableStateFlow<List<PlaybackCandidate>>(emptyList())
    val playbackCandidates: StateFlow<List<PlaybackCandidate>> = _playbackCandidates.asStateFlow()

    private val _selectedCandidateIndex = MutableStateFlow(0)
    val selectedCandidateIndex: StateFlow<Int> = _selectedCandidateIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val initialSync = localSyncStore.load()
    private val _syncPayload = MutableStateFlow(initialSync.payload)
    val syncPayload: StateFlow<SyncPayload> = _syncPayload.asStateFlow()

    private val _favorites = MutableStateFlow(
        (
            preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty() +
                initialSync.payload.favorites
            ).toSet()
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _pairingState = MutableStateFlow<PairingApprovalState>(PairingApprovalState.Idle)
    val pairingState: StateFlow<PairingApprovalState> = _pairingState.asStateFlow()

    private val _darkTheme = MutableStateFlow(preferences.getBoolean(KEY_DARK_THEME, true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(
        preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private var syncPushJob: Job? = null
    private var playbackPlanJob: Job? = null
    private var epgRefreshJob: Job? = null
    private var playbackPlanGeneration = 0L
    private var lastReconciledAccountId: String? = null
    private val fourKCapable = DevicePlaybackCapabilities.supportsHevc4k()

    init {
        refresh()
        viewModelScope.launch { accountManager.restoreSession() }
        viewModelScope.launch {
            accountState.collect { state ->
                val signedIn = state as? AulamaAccountState.SignedIn
                if (signedIn == null) {
                    lastReconciledAccountId = null
                    rebuildPlaybackPlan()
                } else if (lastReconciledAccountId != signedIn.profile.id) {
                    lastReconciledAccountId = signedIn.profile.id
                    reconcileSync()
                } else {
                    rebuildPlaybackPlan()
                }
            }
        }
    }

    fun refresh() {
        epgRefreshJob?.cancel()
        _epgState.value = _epgState.value.copy(loading = false)
        viewModelScope.launch {
            _uiState.value = MobileUiState.Loading
            val builtInSources = Constants.AULAMA_REGION_SOURCE_LIST
            val customSources = _syncPayload.value.customSources
                .filter { it.deletedAt == null }
                .map { it.name to IptvSource(name = it.name, url = it.url) }
            val sources = (builtInSources + customSources)
                .distinctBy { (_, source) -> source.url }

            val results = supervisorScope {
                sources.map { (region, source) ->
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
            refreshEpg(loaded)
        }
    }

    private fun refreshEpg(regions: List<RegionChannels>) {
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            _epgState.value = MobileEpgState(loading = true)
            val requests = buildList {
                val traditionalChannels = regions
                    .filterNot {
                        it.name.contains("中國") || it.name.contains("中国") || it.name.contains("內地")
                    }
                    .flatMap(RegionChannels::channels)
                    .map { it.epgName.ifBlank { it.name } }
                if (traditionalChannels.isNotEmpty()) {
                    add(
                        Triple(
                            Constants.EPG_SOURCE_TRADITIONAL,
                            Constants.EPG_SOURCE_SIMPLIFIED,
                            traditionalChannels,
                        )
                    )
                }

                val simplifiedChannels = regions
                    .filter {
                        it.name.contains("中國") || it.name.contains("中国") || it.name.contains("內地")
                    }
                    .flatMap(RegionChannels::channels)
                    .map { it.epgName.ifBlank { it.name } }
                if (simplifiedChannels.isNotEmpty()) {
                    add(
                        Triple(
                            Constants.EPG_SOURCE_SIMPLIFIED,
                            Constants.EPG_SOURCE_TRADITIONAL,
                            simplifiedChannels,
                        )
                    )
                }
            }

            if (requests.isEmpty()) {
                _epgState.value = MobileEpgState()
                return@launch
            }

            val primaryResults = supervisorScope {
                requests.map { (primarySource, _, channels) ->
                    async {
                        runCatching {
                            EpgRepository(primarySource).getEpgList(
                                filteredChannels = channels,
                                refreshTimeThreshold = Constants.EPG_REFRESH_TIME_THRESHOLD,
                            )
                        }
                    }
                }.awaitAll()
            }

            val results = supervisorScope {
                requests.zip(primaryResults).map { (request, primaryResult) ->
                    async {
                        val primaryEpg = primaryResult.getOrNull()
                        if (primaryEpg != null && primaryEpg.isNotEmpty()) {
                            Result.success(primaryEpg)
                        } else {
                            val (_, fallbackSource, channels) = request
                            runCatching {
                                EpgRepository(fallbackSource).getEpgList(
                                    filteredChannels = channels,
                                    refreshTimeThreshold = Constants.EPG_REFRESH_TIME_THRESHOLD,
                                ).also {
                                    check(it.isNotEmpty()) { "後備節目單未有匹配資料" }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            val available = results.mapNotNull { it.getOrNull() }.filter { it.isNotEmpty() }
            if (available.isEmpty()) {
                _epgState.value = MobileEpgState(
                    error = if (_epgList.value.isEmpty()) {
                        "節目單暫時未能載入"
                    } else {
                        "節目單暫時未能更新，舊資料已保留"
                    },
                )
                return@launch
            }

            val merged = EpgList.merge(*available.toTypedArray())
            if (merged.isEmpty()) {
                _epgState.value = MobileEpgState(
                    error = if (_epgList.value.isEmpty()) {
                        "暫時未有節目單資料"
                    } else {
                        "節目單暫時未能更新，舊資料已保留"
                    },
                )
                return@launch
            }

            _epgList.value = merged
            _epgState.value = MobileEpgState(
                error = if (results.any { it.isFailure }) "部分頻道節目單暫時未能更新" else null,
            )
        }
    }

    private fun restoreSelection(regions: List<RegionChannels>) {
        val region = regions.firstOrNull { it.name == _selectedRegion.value } ?: regions.first()
        val savedChannel = preferences.getString(KEY_LAST_CHANNEL, null)
        val channel = region.channels.firstOrNull { it.name == savedChannel }
            ?: region.channels.firstOrNull()
        _selectedRegion.value = region.name
        channel?.let { selectChannel(region.name, it) }
    }

    fun selectRegion(region: String) {
        _selectedRegion.value = region
        preferences.edit().putString(KEY_LAST_REGION, region).apply()
        if (_selectedChannel.value?.region == region) return
        val ready = _uiState.value as? MobileUiState.Ready ?: return
        ready.regions.firstOrNull { it.name == region }?.channels?.firstOrNull()
            ?.let { selectChannel(region, it) }
    }

    fun selectChannel(region: String, channel: Channel) {
        _selectedRegion.value = region
        _selectedChannel.value = SelectedChannel(region, channel)
        _selectedRouteIndex.value = 0
        _selectedCandidateIndex.value = 0
        rebuildPlaybackPlan()
        preferences.edit()
            .putString(KEY_LAST_REGION, region)
            .putString(KEY_LAST_CHANNEL, channel.name)
            .apply()
    }

    fun selectRoute(index: Int) {
        val selected = _selectedChannel.value ?: return
        val sourceUrl = selected.channel.routes.getOrNull(index)?.url ?: return
        val candidateIndex = _playbackCandidates.value.indexOfFirst { it.sourceUrl == sourceUrl }
        if (candidateIndex >= 0) {
            _selectedRouteIndex.value = index
            _selectedCandidateIndex.value = candidateIndex
        }
    }

    fun tryNextRoute(): Boolean {
        val current = _playbackCandidates.value.getOrNull(_selectedCandidateIndex.value)
            ?: return false
        val candidate = AutomaticFallbackPolicy.choose(
            current = current,
            candidates = _playbackCandidates.value,
            health = healthStore::get,
            nowMs = System.currentTimeMillis(),
            fourKCapable = fourKCapable,
        ) ?: return false
        _selectedCandidateIndex.value = _playbackCandidates.value.indexOf(candidate)
        updateOriginalRouteIndex(candidate.sourceUrl)
        return true
    }

    fun toggleRoutePriority(routeUrl: String) {
        val selected = _selectedChannel.value ?: return
        val key = routePriorityKey(selected.region, selected.channel)
        val current = _syncPayload.value.routePriorities[key].orEmpty()
        val updated = if (routeUrl in current) current - routeUrl else current + routeUrl
        val priorities = _syncPayload.value.routePriorities.toMutableMap().apply {
            if (updated.isEmpty()) remove(key) else put(key, updated)
        }
        updateSyncPayload(_syncPayload.value.copy(routePriorities = priorities))
        rebuildPlaybackPlan()
    }

    fun routePriorityRank(region: String, channel: Channel, routeUrl: String): Int? {
        val routes = _syncPayload.value.routePriorities[routePriorityKey(region, channel)].orEmpty()
        return routes.indexOf(routeUrl).takeIf { it >= 0 }?.plus(1)
    }

    fun recordPlaybackSample(sample: PlaybackHealthSample) {
        healthStore.record(sample)
    }

    fun signInWithGoogle(client: CredentialManagerAuthClient) {
        viewModelScope.launch {
            accountManager.startSignIn("Google")
            try {
                val nonce = accountManager.googleNonce()
                when (
                    val result = client.requestGoogleIdentity(
                        nonce.serverClientId,
                        nonce.value,
                    )
                ) {
                    is CredentialAttempt.GoogleIdentity -> accountManager.completeGoogleSignIn(
                        result.idToken,
                        nonce.value,
                    )
                    CredentialAttempt.Cancelled -> accountManager.cancelSignIn()
                    is CredentialAttempt.Unavailable -> accountManager.credentialFailure(result.reason)
                    is CredentialAttempt.Failed -> accountManager.credentialFailure(result.reason)
                    is CredentialAttempt.PasskeyAssertion -> accountManager.credentialFailure("登入結果類型錯誤")
                }
            } catch (error: Throwable) {
                accountManager.apiFailure(error)
            }
        }
    }

    fun signInWithPasskey(client: CredentialManagerAuthClient) {
        viewModelScope.launch {
            accountManager.startSignIn("Passkey")
            try {
                val request = accountManager.passkeyOptions()
                when (val result = client.requestPasskey(request.requestJson)) {
                    is CredentialAttempt.PasskeyAssertion -> accountManager.completePasskeySignIn(
                        request.requestId,
                        result.responseJson,
                    )
                    CredentialAttempt.Cancelled -> accountManager.cancelSignIn()
                    is CredentialAttempt.Unavailable -> accountManager.credentialFailure(result.reason)
                    is CredentialAttempt.Failed -> accountManager.credentialFailure(result.reason)
                    is CredentialAttempt.GoogleIdentity -> accountManager.credentialFailure("登入結果類型錯誤")
                }
            } catch (error: Throwable) {
                accountManager.apiFailure(error)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountManager.logout()
            _syncState.value = SyncUiState.Idle
            rebuildPlaybackPlan()
        }
    }

    fun approvePairing(code: String) {
        if (accountManager.signedInProfile() == null) {
            _pairingState.value = PairingApprovalState.Failed("請先登入 Aulama ID")
            return
        }
        viewModelScope.launch {
            _pairingState.value = PairingApprovalState.Approving
            try {
                accountManager.approveDevice(code)
                _pairingState.value = PairingApprovalState.Approved
            } catch (_: AulamaApiException.ConfigurationUnavailable) {
                _pairingState.value = PairingApprovalState.Failed(
                    "電視配對後端尚未配置完成。",
                    configurationMissing = true,
                )
            } catch (_: AulamaApiException.Unauthorized) {
                _pairingState.value = PairingApprovalState.Failed("登入已失效，請重新登入。")
            } catch (_: IOException) {
                _pairingState.value = PairingApprovalState.Failed("暫時無法連接配對服務。")
            } catch (_: Throwable) {
                _pairingState.value = PairingApprovalState.Failed("配對未完成，請稍後再試。")
            }
        }
    }

    fun resetPairingState() {
        _pairingState.value = PairingApprovalState.Idle
    }

    fun syncNow() {
        viewModelScope.launch { reconcileSync() }
    }

    private suspend fun reconcileSync() {
        _syncState.value = SyncUiState.Syncing
        try {
            val document = syncManager.reconcile()
            applySyncedDocument(document.payload)
            _syncState.value = SyncUiState.Synced(document.revision)
        } catch (_: AulamaApiException.ConfigurationUnavailable) {
            _syncState.value = SyncUiState.Unavailable("同步後端尚未配置完成")
        } catch (_: Throwable) {
            _syncState.value = SyncUiState.Unavailable("同步暫時中斷，本機資料已保留")
        } finally {
            rebuildPlaybackPlan()
        }
    }

    private fun updateSyncPayload(payload: SyncPayload) {
        _syncPayload.value = payload
        syncManager.saveLocal(payload)
        scheduleSyncPush()
    }

    private fun scheduleSyncPush() {
        if (accountManager.signedInProfile() == null) return
        syncPushJob?.cancel()
        syncPushJob = viewModelScope.launch {
            delay(SYNC_DEBOUNCE_MS)
            _syncState.value = SyncUiState.Syncing
            try {
                val document = syncManager.push(_syncPayload.value)
                applySyncedDocument(document.payload)
                _syncState.value = SyncUiState.Synced(document.revision)
            } catch (_: Throwable) {
                _syncState.value = SyncUiState.Unavailable("同步暫時中斷，本機改動已保留")
            }
        }
    }

    private fun applySyncedDocument(payload: SyncPayload) {
        val sourcesChanged = payload.customSources != _syncPayload.value.customSources
        _syncPayload.value = payload
        _favorites.value = payload.favorites.toSet()
        preferences.edit().putStringSet(KEY_FAVORITES, _favorites.value).apply()
        rebuildPlaybackPlan()
        if (sourcesChanged) refresh()
    }

    private fun rebuildPlaybackPlan() {
        val generation = ++playbackPlanGeneration
        playbackPlanJob?.cancel()
        val selected = _selectedChannel.value ?: run {
            _playbackCandidates.value = emptyList()
            return
        }
        if (accountState.value is AulamaAccountState.Restoring) {
            _playbackCandidates.value = emptyList()
            return
        }
        val key = routePriorityKey(selected.region, selected.channel)
        val manualPriorities = _syncPayload.value.routePriorities[key].orEmpty()
        val profile = accountManager.signedInProfile()
        val token = accountManager.currentAccessTokenForPlayback()
        if (profile?.isSuperAdmin != true || token.isNullOrBlank()) {
            applyPlaybackPlan(
                selected = selected,
                candidates = PlaybackPlanPolicy.rank(
                    routes = selected.channel.routes,
                    manualPriorityUrls = manualPriorities,
                    relayPlans = emptyMap(),
                    superAdmin = false,
                    accessToken = null,
                    fourKCapable = fourKCapable,
                    health = healthStore::get,
                    nowMs = System.currentTimeMillis(),
                ),
            )
            return
        }

        _playbackCandidates.value = emptyList()
        _selectedCandidateIndex.value = 0
        playbackPlanJob = viewModelScope.launch {
            val plans = supervisorScope {
                selected.channel.routes.map { route ->
                    async {
                        route.url to runCatching {
                            accountManager.relayPlan(
                                routeUrl = route.url,
                                referrer = route.referrer,
                                userAgent = route.userAgent,
                            )
                        }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().toMap()
            }
            if (generation != playbackPlanGeneration || _selectedChannel.value != selected) return@launch
            applyPlaybackPlan(
                selected = selected,
                candidates = PlaybackPlanPolicy.rank(
                    routes = selected.channel.routes,
                    manualPriorityUrls = manualPriorities,
                    relayPlans = plans,
                    superAdmin = true,
                    accessToken = accountManager.currentAccessTokenForPlayback(),
                    fourKCapable = fourKCapable,
                    health = healthStore::get,
                    nowMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun applyPlaybackPlan(
        selected: SelectedChannel,
        candidates: List<PlaybackCandidate>,
    ) {
        if (_selectedChannel.value != selected) return
        val current = _playbackCandidates.value.getOrNull(_selectedCandidateIndex.value)
        _playbackCandidates.value = candidates
        _selectedCandidateIndex.value = current?.let { previous ->
            candidates.indexOfFirst { it.key == previous.key }
                .takeIf { it >= 0 }
                ?: candidates.indexOfFirst { it.sourceUrl == previous.sourceUrl }
                    .takeIf { it >= 0 }
        } ?: 0
        candidates.getOrNull(_selectedCandidateIndex.value)?.let {
            updateOriginalRouteIndex(it.sourceUrl)
        }
    }

    private fun updateOriginalRouteIndex(sourceUrl: String) {
        val index = _selectedChannel.value?.channel?.routes?.indexOfFirst { it.url == sourceUrl } ?: -1
        if (index >= 0) _selectedRouteIndex.value = index
    }

    fun updateSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun toggleFavoritesOnly() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    fun favoriteKey(region: String, channel: Channel) = "$region|${channel.name}"

    fun routePriorityKey(region: String, channel: Channel): String =
        "$region|${channel.epgName.ifBlank { channel.name }}"

    fun toggleFavorite(region: String, channel: Channel) {
        val key = favoriteKey(region, channel)
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        _favorites.value = updated
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
        updateSyncPayload(_syncPayload.value.copy(favorites = updated.sorted()))
    }

    fun toggleTheme() {
        val updated = !_darkTheme.value
        _darkTheme.value = updated
        preferences.edit().putBoolean(KEY_DARK_THEME, updated).apply()
    }

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    override fun onCleared() {
        playbackPlanJob?.cancel()
        epgRefreshJob?.cancel()
        accountManager.close()
        super.onCleared()
    }

    private companion object {
        const val KEY_LAST_REGION = "last_region"
        const val KEY_LAST_CHANNEL = "last_channel"
        const val KEY_FAVORITES = "favorites"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val SYNC_DEBOUNCE_MS = 700L
    }
}

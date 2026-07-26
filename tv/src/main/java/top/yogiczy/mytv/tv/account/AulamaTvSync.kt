package top.yogiczy.mytv.tv.account

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.IptvRoutePriorityStore
import java.net.URI
import java.security.MessageDigest

internal sealed interface AulamaSyncState {
    data object Idle : AulamaSyncState
    data object Syncing : AulamaSyncState
    data class Synced(val revision: Long) : AulamaSyncState
    data class Deferred(val message: String) : AulamaSyncState
}

internal object AulamaTvSync {
    private const val baseKeyPrefix = "AULAMA_TV_SYNC_BASE_V1_"
    private const val lastOwnerKey = "AULAMA_TV_SYNC_LAST_OWNER_V1"
    private const val periodicSyncMs = 5 * 60 * 1_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _state = MutableStateFlow<AulamaSyncState>(AulamaSyncState.Idle)
    private val _appliedVersion = MutableStateFlow(0L)
    val state: StateFlow<AulamaSyncState> = _state.asStateFlow()
    val appliedVersion: StateFlow<Long> = _appliedVersion.asStateFlow()

    private var manager: AulamaAccountManager? = null
    private var initialized = false
    private var periodicJob: Job? = null
    private var applyingRemote = false

    fun initialize(accountManager: AulamaAccountManager) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            manager = accountManager
            initialized = true
            observeAccount(accountManager)
            observeLocalChanges(accountManager)
        }
    }

    fun notifyLocalChange() {
        if (!initialized || applyingRemote) return
        localChanges.tryEmit(Unit)
    }

    fun syncNow() {
        val accountManager = manager ?: return
        scope.launch { synchronize(accountManager) }
    }

    private fun observeAccount(accountManager: AulamaAccountManager) {
        scope.launch {
            accountManager.state.collectLatest { accountState ->
                periodicJob?.cancel()
                periodicJob = null
                if (accountState !is AulamaAccountState.SignedIn) {
                    _state.value = AulamaSyncState.Idle
                    return@collectLatest
                }
                periodicJob = scope.launch {
                    while (isActive && accountManager.state.value is AulamaAccountState.SignedIn) {
                        synchronize(accountManager)
                        delay(periodicSyncMs)
                    }
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeLocalChanges(accountManager: AulamaAccountManager) {
        scope.launch {
            localChanges.debounce(1_000L).collect {
                if (accountManager.state.value is AulamaAccountState.SignedIn) {
                    synchronize(accountManager)
                }
            }
        }
    }

    private suspend fun synchronize(accountManager: AulamaAccountManager) = mutex.withLock {
        if (accountManager.state.value !is AulamaAccountState.SignedIn) return
        val ownerId = accountManager.accountOwnerId() ?: return
        _state.value = AulamaSyncState.Syncing
        try {
            var remote = accountManager.getSyncDocument() ?: return
            val previousOwner = SP.getString(lastOwnerKey, "")
            if (previousOwner.isNotBlank() && previousOwner != ownerId) {
                applyPayload(remote.payload)
                writeBase(ownerId, remote.payload)
                SP.putString(lastOwnerKey, ownerId)
                _state.value = AulamaSyncState.Synced(remote.revision)
                return
            }
            repeat(3) {
                val local = snapshot()
                val merged = AulamaSyncMergePolicy.merge(
                    readBase(ownerId),
                    local,
                    remote.payload,
                )
                applyPayload(merged)
                if (merged == remote.payload) {
                    writeBase(ownerId, merged)
                    SP.putString(lastOwnerKey, ownerId)
                    _state.value = AulamaSyncState.Synced(remote.revision)
                    return
                }
                try {
                    remote = accountManager.putSyncDocument(remote.revision, merged) ?: return
                    applyPayload(remote.payload)
                    writeBase(ownerId, remote.payload)
                    SP.putString(lastOwnerKey, ownerId)
                    _state.value = AulamaSyncState.Synced(remote.revision)
                    return
                } catch (_: AulamaSyncConflict) {
                    remote = accountManager.getSyncDocument() ?: return
                }
            }
            _state.value = AulamaSyncState.Deferred("同步資料剛在其他裝置更新，稍後會再合併")
        } catch (_: Exception) {
            _state.value = AulamaSyncState.Deferred("暫時未能同步，本機設定已保留")
        }
    }

    private fun snapshot(): AulamaSyncPayload = AulamaSyncPayload(
        favorites = Configs.iptvChannelFavoriteList
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 512 }
            .distinct()
            .sorted(),
        customSources = Configs.iptvSourceList.mapNotNull { source ->
            val name = source.name.trim().takeIf { it.isNotEmpty() && it.length <= 256 }
                ?: "自訂直播源"
            source.takeIf {
                !it.isLocal && it.url.length <= 4_096 && it.url.isSafeRemoteSource()
            }?.let {
                AulamaCustomSource(
                    id = sourceId(it.url),
                    name = name,
                    url = it.url,
                )
            }
        },
        routePriorities = IptvRoutePriorityStore.snapshot()
            .filterKeys { it.isNotBlank() && it.length <= 512 }
            .mapValues { (_, urls) ->
                urls.filter { it.isNotBlank() && it.length <= 4_096 }.distinct().take(32)
            }
            .filterValues { it.isNotEmpty() },
    )

    private fun applyPayload(payload: AulamaSyncPayload) {
        applyingRemote = true
        try {
            Configs.iptvChannelFavoriteList = payload.favorites.toSet()
            Configs.iptvSourceList = IptvSourceList(
                payload.customSources
                    .filter { it.deletedAt == null && it.url.isSafeRemoteSource() }
                    .map { IptvSource(name = it.name, url = it.url, isLocal = false) }
                    .distinctBy { it.url }
            )
            IptvRoutePriorityStore.replaceAll(payload.routePriorities, notifySync = false)
            _appliedVersion.value += 1L
        } finally {
            applyingRemote = false
        }
    }

    private fun readBase(ownerId: String): AulamaSyncPayload = runCatching {
        val raw = SP.getString(baseKey(ownerId), "")
        if (raw.isBlank()) return AulamaSyncPayload()
        AulamaSyncProtocol.parseDocument(raw)?.payload ?: AulamaSyncPayload()
    }.getOrDefault(AulamaSyncPayload())

    private fun writeBase(ownerId: String, payload: AulamaSyncPayload) {
        val root = JsonObject().apply {
            addProperty("revision", 0)
            add("sync", AulamaSyncProtocol.toJson(payload))
        }
        SP.putString(baseKey(ownerId), root.toString())
    }

    private fun baseKey(ownerId: String): String = baseKeyPrefix + sourceId(ownerId).take(24)

    private fun sourceId(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun String.isSafeRemoteSource(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)
}

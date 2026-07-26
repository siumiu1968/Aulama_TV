package top.yogiczy.mytv.tv.account

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

internal sealed interface AulamaAccountState {
    data class Guest(val notice: String? = null) : AulamaAccountState
    data object Restoring : AulamaAccountState
    data object StartingPairing : AulamaAccountState

    data class Pairing(
        val userCode: String,
        val verificationUriComplete: String,
        val expiresAtMs: Long,
        val networkRetry: Boolean = false,
    ) : AulamaAccountState

    data class SignedIn(
        val profile: AulamaAccountProfile,
        val connectionNotice: String? = null,
    ) : AulamaAccountState

    data class Unavailable(
        val kind: UnavailableKind,
        val message: String,
    ) : AulamaAccountState

    data object Expired : AulamaAccountState
}

internal enum class UnavailableKind {
    CONFIGURATION,
    NETWORK,
    SECURE_STORAGE,
    SESSION,
}

internal class AulamaAccountManager(
    private val gateway: AulamaAccountGateway,
    private val refreshTokenStore: RefreshTokenStore,
    private val deviceName: String,
    private val appVersion: String,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AulamaAccountState>(AulamaAccountState.Guest())
    val state: StateFlow<AulamaAccountState> = _state.asStateFlow()

    private var operationJob: Job? = null
    private var refreshJob: Job? = null
    private var accessToken: String? = null
    private var accessTokenExpiresAtMs: Long = 0L
    private val tokenMutex = Mutex()

    fun restoreSession() {
        operationJob?.cancel()
        operationJob = scope.launch {
            _state.value = AulamaAccountState.Restoring
            when (val stored = refreshTokenStore.load()) {
                RefreshTokenLoadResult.Missing -> {
                    _state.value = AulamaAccountState.Guest()
                }

                RefreshTokenLoadResult.Invalidated -> {
                    accessToken = null
                    _state.value = AulamaAccountState.Guest(
                        "登入資料無法解密，已安全登出"
                    )
                }

                is RefreshTokenLoadResult.Available -> restoreWithToken(stored.token)
            }
        }
    }

    fun startPairing() {
        operationJob?.cancel()
        refreshJob?.cancel()
        clearSession()
        operationJob = scope.launch {
            _state.value = AulamaAccountState.StartingPairing
            val response = try {
                gateway.startDevicePairing(deviceName = deviceName, appVersion = appVersion)
            } catch (_: AulamaAccountApiException.ConfigurationUnavailable) {
                showConfigurationUnavailable()
                return@launch
            } catch (_: AulamaAccountApiException.ProtocolFailure) {
                _state.value = AulamaAccountState.Unavailable(
                    UnavailableKind.CONFIGURATION,
                    "配對服務回應格式未完成，請稍後再試；你仍可使用訪客模式。",
                )
                return@launch
            } catch (_: AulamaAccountApiException.HttpFailure) {
                showNetworkUnavailable()
                return@launch
            } catch (_: IOException) {
                showNetworkUnavailable()
                return@launch
            }

            pollUntilAuthorized(DevicePairingStateMachine.begin(response, nowMs()))
        }
    }

    fun retry() {
        when (_state.value) {
            AulamaAccountState.Restoring,
            AulamaAccountState.StartingPairing,
            is AulamaAccountState.Pairing,
            -> Unit

            is AulamaAccountState.Unavailable -> {
                if (refreshTokenStore.load() is RefreshTokenLoadResult.Available) {
                    restoreSession()
                } else {
                    startPairing()
                }
            }

            else -> startPairing()
        }
    }

    fun continueAsGuest(notice: String? = null) {
        operationJob?.cancel()
        refreshJob?.cancel()
        clearSession()
        _state.value = AulamaAccountState.Guest(notice)
    }

    fun logout() {
        continueAsGuest("已登出 Aulama ID")
    }

    internal fun isSuperAdmin(): Boolean =
        (_state.value as? AulamaAccountState.SignedIn)?.profile?.isSuperAdmin == true

    internal fun accountOwnerId(): String? =
        (_state.value as? AulamaAccountState.SignedIn)?.profile?.id

    internal suspend fun getSyncDocument(): AulamaSyncDocument? =
        withAccessToken { token -> gateway.getSync(token) }

    internal suspend fun putSyncDocument(
        expectedRevision: Long,
        payload: AulamaSyncPayload,
    ): AulamaSyncDocument? = withAccessToken { token ->
        gateway.putSync(token, expectedRevision, payload)
    }

    internal suspend fun playbackCandidates(
        url: String,
        referrer: String?,
        userAgent: String?,
    ): List<AulamaPlaybackCandidate> {
        val signedIn = _state.value as? AulamaAccountState.SignedIn
            ?: return AulamaPlaybackPolicy.candidates(url, false, emptyList())
        if (!signedIn.profile.isSuperAdmin) {
            return AulamaPlaybackPolicy.candidates(url, false, emptyList())
        }
        val plan = runCatching {
            withAccessToken { token ->
                gateway.resolveRelayPlan(token, url, referrer, userAgent).also { candidates ->
                    candidates.forEach { candidate ->
                        AulamaPlaybackAuthorization.bind(candidate, token)
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        return AulamaPlaybackPolicy.candidates(url, true, plan)
    }

    internal suspend fun <T> withAccessToken(
        operation: suspend (String) -> T,
    ): T? {
        var token = validAccessToken(forceRefresh = false) ?: return null
        return try {
            operation(token)
        } catch (_: AulamaAccountApiException.Unauthorized) {
            token = validAccessToken(forceRefresh = true) ?: return null
            operation(token)
        }
    }

    private suspend fun pollUntilAuthorized(initial: DevicePairingMachineState) {
        var machine = initial
        publishPairing(machine)

        while (scope.isActive) {
            val delayMs = DevicePairingStateMachine.nextPollDelayMs(machine, nowMs())
            if (delayMs <= 0L && DevicePairingStateMachine.isExpired(machine, nowMs())) {
                _state.value = AulamaAccountState.Expired
                return
            }
            delay(delayMs)

            if (DevicePairingStateMachine.isExpired(machine, nowMs())) {
                _state.value = AulamaAccountState.Expired
                return
            }

            val result = try {
                gateway.pollDeviceToken(machine.session.deviceCode)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: IOException) {
                machine = DevicePairingStateMachine.networkRetry(machine, nowMs())
                publishPairing(machine)
                continue
            }

            machine = DevicePairingStateMachine.reduce(machine, result, nowMs())
            when (machine.phase) {
                DevicePairingPhase.PENDING,
                DevicePairingPhase.NETWORK_RETRY,
                -> publishPairing(machine)

                DevicePairingPhase.AUTHORIZED -> {
                    completeAuthorization(
                        machine.tokens ?: run {
                            _state.value = AulamaAccountState.Unavailable(
                                UnavailableKind.CONFIGURATION,
                                "配對服務回應不完整，請稍後再試。",
                            )
                            return
                        }
                    )
                    return
                }

                DevicePairingPhase.EXPIRED -> {
                    _state.value = AulamaAccountState.Expired
                    return
                }

                DevicePairingPhase.FAILED -> {
                    if (machine.errorCode == "configuration_unavailable" ||
                        machine.errorCode == "invalid_response"
                    ) {
                        showConfigurationUnavailable()
                    } else {
                        _state.value = AulamaAccountState.Unavailable(
                            UnavailableKind.SESSION,
                            "配對未完成或已被拒絕，請重新產生配對碼。",
                        )
                    }
                    return
                }
            }
        }
    }

    private fun publishPairing(machine: DevicePairingMachineState) {
        _state.value = AulamaAccountState.Pairing(
            userCode = machine.session.userCode,
            verificationUriComplete = machine.session.verificationUriComplete,
            expiresAtMs = machine.session.expiresAtMs,
            networkRetry = machine.phase == DevicePairingPhase.NETWORK_RETRY,
        )
    }

    private suspend fun completeAuthorization(tokens: AulamaSessionTokens) {
        if (refreshTokenStore.save(tokens.refreshToken).isFailure) {
            clearSession()
            _state.value = AulamaAccountState.Unavailable(
                UnavailableKind.SECURE_STORAGE,
                "此裝置無法安全保存登入資料，已自動登出；你仍可使用訪客模式。",
            )
            return
        }

        accessToken = tokens.accessToken
        accessTokenExpiresAtMs = nowMs() + tokens.expiresInSeconds * 1_000L
        val profile = try {
            gateway.getProfile(tokens.accessToken)
        } catch (_: AulamaAccountApiException.Unauthorized) {
            clearSession()
            _state.value = AulamaAccountState.Guest("登入已失效，請重新配對")
            return
        } catch (_: AulamaAccountApiException.ConfigurationUnavailable) {
            _state.value = AulamaAccountState.Unavailable(
                UnavailableKind.CONFIGURATION,
                "帳戶資料服務尚未配置，登入資料已安全保存，可稍後重試。",
            )
            return
        } catch (_: IOException) {
            _state.value = AulamaAccountState.Unavailable(
                UnavailableKind.NETWORK,
                "已完成配對，但暫時無法讀取帳戶資料，請檢查網絡後重試。",
            )
            return
        }

        _state.value = AulamaAccountState.SignedIn(profile)
        scheduleRefresh(tokens.expiresInSeconds)
    }

    private suspend fun validAccessToken(forceRefresh: Boolean): String? = tokenMutex.withLock {
        val currentToken = accessToken
        if (!forceRefresh &&
            currentToken != null &&
            nowMs() + ACCESS_TOKEN_EARLY_REFRESH_MS < accessTokenExpiresAtMs
        ) {
            return@withLock currentToken
        }

        val stored = refreshTokenStore.load() as? RefreshTokenLoadResult.Available
            ?: return@withLock null
        val tokens = try {
            gateway.refresh(stored.token)
        } catch (_: AulamaAccountApiException.Unauthorized) {
            clearSession()
            _state.value = AulamaAccountState.Guest("登入已過期，請重新配對")
            return@withLock null
        }
        if (refreshTokenStore.save(tokens.refreshToken).isFailure) {
            clearSession()
            _state.value = AulamaAccountState.Guest("無法安全更新登入資料，已登出")
            return@withLock null
        }
        accessToken = tokens.accessToken
        accessTokenExpiresAtMs = nowMs() + tokens.expiresInSeconds * 1_000L
        val currentProfile = (_state.value as? AulamaAccountState.SignedIn)?.profile
        val profile = try {
            gateway.getProfile(tokens.accessToken)
        } catch (_: AulamaAccountApiException.Unauthorized) {
            clearSession()
            _state.value = AulamaAccountState.Guest("登入已過期，請重新配對")
            return@withLock null
        } catch (_: IOException) {
            currentProfile
        }
        if (profile != null) {
            _state.value = AulamaAccountState.SignedIn(profile)
        }
        scheduleRefresh(tokens.expiresInSeconds)
        tokens.accessToken
    }

    private suspend fun restoreWithToken(refreshToken: String) {
        val tokens = try {
            gateway.refresh(refreshToken)
        } catch (_: AulamaAccountApiException.Unauthorized) {
            clearSession()
            _state.value = AulamaAccountState.Guest("登入已過期，請重新配對")
            return
        } catch (_: AulamaAccountApiException.ConfigurationUnavailable) {
            showConfigurationUnavailable()
            return
        } catch (_: AulamaAccountApiException.ProtocolFailure) {
            _state.value = AulamaAccountState.Unavailable(
                UnavailableKind.CONFIGURATION,
                "登入服務回應格式未完成，請稍後再試。",
            )
            return
        } catch (_: IOException) {
            _state.value = AulamaAccountState.Unavailable(
                UnavailableKind.NETWORK,
                "暫時無法恢復 Aulama ID，訪客播放不受影響。",
            )
            return
        }

        completeAuthorization(tokens)
    }

    private fun scheduleRefresh(expiresInSeconds: Long) {
        refreshJob?.cancel()
        val refreshAfterSeconds = when {
            expiresInSeconds <= 10L -> 5L
            expiresInSeconds <= 120L -> expiresInSeconds / 2L
            else -> expiresInSeconds - 60L
        }
        refreshJob = scope.launch {
            delay(refreshAfterSeconds.coerceAtLeast(1L) * 1_000L)
            val currentSignedIn = _state.value as? AulamaAccountState.SignedIn ?: return@launch
            try {
                if (validAccessToken(forceRefresh = true) == null &&
                    _state.value is AulamaAccountState.SignedIn
                ) {
                    _state.value = AulamaAccountState.Guest("登入資料已清除")
                }
            } catch (_: IOException) {
                _state.value = currentSignedIn.copy(
                    connectionNotice = "登入續期暫時中斷，將於稍後重試"
                )
                scheduleRefresh(90L)
            }
        }
    }

    private fun showConfigurationUnavailable() {
        _state.value = AulamaAccountState.Unavailable(
            UnavailableKind.CONFIGURATION,
            "Aulama ID 配對服務尚未配置，請稍後再試；你仍可使用訪客模式。",
        )
    }

    private fun showNetworkUnavailable() {
        _state.value = AulamaAccountState.Unavailable(
            UnavailableKind.NETWORK,
            "無法連接 Aulama ID 配對服務，請檢查網絡後重試；訪客播放不受影響。",
        )
    }

    private fun clearSession() {
        accessToken = null
        accessTokenExpiresAtMs = 0L
        AulamaPlaybackAuthorization.clear()
        refreshTokenStore.clear()
    }

    private companion object {
        const val ACCESS_TOKEN_EARLY_REFRESH_MS = 60_000L
    }
}

internal object AulamaAccount {
    private var initialized = false
    lateinit var manager: AulamaAccountManager
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            manager = AulamaAccountManager(
                gateway = AulamaAccountApi(),
                refreshTokenStore = RefreshTokenStoreFactory.create(appContext),
                deviceName = buildDeviceName(),
                appVersion = appContext.packageManager
                    .getPackageInfo(appContext.packageName, 0)
                    .versionName
                    .orEmpty()
                    .ifBlank { "unknown" },
            )
            AulamaTvSync.initialize(manager)
            initialized = true
            manager.restoreSession()
        }
    }

    private fun buildDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android TV" }
            .take(80)
    }
}

package org.aulama.iptv.mobile.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

internal class AulamaAccountManager(
    private val gateway: AulamaAuthGateway,
    private val refreshTokenStore: RefreshTokenStore,
    private val appVersion: String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private val _state = MutableStateFlow<AulamaAccountState>(AulamaAccountState.Restoring)
    val state: StateFlow<AulamaAccountState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var accessTokenExpiresAtMs: Long = 0L
    private var refreshJob: Job? = null

    suspend fun restoreSession() {
        _state.value = AulamaAccountState.Restoring
        when (val stored = refreshTokenStore.load()) {
            RefreshTokenLoadResult.Missing -> _state.value = AulamaAccountState.Guest()
            RefreshTokenLoadResult.Invalidated -> {
                clearLocalSession()
                _state.value = AulamaAccountState.Guest("登入資料無法解密，已安全登出")
            }
            is RefreshTokenLoadResult.Available -> refreshWith(stored.token, restoring = true)
        }
    }

    suspend fun googleNonce(): GoogleNonce = gateway.googleNonce(appVersion)

    fun startSignIn(provider: String) {
        _state.value = AulamaAccountState.SigningIn(provider)
    }

    suspend fun completeGoogleSignIn(idToken: String, nonce: String) {
        _state.value = AulamaAccountState.SigningIn("Google")
        authorize { gateway.verifyGoogle(idToken, nonce, appVersion) }
    }

    suspend fun passkeyOptions(): PasskeyRequest = gateway.passkeyOptions(appVersion)

    suspend fun completePasskeySignIn(requestId: String, assertionJson: String) {
        _state.value = AulamaAccountState.SigningIn("Passkey")
        authorize { gateway.verifyPasskey(requestId, assertionJson, appVersion) }
    }

    fun cancelSignIn() {
        if (_state.value is AulamaAccountState.SigningIn) {
            _state.value = AulamaAccountState.Guest()
        }
    }

    fun credentialFailure(message: String) {
        _state.value = AulamaAccountState.Guest(message)
    }

    fun apiFailure(error: Throwable) {
        publishFailure(error, duringRestore = false)
    }

    suspend fun approveDevice(userCode: String) {
        withAccessToken { gateway.approveDevice(it, userCode) }
    }

    suspend fun relayPlan(
        routeUrl: String,
        referrer: String?,
        userAgent: String?,
    ): List<org.aulama.iptv.mobile.data.playback.RelayPlanCandidate> =
        withAccessToken { gateway.relayPlan(it, routeUrl, referrer, userAgent) }

    suspend fun getSync(): SyncDocument = withAccessToken(gateway::getSync)

    suspend fun putSync(document: SyncDocument): SyncDocument =
        withAccessToken { gateway.putSync(it, document) }

    suspend fun logout() {
        refreshJob?.cancel()
        val stored = refreshTokenStore.load() as? RefreshTokenLoadResult.Available
        if (stored != null) runCatching { gateway.revoke(stored.token) }
        clearLocalSession()
        _state.value = AulamaAccountState.Guest("已登出 Aulama ID")
    }

    fun signedInProfile(): AulamaAccountProfile? =
        (_state.value as? AulamaAccountState.SignedIn)?.profile

    fun currentAccessTokenForPlayback(): String? = accessToken
        ?.takeIf { signedInProfile()?.isSuperAdmin == true && nowMs() < accessTokenExpiresAtMs }

    fun close() {
        refreshJob?.cancel()
    }

    private suspend fun authorize(block: suspend () -> AulamaSessionTokens) {
        try {
            completeAuthorization(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            publishFailure(error, duringRestore = false)
        }
    }

    private suspend fun completeAuthorization(tokens: AulamaSessionTokens) {
        if (refreshTokenStore.save(tokens.refreshToken).isFailure) {
            clearLocalSession()
            _state.value = AulamaAccountState.Unavailable(
                AccountUnavailableKind.SECURE_STORAGE,
                "此裝置無法安全保存登入資料，已自動登出；仍可使用訪客模式。",
            )
            return
        }
        accessToken = tokens.accessToken
        accessTokenExpiresAtMs = nowMs() + tokens.expiresInSeconds * 1_000L
        val profile = try {
            tokens.profile ?: gateway.profile(tokens.accessToken)
        } catch (error: Throwable) {
            publishFailure(error, duringRestore = false)
            return
        }
        _state.value = AulamaAccountState.SignedIn(profile)
        scheduleRefresh(tokens.expiresInSeconds)
    }

    private suspend fun <T> withAccessToken(block: suspend (String) -> T): T {
        val token = sessionMutex.withLock {
            if (accessToken == null || nowMs() >= accessTokenExpiresAtMs - REFRESH_SKEW_MS) {
                val stored = refreshTokenStore.load() as? RefreshTokenLoadResult.Available
                    ?: throw AulamaApiException.Unauthorized()
                val tokens = gateway.refresh(stored.token)
                if (refreshTokenStore.save(tokens.refreshToken).isFailure) {
                    clearLocalSession()
                    throw AulamaApiException.Unauthorized()
                }
                accessToken = tokens.accessToken
                accessTokenExpiresAtMs = nowMs() + tokens.expiresInSeconds * 1_000L
                scheduleRefresh(tokens.expiresInSeconds)
            }
            accessToken ?: throw AulamaApiException.Unauthorized()
        }
        return try {
            block(token)
        } catch (error: AulamaApiException.Unauthorized) {
            clearLocalSession()
            _state.value = AulamaAccountState.Guest("登入已過期，請重新登入")
            throw error
        }
    }

    private suspend fun refreshWith(refreshToken: String, restoring: Boolean) {
        try {
            completeAuthorization(gateway.refresh(refreshToken))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            publishFailure(error, duringRestore = restoring)
        }
    }

    private fun scheduleRefresh(expiresInSeconds: Long) {
        refreshJob?.cancel()
        val delaySeconds = when {
            expiresInSeconds <= 10L -> 5L
            expiresInSeconds <= 120L -> expiresInSeconds / 2L
            else -> expiresInSeconds - 60L
        }.coerceAtLeast(1L)
        refreshJob = scope.launch {
            delay(delaySeconds * 1_000L)
            val stored = refreshTokenStore.load() as? RefreshTokenLoadResult.Available
                ?: return@launch
            val currentProfile = signedInProfile() ?: return@launch
            try {
                completeAuthorization(gateway.refresh(stored.token))
            } catch (_: AulamaApiException.Unauthorized) {
                clearLocalSession()
                _state.value = AulamaAccountState.Guest("登入已過期，請重新登入")
            } catch (_: Throwable) {
                _state.value = AulamaAccountState.SignedIn(
                    currentProfile,
                    connectionNotice = "登入續期暫時中斷，稍後會再試",
                )
                scheduleRefresh(90L)
            }
        }
    }

    private fun publishFailure(error: Throwable, duringRestore: Boolean) {
        when (error) {
            is AulamaApiException.ConfigurationUnavailable -> {
                _state.value = AulamaAccountState.Unavailable(
                    AccountUnavailableKind.CONFIGURATION,
                    "Aulama ID 後端尚未配置完成；仍可使用訪客模式。",
                )
            }
            is AulamaApiException.Unauthorized -> {
                clearLocalSession()
                _state.value = AulamaAccountState.Guest("登入已失效，請重新登入")
            }
            is AulamaApiException.Forbidden -> {
                _state.value = AulamaAccountState.Unavailable(
                    AccountUnavailableKind.SESSION,
                    "帳戶暫時冇權限完成呢個操作；登入資料仍然保留。",
                )
            }
            is AulamaApiException.ProtocolFailure -> {
                _state.value = AulamaAccountState.Unavailable(
                    AccountUnavailableKind.CONFIGURATION,
                    "Aulama ID 回應格式未完成；仍可使用訪客模式。",
                )
            }
            is IOException, is AulamaApiException.HttpFailure -> {
                _state.value = AulamaAccountState.Unavailable(
                    AccountUnavailableKind.NETWORK,
                    if (duringRestore) "暫時無法恢復登入，訪客播放不受影響。"
                    else "暫時無法連接 Aulama ID，請檢查網絡後再試。",
                )
            }
            else -> {
                _state.value = AulamaAccountState.Unavailable(
                    AccountUnavailableKind.SESSION,
                    "登入未完成，請重新嘗試；仍可使用訪客模式。",
                )
            }
        }
    }

    private fun clearLocalSession() {
        refreshJob?.cancel()
        accessToken = null
        accessTokenExpiresAtMs = 0L
        refreshTokenStore.clear()
    }

    private companion object {
        const val REFRESH_SKEW_MS = 60_000L
    }
}

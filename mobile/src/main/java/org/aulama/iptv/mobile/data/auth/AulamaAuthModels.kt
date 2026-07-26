package org.aulama.iptv.mobile.data.auth

data class AulamaSessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val profile: AulamaAccountProfile? = null,
)

data class AulamaAccountProfile(
    val id: String,
    val email: String?,
    val displayName: String?,
    val role: String,
    val isSuperAdmin: Boolean,
) {
    val primaryLabel: String
        get() = displayName?.takeIf(String::isNotBlank)
            ?: email?.takeIf(String::isNotBlank)
            ?: "Aulama ID"

    val roleLabel: String
        get() = when {
            isSuperAdmin -> "最高管理員"
            role == "admin" -> "管理員"
            role == "premium" -> "Premium"
            else -> "一般用戶"
        }
}

data class GoogleNonce(
    val value: String,
    val serverClientId: String,
    val expiresInSeconds: Long,
)

data class PasskeyRequest(
    val requestId: String,
    val requestJson: String,
)

sealed class AulamaApiException(message: String) : Exception(message) {
    class ConfigurationUnavailable : AulamaApiException("Aulama ID API is not configured")
    class Unauthorized : AulamaApiException("Aulama ID session is unauthorized")
    class Forbidden : AulamaApiException("Aulama ID action is forbidden")
    class Conflict(val currentRevision: Long?) : AulamaApiException("Sync revision conflict")
    class ProtocolFailure : AulamaApiException("Aulama ID API returned an invalid response")
    class HttpFailure(val statusCode: Int) : AulamaApiException("Aulama ID API HTTP $statusCode")
}

enum class AccountUnavailableKind {
    CONFIGURATION,
    NETWORK,
    SECURE_STORAGE,
    SESSION,
}

sealed interface AulamaAccountState {
    data class Guest(val notice: String? = null) : AulamaAccountState
    data object Restoring : AulamaAccountState
    data class SigningIn(val provider: String) : AulamaAccountState
    data class SignedIn(
        val profile: AulamaAccountProfile,
        val connectionNotice: String? = null,
    ) : AulamaAccountState

    data class Unavailable(
        val kind: AccountUnavailableKind,
        val message: String,
    ) : AulamaAccountState
}

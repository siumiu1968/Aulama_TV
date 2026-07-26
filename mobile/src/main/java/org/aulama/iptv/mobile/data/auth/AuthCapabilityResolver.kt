package org.aulama.iptv.mobile.data.auth

enum class AuthCapabilityStatus {
    AVAILABLE,
    CONFIGURATION_REQUIRED,
    UNSUPPORTED,
}

data class AuthCapabilities(
    val google: AuthCapabilityStatus,
    val passkey: AuthCapabilityStatus,
)

object AuthCapabilityResolver {
    fun resolve(
        sdkInt: Int,
        googleWebClientId: String,
        hasPasskeyChallenge: Boolean,
    ): AuthCapabilities {
        val google = if (googleWebClientId.isBlank()) {
            AuthCapabilityStatus.CONFIGURATION_REQUIRED
        } else {
            AuthCapabilityStatus.AVAILABLE
        }

        val passkey = when {
            sdkInt < PASSKEY_MIN_SDK -> AuthCapabilityStatus.UNSUPPORTED
            !hasPasskeyChallenge -> AuthCapabilityStatus.CONFIGURATION_REQUIRED
            else -> AuthCapabilityStatus.AVAILABLE
        }

        return AuthCapabilities(google = google, passkey = passkey)
    }

    private const val PASSKEY_MIN_SDK = 28
}

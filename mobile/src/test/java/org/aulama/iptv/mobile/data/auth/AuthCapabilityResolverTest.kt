package org.aulama.iptv.mobile.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthCapabilityResolverTest {
    @Test
    fun missingGoogleClientIdKeepsGoogleDisabled() {
        val result = AuthCapabilityResolver.resolve(
            sdkInt = 35,
            googleWebClientId = "",
            hasPasskeyChallenge = false,
        )

        assertEquals(AuthCapabilityStatus.CONFIGURATION_REQUIRED, result.google)
        assertEquals(AuthCapabilityStatus.CONFIGURATION_REQUIRED, result.passkey)
    }

    @Test
    fun passkeyRemainsUnsupportedBelowApi28() {
        val result = AuthCapabilityResolver.resolve(
            sdkInt = 27,
            googleWebClientId = "configured-client-id",
            hasPasskeyChallenge = true,
        )

        assertEquals(AuthCapabilityStatus.AVAILABLE, result.google)
        assertEquals(AuthCapabilityStatus.UNSUPPORTED, result.passkey)
    }

    @Test
    fun passkeyNeedsBothApi28AndServerChallenge() {
        val unavailable = AuthCapabilityResolver.resolve(
            sdkInt = 28,
            googleWebClientId = "configured-client-id",
            hasPasskeyChallenge = false,
        )
        val available = AuthCapabilityResolver.resolve(
            sdkInt = 28,
            googleWebClientId = "configured-client-id",
            hasPasskeyChallenge = true,
        )

        assertEquals(AuthCapabilityStatus.CONFIGURATION_REQUIRED, unavailable.passkey)
        assertEquals(AuthCapabilityStatus.AVAILABLE, available.passkey)
    }
}

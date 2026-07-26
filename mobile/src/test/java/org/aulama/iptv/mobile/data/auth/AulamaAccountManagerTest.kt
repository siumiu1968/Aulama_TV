package org.aulama.iptv.mobile.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AulamaAccountManagerTest {
    @Test
    fun googleSignInStoresOnlyRotatedRefreshTokenAndPublishesProfile() = runBlocking {
        val store = RecordingTokenStore()
        val gateway = FakeGateway()
        val manager = AulamaAccountManager(gateway, store, "test", nowMs = { 1_000L })

        manager.completeGoogleSignIn("id-token", "server-nonce")

        assertEquals(listOf("refresh-1"), store.savedTokens)
        assertTrue(store.savedTokens.none { it == "access-1" || it == "id-token" })
        val signedIn = manager.state.value as AulamaAccountState.SignedIn
        assertEquals("user-1", signedIn.profile.id)
        assertEquals("super_admin", signedIn.profile.role)
        manager.close()
    }

    @Test
    fun restoringSessionRotatesRefreshToken() = runBlocking {
        val store = RecordingTokenStore(initial = "refresh-old")
        val gateway = FakeGateway()
        val manager = AulamaAccountManager(gateway, store, "test", nowMs = { 1_000L })

        manager.restoreSession()

        assertEquals("refresh-2", store.current)
        assertEquals(listOf("refresh-2"), store.savedTokens)
        assertTrue(manager.state.value is AulamaAccountState.SignedIn)
        manager.close()
    }

    private class RecordingTokenStore(initial: String? = null) : RefreshTokenStore {
        var current: String? = initial
        val savedTokens = mutableListOf<String>()

        override fun load(): RefreshTokenLoadResult = current
            ?.let(RefreshTokenLoadResult::Available)
            ?: RefreshTokenLoadResult.Missing

        override fun save(token: String): Result<Unit> = runCatching {
            current = token
            savedTokens += token
        }

        override fun clear() {
            current = null
        }
    }

    private class FakeGateway : AulamaAuthGateway {
        override suspend fun googleNonce(appVersion: String) = GoogleNonce("nonce", "client", 60)
        override suspend fun verifyGoogle(idToken: String, nonce: String, appVersion: String) =
            AulamaSessionTokens("access-1", "refresh-1", 3_600)

        override suspend fun passkeyOptions(appVersion: String) = error("unused")
        override suspend fun verifyPasskey(
            requestId: String,
            assertionJson: String,
            appVersion: String,
        ) = error("unused")

        override suspend fun refresh(refreshToken: String) =
            AulamaSessionTokens("access-2", "refresh-2", 3_600)

        override suspend fun profile(accessToken: String) = AulamaAccountProfile(
            id = "user-1",
            email = "user@example.com",
            displayName = "User",
            role = "super_admin",
            isSuperAdmin = true,
        )

        override suspend fun revoke(refreshToken: String) = Unit
        override suspend fun approveDevice(accessToken: String, userCode: String) = Unit
        override suspend fun relayPlan(
            accessToken: String,
            routeUrl: String,
            referrer: String?,
            userAgent: String?,
        ) = emptyList<org.aulama.iptv.mobile.data.playback.RelayPlanCandidate>()
        override suspend fun getSync(accessToken: String) = SyncDocument(0, SyncPayload())
        override suspend fun putSync(accessToken: String, document: SyncDocument) = document
    }
}

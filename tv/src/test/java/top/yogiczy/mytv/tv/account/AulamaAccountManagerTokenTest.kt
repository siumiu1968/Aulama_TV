package top.yogiczy.mytv.tv.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AulamaAccountManagerTokenTest {
    @Test
    fun `authorized operation refreshes rotated token and reuses valid access token`() = runBlocking {
        var now = 10_000L
        val gateway = FakeGateway()
        val store = MemoryRefreshTokenStore().apply { save("refresh-0") }
        val manager = AulamaAccountManager(
            gateway = gateway,
            refreshTokenStore = store,
            deviceName = "Test TV",
            appVersion = "test",
            nowMs = { now },
        )

        assertEquals("access-1", manager.withAccessToken { it })
        assertEquals("access-1", manager.withAccessToken { it })
        assertEquals(1, gateway.refreshCount)
        assertEquals("refresh-1", (store.load() as RefreshTokenLoadResult.Available).token)
        assertTrue(manager.state.value is AulamaAccountState.SignedIn)

        now += 10 * 60 * 1_000L
        assertEquals("access-2", manager.withAccessToken { it })
        assertEquals(2, gateway.refreshCount)
        assertEquals("refresh-2", (store.load() as RefreshTokenLoadResult.Available).token)
    }

    private class FakeGateway : AulamaAccountGateway {
        var refreshCount = 0

        override suspend fun startDevicePairing(
            deviceName: String,
            appVersion: String,
        ): DeviceStartResponse = error("Not used")

        override suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPollResult =
            error("Not used")

        override suspend fun refresh(refreshToken: String): AulamaSessionTokens {
            refreshCount += 1
            return AulamaSessionTokens(
                accessToken = "access-$refreshCount",
                refreshToken = "refresh-$refreshCount",
                expiresInSeconds = 5 * 60L,
            )
        }

        override suspend fun getProfile(accessToken: String): AulamaAccountProfile =
            AulamaAccountProfile(
                id = "owner-1",
                email = "owner@example.com",
                displayName = "Owner",
                role = "super_admin",
                isSuperAdmin = true,
            )

        override suspend fun getSync(accessToken: String): AulamaSyncDocument = error("Not used")

        override suspend fun putSync(
            accessToken: String,
            expectedRevision: Long,
            payload: AulamaSyncPayload,
        ): AulamaSyncDocument = error("Not used")

        override suspend fun resolveRelayPlan(
            accessToken: String,
            url: String,
            referrer: String?,
            userAgent: String?,
        ): List<AulamaPlanCandidate> = error("Not used")
    }
}

package top.yogiczy.mytv.tv.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingStateMachineTest {
    private val response = DeviceStartResponse(
        deviceCode = "secret-device-code",
        userCode = "ABCD-EFGH",
        verificationUri = "https://aulama.org/iptv/pair/",
        verificationUriComplete = "https://aulama.org/iptv/pair/?code=ABCD-EFGH",
        expiresInSeconds = 600,
        intervalSeconds = 5,
    )

    @Test
    fun `begin follows server poll interval and expiry`() {
        val state = DevicePairingStateMachine.begin(response, nowMs = 10_000L)

        assertEquals(DevicePairingPhase.PENDING, state.phase)
        assertEquals(5_000L, state.pollIntervalMs)
        assertEquals(610_000L, state.session.expiresAtMs)
        assertEquals(5_000L, DevicePairingStateMachine.nextPollDelayMs(state, 10_000L))
    }

    @Test
    fun `slow down adds five seconds before next poll`() {
        val initial = DevicePairingStateMachine.begin(response, nowMs = 0L)
        val slowed = DevicePairingStateMachine.reduce(
            initial,
            DeviceTokenPollResult.SlowDown,
            nowMs = 5_000L,
        )

        assertEquals(10_000L, slowed.pollIntervalMs)
        assertEquals(10_000L, DevicePairingStateMachine.nextPollDelayMs(slowed, 5_000L))
    }

    @Test
    fun `network retry retains pairing and server interval`() {
        val initial = DevicePairingStateMachine.begin(response, nowMs = 0L)
        val retry = DevicePairingStateMachine.networkRetry(initial, nowMs = 5_000L)

        assertEquals(DevicePairingPhase.NETWORK_RETRY, retry.phase)
        assertEquals(5_000L, retry.pollIntervalMs)
        assertEquals("ABCD-EFGH", retry.session.userCode)
    }

    @Test
    fun `expired pairing never polls again`() {
        val initial = DevicePairingStateMachine.begin(response, nowMs = 0L)
        val expired = DevicePairingStateMachine.reduce(
            initial,
            DeviceTokenPollResult.AuthorizationPending,
            nowMs = 600_000L,
        )

        assertEquals(DevicePairingPhase.EXPIRED, expired.phase)
        assertTrue(DevicePairingStateMachine.isExpired(expired, 600_000L))
        assertEquals(0L, DevicePairingStateMachine.nextPollDelayMs(expired, 600_000L))
    }

    @Test
    fun `authorized transition carries session tokens in memory state`() {
        val initial = DevicePairingStateMachine.begin(response, nowMs = 0L)
        val authorized = DevicePairingStateMachine.reduce(
            initial,
            DeviceTokenPollResult.Authorized(
                AulamaSessionTokens("access", "refresh", 900L)
            ),
            nowMs = 5_000L,
        )

        assertEquals(DevicePairingPhase.AUTHORIZED, authorized.phase)
        assertEquals("access", authorized.tokens?.accessToken)
    }
}

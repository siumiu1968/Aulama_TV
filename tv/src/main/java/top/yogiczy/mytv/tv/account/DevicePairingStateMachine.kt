package top.yogiczy.mytv.tv.account

internal const val DEVICE_SLOW_DOWN_INCREMENT_MS = 5_000L

internal enum class DevicePairingPhase {
    PENDING,
    NETWORK_RETRY,
    AUTHORIZED,
    EXPIRED,
    FAILED,
}

internal data class DevicePairingSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUriComplete: String,
    val expiresAtMs: Long,
)

internal data class DevicePairingMachineState(
    val phase: DevicePairingPhase,
    val session: DevicePairingSession,
    val pollIntervalMs: Long,
    val lastPollAtMs: Long,
    val tokens: AulamaSessionTokens? = null,
    val errorCode: String? = null,
)

internal object DevicePairingStateMachine {
    fun begin(response: DeviceStartResponse, nowMs: Long): DevicePairingMachineState {
        val expiresInMs = secondsToMillis(response.expiresInSeconds)
        return DevicePairingMachineState(
            phase = DevicePairingPhase.PENDING,
            session = DevicePairingSession(
                deviceCode = response.deviceCode,
                userCode = response.userCode,
                verificationUriComplete = response.verificationUriComplete,
                expiresAtMs = safeAdd(nowMs, expiresInMs),
            ),
            pollIntervalMs = secondsToMillis(response.intervalSeconds),
            lastPollAtMs = nowMs,
        )
    }

    fun reduce(
        current: DevicePairingMachineState,
        result: DeviceTokenPollResult,
        nowMs: Long,
    ): DevicePairingMachineState {
        if (isExpired(current, nowMs) || result == DeviceTokenPollResult.ExpiredToken) {
            return current.copy(
                phase = DevicePairingPhase.EXPIRED,
                lastPollAtMs = nowMs,
                tokens = null,
            )
        }

        return when (result) {
            DeviceTokenPollResult.AuthorizationPending -> current.copy(
                phase = DevicePairingPhase.PENDING,
                lastPollAtMs = nowMs,
            )

            DeviceTokenPollResult.SlowDown -> current.copy(
                phase = DevicePairingPhase.PENDING,
                pollIntervalMs = safeAdd(
                    current.pollIntervalMs,
                    DEVICE_SLOW_DOWN_INCREMENT_MS,
                ),
                lastPollAtMs = nowMs,
            )

            is DeviceTokenPollResult.Authorized -> current.copy(
                phase = DevicePairingPhase.AUTHORIZED,
                lastPollAtMs = nowMs,
                tokens = result.tokens,
            )

            DeviceTokenPollResult.ConfigurationUnavailable -> current.copy(
                phase = DevicePairingPhase.FAILED,
                lastPollAtMs = nowMs,
                errorCode = "configuration_unavailable",
            )

            is DeviceTokenPollResult.Rejected -> current.copy(
                phase = DevicePairingPhase.FAILED,
                lastPollAtMs = nowMs,
                errorCode = result.errorCode,
            )

            DeviceTokenPollResult.InvalidResponse -> current.copy(
                phase = DevicePairingPhase.FAILED,
                lastPollAtMs = nowMs,
                errorCode = "invalid_response",
            )

            DeviceTokenPollResult.ExpiredToken -> current.copy(
                phase = DevicePairingPhase.EXPIRED,
                lastPollAtMs = nowMs,
            )
        }
    }

    fun networkRetry(
        current: DevicePairingMachineState,
        nowMs: Long,
    ): DevicePairingMachineState = if (isExpired(current, nowMs)) {
        current.copy(phase = DevicePairingPhase.EXPIRED, lastPollAtMs = nowMs)
    } else {
        current.copy(phase = DevicePairingPhase.NETWORK_RETRY, lastPollAtMs = nowMs)
    }

    fun nextPollDelayMs(current: DevicePairingMachineState, nowMs: Long): Long {
        if (current.phase == DevicePairingPhase.EXPIRED || isExpired(current, nowMs)) return 0L
        val untilScheduledPoll = safeAdd(current.lastPollAtMs, current.pollIntervalMs) - nowMs
        val untilExpiry = current.session.expiresAtMs - nowMs
        return minOf(untilScheduledPoll.coerceAtLeast(0L), untilExpiry.coerceAtLeast(0L))
    }

    fun isExpired(current: DevicePairingMachineState, nowMs: Long): Boolean =
        nowMs >= current.session.expiresAtMs
}

private fun secondsToMillis(seconds: Long): Long = when {
    seconds <= 0 -> 1_000L
    seconds >= Long.MAX_VALUE / 1_000L -> Long.MAX_VALUE
    else -> seconds * 1_000L
}

private fun safeAdd(left: Long, right: Long): Long =
    if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

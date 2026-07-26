package org.aulama.iptv.mobile.data.playback

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import java.net.URI
import kotlin.math.ln
import kotlin.math.pow

data class PlaybackHealthSample(
    val candidateKey: String,
    val startupMs: Long,
    val bufferingRatio: Double,
    val fatalError: Boolean,
    val degraded: Boolean,
    val stableWatchMs: Long,
    val manualEarlyExit: Boolean,
    val observedAtMs: Long,
)

data class RouteHealthRecord(
    val samples: Int = 0,
    val successEwma: Double = 0.5,
    val startupMsEwma: Double = NEUTRAL_STARTUP_MS,
    val bufferingRatioEwma: Double = 0.05,
    val failureEwma: Double = 0.0,
    val stableWatchMinutesEwma: Double = 0.0,
    val manualEarlyExitEwma: Double = 0.0,
    val consecutiveFailures: Int = 0,
    val cooldownUntilMs: Long = 0L,
    val lastObservedAtMs: Long = 0L,
) {
    fun updated(sample: PlaybackHealthSample): RouteHealthRecord {
        val alpha = if (samples == 0) 1.0 else EWMA_ALPHA
        val failed = sample.fatalError || sample.degraded
        val stableSuccess = !failed && !sample.manualEarlyExit && sample.stableWatchMs >= STABLE_SUCCESS_MS
        val failures = when {
            failed -> (consecutiveFailures + 1).coerceAtMost(MAX_FAILURE_EXPONENT)
            stableSuccess -> 0
            else -> consecutiveFailures
        }
        return copy(
            samples = (samples + 1).coerceAtMost(10_000),
            successEwma = ewma(successEwma, if (stableSuccess) 1.0 else 0.0, alpha),
            startupMsEwma = ewma(startupMsEwma, sample.startupMs.coerceAtLeast(0).toDouble(), alpha),
            bufferingRatioEwma = ewma(
                bufferingRatioEwma,
                sample.bufferingRatio.coerceIn(0.0, 1.0),
                alpha,
            ),
            failureEwma = ewma(failureEwma, if (failed) 1.0 else 0.0, alpha),
            stableWatchMinutesEwma = ewma(
                stableWatchMinutesEwma,
                sample.stableWatchMs.coerceAtLeast(0) / 60_000.0,
                alpha,
            ),
            manualEarlyExitEwma = ewma(
                manualEarlyExitEwma,
                if (sample.manualEarlyExit) 1.0 else 0.0,
                alpha,
            ),
            consecutiveFailures = failures,
            cooldownUntilMs = if (failed) {
                sample.observedAtMs + RouteCooldownPolicy.durationMs(failures)
            } else if (stableSuccess) {
                0L
            } else {
                cooldownUntilMs
            },
            lastObservedAtMs = sample.observedAtMs,
        )
    }

    fun score(nowMs: Long, fourKCapable: Boolean = false, quality: ChannelQuality? = null): Double {
        if (samples == 0) {
            return NEUTRAL_SCORE + qualityBonus(fourKCapable, quality)
        }
        val startupScore = (20.0 - (startupMsEwma - 500.0).coerceAtLeast(0.0) / 575.0)
            .coerceIn(0.0, 20.0)
        val bufferScore = (20.0 * (1.0 - bufferingRatioEwma / 0.30)).coerceIn(0.0, 20.0)
        val stableScore = (ln(1.0 + stableWatchMinutesEwma) / ln(1.0 + 30.0) * 15.0)
            .coerceIn(0.0, 15.0)
        val raw = successEwma * 30.0 + startupScore + bufferScore +
            (1.0 - failureEwma) * 15.0 + stableScore - manualEarlyExitEwma * 12.0
        val ageMs = (nowMs - lastObservedAtMs).coerceAtLeast(0L)
        val recencyWeight = 0.5.pow(ageMs.toDouble() / HEALTH_HALF_LIFE_MS)
        val decayed = NEUTRAL_SCORE + (raw - NEUTRAL_SCORE) * recencyWeight
        return decayed + qualityBonus(fourKCapable, quality)
    }

    fun isCoolingDown(nowMs: Long): Boolean = cooldownUntilMs > nowMs

    private fun ewma(previous: Double, next: Double, alpha: Double): Double =
        previous * (1.0 - alpha) + next * alpha

    private fun qualityBonus(fourKCapable: Boolean, quality: ChannelQuality?): Double =
        if (fourKCapable && quality == ChannelQuality.UHD_4K) FOUR_K_BONUS else 0.0

    companion object {
        const val NEUTRAL_SCORE = 50.0
        private const val NEUTRAL_STARTUP_MS = 4_000.0
        private const val EWMA_ALPHA = 0.28
        private const val STABLE_SUCCESS_MS = 60_000L
        private const val FOUR_K_BONUS = 6.0
        private const val MAX_FAILURE_EXPONENT = 10
        private const val HEALTH_HALF_LIFE_MS = 7.0 * 24 * 60 * 60 * 1_000
    }
}

object RouteCooldownPolicy {
    fun durationMs(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val multiplier = 1L shl (consecutiveFailures - 1).coerceAtMost(10)
        return (BASE_MS * multiplier).coerceAtMost(MAX_MS)
    }

    private const val BASE_MS = 2 * 60_000L
    private const val MAX_MS = 30 * 60_000L
}

enum class DegradationReason {
    FATAL_ERROR,
    FIRST_FRAME_TIMEOUT,
    REPEATED_STALLS,
    HIGH_BUFFER_RATIO,
}

sealed interface PlaybackHealthDecision {
    data object Starting : PlaybackHealthDecision
    data object Healthy : PlaybackHealthDecision
    data class Degraded(val reason: DegradationReason) : PlaybackHealthDecision
}

object PlaybackDegradationPolicy {
    fun evaluate(
        elapsedMs: Long,
        firstFrameRendered: Boolean,
        stallsInLast45Seconds: Int,
        bufferingRatio: Double,
        fatalError: Boolean,
    ): PlaybackHealthDecision = when {
        fatalError -> PlaybackHealthDecision.Degraded(DegradationReason.FATAL_ERROR)
        !firstFrameRendered && elapsedMs >= FIRST_FRAME_TIMEOUT_MS ->
            PlaybackHealthDecision.Degraded(DegradationReason.FIRST_FRAME_TIMEOUT)
        !firstFrameRendered -> PlaybackHealthDecision.Starting
        stallsInLast45Seconds >= STALL_LIMIT ->
            PlaybackHealthDecision.Degraded(DegradationReason.REPEATED_STALLS)
        elapsedMs >= BUFFER_OBSERVATION_MS && bufferingRatio > MAX_BUFFER_RATIO ->
            PlaybackHealthDecision.Degraded(DegradationReason.HIGH_BUFFER_RATIO)
        else -> PlaybackHealthDecision.Healthy
    }

    const val FIRST_FRAME_TIMEOUT_MS = 12_000L
    const val STALL_WINDOW_MS = 45_000L
    const val BUFFER_OBSERVATION_MS = 60_000L
    const val MAX_BUFFER_RATIO = 0.15
    private const val STALL_LIMIT = 3
}

internal class RouteHealthStore(context: Context) {
    private val preferences = context.getSharedPreferences("aulama_mobile_route_health", 0)

    @Synchronized
    fun record(sample: PlaybackHealthSample) {
        val updated = get(sample.candidateKey).updated(sample)
        preferences.edit().putString(sample.candidateKey, RouteHealthJson.encode(updated)).apply()
    }

    @Synchronized
    fun get(candidateKey: String): RouteHealthRecord = preferences.getString(candidateKey, null)
        ?.let(RouteHealthJson::decode)
        ?: RouteHealthRecord()
}

enum class PlaybackTransport(val rank: Int) {
    HK_RELAY(0),
    JP_RELAY(1),
    DIRECT(2),
}

data class RelayPlanCandidate(
    val id: String,
    val url: String,
    val requiresBearer: Boolean,
)

data class PlaybackCandidate(
    val route: ChannelRoute,
    val sourceUrl: String,
    val transport: PlaybackTransport,
    val authorization: String? = null,
) {
    val key: String get() = "${transport.name}|$sourceUrl"
}

object PlaybackPlanPolicy {
    fun rank(
        routes: List<ChannelRoute>,
        manualPriorityUrls: List<String>,
        relayPlans: Map<String, List<RelayPlanCandidate>>,
        superAdmin: Boolean,
        accessToken: String?,
        fourKCapable: Boolean,
        health: (String) -> RouteHealthRecord,
        nowMs: Long,
    ): List<PlaybackCandidate> {
        val manualRanks = manualPriorityUrls.withIndex().associate { it.value to it.index }
        return routes.flatMap { route ->
            candidatesForRoute(route, relayPlans[route.url], superAdmin, accessToken)
        }.distinctBy(PlaybackCandidate::key).sortedWith { left, right ->
            val leftManual = manualRanks[left.sourceUrl]
            val rightManual = manualRanks[right.sourceUrl]
            when {
                leftManual != null || rightManual != null -> {
                    val leftRank = leftManual ?: Int.MAX_VALUE
                    val rightRank = rightManual ?: Int.MAX_VALUE
                    if (leftRank != rightRank) leftRank.compareTo(rightRank)
                    else left.transport.rank.compareTo(right.transport.rank)
                }
                left.transport.rank != right.transport.rank ->
                    left.transport.rank.compareTo(right.transport.rank)
                else -> {
                    val leftScore = health(left.key).score(nowMs, fourKCapable, left.route.quality)
                    val rightScore = health(right.key).score(nowMs, fourKCapable, right.route.quality)
                    when {
                        leftScore != rightScore -> rightScore.compareTo(leftScore)
                        left.route.sourceOrder != right.route.sourceOrder ->
                            left.route.sourceOrder.compareTo(right.route.sourceOrder)
                        else -> left.sourceUrl.compareTo(right.sourceUrl)
                    }
                }
            }
        }
    }

    private fun candidatesForRoute(
        route: ChannelRoute,
        relayPlan: List<RelayPlanCandidate>?,
        superAdmin: Boolean,
        accessToken: String?,
    ): List<PlaybackCandidate> {
        if (!superAdmin || accessToken.isNullOrBlank()) {
            return listOf(directCandidate(route))
        }
        val fromServer = relayPlan.orEmpty().mapNotNull { planned ->
            val transport = when (planned.id) {
                "hk_relay" -> PlaybackTransport.HK_RELAY
                "jp_relay" -> PlaybackTransport.JP_RELAY
                "direct" -> PlaybackTransport.DIRECT
                else -> null
            } ?: return@mapNotNull null
            val bearerAllowed = planned.requiresBearer && planned.url.isTrustedAulamaRelayUrl()
            if (planned.requiresBearer && !bearerAllowed) return@mapNotNull null
            PlaybackCandidate(
                route = if (transport == PlaybackTransport.DIRECT) route else route.copy(
                    url = planned.url,
                    label = "${transport.label()} · ${route.label}",
                    referrer = null,
                    userAgent = null,
                ),
                sourceUrl = route.url,
                transport = transport,
                authorization = if (bearerAllowed) "Bearer $accessToken" else null,
            )
        }
        return if (fromServer.any { it.transport == PlaybackTransport.DIRECT }) {
            fromServer
        } else {
            fromServer + directCandidate(route)
        }
    }

    private fun directCandidate(route: ChannelRoute) = PlaybackCandidate(
        route = route,
        sourceUrl = route.url,
        transport = PlaybackTransport.DIRECT,
    )

    private fun PlaybackTransport.label(): String = when (this) {
        PlaybackTransport.HK_RELAY -> "香港中轉"
        PlaybackTransport.JP_RELAY -> "日本中轉"
        PlaybackTransport.DIRECT -> "直接播放"
    }

    private fun String.isTrustedAulamaRelayUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("aulama.org", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null
    }.getOrDefault(false)
}

object AutomaticFallbackPolicy {
    fun choose(
        current: PlaybackCandidate,
        candidates: List<PlaybackCandidate>,
        health: (String) -> RouteHealthRecord,
        nowMs: Long,
        fourKCapable: Boolean,
        minimumImprovement: Double = 20.0,
    ): PlaybackCandidate? {
        val currentScore = health(current.key).score(nowMs, fourKCapable, current.route.quality)
        return candidates.asSequence()
            .filter { it.key != current.key }
            .filterNot { health(it.key).isCoolingDown(nowMs) }
            .firstOrNull {
                health(it.key).score(nowMs, fourKCapable, it.route.quality) >=
                    currentScore + minimumImprovement
            }
    }
}

internal object RouteHealthJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(record: RouteHealthRecord): String = buildJsonObject {
        put("samples", record.samples)
        put("success", record.successEwma)
        put("startup_ms", record.startupMsEwma)
        put("buffer_ratio", record.bufferingRatioEwma)
        put("failure", record.failureEwma)
        put("stable_minutes", record.stableWatchMinutesEwma)
        put("manual_exit", record.manualEarlyExitEwma)
        put("consecutive_failures", record.consecutiveFailures)
        put("cooldown_until", record.cooldownUntilMs)
        put("last_observed_at", record.lastObservedAtMs)
    }.toString()

    fun decode(value: String): RouteHealthRecord? = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        RouteHealthRecord(
            samples = root["samples"]?.jsonPrimitive?.intOrNull ?: return null,
            successEwma = root["success"]?.jsonPrimitive?.doubleOrNull ?: return null,
            startupMsEwma = root["startup_ms"]?.jsonPrimitive?.doubleOrNull ?: return null,
            bufferingRatioEwma = root["buffer_ratio"]?.jsonPrimitive?.doubleOrNull ?: return null,
            failureEwma = root["failure"]?.jsonPrimitive?.doubleOrNull ?: return null,
            stableWatchMinutesEwma = root["stable_minutes"]?.jsonPrimitive?.doubleOrNull ?: return null,
            manualEarlyExitEwma = root["manual_exit"]?.jsonPrimitive?.doubleOrNull ?: return null,
            consecutiveFailures = root["consecutive_failures"]?.jsonPrimitive?.intOrNull ?: 0,
            cooldownUntilMs = root["cooldown_until"]?.jsonPrimitive?.longOrNull ?: 0L,
            lastObservedAtMs = root["last_observed_at"]?.jsonPrimitive?.longOrNull ?: return null,
        )
    }.getOrNull()
}

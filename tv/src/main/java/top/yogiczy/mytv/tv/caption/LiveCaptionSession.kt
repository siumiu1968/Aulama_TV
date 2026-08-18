package top.yogiczy.mytv.tv.caption

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.tv.account.AulamaAccount
import top.yogiczy.mytv.tv.account.AulamaAccountManager
import top.yogiczy.mytv.tv.account.StrictAulamaHttpClient
import java.util.concurrent.TimeUnit

internal data class LiveCaptionSessionState(
    val mode: LiveCaptionMode = LiveCaptionMode.OFF,
    val status: LiveCaptionStatus = LiveCaptionStatus.OFF,
    val cue: LiveCaptionCue? = null,
    val visibleCue: LiveCaptionCue? = null,
    val message: String? = null,
    val errorCode: String? = null,
    val reconnectAttempt: Int = 0,
    val reconnectDelayMs: Long? = null,
    val quota: LiveCaptionQuota? = null,
)

private data class PendingCaptionPresentation(
    val cue: LiveCaptionCue,
    val token: Any = Any(),
    var job: Job? = null,
)

internal class LiveCaptionSession(
    private val accountManager: AulamaAccountManager = AulamaAccount.manager,
    private val client: OkHttpClient = StrictAulamaHttpClient.create().newBuilder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = CAPTION_WEBSOCKET_ENDPOINT,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _state = MutableStateFlow(LiveCaptionSessionState())
    val state: StateFlow<LiveCaptionSessionState> = _state.asStateFlow()

    private var generation = 0L
    private var mode = LiveCaptionMode.OFF
    private var subscription: LiveCaptionSubscription? = null
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var authRefreshAttempted = false
    private var terminal = false
    private var cueState = LiveCaptionCueState()
    private var presentedCueState = LiveCaptionCueState()
    private var serverClockAnchor: LiveCaptionClockAnchor? = null
    private val pendingPresentations = LinkedHashMap<String, PendingCaptionPresentation>()
    private var expiryJob: Job? = null

    fun start(channel: Channel, route: ChannelRoute, mode: LiveCaptionMode) {
        val nextSubscription = LiveCaptionSubscription.from(channel, route)
        val activeGeneration = synchronized(lock) {
            generation += 1
            closeTransportLocked()
            this.mode = mode
            subscription = nextSubscription
            reconnectAttempt = 0
            authRefreshAttempted = false
            terminal = false
            resetCaptionsLocked()
            _state.value = LiveCaptionSessionState(mode = mode)
            generation
        }
        when {
            mode == LiveCaptionMode.OFF -> Unit
            nextSubscription == null -> updateState(activeGeneration) {
                it.copy(
                    status = LiveCaptionStatus.UNSUPPORTED,
                    message = "此頻道暫未支援即時字幕",
                )
            }
            else -> connect(activeGeneration, forceTokenRefresh = false)
        }
    }

    fun setMode(nextMode: LiveCaptionMode) {
        val activeGeneration: Long
        val shouldConnect: Boolean
        val latestCue: LiveCaptionCue?
        synchronized(lock) {
            if (nextMode == mode) return
            mode = nextMode
            _state.value = _state.value.copy(mode = nextMode)
            if (nextMode == LiveCaptionMode.OFF) {
                generation += 1
                closeTransportLocked()
                reconnectAttempt = 0
                terminal = false
                resetCaptionsLocked()
                _state.value = LiveCaptionSessionState(mode = nextMode)
                return
            }
            shouldConnect = socket == null && subscription != null
            if (shouldConnect) {
                generation += 1
                reconnectAttempt = 0
                authRefreshAttempted = false
                terminal = false
                resetCaptionsLocked()
                _state.value = LiveCaptionSessionState(mode = nextMode)
            }
            activeGeneration = generation
            latestCue = cueState.current
            if (!shouldConnect) {
                _state.value = _state.value.copy(
                    visibleCue = selectLiveCaptionCue(
                        presentedCueState.history.values,
                        nextMode,
                        _state.value.visibleCue,
                    ),
                )
            }
        }
        if (shouldConnect) connect(activeGeneration, forceTokenRefresh = false)
        else latestCue?.let { schedulePresentation(activeGeneration, it) }
    }

    fun stop() {
        synchronized(lock) {
            generation += 1
            closeTransportLocked()
            subscription = null
            reconnectAttempt = 0
            authRefreshAttempted = false
            terminal = false
            resetCaptionsLocked()
            _state.value = LiveCaptionSessionState(mode = mode)
        }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun connect(activeGeneration: Long, forceTokenRefresh: Boolean) {
        updateState(activeGeneration) {
            it.copy(
                status = if (reconnectAttempt > 0) {
                    LiveCaptionStatus.RECONNECTING
                } else {
                    LiveCaptionStatus.CONNECTING
                },
                message = if (reconnectAttempt > 0) {
                    "字幕連線不穩，正在重試…"
                } else {
                    "正在連接即時字幕…"
                },
            )
        }
        scope.launch {
            val token = runCatching {
                accountManager.accessToken(forceRefresh = forceTokenRefresh)
            }.getOrNull()
            if (!isActive(activeGeneration)) return@launch
            if (token.isNullOrBlank()) {
                markTerminal(
                    activeGeneration,
                    LiveCaptionStatus.LOGIN_REQUIRED,
                    "請先登入 Aulama ID 使用即時字幕",
                )
                return@launch
            }
            val request = liveCaptionWebSocketRequest(token, endpoint)
            val listener = CaptionWebSocketListener(activeGeneration)
            val openedSocket = client.newWebSocket(request, listener)
            synchronized(lock) {
                if (isActiveLocked(activeGeneration)) socket = openedSocket
                else openedSocket.cancel()
            }
        }
    }

    private inner class CaptionWebSocketListener(
        private val activeGeneration: Long,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isActive(activeGeneration)) {
                webSocket.close(1000, "caption-session-stale")
                return
            }
            val handshake = synchronized(lock) { subscription?.toJson() } ?: return
            updateState(activeGeneration) {
                it.copy(status = LiveCaptionStatus.CONNECTED, message = "字幕準備中…")
            }
            if (!webSocket.send(handshake)) scheduleReconnect(activeGeneration, webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isActive(activeGeneration)) return
            handleServerMessage(activeGeneration, webSocket, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scheduleReconnect(activeGeneration, webSocket)
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            val statusCode = response?.code
            if (statusCode == 401 && markAuthRefresh(activeGeneration, webSocket)) {
                connect(activeGeneration, forceTokenRefresh = true)
                return
            }
            if (statusCode == 401) {
                markTerminal(
                    activeGeneration,
                    LiveCaptionStatus.LOGIN_REQUIRED,
                    "登入已過期，請重新登入 Aulama ID",
                )
                return
            }
            if (statusCode == 403) {
                markTerminal(
                    activeGeneration,
                    LiveCaptionStatus.PREMIUM_REQUIRED,
                    "即時字幕只限高級會員或以上",
                    "caption_premium_required",
                )
                return
            }
            scheduleReconnect(activeGeneration, webSocket)
        }
    }

    private fun handleServerMessage(
        activeGeneration: Long,
        webSocket: WebSocket,
        text: String,
    ) {
        val payload = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        val receivedAtMs = System.currentTimeMillis()
        val serverNowMs = payload.longOrNull("server_now_ms") ?: payload.longOrNull("serverNowMs")
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            if (serverNowMs != null) {
                serverClockAnchor = LiveCaptionClockAnchor(serverNowMs, receivedAtMs)
            }
        }
        when (payload.string("type")) {
            "ping" -> webSocket.send("{\"type\":\"pong\"}")
            "subscribed" -> {
                synchronized(lock) {
                    if (!isActiveLocked(activeGeneration)) return
                    reconnectAttempt = 0
                    authRefreshAttempted = false
                }
                updateState(activeGeneration) {
                    it.copy(
                        status = LiveCaptionStatus.CONNECTED,
                        message = "字幕準備中…",
                        reconnectAttempt = 0,
                        reconnectDelayMs = null,
                    )
                }
            }
            "quota" -> updateQuota(activeGeneration, payload.objectOrNull("quota"))
            "snapshot" -> payload.arrayOrNull("cues")?.forEach { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    acceptCue(activeGeneration, it, receivedAtMs, serverNowMs)
                }
            }
            "state" -> {
                val serverState = payload.string("state")
                updateState(activeGeneration) {
                    it.copy(
                        status = LiveCaptionStatus.CONNECTED,
                        message = when (serverState) {
                            "pipeline_lagging" -> "字幕正在追上直播…"
                            else -> payload.string("message").ifBlank { "字幕準備中…" }
                        },
                    )
                }
                updateQuota(activeGeneration, payload.objectOrNull("quota"))
            }
            "error" -> {
                val mapped = mapLiveCaptionError(payload.string("code"), payload.string("message"))
                updateQuota(activeGeneration, payload.objectOrNull("quota"))
                markTerminal(activeGeneration, mapped.status, mapped.message, mapped.code)
                webSocket.close(1000, "caption-error")
            }
            "cue", "" -> acceptCue(activeGeneration, payload, receivedAtMs, serverNowMs)
        }
    }

    private fun acceptCue(
        activeGeneration: Long,
        payload: JsonObject,
        receivedAtMs: Long,
        eventServerNowMs: Long?,
    ) {
        val derivedServerNowMs = synchronized(lock) {
            eventServerNowMs ?: serverClockAnchor?.serverNowAt(receivedAtMs)
        }
        val cue = payload.toLiveCaptionCue(receivedAtMs, derivedServerNowMs) ?: return
        var accepted: LiveCaptionCue? = null
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            val next = reduceLiveCaptionCue(cueState, cue)
            if (next == cueState) return
            cueState = next
            accepted = next.current
        }
        accepted?.let { schedulePresentation(activeGeneration, it) }
    }

    private fun schedulePresentation(activeGeneration: Long, cue: LiveCaptionCue) {
        val key = liveCaptionCueKey(cue)
        var immediateCue: LiveCaptionCue? = null
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            val currentCues = pendingPresentations.mapValues { it.value.cue }
            val nextCues = reducePendingLiveCaptionPresentations(currentCues, cue)
            if (nextCues == currentCues) return

            pendingPresentations.keys.toList().forEach { pendingKey ->
                if (pendingKey !in nextCues || pendingKey == key) {
                    pendingPresentations.remove(pendingKey)?.job?.cancel()
                }
            }
            val scheduledCue = nextCues[key] ?: return
            val presentationDelayMs = liveCaptionPresentationDelayMs(
                scheduledCue,
                mode,
                System.currentTimeMillis(),
            )
            if (presentationDelayMs <= 0L) {
                immediateCue = scheduledCue
                return@synchronized
            }

            val entry = PendingCaptionPresentation(scheduledCue)
            pendingPresentations[key] = entry
            entry.job = scope.launch {
                delay(presentationDelayMs)
                val readyCue = synchronized(lock) {
                    pendingPresentations[key]
                        ?.takeIf { it.token === entry.token }
                        ?.also { pendingPresentations.remove(key) }
                        ?.cue
                }
                readyCue?.let { presentCue(activeGeneration, it) }
            }
        }
        immediateCue?.let { presentCue(activeGeneration, it) }
    }

    private fun presentCue(activeGeneration: Long, cue: LiveCaptionCue) {
        val visible: LiveCaptionCue?
        val expiryDelayMs: Long?
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            presentedCueState = reduceLiveCaptionCue(presentedCueState, cue)
            visible = selectLiveCaptionCue(
                presentedCueState.history.values,
                mode,
                _state.value.visibleCue,
            )
            _state.value = _state.value.copy(
                cue = presentedCueState.current,
                visibleCue = visible,
            )
            expiryJob?.cancel()
            expiryDelayMs = visible?.let {
                liveCaptionExpiryDelayMs(it, mode, System.currentTimeMillis())
            }
        }
        if (visible != null && expiryDelayMs != null) {
            expiryJob = scope.launch {
                delay(expiryDelayMs)
                synchronized(lock) {
                    if (isActiveLocked(activeGeneration) && _state.value.visibleCue == visible) {
                        _state.value = _state.value.copy(visibleCue = null)
                    }
                }
            }
        }
    }

    private fun updateQuota(activeGeneration: Long, payload: JsonObject?) {
        val quota = payload?.toLiveCaptionQuota() ?: return
        updateState(activeGeneration) { it.copy(quota = quota) }
        if (quota.exhausted) {
            markTerminal(
                activeGeneration,
                LiveCaptionStatus.QUOTA_EXHAUSTED,
                "今日 120 分鐘即時字幕額度已用完",
                "caption_quota_exhausted",
            )
        }
    }

    private fun scheduleReconnect(activeGeneration: Long, webSocket: WebSocket) {
        val delayMs: Long
        val attempt: Int
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration) || terminal || socket !== webSocket) return
            socket = null
            reconnectAttempt += 1
            attempt = reconnectAttempt
            delayMs = LiveCaptionReconnectPolicy.delayMs(attempt) ?: run {
                terminal = true
                _state.value = _state.value.copy(
                    status = LiveCaptionStatus.UNAVAILABLE,
                    message = "字幕服務暫時未能連線，請稍後再試。",
                    errorCode = "reconnect_exhausted",
                    reconnectAttempt = attempt - 1,
                    reconnectDelayMs = null,
                )
                return
            }
            reconnectJob?.cancel()
            _state.value = _state.value.copy(
                status = LiveCaptionStatus.RECONNECTING,
                message = "字幕連線不穩，正在重試…",
                reconnectAttempt = attempt,
                reconnectDelayMs = delayMs,
            )
        }
        reconnectJob = scope.launch {
            delay(delayMs)
            if (isActive(activeGeneration)) connect(activeGeneration, forceTokenRefresh = false)
        }
    }

    private fun markAuthRefresh(activeGeneration: Long, webSocket: WebSocket): Boolean =
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration) || terminal || socket !== webSocket ||
                authRefreshAttempted
            ) return@synchronized false
            authRefreshAttempted = true
            socket = null
            true
        }

    private fun markTerminal(
        activeGeneration: Long,
        status: LiveCaptionStatus,
        message: String,
        errorCode: String? = null,
    ) {
        synchronized(lock) {
            if (!isActiveLocked(activeGeneration)) return
            terminal = true
            reconnectJob?.cancel()
            reconnectJob = null
            socket?.cancel()
            socket = null
            _state.value = _state.value.copy(
                status = status,
                message = message,
                errorCode = errorCode,
                reconnectDelayMs = null,
            )
        }
    }

    private fun updateState(
        activeGeneration: Long,
        transform: (LiveCaptionSessionState) -> LiveCaptionSessionState,
    ) {
        synchronized(lock) {
            if (isActiveLocked(activeGeneration)) _state.value = transform(_state.value)
        }
    }

    private fun isActive(activeGeneration: Long): Boolean = synchronized(lock) {
        isActiveLocked(activeGeneration)
    }

    private fun isActiveLocked(activeGeneration: Long): Boolean =
        activeGeneration == generation && mode != LiveCaptionMode.OFF

    private fun closeTransportLocked() {
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "caption-session-stop")
        socket = null
    }

    private fun resetCaptionsLocked() {
        pendingPresentations.values.forEach { it.job?.cancel() }
        pendingPresentations.clear()
        expiryJob?.cancel()
        expiryJob = null
        cueState = LiveCaptionCueState()
        presentedCueState = LiveCaptionCueState()
        serverClockAnchor = null
    }
}

internal fun liveCaptionWebSocketRequest(
    accessToken: String,
    endpoint: String = CAPTION_WEBSOCKET_ENDPOINT,
): Request = Request.Builder()
    .url(endpoint)
    .header("Authorization", "Bearer $accessToken")
    .header("Origin", CAPTION_ORIGIN)
    .build()

private fun JsonObject.string(name: String): String = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
}.getOrDefault("")

private fun JsonObject.longOrNull(name: String): Long? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asLong
}.getOrNull()

private fun JsonObject.boolean(name: String): Boolean = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean == true
}.getOrDefault(false)

private fun JsonObject.objectOrNull(name: String): JsonObject? = runCatching {
    getAsJsonObject(name)
}.getOrNull()

private fun JsonObject.arrayOrNull(name: String) = runCatching {
    getAsJsonArray(name)
}.getOrNull()

private fun JsonObject.toLiveCaptionCue(
    receivedAtMs: Long,
    fallbackServerNowMs: Long?,
): LiveCaptionCue? {
    val english = string("english").ifBlank { string("source") }.ifBlank { string("text") }
    val zhHant = string("zhHant").ifBlank { string("zh_hant") }
        .ifBlank { string("translated") }.ifBlank { string("translation") }
    if (english.isBlank() && zhHant.isBlank()) return null
    val sourceId = string("source_id").ifBlank { string("sourceId") }
    val sequence = longOrNull("seq")
    val cueId = string("cue_id").ifBlank { string("cueId") }.ifBlank { string("id") }
        .ifBlank { listOfNotNull(sourceId.takeIf(String::isNotBlank), sequence?.toString()).joinToString(":") }
    return LiveCaptionCue(
        cueId = cueId,
        seq = sequence,
        revision = longOrNull("revision") ?: 0,
        sourceId = sourceId,
        english = english,
        zhHant = zhHant,
        final = boolean("final") || boolean("is_final"),
        stability = string("stability").ifBlank { if (boolean("final")) "final" else "partial" },
        audioStartMs = longOrNull("audio_started_at_ms") ?: longOrNull("audio_start_ms")
            ?: longOrNull("audioStartMs"),
        audioEndMs = longOrNull("audio_ended_at_ms") ?: longOrNull("audio_end_ms")
            ?: longOrNull("audioEndMs"),
        emittedAtMs = longOrNull("emitted_at_ms") ?: longOrNull("emittedAtMs"),
        serverNowMs = longOrNull("server_now_ms") ?: longOrNull("serverNowMs")
            ?: fallbackServerNowMs,
        receivedAtMs = receivedAtMs,
        processingMs = longOrNull("processing_ms") ?: longOrNull("processingMs"),
        delayMs = longOrNull("delay_ms") ?: longOrNull("delayMs"),
        suggestedHoldMs = longOrNull("suggested_hold_ms") ?: longOrNull("suggestedHoldMs"),
    )
}

private fun JsonObject.toLiveCaptionQuota(): LiveCaptionQuota = LiveCaptionQuota(
    limited = boolean("limited"),
    limitMs = longOrNull("limitMs") ?: longOrNull("limit_ms"),
    usedMs = longOrNull("usedMs") ?: longOrNull("used_ms") ?: 0,
    remainingMs = longOrNull("remainingMs") ?: longOrNull("remaining_ms"),
    resetAt = string("resetAt").ifBlank { string("reset_at") },
    exhausted = boolean("exhausted"),
)

private const val CAPTION_ORIGIN = "https://aulama.org"
private const val CAPTION_WEBSOCKET_ENDPOINT =
    "https://aulama.org/hermes-auth/iptv/captions/ws"

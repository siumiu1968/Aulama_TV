package org.aulama.iptv.mobile.data.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.aulama.iptv.mobile.data.playback.RelayPlanCandidate
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal const val AULAMA_ORIGIN = "https://aulama.org"

internal interface AulamaAuthGateway {
    suspend fun googleNonce(appVersion: String): GoogleNonce
    suspend fun verifyGoogle(idToken: String, nonce: String, appVersion: String): AulamaSessionTokens
    suspend fun passkeyOptions(appVersion: String): PasskeyRequest
    suspend fun verifyPasskey(requestId: String, assertionJson: String, appVersion: String): AulamaSessionTokens
    suspend fun refresh(refreshToken: String): AulamaSessionTokens
    suspend fun profile(accessToken: String): AulamaAccountProfile
    suspend fun revoke(refreshToken: String)
    suspend fun approveDevice(accessToken: String, userCode: String)
    suspend fun relayPlan(
        accessToken: String,
        routeUrl: String,
        referrer: String?,
        userAgent: String?,
    ): List<RelayPlanCandidate>
    suspend fun getSync(accessToken: String): SyncDocument
    suspend fun putSync(accessToken: String, document: SyncDocument): SyncDocument
}

internal class AulamaAuthApi(
    private val client: OkHttpClient = StrictAulamaHttpClient.create(),
) : AulamaAuthGateway {
    override suspend fun googleNonce(appVersion: String): GoogleNonce {
        val response = post(
            path = "/hermes-auth/iptv/mobile/google/nonce",
            payload = clientPayload(appVersion),
        )
        ensureSuccess(response)
        val json = response.objectBody()
        return GoogleNonce(
            value = json.string("nonce") ?: throw AulamaApiException.ProtocolFailure(),
            serverClientId = json.string("server_client_id")
                ?: throw AulamaApiException.ConfigurationUnavailable(),
            expiresInSeconds = json.long("expires_in")?.takeIf { it > 0 } ?: 300L,
        )
    }

    override suspend fun verifyGoogle(
        idToken: String,
        nonce: String,
        appVersion: String,
    ): AulamaSessionTokens {
        val response = post(
            path = "/hermes-auth/iptv/mobile/google/verify",
            payload = buildJsonObject {
                put("client", "android_mobile")
                put("app_version", appVersion)
                put("id_token", idToken)
                put("nonce", nonce)
            },
        )
        return response.tokens()
    }

    override suspend fun passkeyOptions(appVersion: String): PasskeyRequest {
        val response = post(
            path = "/hermes-auth/iptv/mobile/passkey/options",
            payload = clientPayload(appVersion),
        )
        ensureSuccess(response)
        return AulamaAuthResponseParser.parsePasskeyRequest(response.body)
            ?: throw AulamaApiException.ProtocolFailure()
    }

    override suspend fun verifyPasskey(
        requestId: String,
        assertionJson: String,
        appVersion: String,
    ): AulamaSessionTokens {
        val assertion = runCatching { JSON.parseToJsonElement(assertionJson) }
            .getOrElse { throw AulamaApiException.ProtocolFailure() }
        val response = post(
            path = "/hermes-auth/iptv/mobile/passkey/verify",
            payload = buildJsonObject {
                put("client", "android_mobile")
                put("app_version", appVersion)
                put("challenge_id", requestId)
                put("credential", assertion)
            },
        )
        return response.tokens()
    }

    override suspend fun refresh(refreshToken: String): AulamaSessionTokens {
        val response = post(
            path = "/hermes-auth/iptv/device/refresh",
            payload = buildJsonObject { put("refresh_token", refreshToken) },
        )
        return response.tokens()
    }

    override suspend fun profile(accessToken: String): AulamaAccountProfile {
        val response = get("/hermes-auth/iptv/me", accessToken)
        ensureSuccess(response)
        return response.objectBody().profile()
    }

    override suspend fun revoke(refreshToken: String) {
        val response = post(
            path = "/hermes-auth/iptv/device/revoke",
            payload = buildJsonObject { put("refresh_token", refreshToken) },
        )
        if (response.statusCode !in 200..299 && response.statusCode !in setOf(401, 403, 404)) {
            throw response.failure()
        }
    }

    override suspend fun approveDevice(accessToken: String, userCode: String) {
        val response = post(
            path = "/hermes-auth/iptv/device/approve",
            payload = buildJsonObject {
                put("user_code", userCode)
                put("approve", true)
            },
            accessToken = accessToken,
        )
        ensureSuccess(response)
    }

    override suspend fun relayPlan(
        accessToken: String,
        routeUrl: String,
        referrer: String?,
        userAgent: String?,
    ): List<RelayPlanCandidate> {
        val url = AulamaRelayPlanRequest.url(routeUrl, referrer, userAgent)
        val response = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        )
        if (AulamaHttpStatusPolicy.relayPlanFallsBackToDirect(response.statusCode)) {
            return emptyList()
        }
        ensureSuccess(response)
        return AulamaAuthResponseParser.parseRelayPlan(response.body)
            ?: throw AulamaApiException.ProtocolFailure()
    }

    override suspend fun getSync(accessToken: String): SyncDocument {
        val response = get("/hermes-auth/iptv/sync", accessToken)
        ensureSuccess(response)
        return SyncJsonCodec.parseDocument(response.body)
            ?: throw AulamaApiException.ProtocolFailure()
    }

    override suspend fun putSync(accessToken: String, document: SyncDocument): SyncDocument {
        val response = execute(
            Request.Builder()
                .url("$AULAMA_ORIGIN/hermes-auth/iptv/sync")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .header("If-Match", "\"${document.revision}\"")
                .put(
                    buildJsonObject {
                        put("sync", SyncJsonCodec.toJson(document.payload))
                    }.toString().toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
        )
        if (response.statusCode == 409) {
            val revision = runCatching { response.objectBody().long("current_revision") }.getOrNull()
            throw AulamaApiException.Conflict(revision)
        }
        ensureSuccess(response)
        return SyncJsonCodec.parseDocument(response.body)
            ?: throw AulamaApiException.ProtocolFailure()
    }

    private fun clientPayload(appVersion: String) = buildJsonObject {
        put("client", "android_mobile")
        put("app_version", appVersion)
    }

    private suspend fun get(path: String, accessToken: String): HttpResponse = execute(
        Request.Builder()
            .url("$AULAMA_ORIGIN$path")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", JSON_MEDIA_TYPE.toString())
            .get()
            .build()
    )

    private suspend fun post(
        path: String,
        payload: JsonObject,
        accessToken: String? = null,
    ): HttpResponse {
        val builder = Request.Builder()
            .url("$AULAMA_ORIGIN$path")
            .header("Accept", JSON_MEDIA_TYPE.toString())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return execute(builder.build())
    }

    private suspend fun execute(request: Request): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    continuation.resumeWith(Result.failure(exception))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val contentLength = it.body?.contentLength() ?: 0L
                            if (contentLength > MAX_RESPONSE_BYTES) {
                                throw AulamaApiException.ProtocolFailure()
                            }
                            val body = it.body?.string().orEmpty()
                            if (body.toByteArray().size > MAX_RESPONSE_BYTES) {
                                throw AulamaApiException.ProtocolFailure()
                            }
                            HttpResponse(it.code, body)
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }

    private fun ensureSuccess(response: HttpResponse) {
        if (response.statusCode !in 200..299) throw response.failure()
    }

    private fun HttpResponse.tokens(): AulamaSessionTokens {
        ensureSuccess(this)
        val json = objectBody()
        return AulamaSessionTokens(
            accessToken = json.string("access_token")
                ?: throw AulamaApiException.ProtocolFailure(),
            refreshToken = json.string("refresh_token")
                ?: throw AulamaApiException.ProtocolFailure(),
            expiresInSeconds = json.long("expires_in")?.takeIf { it > 0 }
                ?: throw AulamaApiException.ProtocolFailure(),
            profile = json["profile"]?.let {
                runCatching { it.jsonObject.profile() }
                    .getOrElse { throw AulamaApiException.ProtocolFailure() }
            },
        )
    }

    private fun HttpResponse.failure(): AulamaApiException {
        val errorCode = runCatching { objectBody().string("error") }.getOrNull()
        return when {
            statusCode == 401 -> AulamaApiException.Unauthorized()
            statusCode == 403 -> AulamaApiException.Forbidden()
            statusCode in setOf(404, 405, 501) || errorCode == "server_configuration_error" ->
                AulamaApiException.ConfigurationUnavailable()
            else -> AulamaApiException.HttpFailure(statusCode)
        }
    }

    private fun HttpResponse.objectBody(): JsonObject = runCatching {
        JSON.parseToJsonElement(body).jsonObject
    }.getOrElse { throw AulamaApiException.ProtocolFailure() }

    private data class HttpResponse(val statusCode: Int, val body: String)

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RESPONSE_BYTES = 512 * 1024L
    }
}

internal object AulamaHttpStatusPolicy {
    fun relayPlanFallsBackToDirect(statusCode: Int): Boolean = statusCode == 403
}

internal object AulamaRelayPlanRequest {
    fun url(routeUrl: String, referrer: String?, userAgent: String?) =
        "$AULAMA_ORIGIN/hermes-auth/iptv/relay/plan".toHttpUrl().newBuilder()
            .addQueryParameter("kind", "hls")
            .addQueryParameter("url", routeUrl)
            .apply {
                referrer.bounded(MAX_REFERRER_LENGTH)?.let { addQueryParameter("referrer", it) }
                userAgent.bounded(MAX_USER_AGENT_LENGTH)?.let { addQueryParameter("user_agent", it) }
            }
            .build()

    private fun String?.bounded(maxLength: Int): String? = this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(maxLength)

    const val MAX_REFERRER_LENGTH = 2_048
    const val MAX_USER_AGENT_LENGTH = 512
}

internal object AulamaAuthResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePasskeyRequest(body: String): PasskeyRequest? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val challengeId = root.string("challenge_id")
            ?: root.string("request_id")
            ?: return null
        val requestElement = root["publicKey"]
            ?: root["public_key"]
            ?: root["request_options"]
            ?: root["options"]
            ?: return null
        val requestJson = if (requestElement is JsonPrimitive && requestElement.isString) {
            requestElement.content
        } else {
            requestElement.toString()
        }
        requestJson.takeIf(String::isNotBlank)?.let { PasskeyRequest(challengeId, it) }
    }.getOrNull()

    fun parseRelayPlan(body: String): List<RelayPlanCandidate>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["candidates"]?.jsonArray?.map { element ->
            val candidate = element.jsonObject
            RelayPlanCandidate(
                id = candidate.string("id") ?: return null,
                url = candidate.string("url") ?: return null,
                requiresBearer = candidate.string("authorization") == "bearer",
            )
        } ?: return null
    }.getOrNull()
}

private fun JsonObject.profile(): AulamaAccountProfile {
    val isSuperAdmin = boolean("is_super_admin") ?: false
    val role = string("role")?.lowercase() ?: if (isSuperAdmin) "super_admin" else "free"
    return AulamaAccountProfile(
        id = string("uid")
            ?: string("user_id")
            ?: string("id")
            ?: string("email")
            ?: throw AulamaApiException.ProtocolFailure(),
        email = string("email"),
        displayName = string("display_name") ?: string("displayName"),
        role = role,
        isSuperAdmin = isSuperAdmin || role == "super_admin",
    )
}

internal object StrictAulamaHttpClient {
    fun create(): OkHttpClient {
        val trustManager = systemTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), null)
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    AulamaDnsPolicy.forHost(hostname, Dns.SYSTEM.lookup(hostname))
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps || !url.host.equals("aulama.org", ignoreCase = true)) {
                    throw SSLPeerUnverifiedException("Account request must use aulama.org HTTPS")
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    private fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().singleOrNull()
            ?: error("No system X509TrustManager")
    }
}

internal object AulamaDnsPolicy {
    private val accountOrigin = InetAddress.getByAddress(
        byteArrayOf(138.toByte(), 2, 40, 170.toByte()),
    )

    fun forHost(hostname: String, addresses: List<InetAddress>): List<InetAddress> {
        val ordered = ipv4First(addresses)
        return if (hostname.equals("aulama.org", ignoreCase = true)) {
            (listOf(accountOrigin) + ordered).distinct()
        } else {
            ordered
        }
    }

    fun ipv4First(addresses: List<InetAddress>): List<InetAddress> =
        addresses.sortedBy { address -> if (address is Inet4Address) 0 else 1 }
}

private fun JsonObject.string(name: String): String? = this[name]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)

private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? = this[name]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.toBooleanStrictOrNull()

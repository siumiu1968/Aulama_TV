package top.yogiczy.mytv.tv.account

import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal sealed class AulamaAccountApiException(message: String) : IOException(message) {
    class ConfigurationUnavailable : AulamaAccountApiException("Pairing API is unavailable")
    class Unauthorized : AulamaAccountApiException("Account session is unauthorized")
    class ProtocolFailure : AulamaAccountApiException("Account API returned an invalid response")
    class HttpFailure(val statusCode: Int) :
        AulamaAccountApiException("Account API HTTP $statusCode")
}

internal interface AulamaAccountGateway {
    suspend fun startDevicePairing(
        deviceName: String,
        appVersion: String,
    ): DeviceStartResponse

    suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPollResult
    suspend fun refresh(refreshToken: String): AulamaSessionTokens
    suspend fun getProfile(accessToken: String): AulamaAccountProfile
    suspend fun getSync(accessToken: String): AulamaSyncDocument
    suspend fun putSync(
        accessToken: String,
        expectedRevision: Long,
        payload: AulamaSyncPayload,
    ): AulamaSyncDocument

    suspend fun resolveRelayPlan(
        accessToken: String,
        url: String,
        referrer: String?,
        userAgent: String?,
    ): List<AulamaPlanCandidate>
}

internal class AulamaAccountApi(
    private val client: OkHttpClient = StrictAulamaHttpClient.create(),
) : AulamaAccountGateway {
    override suspend fun startDevicePairing(
        deviceName: String,
        appVersion: String,
    ): DeviceStartResponse {
        val payload = JsonObject().apply {
            addProperty("client", "android_tv")
            addProperty("device_name", deviceName)
            addProperty("app_version", appVersion)
        }
        val response = post("/hermes-auth/iptv/device/start", payload)
        return when (
            val parsed = AulamaAccountResponseParser.parseDeviceStart(
                response.statusCode,
                response.body,
            )
        ) {
            is DeviceStartParseResult.Success -> parsed.response
            DeviceStartParseResult.ConfigurationUnavailable ->
                throw AulamaAccountApiException.ConfigurationUnavailable()

            DeviceStartParseResult.InvalidResponse ->
                throw AulamaAccountApiException.ProtocolFailure()

            is DeviceStartParseResult.HttpFailure ->
                throw AulamaAccountApiException.HttpFailure(parsed.statusCode)
        }
    }

    override suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPollResult {
        val payload = JsonObject().apply { addProperty("device_code", deviceCode) }
        val response = post("/hermes-auth/iptv/device/token", payload)
        return AulamaAccountResponseParser.parseDeviceToken(response.statusCode, response.body)
    }

    override suspend fun refresh(refreshToken: String): AulamaSessionTokens {
        val payload = JsonObject().apply { addProperty("refresh_token", refreshToken) }
        val response = post("/hermes-auth/iptv/device/refresh", payload)
        return when (
            val parsed = AulamaAccountResponseParser.parseDeviceToken(
                response.statusCode,
                response.body,
            )
        ) {
            is DeviceTokenPollResult.Authorized -> parsed.tokens
            DeviceTokenPollResult.ConfigurationUnavailable ->
                throw AulamaAccountApiException.ConfigurationUnavailable()

            is DeviceTokenPollResult.Rejected -> if (
                response.statusCode == 401 ||
                response.statusCode == 403 ||
                parsed.errorCode in INVALID_REFRESH_ERRORS
            ) {
                throw AulamaAccountApiException.Unauthorized()
            } else {
                throw AulamaAccountApiException.HttpFailure(response.statusCode)
            }

            else -> if (response.statusCode in 200..299) {
                throw AulamaAccountApiException.ProtocolFailure()
            } else {
                throw AulamaAccountApiException.HttpFailure(response.statusCode)
            }
        }
    }

    override suspend fun getProfile(accessToken: String): AulamaAccountProfile {
        val request = Request.Builder()
            .url("$AULAMA_ACCOUNT_ORIGIN/hermes-auth/iptv/me")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", JSON_MEDIA_TYPE.toString())
            .get()
            .build()
        val response = execute(request)
        return when (
            val parsed = AulamaAccountResponseParser.parseProfile(
                response.statusCode,
                response.body,
            )
        ) {
            is ProfileParseResult.Success -> parsed.profile
            ProfileParseResult.Unauthorized -> throw AulamaAccountApiException.Unauthorized()
            ProfileParseResult.ConfigurationUnavailable ->
                throw AulamaAccountApiException.ConfigurationUnavailable()

            ProfileParseResult.InvalidResponse ->
                throw AulamaAccountApiException.ProtocolFailure()

            is ProfileParseResult.HttpFailure ->
                throw AulamaAccountApiException.HttpFailure(parsed.statusCode)
        }
    }

    override suspend fun getSync(accessToken: String): AulamaSyncDocument {
        val response = execute(authorizedRequest("/hermes-auth/iptv/sync", accessToken).get().build())
        checkAuthorized(response)
        if (response.statusCode !in 200..299) {
            throw AulamaAccountApiException.HttpFailure(response.statusCode)
        }
        return AulamaSyncProtocol.parseDocument(response.body)
            ?: throw AulamaAccountApiException.ProtocolFailure()
    }

    override suspend fun putSync(
        accessToken: String,
        expectedRevision: Long,
        payload: AulamaSyncPayload,
    ): AulamaSyncDocument {
        val body = JsonObject().apply { add("sync", AulamaSyncProtocol.toJson(payload)) }
        val request = authorizedRequest("/hermes-auth/iptv/sync", accessToken)
            .header("If-Match", "\"$expectedRevision\"")
            .put(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        checkAuthorized(response)
        if (response.statusCode == 409) {
            val revision = AulamaSyncProtocol.parseConflictRevision(response.body)
                ?: throw AulamaAccountApiException.ProtocolFailure()
            throw AulamaSyncConflict(revision)
        }
        if (response.statusCode !in 200..299) {
            throw AulamaAccountApiException.HttpFailure(response.statusCode)
        }
        return AulamaSyncProtocol.parseDocument(response.body)
            ?: throw AulamaAccountApiException.ProtocolFailure()
    }

    override suspend fun resolveRelayPlan(
        accessToken: String,
        url: String,
        referrer: String?,
        userAgent: String?,
    ): List<AulamaPlanCandidate> {
        val commonUrl = AULAMA_ACCOUNT_ORIGIN.toHttpUrl().newBuilder()
            .addPathSegments("hermes-auth/iptv/relay/plan")
            .addQueryParameter("kind", "hls")
            .addQueryParameter("url", url)
            .apply {
                referrer?.takeIf { it.isNotBlank() }?.let { addQueryParameter("referrer", it) }
                userAgent?.takeIf { it.isNotBlank() }?.let { addQueryParameter("user_agent", it) }
            }
            .build()
        val response = execute(
            Request.Builder()
                .url(commonUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        )
        if (response.statusCode == 401) {
            throw AulamaAccountApiException.Unauthorized()
        }
        if (AulamaRelayPlanHttpPolicy.shouldUseDirectFallback(response.statusCode)) {
            return emptyList()
        }
        if (response.statusCode in 200..299) {
            return AulamaSyncProtocol.parseRelayPlan(response.body)
        }
        throw AulamaAccountApiException.HttpFailure(response.statusCode)
    }

    private suspend fun post(path: String, payload: JsonObject): HttpResponse {
        val request = Request.Builder()
            .url("$AULAMA_ACCOUNT_ORIGIN$path")
            .header("Accept", JSON_MEDIA_TYPE.toString())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request)
    }

    private fun authorizedRequest(path: String, accessToken: String): Request.Builder =
        Request.Builder()
            .url("$AULAMA_ACCOUNT_ORIGIN$path")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", JSON_MEDIA_TYPE.toString())

    private fun checkAuthorized(response: HttpResponse) {
        if (response.statusCode == 401 || response.statusCode == 403) {
            throw AulamaAccountApiException.Unauthorized()
        }
        if (response.statusCode in OPTIONAL_ENDPOINT_STATUS_CODES &&
            response.body.contains("configuration", ignoreCase = true)
        ) {
            throw AulamaAccountApiException.ConfigurationUnavailable()
        }
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
                                throw AulamaAccountApiException.ProtocolFailure()
                            }
                            val body = it.body?.string().orEmpty()
                            if (body.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                                throw AulamaAccountApiException.ProtocolFailure()
                            }
                            HttpResponse(
                                statusCode = it.code,
                                body = body,
                                location = it.header("Location"),
                            )
                        }
                    }

                    continuation.resumeWith(result)
                }
            })
        }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val location: String? = null,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RESPONSE_BYTES = 128 * 1024L
        val INVALID_REFRESH_ERRORS = setOf(
            "invalid_grant",
            "invalid_token",
            "invalid_refresh_token",
        )
        val OPTIONAL_ENDPOINT_STATUS_CODES = setOf(404, 405, 501)
    }
}

internal object AulamaRelayPlanHttpPolicy {
    fun shouldUseDirectFallback(statusCode: Int): Boolean =
        statusCode == 403 || statusCode == 404 || statusCode == 405 || statusCode == 501
}

internal object StrictAulamaHttpClient {
    fun create(): OkHttpClient {
        val trustManager = systemTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), null)
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
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
        return factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .singleOrNull()
            ?: error("No system X509TrustManager")
    }
}

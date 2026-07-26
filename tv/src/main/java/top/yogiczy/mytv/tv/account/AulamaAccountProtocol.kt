package top.yogiczy.mytv.tv.account

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

internal const val AULAMA_ACCOUNT_ORIGIN = "https://aulama.org"

internal data class DeviceStartResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

internal data class AulamaSessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class AulamaAccountProfile(
    val id: String,
    val email: String?,
    val displayName: String?,
    val role: String,
    val isSuperAdmin: Boolean,
) {
    val primaryLabel: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: "Aulama ID"

    val roleLabel: String
        get() = when {
            isSuperAdmin || role == "super_admin" -> "最高管理員"
            role == "admin" -> "管理員"
            role == "premium" -> "Premium"
            else -> "一般用戶"
        }
}

internal sealed interface DeviceStartParseResult {
    data class Success(val response: DeviceStartResponse) : DeviceStartParseResult
    data object ConfigurationUnavailable : DeviceStartParseResult
    data object InvalidResponse : DeviceStartParseResult
    data class HttpFailure(val statusCode: Int) : DeviceStartParseResult
}

internal sealed interface DeviceTokenPollResult {
    data object AuthorizationPending : DeviceTokenPollResult
    data object SlowDown : DeviceTokenPollResult
    data object ExpiredToken : DeviceTokenPollResult
    data object ConfigurationUnavailable : DeviceTokenPollResult
    data class Authorized(val tokens: AulamaSessionTokens) : DeviceTokenPollResult
    data class Rejected(val errorCode: String) : DeviceTokenPollResult
    data object InvalidResponse : DeviceTokenPollResult
}

internal sealed interface ProfileParseResult {
    data class Success(val profile: AulamaAccountProfile) : ProfileParseResult
    data object Unauthorized : ProfileParseResult
    data object ConfigurationUnavailable : ProfileParseResult
    data object InvalidResponse : ProfileParseResult
    data class HttpFailure(val statusCode: Int) : ProfileParseResult
}

internal object AulamaAccountResponseParser {
    private val unavailableStatusCodes = setOf(404, 405, 501)

    fun parseDeviceStart(statusCode: Int, body: String): DeviceStartParseResult {
        if (statusCode in unavailableStatusCodes) {
            return DeviceStartParseResult.ConfigurationUnavailable
        }
        if (statusCode !in 200..299) return DeviceStartParseResult.HttpFailure(statusCode)

        val json = body.asJsonObjectOrNull() ?: return DeviceStartParseResult.InvalidResponse
        val response = DeviceStartResponse(
            deviceCode = json.string("device_code") ?: return DeviceStartParseResult.InvalidResponse,
            userCode = json.string("user_code") ?: return DeviceStartParseResult.InvalidResponse,
            verificationUri = json.string("verification_uri")
                ?: return DeviceStartParseResult.InvalidResponse,
            verificationUriComplete = json.string("verification_uri_complete")
                ?: return DeviceStartParseResult.InvalidResponse,
            expiresInSeconds = json.long("expires_in")
                ?.takeIf { it > 0 }
                ?: return DeviceStartParseResult.InvalidResponse,
            intervalSeconds = json.long("interval")
                ?.takeIf { it > 0 }
                ?: return DeviceStartParseResult.InvalidResponse,
        )

        if (!response.verificationUri.isTrustedAulamaHttpsUrl() ||
            !response.verificationUriComplete.isTrustedAulamaHttpsUrl()
        ) {
            return DeviceStartParseResult.InvalidResponse
        }

        return DeviceStartParseResult.Success(response)
    }

    fun parseDeviceToken(statusCode: Int, body: String): DeviceTokenPollResult {
        if (statusCode in unavailableStatusCodes) {
            return DeviceTokenPollResult.ConfigurationUnavailable
        }

        val json = body.asJsonObjectOrNull() ?: return DeviceTokenPollResult.InvalidResponse
        val accessToken = json.string("access_token")
        val refreshToken = json.string("refresh_token")
        if (statusCode in 200..299 && accessToken != null && refreshToken != null) {
            val expiresIn = json.long("expires_in")
                ?.takeIf { it > 0 }
                ?: return DeviceTokenPollResult.InvalidResponse
            return DeviceTokenPollResult.Authorized(
                AulamaSessionTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                )
            )
        }

        return when (val error = json.string("error")) {
            "authorization_pending" -> DeviceTokenPollResult.AuthorizationPending
            "slow_down" -> DeviceTokenPollResult.SlowDown
            "expired_token" -> DeviceTokenPollResult.ExpiredToken
            null -> DeviceTokenPollResult.InvalidResponse
            else -> DeviceTokenPollResult.Rejected(error)
        }
    }

    fun parseProfile(statusCode: Int, body: String): ProfileParseResult {
        if (statusCode == 401 || statusCode == 403) return ProfileParseResult.Unauthorized
        if (statusCode in unavailableStatusCodes) return ProfileParseResult.ConfigurationUnavailable
        if (statusCode !in 200..299) return ProfileParseResult.HttpFailure(statusCode)

        val json = body.asJsonObjectOrNull() ?: return ProfileParseResult.InvalidResponse
        val isSuperAdmin = json.boolean("is_super_admin") ?: false
        val role = json.string("role")?.lowercase()
            ?: if (isSuperAdmin) "super_admin" else "free"
        val id = json.string("uid")
            ?: json.string("user_id")
            ?: json.string("id")
            ?: json.string("email")
            ?: return ProfileParseResult.InvalidResponse

        return ProfileParseResult.Success(
            AulamaAccountProfile(
                id = id,
                email = json.string("email"),
                displayName = json.string("display_name") ?: json.string("displayName"),
                role = role,
                isSuperAdmin = isSuperAdmin || role == "super_admin",
            )
        )
    }
}

private fun String.asJsonObjectOrNull(): JsonObject? = runCatching {
    JsonParser.parseString(this).takeIf { it.isJsonObject }?.asJsonObject
}.getOrNull()

private fun JsonObject.string(name: String): String? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

private fun JsonObject.long(name: String): Long? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asLong
}.getOrNull()

private fun JsonObject.boolean(name: String): Boolean? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
}.getOrNull()

private fun String.isTrustedAulamaHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("aulama.org", ignoreCase = true) &&
        (uri.port == -1 || uri.port == 443) &&
        uri.userInfo == null
}.getOrDefault(false)

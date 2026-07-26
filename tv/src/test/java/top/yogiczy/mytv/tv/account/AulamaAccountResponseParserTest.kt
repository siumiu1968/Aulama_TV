package top.yogiczy.mytv.tv.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AulamaAccountResponseParserTest {
    @Test
    fun `valid start response is accepted`() {
        val result = AulamaAccountResponseParser.parseDeviceStart(
            200,
            """
                {
                  "device_code":"device-secret",
                  "user_code":"ABCD-EFGH",
                  "verification_uri":"https://aulama.org/iptv/pair/",
                  "verification_uri_complete":"https://aulama.org/iptv/pair/?code=ABCD-EFGH",
                  "expires_in":600,
                  "interval":5
                }
            """.trimIndent(),
        )

        assertTrue(result is DeviceStartParseResult.Success)
        val response = (result as DeviceStartParseResult.Success).response
        assertEquals("ABCD-EFGH", response.userCode)
        assertEquals(600L, response.expiresInSeconds)
        assertEquals(5L, response.intervalSeconds)
    }

    @Test
    fun `start response rejects qr outside trusted origin`() {
        val result = AulamaAccountResponseParser.parseDeviceStart(
            200,
            """
                {
                  "device_code":"device-secret",
                  "user_code":"ABCD-EFGH",
                  "verification_uri":"https://aulama.org/iptv/pair/",
                  "verification_uri_complete":"https://evil.example/pair?code=ABCD-EFGH",
                  "expires_in":600,
                  "interval":5
                }
            """.trimIndent(),
        )

        assertEquals(DeviceStartParseResult.InvalidResponse, result)
    }

    @Test
    fun `start response rejects nonstandard qr https port`() {
        val result = AulamaAccountResponseParser.parseDeviceStart(
            200,
            """
                {
                  "device_code":"device-secret",
                  "user_code":"ABCD-EFGH",
                  "verification_uri":"https://aulama.org/iptv/pair/",
                  "verification_uri_complete":"https://aulama.org:8443/iptv/pair/?code=ABCD-EFGH",
                  "expires_in":600,
                  "interval":5
                }
            """.trimIndent(),
        )

        assertEquals(DeviceStartParseResult.InvalidResponse, result)
    }

    @Test
    fun `token response classifies pending slow down and expiry`() {
        assertEquals(
            DeviceTokenPollResult.AuthorizationPending,
            AulamaAccountResponseParser.parseDeviceToken(
                400,
                """{"error":"authorization_pending"}""",
            ),
        )
        assertEquals(
            DeviceTokenPollResult.SlowDown,
            AulamaAccountResponseParser.parseDeviceToken(
                400,
                """{"error":"slow_down"}""",
            ),
        )
        assertEquals(
            DeviceTokenPollResult.ExpiredToken,
            AulamaAccountResponseParser.parseDeviceToken(
                400,
                """{"error":"expired_token"}""",
            ),
        )
    }

    @Test
    fun `token response requires both tokens and expiry`() {
        val missingRefresh = AulamaAccountResponseParser.parseDeviceToken(
            200,
            """{"access_token":"access","expires_in":900}""",
        )
        assertEquals(DeviceTokenPollResult.InvalidResponse, missingRefresh)

        val success = AulamaAccountResponseParser.parseDeviceToken(
            200,
            """{"access_token":"access","refresh_token":"refresh","expires_in":900}""",
        )
        assertTrue(success is DeviceTokenPollResult.Authorized)
        assertEquals(
            900L,
            (success as DeviceTokenPollResult.Authorized).tokens.expiresInSeconds,
        )
    }

    @Test
    fun `profile derives highest administrator role from server response`() {
        val result = AulamaAccountResponseParser.parseProfile(
            200,
            """
                {
                  "uid":"user-1",
                  "email":"owner@example.com",
                  "display_name":"Owner",
                  "role":"free",
                  "is_super_admin":true
                }
            """.trimIndent(),
        )

        assertTrue(result is ProfileParseResult.Success)
        val profile = (result as ProfileParseResult.Success).profile
        assertTrue(profile.isSuperAdmin)
        assertEquals("最高管理員", profile.roleLabel)
    }
}

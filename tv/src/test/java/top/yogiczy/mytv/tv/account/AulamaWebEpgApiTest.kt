package top.yogiczy.mytv.tv.account

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AulamaWebEpgApiTest {
    @Test
    fun `web fallback sends TV authorization only to the fixed Aulama API origin`() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = AulamaAccountApi(OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{}".toResponseBody()).build()
        }.build())
        api.getWebEpgSnapshot()
        api.getWebEpg("test-access", "無綫新聞台 & test")
        assertEquals("/iptv/data/epg.json", requests[0].url.encodedPath)
        assertNull(requests[0].header("Authorization"))
        val live = requests[1]
        assertEquals("https", live.url.scheme)
        assertEquals("aulama.org", live.url.host)
        assertEquals("/hermes-api/iptv/epg", live.url.encodedPath)
        assertEquals("無綫新聞台 & test", live.url.queryParameter("channel"))
        assertEquals(1, live.url.querySize)
        assertEquals("Bearer test-access", live.header("Authorization"))
    }

    @Test
    fun `web API unauthorized response uses the existing token refresh path`() = runBlocking {
        val api = AulamaAccountApi(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(401).message("Unauthorized").body("{}".toResponseBody()).build()
        }.build())
        try {
            api.getWebEpg("expired-test-access", "無綫新聞台")
            fail("Expected Unauthorized")
        } catch (_: AulamaAccountApiException.Unauthorized) {
            // The account manager refreshes this response with its normal secure token flow.
        }
    }
}

package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DownloaderTest {
    @Test
    fun clientAllowsSlowReleaseAssetResponses() {
        val client = Downloader.createClient(onProgressCb = null)

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(120_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun downloadToStreamsResponseIntoTargetFile() = runBlocking {
        val server = MockWebServer()
        val target = newTargetFile()
        server.enqueue(MockResponse().setBody("apk-payload"))
        server.start()

        try {
            Downloader.downloadTo(server.url("/release.apk").toString(), target.path, null)

            assertEquals("apk-payload", target.readText())
        } finally {
            target.delete()
            server.shutdown()
        }
    }

    @Test
    fun failedDownloadRemovesPartialFile() = runBlocking {
        val server = MockWebServer()
        val target = newTargetFile()
        server.enqueue(
            MockResponse()
                .setBody("incomplete-apk-payload")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        server.start()

        try {
            try {
                Downloader.downloadTo(server.url("/release.apk").toString(), target.path, null)
                fail("下載中斷時應拋出錯誤")
            } catch (_: Exception) {
                assertFalse(target.exists())
            }
        } finally {
            target.delete()
            server.shutdown()
        }
    }

    private fun newTargetFile(): File =
        File.createTempFile("aulama-tv-update-", ".apk").apply { delete() }
}

package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubGitReleaseParserTest {
    private val parser = GithubGitReleaseParser()

    @Test
    fun releaseListIgnoresMobileAndSelectsTvApk() = runBlocking {
        val release = parser.parse(
            """
            [
              {
                "tag_name": "android-v1.1.2",
                "draft": false,
                "assets": [{
                  "name": "mytv-android-mobile-1.1.2-all-sdk21.apk",
                  "browser_download_url": "https://example.com/mobile.apk"
                }]
              },
              {
                "tag_name": "v2.6.11-family",
                "draft": false,
                "body": "TV release",
                "assets": [{
                  "name": "mytv-android-tv-2.6.11-family-all-sdk21.apk",
                  "browser_download_url": "https://example.com/tv.apk",
                  "digest": "sha256:abc123"
                }]
              }
            ]
            """.trimIndent(),
        )

        assertEquals("2.6.11-family", release.version)
        assertEquals("https://example.com/tv.apk", release.downloadUrl)
        assertEquals("abc123", release.sha256)
    }

    @Test
    fun releaseWithAbiSplitsPrefersUniversalApkForAutomaticUpdate() = runBlocking {
        val release = parser.parse(
            """
            {
              "tag_name": "v2.7.0-family",
              "draft": false,
              "assets": [
                {
                  "name": "mytv-android-tv-2.7.0-family-arm64-v8a-sdk21.apk",
                  "browser_download_url": "https://example.com/arm64.apk"
                },
                {
                  "name": "mytv-android-tv-2.7.0-family-all-sdk21.apk",
                  "browser_download_url": "https://example.com/universal.apk",
                  "digest": "sha256:universal"
                },
                {
                  "name": "mytv-android-tv-2.7.0-family-armeabi-v7a-sdk21.apk",
                  "browser_download_url": "https://example.com/arm32.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("https://example.com/universal.apk", release.downloadUrl)
        assertEquals("universal", release.sha256)
    }

    @Test
    fun latestMobileObjectIsRejectedWithoutParsingItsTagAsVersion() {
        val error = runCatching {
            runBlocking {
                parser.parse(
                    """
                    {
                      "tag_name": "android-v1.1.2",
                      "draft": false,
                      "assets": [{
                        "name": "mytv-android-mobile-1.1.2-all-sdk21.apk",
                        "browser_download_url": "https://example.com/mobile.apk"
                      }]
                    }
                    """.trimIndent(),
                )
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("找不到可用的 Android TV 發行版", error?.message)
    }

    @Test
    fun stableChannelSkipsPrerelease() = runBlocking {
        val data = """
            [
              {
                "tag_name": "v2.7.0-beta",
                "draft": false,
                "prerelease": true,
                "assets": [{
                  "name": "mytv-android-tv-2.7.0-beta-all-sdk21.apk",
                  "browser_download_url": "https://example.com/beta.apk"
                }]
              },
              {
                "tag_name": "v2.6.11-family",
                "draft": false,
                "prerelease": false,
                "assets": [{
                  "name": "mytv-android-tv-2.6.11-family-all-sdk21.apk",
                  "browser_download_url": "https://example.com/stable.apk"
                }]
              }
            ]
        """.trimIndent()

        assertEquals("2.6.11-family", parser.parse(data).version)
        assertEquals("2.7.0-beta", parser.parse(data, includePrerelease = true).version)
    }
}

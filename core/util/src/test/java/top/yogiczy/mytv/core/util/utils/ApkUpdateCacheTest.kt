package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ApkUpdateCacheTest {
    @Test
    fun verifiedDownloadIsReusedWithoutAnotherNetworkRequest() = withTempDir { cacheDir ->
        val partial = ApkUpdateCache.partialFile(cacheDir).apply {
            writeText("verified-apk-payload")
        }
        val expectedSha = with(ApkUpdateCache) { partial.sha256() }

        ApkUpdateCache.commitDownload(
            cacheDir = cacheDir,
            releaseVersion = "2.6.20-beta.3",
            expectedSha256 = expectedSha,
        )

        assertFalse(partial.exists())
        assertTrue(
            ApkUpdateCache.isReusable(
                cacheDir = cacheDir,
                releaseVersion = "2.6.20-beta.3",
                expectedSha256 = expectedSha,
            )
        )
        assertEquals("verified-apk-payload", ApkUpdateCache.apkFile(cacheDir).readText())
    }

    @Test
    fun recordedDigestAllowsReuseWhenServerDigestIsUnavailable() = withTempDir { cacheDir ->
        ApkUpdateCache.partialFile(cacheDir).writeText("legacy-release-payload")
        ApkUpdateCache.commitDownload(
            cacheDir = cacheDir,
            releaseVersion = "2.6.20-beta.3",
            expectedSha256 = "",
        )

        assertTrue(
            ApkUpdateCache.isReusable(
                cacheDir = cacheDir,
                releaseVersion = "2.6.20-beta.3",
                expectedSha256 = "",
            )
        )
        assertFalse(
            ApkUpdateCache.isReusable(
                cacheDir = cacheDir,
                releaseVersion = "2.6.21-beta.1",
                expectedSha256 = "",
            )
        )
    }

    @Test
    fun integrityFailureDeletesPartialAndNeverPublishesApk() = withTempDir { cacheDir ->
        val partial = ApkUpdateCache.partialFile(cacheDir).apply { writeText("broken") }

        runCatching {
            ApkUpdateCache.commitDownload(
                cacheDir = cacheDir,
                releaseVersion = "2.6.20-beta.3",
                expectedSha256 = "not-the-real-hash",
            )
        }

        assertFalse(partial.exists())
        assertFalse(ApkUpdateCache.apkFile(cacheDir).exists())
    }

    @Test
    fun cleanupOnlyRemovesUpdaterArtifacts() = withTempDir { cacheDir ->
        ApkUpdateCache.apkFile(cacheDir).writeText("apk")
        ApkUpdateCache.partialFile(cacheDir).writeText("partial")
        val unrelated = File(cacheDir, "channel-cache.txt").apply { writeText("keep") }

        ApkUpdateCache.clearAll(cacheDir)

        assertFalse(ApkUpdateCache.apkFile(cacheDir).exists())
        assertFalse(ApkUpdateCache.partialFile(cacheDir).exists())
        assertTrue(unrelated.exists())
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("aulama-update-cache-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}

package top.yogiczy.mytv.core.util.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 只保留一個已驗證的待安裝 APK，避免電視重複下載或累積舊安裝包。
 */
object ApkUpdateCache {
    private const val APK_FILE_NAME = "latest.apk"
    private const val PART_FILE_NAME = "latest.apk.part"
    private const val META_FILE_NAME = "latest.apk.meta"
    private const val EXTERNAL_INSTALL_FILE_NAME = "pending-install.apk"
    private const val UPLOADED_APK_FILE_NAME = "uploaded_apk.apk"

    fun apkFile(cacheDir: File): File = File(cacheDir, APK_FILE_NAME)

    fun partialFile(cacheDir: File): File = File(cacheDir, PART_FILE_NAME)

    fun isReusable(
        cacheDir: File,
        releaseVersion: String,
        expectedSha256: String,
    ): Boolean = runCatching {
        val apk = apkFile(cacheDir)
        if (!apk.isFile || apk.length() <= 0L) return@runCatching false

        val actualSha256 = apk.sha256()
        if (expectedSha256.isNotBlank()) {
            return@runCatching actualSha256.equals(expectedSha256, ignoreCase = true)
        }

        val metadata = readMetadata(cacheDir) ?: return@runCatching false
        metadata.version == releaseVersion &&
            actualSha256.equals(metadata.sha256, ignoreCase = true)
    }.getOrDefault(false)

    fun prepareDownload(cacheDir: File) {
        partialFile(cacheDir).delete()
        removeVerifiedArtifact(cacheDir)
    }

    fun commitDownload(
        cacheDir: File,
        releaseVersion: String,
        expectedSha256: String,
    ) {
        val partial = partialFile(cacheDir)
        require(partial.isFile && partial.length() > 0L) { "下載檔案不存在或為空" }

        val actualSha256 = partial.sha256()
        if (expectedSha256.isNotBlank() &&
            !actualSha256.equals(expectedSha256, ignoreCase = true)
        ) {
            partial.delete()
            error("APK 完整性校驗失敗")
        }

        val apk = apkFile(cacheDir)
        apk.parentFile?.mkdirs()
        apk.delete()
        check(partial.renameTo(apk)) { "無法保存已下載的安裝包" }
        metadataFile(cacheDir).writeText("$releaseVersion\n$actualSha256\n")
    }

    fun discardPartial(cacheDir: File) {
        partialFile(cacheDir).delete()
    }

    /** 安裝完成後清除 OTA、手動上載及臨時安裝副本。 */
    fun clearAll(cacheDir: File) {
        removeVerifiedArtifact(cacheDir)
        partialFile(cacheDir).delete()
        File(cacheDir, EXTERNAL_INSTALL_FILE_NAME).delete()
        File(cacheDir, UPLOADED_APK_FILE_NAME).delete()
    }

    /**
     * App 啟動時清走中斷下載；若快取版本已安裝、格式無效或並非本 App，亦一併刪除。
     * 未完成安裝而版本仍較新的有效 APK 會保留，供下次直接重開安裝器。
     */
    fun cleanupAfterAppStart(context: Context) {
        val cacheDir = context.cacheDir
        discardPartial(cacheDir)
        val apk = apkFile(cacheDir)
        if (!apk.exists()) {
            metadataFile(cacheDir).delete()
            return
        }

        val packageManager = context.packageManager
        val cachedPackage = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.path, 0)
        }.getOrNull()
        val installedPackage = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val shouldDelete = cachedPackage == null || installedPackage == null ||
            cachedPackage.packageName != context.packageName ||
            packageVersionCode(cachedPackage) <= packageVersionCode(installedPackage)
        if (shouldDelete) removeVerifiedArtifact(cacheDir)
    }

    internal fun externalInstallFile(cacheDir: File): File =
        File(cacheDir, EXTERNAL_INSTALL_FILE_NAME)

    internal fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun removeVerifiedArtifact(cacheDir: File) {
        apkFile(cacheDir).delete()
        metadataFile(cacheDir).delete()
    }

    private fun metadataFile(cacheDir: File): File = File(cacheDir, META_FILE_NAME)

    private fun readMetadata(cacheDir: File): Metadata? = runCatching {
        val lines = metadataFile(cacheDir).readLines()
        Metadata(version = lines[0], sha256 = lines[1])
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    private data class Metadata(
        val version: String,
        val sha256: String,
    )
}

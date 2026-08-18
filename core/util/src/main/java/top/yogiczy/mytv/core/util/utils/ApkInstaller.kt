package top.yogiczy.mytv.core.util.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String): ApkInstallResult {
        val sourceFile = File(filePath)
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            return ApkInstallResult.Failed("搵唔到已下載的安裝包")
        }

        return try {
            val sharedFile = shareableFile(context, sourceFile).apply {
                // Android 6 的部分安裝器仍會檢查檔案可讀性。
                setReadable(true, false)
            }
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".FileProvider",
                    sharedFile,
                )
            } else {
                Uri.fromFile(sharedFile)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri("Aulama TV update", uri)
                setDataAndType(uri, APK_MIME_TYPE)
            }
            val installer = installIntent.resolveActivity(context.packageManager)
                ?: return ApkInstallResult.Failed("系統未提供 APK 安裝程式")
            context.grantUriPermission(
                installer.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            context.startActivity(installIntent)
            ApkInstallResult.Started
        } catch (ex: Exception) {
            ApkInstallResult.Failed(
                message = when (ex) {
                    is SecurityException -> "系統拒絕開啟安裝程式，請檢查未知來源權限"
                    else -> "無法開啟系統安裝程式"
                },
                cause = ex,
            )
        }
    }

    private fun shareableFile(context: Context, sourceFile: File): File {
        val sourcePath = sourceFile.canonicalPath
        val cachePath = context.cacheDir.canonicalPath
        if (sourcePath == cachePath || sourcePath.startsWith("$cachePath${File.separator}")) {
            return sourceFile
        }

        val target = ApkUpdateCache.externalInstallFile(context.cacheDir)
        sourceFile.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        return target
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

sealed interface ApkInstallResult {
    data object Started : ApkInstallResult

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : ApkInstallResult
}

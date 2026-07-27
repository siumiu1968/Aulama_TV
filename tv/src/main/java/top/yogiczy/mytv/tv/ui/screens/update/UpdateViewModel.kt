package top.yogiczy.mytv.tv.ui.screens.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.repositories.git.GitRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.Downloader
import top.yogiczy.mytv.core.util.utils.compareVersion
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import java.io.File
import java.security.MessageDigest

class UpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking by mutableStateOf(false)
    val isChecking get() = _isChecking

    private var _isUpdating = false
    private var lastCheckedChannel = ""

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _updateDownloaded by mutableStateOf(false)
    val updateDownloaded get() = _updateDownloaded

    private var _latestRelease by mutableStateOf(GitRelease())
    val latestRelease get() = _latestRelease

    private var _checkError by mutableStateOf<String?>(null)
    val checkError get() = _checkError


    var visible by mutableStateOf(false)

    suspend fun checkUpdate(
        currentVersion: String,
        channel: String,
        force: Boolean = false,
    ): Boolean {
        if (_isChecking) return _isUpdateAvailable
        if (!force && lastCheckedChannel == channel) return _isUpdateAvailable

        try {
            val releaseUrl = Constants.GIT_RELEASE_LATEST_URL[channel] ?: return false

            _isChecking = true
            _checkError = null
            _latestRelease = GitRepository().latestRelease(
                url = releaseUrl,
                includePrerelease = channel == "beta",
            )
            lastCheckedChannel = channel
            log.d("線上版本: ${_latestRelease.version}")
            _isUpdateAvailable = _latestRelease.version.compareVersion(currentVersion) > 0
        } catch (ex: Exception) {
            log.e("檢查更新失敗", ex)
            _checkError = ex.message ?: "檢查更新失敗"
            _isUpdateAvailable = false
        } finally {
            _isChecking = false
        }

        return _isUpdateAvailable
    }

    suspend fun downloadAndUpdate(latestFile: File) {
        if (!_isUpdateAvailable) return
        if (_isUpdating) return

        _isUpdating = true
        _updateDownloaded = false
        Snackbar.show(
            "開始下載更新",
            leadingLoading = true,
            duration = 10_000,
            id = "downloadProcess"
        )

        try {
            Downloader.downloadTo(_latestRelease.downloadUrl, latestFile.path) {
                Snackbar.show(
                    "正在下載更新: $it%",
                    leadingLoading = true,
                    duration = 10_000,
                    id = "downloadProcess"
                )
            }

            if (_latestRelease.sha256.isNotBlank()) {
                val actualSha256 = latestFile.sha256()
                if (!actualSha256.equals(_latestRelease.sha256, ignoreCase = true)) {
                    latestFile.delete()
                    error("APK 完整性校驗失敗")
                }
            }

            _updateDownloaded = true
            Snackbar.show("下載更新成功")
        } catch (ex: Exception) {
            log.e("下載更新失敗", ex)
            Snackbar.show("下載更新失敗", type = SnackbarType.ERROR)
        } finally {
            _isUpdating = false
        }
    }

    private fun File.sha256(): String {
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
}

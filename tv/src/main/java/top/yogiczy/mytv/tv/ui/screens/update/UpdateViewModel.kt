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

class UpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking = false
    private var _isUpdating = false

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _updateDownloaded by mutableStateOf(false)
    val updateDownloaded get() = _updateDownloaded

    private var _latestRelease by mutableStateOf(GitRelease())
    val latestRelease get() = _latestRelease


    var visible by mutableStateOf(false)

    suspend fun checkUpdate(currentVersion: String, channel: String) {
        if (_isChecking) return
        if (_isUpdateAvailable) return

        try {
            val releaseUrl = Constants.GIT_RELEASE_LATEST_URL[channel] ?: return

            _isChecking = true
            _latestRelease = GitRepository().latestRelease(releaseUrl)
            log.d("線上版本: ${_latestRelease.version}")
            _isUpdateAvailable = _latestRelease.version.compareVersion(currentVersion) > 0
        } catch (ex: Exception) {
            log.e("檢查更新失敗", ex)
            _latestRelease = _latestRelease.copy(description = ex.message ?: "檢查更新失敗")
        } finally {
            _isChecking = false
        }
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

            _updateDownloaded = true
            Snackbar.show("下載更新成功")
        } catch (ex: Exception) {
            Snackbar.show("下載更新失敗", type = SnackbarType.ERROR)
        } finally {
            _isUpdating = false
        }
    }
}
package top.yogiczy.mytv.tv.ui.screens.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.repositories.git.GitRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.ApkUpdateCache
import top.yogiczy.mytv.core.util.utils.Downloader
import top.yogiczy.mytv.core.util.utils.compareVersion
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import java.io.File

class UpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking by mutableStateOf(false)
    val isChecking get() = _isChecking

    private var _isUpdating by mutableStateOf(false)
    val isUpdating get() = _isUpdating

    private var _downloadProgress by mutableIntStateOf(0)
    val downloadProgress get() = _downloadProgress

    private var _downloadFailed by mutableStateOf(false)
    val downloadFailed get() = _downloadFailed

    private var _installerLaunched by mutableStateOf(false)
    val installerLaunched get() = _installerLaunched

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

    suspend fun downloadAndUpdate(latestFile: File): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!_isUpdateAvailable) return@withContext false
        if (_isUpdating) return@withContext false

        _isUpdating = true
        _downloadProgress = 0
        _downloadFailed = false
        _installerLaunched = false
        _updateDownloaded = false

        try {
            val reusable = withContext(Dispatchers.IO) {
                ApkUpdateCache.isReusable(
                    cacheDir = latestFile.parentFile ?: latestFile,
                    releaseVersion = _latestRelease.version,
                    expectedSha256 = _latestRelease.sha256,
                )
            }
            if (reusable) {
                _downloadProgress = 100
                _updateDownloaded = true
                Snackbar.show("已找到完整安裝包，直接開啟系統安裝畫面")
                return@withContext true
            }

            val cacheDir = latestFile.parentFile ?: error("更新快取目錄不存在")
            withContext(Dispatchers.IO) { ApkUpdateCache.prepareDownload(cacheDir) }
            Snackbar.show(
                "開始下載更新",
                leadingLoading = true,
                duration = 10_000,
                id = "downloadProcess",
            )
            Downloader.downloadTo(
                url = _latestRelease.downloadUrl,
                filePath = ApkUpdateCache.partialFile(cacheDir).path,
            ) { progress ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _downloadProgress = progress
                    Snackbar.show(
                        "正在下載更新: $progress%",
                        leadingLoading = true,
                        duration = 10_000,
                        id = "downloadProcess",
                    )
                }
            }
            withContext(Dispatchers.IO) {
                ApkUpdateCache.commitDownload(
                    cacheDir = cacheDir,
                    releaseVersion = _latestRelease.version,
                    expectedSha256 = _latestRelease.sha256,
                )
            }

            _downloadProgress = 100
            _updateDownloaded = true
            Snackbar.show("下載完成並已驗證，正在開啟系統安裝畫面")
            true
        } catch (ex: Exception) {
            log.e("下載更新失敗", ex)
            withContext(Dispatchers.IO) {
                val cacheDir = latestFile.parentFile
                if (cacheDir != null) ApkUpdateCache.discardPartial(cacheDir)
            }
            _downloadFailed = true
            Snackbar.show(
                "下載更新失敗，未完成安裝包已清理",
                type = SnackbarType.ERROR,
            )
            false
        } finally {
            _isUpdating = false
        }
    }

    fun markInstallerLaunched() {
        _installerLaunched = true
    }

    fun markInstallerLaunchFailed(message: String, cause: Throwable? = null) {
        _installerLaunched = false
        log.e("開啟系統安裝器失敗", cause)
        Snackbar.show(message, type = SnackbarType.ERROR, duration = 6_000)
    }
}

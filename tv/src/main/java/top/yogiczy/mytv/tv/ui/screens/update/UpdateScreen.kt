package top.yogiczy.mytv.tv.ui.screens.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.util.utils.ApkInstallResult
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.core.util.utils.ApkUpdateCache
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.components.UpdateContent
import top.yogiczy.mytv.tv.ui.utils.captureBackKey

@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val latestFile = remember { ApkUpdateCache.apkFile(Globals.cacheDir) }
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val currentVersion = packageInfo.versionName ?: "0.0.0"
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    var installAfterPermission by remember { mutableStateOf(false) }
    var pendingInstallerLaunch by remember { mutableStateOf(false) }

    val downloadUpdate: () -> Unit = {
        coroutineScope.launch {
            if (updateViewModel.downloadAndUpdate(latestFile)) {
                pendingInstallerLaunch = true
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!waitingForInstallPermission) return@rememberLauncherForActivityResult

            waitingForInstallPermission = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                if (installAfterPermission) {
                    pendingInstallerLaunch = true
                } else {
                    downloadUpdate()
                }
            } else {
                Snackbar.show("未授予安裝更新權限", type = SnackbarType.ERROR)
            }
        }

    val requestInstallPermission: (Boolean) -> Unit = { installDownloadedApk ->
        waitingForInstallPermission = true
        installAfterPermission = installDownloadedApk
        val appPermissionIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            launcher.launch(appPermissionIntent)
        } catch (_: Exception) {
            try {
                launcher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (_: Exception) {
                waitingForInstallPermission = false
                Snackbar.show(
                    "無法開啟安裝權限設定，請在 Android TV 設定中手動允許",
                    type = SnackbarType.ERROR,
                )
            }
        }
    }

    LaunchedEffect(pendingInstallerLaunch, lifecycleOwner) {
        if (!pendingInstallerLaunch) return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first {
            it.isAtLeast(Lifecycle.State.RESUMED)
        }
        pendingInstallerLaunch = false

        when (val result = ApkInstaller.installApk(context, latestFile.path)) {
            ApkInstallResult.Started -> {
                updateViewModel.markInstallerLaunched()
                Snackbar.show(
                    "系統安裝畫面已開啟；如未顯示可直接重新嘗試",
                    duration = 6_000,
                )
            }

            is ApkInstallResult.Failed -> {
                updateViewModel.markInstallerLaunchFailed(result.message, result.cause)
            }
        }
    }

    LaunchedEffect(currentVersion, settingsViewModel.updateChannel) {
        val updateAvailable = updateViewModel.checkUpdate(
            currentVersion = currentVersion,
            channel = settingsViewModel.updateChannel,
        )
        if (!updateAvailable) return@LaunchedEffect

        if (settingsViewModel.updateForceRemind) {
            updateViewModel.visible = true
        } else {
            Snackbar.show(
                "發現新版本 v${updateViewModel.latestRelease.version}，可到設定的更新頁安裝",
                type = SnackbarType.PRIMARY,
                duration = 8_000,
            )
        }
    }

    PopupContent(
        visibleProvider = { updateViewModel.visible },
        onDismissRequest = { updateViewModel.visible = false },
    ) {
        UpdateContent(
            modifier = modifier
                .captureBackKey { updateViewModel.visible = false }
                .pointerInput(Unit) { detectTapGestures { } },
            onDismissRequest = { updateViewModel.visible = false },
            releaseProvider = { updateViewModel.latestRelease },
            isUpdateAvailableProvider = { updateViewModel.isUpdateAvailable },
            isUpdatingProvider = { updateViewModel.isUpdating },
            downloadProgressProvider = { updateViewModel.downloadProgress },
            downloadFailedProvider = { updateViewModel.downloadFailed },
            updateDownloadedProvider = { updateViewModel.updateDownloaded },
            installerLaunchedProvider = { updateViewModel.installerLaunched },
            onUpdateAndInstall = {
                if (updateViewModel.isUpdating) return@UpdateContent
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    context.packageManager.canRequestPackageInstalls()
                ) {
                    downloadUpdate()
                } else {
                    requestInstallPermission(false)
                }
            },
        )
    }
}

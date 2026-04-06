package top.yogiczy.mytv.tv.ui.screens.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.components.UpdateContent
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import java.io.File

@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latestFile = remember { File(Globals.cacheDir, "latest.apk") }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    ApkInstaller.installApk(context, latestFile.path)
                } else {
                    Snackbar.show("未授予安裝權限", type = SnackbarType.ERROR)
                }
            }
        }

    LaunchedEffect(updateViewModel.updateDownloaded) {
        if (!updateViewModel.updateDownloaded) return@LaunchedEffect

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            ApkInstaller.installApk(context, latestFile.path)
        } else {
            if (context.packageManager.canRequestPackageInstalls()) {
                ApkInstaller.installApk(context, latestFile.path)
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    launcher.launch(intent)
                } catch (_: Exception) {
                    Snackbar.show(
                        "無法找到相應的設置項，請手動啓用未知來源安裝權限。",
                        type = SnackbarType.ERROR,
                    )
                }
            }
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
            onUpdateAndInstall = {
                updateViewModel.visible = false
                coroutineScope.launch(Dispatchers.IO) {
                    updateViewModel.downloadAndUpdate(latestFile)
                }
            },
        )
    }
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)

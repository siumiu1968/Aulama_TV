package top.yogiczy.mytv.tv.ui.screens.settings.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screens.update.UpdateViewModel

@Composable
fun SettingsCategoryUpdate(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }
    var canInstallUpdates by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
        )
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        canInstallUpdates = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    SettingsContentList(modifier) {
        item {
            val channels = linkedMapOf(
                "stable" to "GitHub 正式版",
                "beta" to "GitHub 搶先版",
            )

            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "更新通道",
                supportingContent = "從官方 GitHub Release 接收 Android TV 更新",
                trailingContent = channels[settingsViewModel.updateChannel] ?: "GitHub 正式版",
                onSelected = {
                    val keys = channels.keys.toList()
                    val currentIndex = keys.indexOf(settingsViewModel.updateChannel).coerceAtLeast(0)
                    settingsViewModel.updateChannel = keys[(currentIndex + 1) % keys.size]
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "檢查更新",
                supportingContent = "目前版本 v$currentVersion",
                trailingContent = if (updateViewModel.isChecking) "檢查中" else "立即檢查",
                onSelected = {
                    if (!updateViewModel.isChecking) {
                        coroutineScope.launch {
                            val available = updateViewModel.checkUpdate(
                                currentVersion = currentVersion,
                                channel = settingsViewModel.updateChannel,
                                force = true,
                            )
                            when {
                                available -> updateViewModel.visible = true
                                updateViewModel.checkError != null -> Snackbar.show(
                                    updateViewModel.checkError ?: "檢查更新失敗",
                                    type = SnackbarType.ERROR,
                                )
                                else -> Snackbar.show(
                                    "目前已是最新版本",
                                    type = SnackbarType.PRIMARY,
                                )
                            }
                        }
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "安裝更新權限",
                supportingContent = "Android TV 會在安裝前再次顯示系統確認",
                trailingContent = if (canInstallUpdates) "已允許" else "需要設定",
                onSelected = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallUpdates) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                        try {
                            installPermissionLauncher.launch(intent)
                        } catch (_: Exception) {
                            installPermissionLauncher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "更新強提醒",
                supportingContent = if (settingsViewModel.updateForceRemind) "發現新版時顯示完整更新畫面"
                else "發現新版時顯示簡短通知",
                trailingContent = {
                    Switch(settingsViewModel.updateForceRemind, null)
                },
                onSelected = {
                    settingsViewModel.updateForceRemind = !settingsViewModel.updateForceRemind
                },
            )
        }
    }
}

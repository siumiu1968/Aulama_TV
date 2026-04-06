package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel

@Composable
fun SettingsCategoryUpdate(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            val list = mapOf(
                "stable" to "穩定版",
                "beta" to "測試版",
            )

            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "更新通道",
                trailingContent = list[settingsViewModel.updateChannel] ?: "",
                onSelected = {
                    settingsViewModel.updateChannel =
                        list.keys.first { it != settingsViewModel.updateChannel }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "更新強提醒",
                supportingContent = if (settingsViewModel.updateForceRemind) "檢測到新版本時會全屏提醒"
                else "檢測到新版本時僅消息提示",
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
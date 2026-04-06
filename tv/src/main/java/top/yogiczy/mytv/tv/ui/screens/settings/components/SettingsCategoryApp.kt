package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel

@Composable
fun SettingsCategoryApp(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "開機自啓",
                supportingContent = "請確保當前設備支持該功能",
                trailingContent = {
                    Switch(settingsViewModel.appBootLaunch, null)
                },
                onSelected = {
                    settingsViewModel.appBootLaunch = !settingsViewModel.appBootLaunch
                },
            )
        }

        item {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            SettingsListItem(
                headlineContent = "恢復初始化",
                onSelected = {
                    coroutineScope.launch {
                        SP.clear()
                        Snackbar.show("已恢復初始化")
                    }
                },
            )
        }
    }
}

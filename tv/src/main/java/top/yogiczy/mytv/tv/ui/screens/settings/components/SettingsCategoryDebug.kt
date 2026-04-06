package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel

@Composable
fun SettingsCategoryDebug(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "顯示FPS",
                supportingContent = "在屏幕左上角顯示fps和柱狀圖",
                trailingContent = {
                    Switch(settingsViewModel.debugShowFps, null)
                },
                onSelected = {
                    settingsViewModel.debugShowFps = !settingsViewModel.debugShowFps
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "顯示播放器信息",
                supportingContent = "顯示播放器詳細信息（編碼、解碼器、採樣率等）",
                trailingContent = {
                    Switch(settingsViewModel.debugShowVideoPlayerMetadata, null)
                },
                onSelected = {
                    settingsViewModel.debugShowVideoPlayerMetadata =
                        !settingsViewModel.debugShowVideoPlayerMetadata
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "顯示佈局網格",
                trailingContent = {
                    Switch(settingsViewModel.debugShowLayoutGrids, null)
                },
                onSelected = {
                    settingsViewModel.debugShowLayoutGrids = !settingsViewModel.debugShowLayoutGrids
                },
            )
        }
    }
}
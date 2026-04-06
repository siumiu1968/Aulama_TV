package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.screens.components.SelectDialog
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.text.DecimalFormat

@Composable
fun SettingsCategoryUI(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "節目進度",
                supportingContent = "在頻道項底部顯示當前節目進度條",
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammeProgress, null)
                },
                onSelected = {
                    settingsViewModel.uiShowEpgProgrammeProgress =
                        !settingsViewModel.uiShowEpgProgrammeProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "常駐底部節目進度",
                supportingContent = "在播放器底部顯示當前節目進度條",
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammePermanentProgress, null)
                },
                onSelected = {
                    settingsViewModel.uiShowEpgProgrammePermanentProgress =
                        !settingsViewModel.uiShowEpgProgrammePermanentProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "台標顯示",
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelLogo, null)
                },
                onSelected = {
                    settingsViewModel.uiShowChannelLogo = !settingsViewModel.uiShowChannelLogo
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "經典選台界面",
                supportingContent = "將選台界面替換為經典三段式結構",
                trailingContent = {
                    Switch(settingsViewModel.uiUseClassicPanelScreen, null)
                },
                onSelected = {
                    settingsViewModel.uiUseClassicPanelScreen =
                        !settingsViewModel.uiUseClassicPanelScreen
                },
            )
        }

        item {
            val timeShowRangeSeconds = Constants.UI_TIME_SCREEN_SHOW_DURATION / 1000

            SettingsListItem(
                headlineContent = "時間顯示",
                supportingContent = when (settingsViewModel.uiTimeShowMode) {
                    Configs.UiTimeShowMode.HIDDEN -> "不顯示時間"
                    Configs.UiTimeShowMode.ALWAYS -> "總是顯示時間"
                    Configs.UiTimeShowMode.EVERY_HOUR -> "整點前後${timeShowRangeSeconds}s顯示時間"
                    Configs.UiTimeShowMode.HALF_HOUR -> "半點前後${timeShowRangeSeconds}s顯示時間"
                },
                trailingContent = when (settingsViewModel.uiTimeShowMode) {
                    Configs.UiTimeShowMode.HIDDEN -> "隱藏"
                    Configs.UiTimeShowMode.ALWAYS -> "常顯"
                    Configs.UiTimeShowMode.EVERY_HOUR -> "整點"
                    Configs.UiTimeShowMode.HALF_HOUR -> "半點"
                },
                onSelected = {
                    settingsViewModel.uiTimeShowMode =
                        Configs.UiTimeShowMode.entries.let {
                            it[(it.indexOf(settingsViewModel.uiTimeShowMode) + 1) % it.size]
                        }
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "超時自動關閉界面",
                supportingContent = "影響選台界面，快捷操作等界面",
                trailingContent = settingsViewModel.uiScreenAutoCloseDelay.humanizeMs(),
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "超時自動關閉界面",
                currentDataProvider = { settingsViewModel.uiScreenAutoCloseDelay },
                dataListProvider = { listOf(5, 10, 15, 20, 25, 30).map { it.toLong() * 1000 } },
                dataText = { it.humanizeMs() },
                onDataSelected = {
                    settingsViewModel.uiScreenAutoCloseDelay = it
                    visible = false
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "界面整體縮放比例",
                trailingContent = when (settingsViewModel.uiDensityScaleRatio) {
                    0f -> "自適應"
                    else -> "×${DecimalFormat("#.#").format(settingsViewModel.uiDensityScaleRatio)}"
                },
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "界面整體縮放比例",
                currentDataProvider = { settingsViewModel.uiDensityScaleRatio },
                dataListProvider = { listOf(0f) + (5..20).map { it * 0.1f } },
                dataText = {
                    when (it) {
                        0f -> "自適應"
                        else -> "×${DecimalFormat("#.#").format(it)}"
                    }
                },
                onDataSelected = {
                    settingsViewModel.uiDensityScaleRatio = it
                    visible = false
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "界面字體縮放比例",
                trailingContent = "×${DecimalFormat("#.#").format(settingsViewModel.uiFontScaleRatio)}",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "界面字體縮放比例",
                currentDataProvider = { settingsViewModel.uiFontScaleRatio },
                dataListProvider = { (5..20).map { it * 0.1f } },
                dataText = { "×${DecimalFormat("#.#").format(it)}" },
                onDataSelected = {
                    settingsViewModel.uiFontScaleRatio = it
                    visible = false
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "焦點優化",
                supportingContent = "關閉後可解決觸摸設備在部分場景下閃退",
                trailingContent = {
                    Switch(settingsViewModel.uiFocusOptimize, null)
                },
                onSelected = {
                    settingsViewModel.uiFocusOptimize = !settingsViewModel.uiFocusOptimize
                },
            )
        }
    }
}
package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.screens.channelgroup.ChannelGroupManageScreen
import top.yogiczy.mytv.tv.ui.screens.components.SelectDialog
import top.yogiczy.mytv.tv.ui.screens.iptvsource.IptvSourceScreen
import top.yogiczy.mytv.tv.ui.screens.main.MainViewModel
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
) {
    val coroutineScope = rememberCoroutineScope()

    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "數字選台",
                supportingContent = "通過數字選擇頻道",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelNoSelectEnable, null)
                },
                onSelected = {
                    settingsViewModel.iptvChannelNoSelectEnable =
                        !settingsViewModel.iptvChannelNoSelectEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "換台反轉",
                supportingContent = if (settingsViewModel.iptvChannelChangeFlip) "方向鍵上：下一個頻道；方向鍵下：上一個頻道"
                else "方向鍵上：上一個頻道；方向鍵下：下一個頻道",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelChangeFlip, null)
                },
                onSelected = {
                    settingsViewModel.iptvChannelChangeFlip =
                        !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "直播源緩存時間",
                trailingContent = when (settingsViewModel.iptvSourceCacheTime) {
                    0L -> "不緩存"
                    Long.MAX_VALUE -> "永久"
                    else -> settingsViewModel.iptvSourceCacheTime.humanizeMs()
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
                title = "直播源緩存時間",
                currentDataProvider = { settingsViewModel.iptvSourceCacheTime },
                dataListProvider = {
                    (0..<24).map { it * 1000L * 60 * 60 }
                        .plus((1..15).map { it * 1000L * 60 * 60 * 24 })
                        .plus(listOf(Long.MAX_VALUE))
                },
                dataText = {
                    when (it) {
                        0L -> "不緩存"
                        Long.MAX_VALUE -> "永久"
                        else -> it.humanizeMs()
                    }
                },
                onDataSelected = {
                    settingsViewModel.iptvSourceCacheTime = it
                    visible = false
                },
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            val currentIptvSource = settingsViewModel.iptvSourceCurrent
            var isIptvSourceScreenVisible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "自定義直播源",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Tag(if (currentIptvSource.isLocal) "本地" else "遠程")
                        Text(currentIptvSource.name)
                    }
                },
                onSelected = {
                    popupManager.push(focusRequester, true)
                    isIptvSourceScreenVisible = true
                },
                remoteConfig = true,
            )

            SimplePopup(
                visibleProvider = { isIptvSourceScreenVisible },
                onDismissRequest = { isIptvSourceScreenVisible = false },
            ) {
                IptvSourceScreen(
                    iptvSourceListProvider = { settingsViewModel.iptvSourceList },
                    currentIptvSourceProvider = { settingsViewModel.iptvSourceCurrent },
                    onIptvSourceSelected = {
                        isIptvSourceScreenVisible = false
                        if (settingsViewModel.iptvSourceCurrent != it) {
                            settingsViewModel.iptvSourceCurrent = it
                            settingsViewModel.iptvLastChannelIdx = 0
                            settingsViewModel.iptvChannelGroupHiddenList = emptySet()
                            coroutineScope.launch {
                                IptvRepository(settingsViewModel.iptvSourceCurrent).clearCache()
                            }
                            mainViewModel.init()
                        }
                    },
                    onIptvSourceDeleted = {
                        settingsViewModel.iptvSourceList =
                            IptvSourceList(settingsViewModel.iptvSourceList - it)
                    },
                )
            }
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "頻道分組管理",
                supportingContent = "管理頻道分組可見、隱藏狀態",
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true,
            )

            SimplePopup(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
            ) {
                ChannelGroupManageScreen(
                    channelGroupListProvider = {
                        channelGroupListProvider().map { it.name }.toPersistentList()
                    },
                    channelGroupHiddenListProvider = { settingsViewModel.iptvChannelGroupHiddenList.toPersistentList() },
                    onChannelGroupHiddenListChange = {
                        settingsViewModel.iptvChannelGroupHiddenList = it.toSet()
                    },
                    onClose = { visible = false },
                )
            }
        }

        item {
            SettingsListItem(
                headlineContent = "混合模式",
                supportingContent = when (settingsViewModel.iptvHybridMode) {
                    Configs.IptvHybridMode.DISABLE -> ""
                    Configs.IptvHybridMode.IPTV_FIRST -> "優先嚐試播放直播源中線路，若所有直播源線路不可用，則進入混合模式"
                    Configs.IptvHybridMode.HYBRID_FIRST -> "優先進入混合模式，若混合模式不可用，則播放直播源中線路"
                },
                trailingContent = when (settingsViewModel.iptvHybridMode) {
                    Configs.IptvHybridMode.DISABLE -> "禁用"
                    Configs.IptvHybridMode.IPTV_FIRST -> "直播源優先"
                    Configs.IptvHybridMode.HYBRID_FIRST -> "混合優先"
                },
                onSelected = {
                    settingsViewModel.iptvHybridMode =
                        Configs.IptvHybridMode.entries.let { it[(it.indexOf(settingsViewModel.iptvHybridMode) + 1) % it.size] }
                },
            )
        }
    }
}
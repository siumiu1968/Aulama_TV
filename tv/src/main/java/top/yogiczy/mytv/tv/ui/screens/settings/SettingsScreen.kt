package top.yogiczy.mytv.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.material.AulamaBrandLogo
import top.yogiczy.mytv.tv.ui.screens.settings.components.SettingsCategoryContent
import top.yogiczy.mytv.tv.ui.screens.settings.components.SettingsCategoryList
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.customBackground

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    onClose: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val childPadding = rememberChildPadding()
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var currentCategory by remember { mutableStateOf(SettingsCategories.entries.first()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            settingsViewModel.refresh()
        }
    }

    Box(
        modifier = modifier
            .captureBackKey { onClose() }
            .pointerInput(Unit) { detectTapGestures { } }
            .fillMaxSize()
            .customBackground()
            .padding(
                start = childPadding.start,
                top = childPadding.top,
                end = childPadding.end,
                bottom = childPadding.bottom,
            ),
    ) {
        val panelShape = RoundedCornerShape(22.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(224.dp)
                    .fillMaxHeight()
                    .clip(panelShape)
                    .background(MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.82f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.62f),
                        shape = panelShape,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AulamaBrandLogo(modifier = Modifier.width(104.dp))
                    Text(
                        text = "設定",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.45f)),
                )

                SettingsCategoryList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    currentCategoryProvider = { currentCategory },
                    onCategorySelected = { currentCategory = it },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.45f)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Android TV",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(panelShape)
                    .background(MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.76f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.55f),
                        shape = panelShape,
                    )
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                SettingsCategoryContent(
                    modifier = Modifier.fillMaxSize(),
                    currentCategoryProvider = { currentCategory },
                    channelGroupListProvider = channelGroupListProvider,
                )
            }
        }
    }
}

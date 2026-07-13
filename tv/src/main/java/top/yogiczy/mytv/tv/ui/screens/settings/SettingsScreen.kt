package top.yogiczy.mytv.tv.ui.screens.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.rememberChildPadding
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
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val panelShape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .width(228.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.96f),
                                MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.96f),
                            )
                        ),
                        shape = panelShape,
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.65f),
                        panelShape,
                    )
                    .padding(10.dp),
            ) {
                SettingsCategoryList(
                    modifier = Modifier.fillMaxSize(),
                    currentCategoryProvider = { currentCategory },
                    onCategorySelected = { currentCategory = it },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.94f),
                        panelShape,
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.55f),
                        panelShape,
                    )
                    .padding(horizontal = 28.dp, vertical = 22.dp),
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

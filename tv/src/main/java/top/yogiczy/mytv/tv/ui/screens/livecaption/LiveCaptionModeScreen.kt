package top.yogiczy.mytv.tv.ui.screens.livecaption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.caption.LiveCaptionMode
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

internal fun liveCaptionModeLabel(mode: LiveCaptionMode): String = when (mode) {
    LiveCaptionMode.OFF -> "關閉字幕"
    LiveCaptionMode.ENGLISH -> "英文原文"
    LiveCaptionMode.BILINGUAL -> "中英雙語"
    LiveCaptionMode.TRADITIONAL_CHINESE -> "繁中翻譯"
}

@Composable
fun LiveCaptionModeScreen(
    modifier: Modifier = Modifier,
    currentModeProvider: () -> LiveCaptionMode = { LiveCaptionMode.OFF },
    statusMessageProvider: () -> String = { "" },
    accessMessageProvider: () -> String = { "" },
    onModeSelected: (LiveCaptionMode) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)
    val currentMode = currentModeProvider()

    Drawer(
        modifier = modifier.captureBackKey(onBackPressed = onClose),
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text("即時字幕") },
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "同步模式會將直播延遲約 7–10 秒，令字幕同畫面更接近。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            accessMessageProvider().takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LiveCaptionMode.entries) { mode ->
                    val selected = mode == currentMode
                    ListItem(
                        modifier = Modifier
                            .ifElse(selected, Modifier.focusOnLaunchedSaveable(mode))
                            .handleKeyEvents(
                                onSelect = {
                                    screenAutoCloseState.active()
                                    onModeSelected(mode)
                                },
                            ),
                        selected = false,
                        onClick = {},
                        headlineContent = { Text(liveCaptionModeLabel(mode)) },
                        supportingContent = when (mode) {
                            LiveCaptionMode.OFF -> null
                            LiveCaptionMode.ENGLISH -> ({ Text("約 7 秒同步延遲") })
                            LiveCaptionMode.BILINGUAL -> ({ Text("繁中置上、英文置下") })
                            LiveCaptionMode.TRADITIONAL_CHINESE -> ({ Text("約 10 秒同步延遲") })
                        },
                        trailingContent = { RadioButton(selected = selected, onClick = {}) },
                    )
                }
            }

            statusMessageProvider().takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

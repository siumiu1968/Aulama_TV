package top.yogiczy.mytv.tv.ui.screens.update.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun UpdateContent(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    releaseProvider: () -> GitRelease = { GitRelease() },
    isUpdateAvailableProvider: () -> Boolean = { false },
    isUpdatingProvider: () -> Boolean = { false },
    downloadProgressProvider: () -> Int = { 0 },
    downloadFailedProvider: () -> Boolean = { false },
    updateDownloadedProvider: () -> Boolean = { false },
    installerLaunchedProvider: () -> Boolean = { false },
    onUpdateAndInstall: () -> Unit = {},
) {
    val release = releaseProvider()
    val isUpdating = isUpdatingProvider()
    val updateDownloaded = updateDownloadedProvider()
    val primaryActionLabel = when {
        isUpdating -> "下載中 ${downloadProgressProvider()}%"
        updateDownloaded && installerLaunchedProvider() -> "重新開啟安裝畫面"
        updateDownloaded -> "開啟安裝畫面"
        downloadFailedProvider() -> "重新下載"
        else -> "下載並安裝"
    }
    val updateStatus = when {
        isUpdating -> "正在下載並驗證安裝包，完成後會開啟系統安裝畫面"
        updateDownloaded -> "安裝包已驗證並保留；重試時唔需要再次下載"
        downloadFailedProvider() -> "下載未完成的檔案已清理，可以安全重新嘗試"
        else -> "下載後會保留一份已驗證安裝包，安裝成功即自動清理"
    }

    val dialogShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(920.dp)
                .heightIn(min = 430.dp, max = 610.dp)
                .clip(dialogShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.border.copy(alpha = 0.7f)),
                    dialogShape,
                )
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (isUpdateAvailableProvider()) "Android TV 新版本已準備好"
                        else "已是最新版本",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "GitHub Release · v${release.version}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (isUpdateAvailableProvider()) {
                        Text(
                            updateStatus,
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp, max = 310.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "更新內容",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    Text(
                        release.description.ifBlank { "暫時未有更新說明。" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
            ) {
                if (isUpdateAvailableProvider()) {
                    val primaryAction = {
                        if (!isUpdating) onUpdateAndInstall()
                    }
                    WideButton(
                        modifier = Modifier
                            .width(260.dp)
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = primaryAction),
                        onClick = primaryAction,
                        title = { Text(primaryActionLabel) },
                    )

                    WideButton(
                        modifier = Modifier
                            .width(170.dp)
                            .handleKeyEvents(onSelect = onDismissRequest),
                        onClick = onDismissRequest,
                        title = { Text("稍後處理") },
                    )
                } else {
                    WideButton(
                        modifier = Modifier
                            .width(190.dp)
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = onDismissRequest),
                        onClick = onDismissRequest,
                        title = { Text("完成") },
                    )
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun UpdateDialogPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            UpdateContent(
                releaseProvider = {
                    GitRelease(
                        version = "1.0.0",
                        downloadUrl = "",
                        description = "更新日誌".repeat(100),
                    )
                },
            )
        }
    }
}

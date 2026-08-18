package top.yogiczy.mytv.tv.ui.screens.quickop.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched

@Composable
fun QuickOpBtnList(
    modifier: Modifier = Modifier,
    onShowEpg: () -> Unit = {},
    onShowChannelUrl: () -> Unit = {},
    onShowVideoPlayerController: () -> Unit = {},
    onShowVideoPlayerDisplayMode: () -> Unit = {},
    showLiveCaptionProvider: () -> Boolean = { false },
    onShowLiveCaption: () -> Unit = {},
    onShowMoreSettings: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    LazyRow(
        modifier = modifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = childPadding.start, end = childPadding.end),
    ) {
        item {
            QuickOpBtn(
                modifier = Modifier.focusOnLaunched(),
                icon = Icons.Default.DateRange,
                title = "節目指南",
                onSelect = onShowEpg,
            )
        }

        item {
            QuickOpBtn(
                icon = Icons.AutoMirrored.Filled.AltRoute,
                title = "切換線路",
                onSelect = onShowChannelUrl,
            )
        }

        item {
            QuickOpBtn(
                icon = Icons.Default.PlayCircle,
                title = "播放控制",
                onSelect = onShowVideoPlayerController,
            )
        }

        if (showLiveCaptionProvider()) {
            item {
                QuickOpBtn(
                    icon = Icons.Default.ClosedCaption,
                    title = "即時字幕",
                    onSelect = onShowLiveCaption,
                )
            }
        }

        item {
            QuickOpBtn(
                icon = Icons.Default.AspectRatio,
                title = "畫面比例",
                onSelect = onShowVideoPlayerDisplayMode,
            )
        }

        item {
            QuickOpBtn(
                icon = Icons.Default.Refresh,
                title = "重新整理",
                onSelect = onClearCache,
            )
        }
        item {
            QuickOpBtn(
                icon = Icons.Default.Settings,
                title = "設定",
                onSelect = onShowMoreSettings,
            )
        }
    }
}

@Preview
@Composable
private fun QuickOpBtnListPreview() {
    MyTVTheme {
        QuickOpBtnList()
    }
}

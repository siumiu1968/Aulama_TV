package top.yogiczy.mytv.tv.ui.screens.channelurl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.account.AulamaPlaybackPolicy
import top.yogiczy.mytv.tv.ui.screens.channelurl.components.ChannelUrlItemList
import top.yogiczy.mytv.tv.ui.screens.channelurl.components.PlaybackTransportItemList
import top.yogiczy.mytv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.IptvRoutePriorityStore
import top.yogiczy.mytv.tv.ui.utils.captureBackKey

@Composable
fun ChannelUrlScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    currentUrlProvider: () -> String = { "" },
    isSuperAdminProvider: () -> Boolean = { false },
    transportPreferenceIdProvider: () -> String = {
        AulamaPlaybackPolicy.DEFAULT_PREFERENCE_ID
    },
    onTransportPreferenceSelected: (String) -> Unit = {},
    onUrlSelected: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)
    val channel = channelProvider()
    val sourceEntryFocusRequester = remember(channel.name) { FocusRequester() }
    var priorityUrls by remember(channel.name) {
        mutableStateOf(IptvRoutePriorityStore.priorities(channel.name))
    }

    Drawer(
        modifier = modifier.captureBackKey { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
    ) {
        val isSuperAdmin = isSuperAdminProvider()
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown) screenAutoCloseState.active()
                    false
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "播放線路",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${channel.urlList.size} 條",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (isSuperAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "連線方式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "管理員",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                PlaybackTransportItemList(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIdProvider = transportPreferenceIdProvider,
                    sourceEntryFocusRequester = sourceEntryFocusRequester,
                    onSelected = onTransportPreferenceSelected,
                    onUserAction = { screenAutoCloseState.active() },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.5f)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "頻道線路",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (priorityUrls.isNotEmpty()) {
                    Text(
                        text = "已優先 ${priorityUrls.size} 條",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            ChannelUrlItemList(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                urlListProvider = { channel.urlList.toPersistentList() },
                currentUrlProvider = currentUrlProvider,
                priorityUrlsProvider = { priorityUrls },
                entryFocusRequester = sourceEntryFocusRequester,
                focusSelectedOnLaunch = !isSuperAdmin,
                onSelected = onUrlSelected,
                onPriorityToggle = { url ->
                    priorityUrls = IptvRoutePriorityStore.toggle(channel.name, url)
                    val rank = priorityUrls.indexOf(url).takeIf { it >= 0 }?.plus(1)
                    Snackbar.show(
                        rank?.let { "已設為優先 $it：線路${channel.urlList.indexOf(url) + 1}" }
                            ?: "已取消優先：線路${channel.urlList.indexOf(url) + 1}"
                    )
                },
                onUserAction = { screenAutoCloseState.active() },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ChannelUrlScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelUrlScreen(
                channelProvider = { Channel.EXAMPLE },
            )
        }
    }
}

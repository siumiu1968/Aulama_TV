package top.yogiczy.mytv.tv.ui.screens.channelurl

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screens.channelurl.components.ChannelUrlItemList
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
    onUrlSelected: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)
    val channel = channelProvider()
    var priorityUrls by remember(channel.name) {
        mutableStateOf(IptvRoutePriorityStore.priorities(channel.name))
    }

    Drawer(
        modifier = modifier.captureBackKey { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text("多線路") },
    ) {
        ChannelUrlItemList(
            modifier = Modifier.width(360.dp),
            urlListProvider = { channel.urlList.toPersistentList() },
            currentUrlProvider = currentUrlProvider,
            priorityUrlsProvider = { priorityUrls },
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

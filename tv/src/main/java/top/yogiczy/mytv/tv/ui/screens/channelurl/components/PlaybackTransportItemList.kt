package top.yogiczy.mytv.tv.ui.screens.channelurl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.account.AulamaPlaybackPolicy
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus

private data class PlaybackTransportOption(
    val id: String,
    val title: String,
    val detail: String,
)

private val playbackTransportOptions = listOf(
    PlaybackTransportOption(
        AulamaPlaybackPolicy.AUTO_PREFERENCE_ID,
        "自動",
        "香港 → 日本 → 直接",
    ),
    PlaybackTransportOption("hk_relay", "香港中轉", "香港優先，自動後備"),
    PlaybackTransportOption("jp_relay", "日本中轉", "日本優先，自動後備"),
    PlaybackTransportOption("direct", "直接連線", "直連優先，自動後備"),
)

@Composable
fun PlaybackTransportItemList(
    modifier: Modifier = Modifier,
    selectedIdProvider: () -> String = { AulamaPlaybackPolicy.AUTO_PREFERENCE_ID },
    onSelected: (String) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val selectedId = selectedIdProvider()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        playbackTransportOptions.forEach { option ->
            val selected = option.id == selectedId
            val focusRequester = androidx.compose.runtime.remember(option.id) { FocusRequester() }

            LaunchedEffect(selected) {
                if (selected) focusRequester.saveRequestFocus()
            }

            ListItem(
                modifier = Modifier.focusRequester(focusRequester),
                selected = selected,
                onClick = {
                    onUserAction()
                    onSelected(option.id)
                },
                headlineContent = { Text(option.title) },
                supportingContent = { Text(option.detail) },
                trailingContent = {
                    RadioButton(selected = selected, onClick = null)
                },
            )
        }
    }
}

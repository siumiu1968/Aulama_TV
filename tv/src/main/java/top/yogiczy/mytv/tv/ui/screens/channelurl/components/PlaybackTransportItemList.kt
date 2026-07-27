package top.yogiczy.mytv.tv.ui.screens.channelurl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
    sourceEntryFocusRequester: FocusRequester? = null,
    onSelected: (String) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val selectedId = selectedIdProvider()
    val selectedOption = playbackTransportOptions.firstOrNull { it.id == selectedId }
        ?: playbackTransportOptions.first()

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val optionRows = playbackTransportOptions.chunked(2)
        optionRows.forEachIndexed { rowIndex, rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    val selected = option.id == selectedOption.id
                    val focusRequester = remember(option.id) { FocusRequester() }

                    LaunchedEffect(selected) {
                        if (selected) focusRequester.saveRequestFocus()
                    }

                    ListItem(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .focusRequester(focusRequester)
                            .focusProperties {
                                if (rowIndex == optionRows.lastIndex) {
                                    sourceEntryFocusRequester?.let { down = it }
                                }
                            },
                        selected = selected,
                        onClick = {
                            onUserAction()
                            onSelected(option.id)
                        },
                        headlineContent = { Text(option.title) },
                        trailingContent = {
                            RadioButton(selected = selected, onClick = null)
                        },
                    )
                }
            }
        }

        Text(
            text = selectedOption.detail,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

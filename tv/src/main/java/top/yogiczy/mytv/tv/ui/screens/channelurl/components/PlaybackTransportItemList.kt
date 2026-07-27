package top.yogiczy.mytv.tv.ui.screens.channelurl.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
    PlaybackTransportOption("hk_relay", "香港", "香港中轉優先，自動後備"),
    PlaybackTransportOption("jp_relay", "日本", "日本中轉優先，自動後備"),
    PlaybackTransportOption("direct", "直連", "直接連線優先，自動後備"),
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
    val optionShape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            playbackTransportOptions.forEach { option ->
                val selected = option.id == selectedOption.id
                val focusRequester = remember(option.id) { FocusRequester() }

                LaunchedEffect(selected) {
                    if (selected) focusRequester.saveRequestFocus()
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                        .focusProperties {
                            sourceEntryFocusRequester?.let { down = it }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        focusedContentColor = MaterialTheme.colorScheme.secondary,
                        pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        pressedContentColor = MaterialTheme.colorScheme.secondary,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    shape = ClickableSurfaceDefaults.shape(optionShape),
                    border = ClickableSurfaceDefaults.border(
                        border = if (selected) {
                            Border(
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
                                ),
                                shape = optionShape,
                            )
                        } else {
                            Border.None
                        },
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            shape = optionShape,
                        ),
                    ),
                    onClick = {
                        onUserAction()
                        onSelected(option.id)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 3.dp)
                                    .size(15.dp),
                            )
                        }
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                    shape = optionShape,
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "目前",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = selectedOption.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

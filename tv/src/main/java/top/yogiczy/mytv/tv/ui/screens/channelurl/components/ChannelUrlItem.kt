package top.yogiczy.mytv.tv.ui.screens.channelurl.components

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import androidx.tv.material3.Border
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.util.utils.isIPv6
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus
import java.io.IOException
import kotlin.system.measureTimeMillis

@Composable
fun ChannelUrlItem(
    modifier: Modifier = Modifier,
    urlProvider: () -> String = { "" },
    urlIdxProvider: () -> Int = { 0 },
    isSelectedProvider: () -> Boolean = { false },
    priorityRankProvider: () -> Int? = { null },
    externalRowFocusRequester: FocusRequester? = null,
    focusSelectedOnLaunch: Boolean = true,
    onSelected: () -> Unit = {},
    onPriorityToggle: () -> Unit = {},
) {
    val url = urlProvider()
    val urlIdx = urlIdxProvider()
    val isSelected = isSelectedProvider()
    val priorityRank = priorityRankProvider()
    val fallbackRowFocusRequester = remember(url) { FocusRequester() }
    val rowFocusRequester = externalRowFocusRequester ?: fallbackRowFocusRequester
    val priorityFocusRequester = remember(url) { FocusRequester() }

    val urlDelay = rememberUrlDelay(url)
    val itemShape = RoundedCornerShape(6.dp)

    LaunchedEffect(isSelected, focusSelectedOnLaunch) {
        if (isSelected && focusSelectedOnLaunch) rowFocusRequester.saveRequestFocus()
    }

    ListItem(
        modifier = modifier
            .height(66.dp)
            .focusRequester(rowFocusRequester)
            .focusProperties { right = priorityFocusRequester },
        selected = isSelected,
        onClick = onSelected,
        shape = ListItemDefaults.shape(shape = itemShape),
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        scale = ListItemDefaults.scale(focusedScale = 1f),
        border = ListItemDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = itemShape,
            ),
        ),
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "線路 ${urlIdx + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )

                if (ChannelUtil.isHybridWebViewUrl(url)) {
                    Text(
                        text = "混合 · ${ChannelUtil.getHybridWebViewUrlProvider(url)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                } else {
                    val routeInfo = buildList {
                        if (ChannelUtil.urlSupportPlayback(url)) add("回放")
                        add(if (url.isIPv6()) "IPV6" else "IPV4")
                        if (urlDelay != 0L) add("$urlDelay ms")
                    }.joinToString(" · ")
                    Text(
                        text = routeInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = url,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                priorityRank?.let {
                    Text(
                        text = "#$it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(
                    modifier = Modifier
                        .size(44.dp)
                        .focusRequester(priorityFocusRequester)
                        .focusProperties {
                            left = rowFocusRequester
                        }
                        .onPreviewKeyEvent { event ->
                            val isSelectKey = event.nativeKeyEvent.keyCode in setOf(
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                            )
                            if (!isSelectKey) return@onPreviewKeyEvent false

                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                onPriorityToggle()
                            }
                            true
                        },
                    onClick = onPriorityToggle,
                ) {
                    Icon(
                        imageVector = if (priorityRank == null) {
                            Icons.Outlined.StarBorder
                        } else {
                            Icons.Filled.Star
                        },
                        contentDescription = if (priorityRank == null) {
                            "設為優先線路"
                        } else {
                            "取消優先線路 $priorityRank"
                        },
                        modifier = Modifier.size(19.dp),
                    )
                }
                RadioButton(selected = isSelected, onClick = null)
            }
        },
    )
}

@Composable
private fun rememberUrlDelay(url: String): Long {
    var elapsedTime by remember { mutableLongStateOf(0) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val client = OkHttp.client
                val request = Request.Builder().url(url).build()

                elapsedTime = measureTimeMillis {
                    try {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("Unexpected code $response")
                        }
                    } catch (_: IOException) {
                        hasError = true
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    return if (hasError) 0 else elapsedTime
}

@Preview
@Composable
private fun ChannelUrlItemPreview() {
    MyTVTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChannelUrlItem(
                urlProvider = { "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8" },
                urlIdxProvider = { 0 },
                isSelectedProvider = { true },
                priorityRankProvider = { 1 },
            )

            ChannelUrlItem(
                urlProvider = { "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS" },
                urlIdxProvider = { 0 },
            )

            ChannelUrlItem(
                urlProvider = { ChannelUtil.getHybridWebViewUrl("cctv1")!!.first() },
                urlIdxProvider = { 0 },
                isSelectedProvider = { true },
            )
        }
    }
}

package top.yogiczy.mytv.tv.ui.screens.epg.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.progress
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.theme.AulamaTvBlue
import top.yogiczy.mytv.tv.ui.theme.AulamaTvCyan
import top.yogiczy.mytv.tv.ui.theme.AulamaTvOrange
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun EpgProgrammeItem(
    modifier: Modifier = Modifier,
    epgProgrammeProvider: () -> EpgProgramme = { EpgProgramme() },
    supportPlaybackProvider: () -> Boolean = { false },
    isPlaybackProvider: () -> Boolean = { false },
    currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    hasReservedProvider: () -> Boolean = { false },
    onPlayback: () -> Unit = {},
    onReserve: () -> Unit = {},
    focusOnLive: Boolean = true,
) {
    val programme = epgProgrammeProvider()
    val timeFormat = remember {
        SimpleDateFormat("HH:mm", Locale.TRADITIONAL_CHINESE).apply {
            timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        }
    }
    val currentTime = currentTimeProvider()
    val isLive = currentTime in programme.startAt..<programme.endAt
    val isPlayback = isPlaybackProvider()
    val liveProgress = if (isLive) programme.progress(currentTime).coerceIn(0f, 1f) else 0f

    var isFocused by remember { mutableStateOf(false) }

    val itemShape = RoundedCornerShape(6.dp)
    Box(modifier = Modifier.height(76.dp)) {
        ListItem(
            modifier = modifier
                .fillMaxWidth()
                .height(76.dp)
                .ifElse(isLive && focusOnLive, Modifier.focusOnLaunchedSaveable())
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                .handleKeyEvents(
                    onSelect = {
                        if (programme.endAt < currentTime && supportPlaybackProvider()) onPlayback()
                        else if (programme.startAt > currentTime) onReserve()
                    }
                ),
            shape = ListItemDefaults.shape(shape = itemShape),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = AulamaTvBlue.copy(alpha = 0.82f),
                focusedContentColor = Color.White,
                selectedContainerColor = AulamaTvCyan.copy(alpha = 0.16f),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                focusedSelectedContainerColor = AulamaTvBlue.copy(alpha = 0.88f),
                focusedSelectedContentColor = Color.White,
            ),
            scale = ListItemDefaults.scale(focusedScale = 1.01f),
            border = ListItemDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, AulamaTvCyan),
                    shape = itemShape,
                ),
            ),
            selected = isPlayback,
            onClick = {},
            leadingContent = {
                Column(
                    modifier = Modifier.width(78.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ProgrammeTime("開始", timeFormat.format(programme.startAt), isFocused)
                    ProgrammeTime("結束", timeFormat.format(programme.endAt), isFocused)
                }
            },
            headlineContent = {
                Text(
                    text = programme.title.ifBlank { "未命名節目" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                ProgrammeStatus(
                    isLive = isLive,
                    isPlayback = isPlayback,
                    isPast = programme.endAt < currentTime,
                    supportsPlayback = supportPlaybackProvider(),
                    hasReserved = hasReservedProvider(),
                    progress = liveProgress,
                    isFocused = isFocused,
                )
            },
        )

        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(liveProgress)
                    .height(4.dp)
                    .background(AulamaTvOrange, RoundedCornerShape(bottomStart = 6.dp)),
            )
        }
    }
}

@Composable
private fun ProgrammeTime(label: String, time: String, isFocused: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgrammeStatus(
    isLive: Boolean,
    isPlayback: Boolean,
    isPast: Boolean,
    supportsPlayback: Boolean,
    hasReserved: Boolean,
    progress: Float,
    isFocused: Boolean,
) {
    val statusColor = if (isFocused) Color.White else when {
        isLive -> AulamaTvOrange
        isPlayback -> AulamaTvCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = when {
                isPlayback && isLive -> "直播播放中"
                isPlayback -> "正在回放"
                isLive -> "直播中"
                isPast && supportsPlayback -> "可回放"
                !isPast && hasReserved -> "已預約"
                !isPast -> "可預約"
                else -> "已結束"
            },
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isLive) {
            Text(
                text = "進度 ${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemPreview() {
    MyTVTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            EpgProgrammeItem(
                epgProgrammeProvider = { EpgProgramme.EXAMPLE },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() - 200000,
                        endAt = System.currentTimeMillis() - 100000,
                    )
                },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
                hasReservedProvider = { true },
            )
        }
    }
}

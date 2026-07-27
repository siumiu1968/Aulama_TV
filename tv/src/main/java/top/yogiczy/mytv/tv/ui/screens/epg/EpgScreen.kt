package top.yogiczy.mytv.tv.ui.screens.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.screens.epg.components.EpgDayItemList
import top.yogiczy.mytv.tv.ui.screens.epg.components.EpgProgrammeItemList
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.captureBackKey
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun EpgScreen(
    modifier: Modifier = Modifier,
    epgProvider: () -> Epg = { Epg() },
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    supportPlaybackProvider: () -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    onEpgProgrammePlayback: (EpgProgramme) -> Unit = {},
    onEpgProgrammeReserve: (EpgProgramme) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)

    val hongKongTimeZone = remember { TimeZone.getTimeZone("Asia/Hong_Kong") }
    val dateFormat = remember {
        SimpleDateFormat("E MM-dd", Locale.TRADITIONAL_CHINESE).apply {
            timeZone = hongKongTimeZone
        }
    }
    val headerTimeFormat = remember {
        SimpleDateFormat("MM月dd日 E  HH:mm", Locale.TRADITIONAL_CHINESE).apply {
            timeZone = hongKongTimeZone
        }
    }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            currentTime = System.currentTimeMillis()
        }
    }

    val epg = epgProvider()
    val programDayGroup = epg.programmeList
        .sortedBy { it.startAt }
        .groupBy { dateFormat.format(it.startAt) }
    val today = dateFormat.format(currentTime)
    var currentDay by remember(epg.channel, programDayGroup.keys) {
        mutableStateOf(if (today in programDayGroup) today else programDayGroup.keys.firstOrNull().orEmpty())
    }

    Drawer(
        modifier = modifier
            .captureBackKey { onClose() }
            .focusOnLaunched(),
        onDismissRequest = onClose,
        position = DrawerPosition.Start,
        header = {
            Column(modifier = Modifier.width(500.dp)) {
                Text(
                    text = epg.channel.ifBlank { "節目單" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "節目單 · 香港時間 ${headerTimeFormat.format(currentTime)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EpgProgrammeItemList(
                modifier = Modifier.width(500.dp),
                epgProgrammeListProvider = {
                    EpgProgrammeList(programDayGroup.getOrElse(currentDay) { listOf(EpgProgramme.EMPTY) })
                },
                epgProgrammeReserveListProvider = epgProgrammeReserveListProvider,
                supportPlaybackProvider = supportPlaybackProvider,
                currentPlaybackProvider = currentPlaybackEpgProgrammeProvider,
                currentTimeProvider = { currentTime },
                onPlayback = onEpgProgrammePlayback,
                onReserve = onEpgProgrammeReserve,
                onUserAction = { screenAutoCloseState.active() },
            )

            if (programDayGroup.size > 1) {
                EpgDayItemList(
                    modifier = Modifier.width(104.dp),
                    dayListProvider = { programDayGroup.keys.toPersistentList() },
                    currentDayProvider = { currentDay },
                    onDaySelected = { currentDay = it },
                    onUserAction = { screenAutoCloseState.active() },
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun EpgScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            EpgScreen(
                epgProvider = { Epg.example(Channel.EXAMPLE) },
            )
        }
    }
}

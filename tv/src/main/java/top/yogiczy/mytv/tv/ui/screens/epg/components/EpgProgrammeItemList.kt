package top.yogiczy.mytv.tv.ui.screens.epg.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.isLive
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import kotlin.math.max

@Composable
fun EpgProgrammeItemList(
    modifier: Modifier = Modifier,
    epgProgrammeListProvider: () -> EpgProgrammeList = { EpgProgrammeList() },
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    supportPlaybackProvider: () -> Boolean = { false },
    currentPlaybackProvider: () -> EpgProgramme? = { null },
    currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    onPlayback: (EpgProgramme) -> Unit = {},
    onReserve: (EpgProgramme) -> Unit = {},
    focusOnLive: Boolean = true,
    onUserAction: () -> Unit = {},
) {
    val epgProgrammeList = epgProgrammeListProvider()
    val itemFocusRequesterList = androidx.compose.runtime.remember(epgProgrammeList) {
        List(epgProgrammeList.size) { FocusRequester() }
    }

    val liveProgrammeIndex = epgProgrammeList.indexOfFirst { it.isLive() }
    val initialProgrammeIndex = max(0, liveProgrammeIndex - 1)
    val listState = androidx.compose.runtime.remember(epgProgrammeList) {
        LazyListState(initialProgrammeIndex)
    }
    LaunchedEffect(listState, liveProgrammeIndex) {
        if (liveProgrammeIndex >= 0) {
            delay(120)
            listState.scrollToItem(initialProgrammeIndex)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    LazyColumn(
        modifier = modifier,
        // FIXME 閃退
        // .focusRestorer {
        //     itemFocusRequesterList[max(0, epgProgrammeList.indexOfFirst { it.isLive() })]
        // },
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            epgProgrammeList,
            key = { _, programme -> programme.hashCode() },
        ) { index, programme ->
            EpgProgrammeItem(
                modifier = Modifier.focusRequester(itemFocusRequesterList[index]),
                epgProgrammeProvider = { programme },
                supportPlaybackProvider = supportPlaybackProvider,
                isPlaybackProvider = { currentPlaybackProvider() == programme },
                currentTimeProvider = currentTimeProvider,
                hasReservedProvider = { epgProgrammeReserveListProvider().firstOrNull { it.programme == programme.title } != null },
                onPlayback = { onPlayback(programme) },
                onReserve = { onReserve(programme) },
                focusOnLive = focusOnLive,
            )
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemListPreview() {
    MyTVTheme {
        EpgProgrammeItemList(
            epgProgrammeListProvider = { EpgProgrammeList(Epg.example(Channel.EXAMPLE).programmeList) }
        )
    }
}

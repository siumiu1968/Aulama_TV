package top.yogiczy.mytv.tv.ui.screens.classicchannel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Border
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.progress
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeRecent
import top.yogiczy.mytv.tv.ui.material.rememberDebounceState
import top.yogiczy.mytv.tv.ui.screens.channel.components.ChannelItemLogo
import top.yogiczy.mytv.tv.ui.screens.settings.LocalSettings
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.theme.AulamaTvBlue
import top.yogiczy.mytv.tv.ui.theme.AulamaTvCyan
import top.yogiczy.mytv.tv.ui.theme.AulamaTvOrange
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.utils.saveFocusRestorer
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClassicChannelItemList(
    modifier: Modifier = Modifier,
    channelGroupProvider: () -> ChannelGroup = { ChannelGroup() },
    channelListProvider: () -> ChannelList = { ChannelList() },
    initialChannelProvider: () -> Channel = { Channel() },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    onChannelFocused: (Channel) -> Unit = { },
    epgListProvider: () -> EpgList = { EpgList() },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    inFavoriteModeProvider: () -> Boolean = { false },
    onUserAction: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val channelGroup = channelGroupProvider()
    val channelList = channelListProvider()
    val initialChannel = initialChannelProvider()
    val itemFocusRequesterList =
        remember(channelList) { List(channelList.size) { FocusRequester() } }

    var hasFocused by rememberSaveable { mutableStateOf(!channelList.contains(initialChannel)) }
    var focusedChannel by remember(channelList) {
        mutableStateOf(
            if (hasFocused) channelList.firstOrNull() ?: Channel() else initialChannel
        )
    }

    val onChannelFocusedDebounce = rememberDebounceState(wait = 100L) {
        onChannelFocused(focusedChannel)
    }

    val listState = remember(channelGroup) {
        LazyListState(
            if (hasFocused) 0
            else max(0, channelList.indexOf(initialChannel) - 2)
        )
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    val coroutineScope = rememberCoroutineScope()
    val firstFocusRequester = remember { FocusRequester() }
    val lastFocusRequester = remember { FocusRequester() }
    fun scrollToFirst() {
        coroutineScope.launch {
            listState.scrollToItem(0)
            firstFocusRequester.saveRequestFocus()
        }
    }

    fun scrollToLast() {
        coroutineScope.launch {
            listState.scrollToItem(channelList.lastIndex)
            lastFocusRequester.saveRequestFocus()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .width(if (showChannelLogoProvider()) 292.dp else 236.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE60A1725),
                        Color(0xD90B1B2B),
                        Color(0xD9101725),
                    )
                )
            )
            .ifElse(
                LocalSettings.current.uiFocusOptimize,
                Modifier.saveFocusRestorer {
                    itemFocusRequesterList[channelList.indexOf(focusedChannel)]
                },
            ),
        state = listState,
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(channelList, key = { _, channel -> channel.hashCode() }) { index, channel ->
            val isSelected by remember { derivedStateOf { channel == focusedChannel } }
            val initialFocused by remember {
                derivedStateOf { !hasFocused && channel == initialChannel }
            }

            ClassicChannelItem(
                modifier = Modifier
                    .ifElse(
                        index == 0,
                        Modifier
                            .focusRequester(firstFocusRequester)
                            .handleKeyEvents(onUp = { scrollToLast() })
                    )
                    .ifElse(
                        index == channelList.lastIndex,
                        Modifier
                            .focusRequester(lastFocusRequester)
                            .handleKeyEvents(onDown = { scrollToFirst() })
                    ),
                channelProvider = { channel },
                onChannelSelected = { onChannelSelected(channel) },
                onChannelFavoriteToggle = {
                    if (inFavoriteModeProvider()) {
                        if (channelList.size == 1) {
                            focusManager.moveFocus(FocusDirection.Left)
                        } else if (channelList.first() == channel) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else if (channelList.last() == channel) {
                            focusManager.moveFocus(FocusDirection.Up)
                        } else {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                    onChannelFavoriteToggle(channel)
                },
                onChannelFocused = {
                    focusedChannel = channel
                    onChannelFocusedDebounce.send()
                },
                recentEpgProgrammeProvider = { epgListProvider().recentProgramme(channel) },
                showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                focusRequesterProvider = { itemFocusRequesterList[index] },
                initialFocusedProvider = { initialFocused },
                onInitialFocused = { hasFocused = true },
                isSelectedProvider = { isSelected },
                showChannelLogoProvider = showChannelLogoProvider,
            )
        }
    }
}

@Composable
private fun ClassicChannelItem(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: () -> Unit = {},
    onChannelFavoriteToggle: () -> Unit = {},
    onChannelFocused: () -> Unit = {},
    recentEpgProgrammeProvider: () -> EpgProgrammeRecent? = { null },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    focusRequesterProvider: () -> FocusRequester = { FocusRequester() },
    initialFocusedProvider: () -> Boolean = { false },
    onInitialFocused: () -> Unit = {},
    isSelectedProvider: () -> Boolean = { false },
) {
    val channel = channelProvider()
    val nowEpgProgramme = recentEpgProgrammeProvider()?.now
    val showEpgProgrammeProgress = showEpgProgrammeProgressProvider()
    val focusRequester = focusRequesterProvider()

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onInitialFocused()
            focusRequester.saveRequestFocus()
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showChannelLogoProvider()) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .aspectRatio(1.6f),
                contentAlignment = Alignment.Center
            ) {
                ChannelItemLogo(
                    modifier = Modifier.fillMaxHeight(),
                    logoProvider = { channel.logo },
                    textFallbackProvider = { channel.name },
                )
            }
        }

        val itemShape = RoundedCornerShape(12.dp)
        Box(modifier = modifier.clip(itemShape)) {
            DenseListItem(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (isFocused) onChannelFocused()
                    }
                    .handleKeyEvents(
                        onSelect = onChannelSelected,
                        onLongSelect = onChannelFavoriteToggle,
                    ),
                shape = ListItemDefaults.shape(shape = itemShape),
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = AulamaTvBlue.copy(alpha = 0.82f),
                    focusedContentColor = Color.White,
                    selectedContainerColor = AulamaTvCyan.copy(alpha = 0.16f),
                    selectedContentColor = Color.White,
                    focusedSelectedContainerColor = AulamaTvBlue.copy(alpha = 0.88f),
                    focusedSelectedContentColor = Color.White,
                ),
                scale = ListItemDefaults.scale(focusedScale = 1.018f),
                border = ListItemDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, AulamaTvCyan),
                        shape = itemShape,
                    ),
                ),
                selected = isSelectedProvider(),
                onClick = {},
                headlineContent = {
                    Row{
                        Text(
                            text = channel.id,
                            maxLines = 1,
                            color = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                        )
                        Text(
                            channel.name,
                            maxLines = 1,
                            modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
                        )
                    }

                },
                supportingContent = {
                    Text(
                        text = nowEpgProgramme?.title ?: "精彩節目",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
                    )
                },
            )

            if (showEpgProgrammeProgress && nowEpgProgramme != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(nowEpgProgramme.progress())
                        .height(3.dp)
                        .background(AulamaTvOrange),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ClassicChannelItemListPreview() {
    MyTVTheme {
        Row {
            ClassicChannelItemList(
                channelListProvider = { ChannelList.EXAMPLE },
                initialChannelProvider = { ChannelList.EXAMPLE.first() },
                epgListProvider = { EpgList.example(ChannelList.EXAMPLE) },
                showEpgProgrammeProgressProvider = { true },
            )
        }
    }
}

@Preview
@Composable
private fun ClassicChannelItemListWithChannelLogoPreview() {
    MyTVTheme {
        Row {
            ClassicChannelItemList(
                channelListProvider = { ChannelList.EXAMPLE },
                initialChannelProvider = { ChannelList.EXAMPLE.first() },
                epgListProvider = { EpgList.example(ChannelList.EXAMPLE) },
                showEpgProgrammeProgressProvider = { true },
                showChannelLogoProvider = { true },
            )
        }
    }
}

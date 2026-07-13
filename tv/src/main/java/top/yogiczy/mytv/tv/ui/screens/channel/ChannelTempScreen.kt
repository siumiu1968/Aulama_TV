package top.yogiczy.mytv.tv.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeRecent
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screens.channel.components.ChannelInfo
import top.yogiczy.mytv.tv.ui.screens.channel.components.ChannelNumber
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids

@Composable
fun ChannelTempScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    channelUrlIdxProvider: () -> Int = { 0 },
    channelNumberProvider: () -> Int = { 0 },
    showChannelLogoProvider: () -> Boolean = { false },
    recentEpgProgrammeProvider: () -> EpgProgrammeRecent? = { null },
    isInTimeShiftProvider: () -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        ChannelNumber(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = childPadding.top, end = childPadding.end)
                .background(
                    MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.86f),
                    RoundedCornerShape(16.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.7f),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            channelNumberProvider = { channelNumberProvider().toString().padStart(2, '0') },
        )

        ChannelInfo(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = childPadding.start, bottom = childPadding.bottom)
                .fillMaxWidth(0.62f)
                .background(
                    MaterialTheme.colors.surfaceContainerLow.copy(alpha = 0.9f),
                    RoundedCornerShape(18.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.7f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            channelProvider = channelProvider,
            channelUrlIdxProvider = channelUrlIdxProvider,
            recentEpgProgrammeProvider = recentEpgProgrammeProvider,
            isInTimeShiftProvider = isInTimeShiftProvider,
            currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
            videoPlayerMetadataProvider = videoPlayerMetadataProvider,
            showChannelLogoProvider = showChannelLogoProvider,
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ChannelTempScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelTempScreen(
                channelProvider = { Channel.EXAMPLE.copy(name = "長標題".repeat(4)) },
                channelUrlIdxProvider = { 0 },
                channelNumberProvider = { 8 },
                recentEpgProgrammeProvider = { EpgProgrammeRecent.EXAMPLE },
            )
        }
    }
}

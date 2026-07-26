package top.yogiczy.mytv.tv.ui.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import top.yogiczy.mytv.core.data.entities.channel.ChannelQuality
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.LeTvVideoPlayer

object IptvDisplayCapabilities {
    fun supportsHdrOutput(context: Context): Boolean {
        if (LeTvVideoPlayer.isAvailable(context)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return display.hdrCapabilities.supportedHdrTypes.isNotEmpty()
    }
}

internal fun orderRoutesForDisplay(
    routes: List<ChannelRoute>,
    rankedIndices: List<Int>,
    requestedIndex: Int?,
    supportsHdrOutput: Boolean,
): List<Int> {
    val requested = requestedIndex?.takeIf(routes.indices::contains)
    val remaining = rankedIndices.filterNot { it == requested }
    val displayOrdered = if (supportsHdrOutput) {
        remaining
    } else {
        val (sdr, uhd) = remaining.partition {
            routes.getOrNull(it)?.quality != ChannelQuality.UHD_4K
        }
        sdr + uhd
    }

    return requested?.let { listOf(it) + displayOrdered } ?: displayOrdered
}

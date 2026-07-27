package top.yogiczy.mytv.tv.ui.screens.epg.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.theme.AulamaTvBlue
import top.yogiczy.mytv.tv.ui.theme.AulamaTvCyan
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun EpgDayItem(
    modifier: Modifier = Modifier,
    dayProvider: () -> String = { "" }, // 格式：E MM-dd
    isSelectedProvider: () -> Boolean = { false },
    onDaySelected: () -> Unit = {},
) {
    val day = dayProvider()

    val dateFormat = SimpleDateFormat("E MM-dd", Locale.TRADITIONAL_CHINESE).apply {
        timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
    }
    val today = dateFormat.format(System.currentTimeMillis())
    val tomorrow =
        dateFormat.format(System.currentTimeMillis() + 24 * 3600 * 1000)
    val dayAfterTomorrow =
        dateFormat.format(System.currentTimeMillis() + 48 * 3600 * 1000)

    val itemShape = RoundedCornerShape(6.dp)
    ListItem(
        modifier = modifier
            .height(64.dp)
            .handleKeyEvents(onSelect = onDaySelected),
        shape = ListItemDefaults.shape(shape = itemShape),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AulamaTvBlue.copy(alpha = 0.82f),
            focusedContentColor = Color.White,
            selectedContainerColor = AulamaTvCyan.copy(alpha = 0.16f),
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            focusedSelectedContainerColor = AulamaTvBlue.copy(alpha = 0.88f),
            focusedSelectedContentColor = Color.White,
        ),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        border = ListItemDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, AulamaTvCyan),
                shape = itemShape,
            ),
        ),
        selected = isSelectedProvider(),
        onClick = {},
        headlineContent = {
            val lines = day.split(" ", limit = 2)

            Text(
                when (day) {
                    today -> "今天"
                    tomorrow -> "明天"
                    dayAfterTomorrow -> "後天"
                    else -> lines[0]
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                lines.getOrElse(1) { "" },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}

@Preview
@Composable
private fun EpgDayItemPreview() {
    MyTVTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EpgDayItem(
                dayProvider = { "週一 07-09" },
            )

            EpgDayItem(
                dayProvider = { "週一 07-09" },
                isSelectedProvider = { true },
            )
        }
    }
}

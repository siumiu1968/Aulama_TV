package top.yogiczy.mytv.tv.ui.screens.livecaption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.caption.LiveCaptionMode

@Composable
fun LiveCaptionOverlay(
    modifier: Modifier = Modifier,
    modeProvider: () -> LiveCaptionMode = { LiveCaptionMode.OFF },
    englishProvider: () -> String = { "" },
    traditionalChineseProvider: () -> String = { "" },
    stateMessageProvider: () -> String = { "" },
) {
    val mode = modeProvider()
    if (mode == LiveCaptionMode.OFF) return
    val english = englishProvider()
    val traditionalChinese = traditionalChineseProvider()
    val showEnglish = mode == LiveCaptionMode.ENGLISH || mode == LiveCaptionMode.BILINGUAL
    val showChinese = mode == LiveCaptionMode.TRADITIONAL_CHINESE || mode == LiveCaptionMode.BILINGUAL
    val hasCaption = (showEnglish && english.isNotBlank()) ||
        (showChinese && traditionalChinese.isNotBlank())

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 56.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (hasCaption) {
            Column(
                modifier = Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxWidth(0.88f)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showChinese && traditionalChinese.isNotBlank()) {
                    Text(
                        text = traditionalChinese,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                if (showEnglish && english.isNotBlank()) {
                    Text(
                        text = english,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            stateMessageProvider().takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

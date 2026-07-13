package top.yogiczy.mytv.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val AulamaTvBlue = Color(0xFF267CFF)
val AulamaTvCyan = Color(0xFF55E4F5)
val AulamaTvOrange = Color(0xFFFFAD4A)
val AulamaTvPink = Color(0xFFFF4C9A)

/**
 * 播放畫面上使用嘅低成本毛玻璃近似層。
 * 保留足夠透明度睇到節目，但避免實時 blur 令低階 Android TV 卡頓。
 */
@Composable
fun Modifier.aulamaFrostedPanel(
    shape: Shape = RoundedCornerShape(18.dp),
    opacity: Float = 0.84f,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF071726).copy(alpha = opacity),
                Color(0xFF0B1B2A).copy(alpha = (opacity - 0.04f).coerceAtLeast(0f)),
                Color(0xFF151426).copy(alpha = (opacity - 0.08f).coerceAtLeast(0f)),
            ),
        ),
        shape = shape,
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                AulamaTvCyan.copy(alpha = 0.42f),
                AulamaTvPink.copy(alpha = 0.22f),
            )
        ),
        shape = shape,
    )

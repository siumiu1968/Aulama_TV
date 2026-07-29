package org.aulama.iptv.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DDBF7),
    onPrimary = Color(0xFF003641),
    primaryContainer = Color(0xFF164B5A),
    onPrimaryContainer = Color(0xFFC8F1FF),
    secondary = Color(0xFF95B9FF),
    onSecondary = Color(0xFF102F61),
    tertiary = Color(0xFFFF79BA),
    background = Color(0xFF080D18),
    onBackground = Color(0xFFEAF1F8),
    surface = Color(0xFF101927),
    onSurface = Color(0xFFEAF1F8),
    surfaceVariant = Color(0xFF1B2738),
    onSurfaceVariant = Color(0xFFBBC8D8),
    outline = Color(0xFF617084),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF087D99),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F0FA),
    onPrimaryContainer = Color(0xFF073842),
    secondary = Color(0xFF4169A8),
    onSecondary = Color.White,
    tertiary = Color(0xFFC43F82),
    background = Color(0xFFF5F9FC),
    onBackground = Color(0xFF121C26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121C26),
    surfaceVariant = Color(0xFFE8F0F5),
    onSurfaceVariant = Color(0xFF4A5965),
    outline = Color(0xFF75838E),
    error = Color(0xFFBA1A1A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AulamaTvTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AulamaTypography,
        content = content,
    )
}

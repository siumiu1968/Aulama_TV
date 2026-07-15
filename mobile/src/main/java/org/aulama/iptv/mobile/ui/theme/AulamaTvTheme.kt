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
    primary = Color(0xFF59D8FF),
    onPrimary = Color(0xFF002B38),
    primaryContainer = Color(0xFF004F63),
    onPrimaryContainer = Color(0xFFB5EBFF),
    secondary = Color(0xFFFFB454),
    onSecondary = Color(0xFF462A00),
    tertiary = Color(0xFFFF70C9),
    background = Color(0xFF070D16),
    onBackground = Color(0xFFE4EDF7),
    surface = Color(0xFF0C1623),
    onSurface = Color(0xFFE4EDF7),
    surfaceVariant = Color(0xFF172636),
    onSurfaceVariant = Color(0xFFB8C8D8),
    outline = Color(0xFF516477),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00687F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EAFA),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF8A5100),
    onSecondary = Color.White,
    tertiary = Color(0xFF9B386F),
    background = Color(0xFFF4F8FC),
    onBackground = Color(0xFF14202A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14202A),
    surfaceVariant = Color(0xFFE4EDF4),
    onSurfaceVariant = Color(0xFF43515D),
    outline = Color(0xFF73828E),
    error = Color(0xFFBA1A1A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AulamaTvTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}

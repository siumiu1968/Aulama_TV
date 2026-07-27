package top.yogiczy.mytv.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import top.yogiczy.mytv.tv.R

private val AulamaDisplayFont = FontFamily(
    Font(
        resId = R.font.resource_han_rounded_hk_heavy,
        weight = FontWeight.Black,
    ),
)

private val AulamaTextFont = AulamaDisplayFont

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AulamaDisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AulamaTextFont,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

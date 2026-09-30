package com.zapshare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zapshare.app.data.Prefs

// ---- Brand palette (from the brief) ----
val Cyan1 = Color(0xFF28D4FF)
val Cyan2 = Color(0xFF16C9F6)
val Cyan3 = Color(0xFF00C6F8)
/** Dark ink used for text on cyan fills (white on #00C6F8 is too low-contrast to read). */
val Navy = Color(0xFF002B3A)
/** Deeper cyan for text / icons on white so they stay readable. */
val CyanInk = Color(0xFF007FA6)

val BrandGradient = Brush.linearGradient(listOf(Cyan1, Cyan2, Cyan3))
val BrandGradientV = Brush.verticalGradient(listOf(Cyan1, Cyan3))
/** Slightly deeper blue for icon tiles so white icons stay visible. */
val BrandTileGradient = Brush.linearGradient(listOf(Cyan1, Cyan3, Color(0xFF0094D4)))

// Category accent colors (file-type tiles)
val CatMusic = Color(0xFFFF5A5F)
val CatApps = Color(0xFF3D8BFF)
val CatVideo = Color(0xFF8B5CF6)
val CatPhoto = Color(0xFF22C55E)
val CatDocs = Color(0xFFF59E0B)
val CatPdf = Color(0xFFEF4444)
val CatSafe = Color(0xFF10B981)
val CatDownload = Color(0xFF6366F1)
val CatPink = Color(0xFFEC4899)
val CatCyan = Color(0xFF06B6D4)

private val Light = lightColorScheme(
    primary = CyanInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5FF),
    onPrimaryContainer = Color(0xFF00384D),
    secondary = Cyan2,
    onSecondary = Navy,
    secondaryContainer = Color(0xFFE3F8FF),
    onSecondaryContainer = Navy,
    tertiary = Cyan3,
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0B2530),
    surface = Color.White,
    onSurface = Color(0xFF0B2530),
    surfaceVariant = Color(0xFFEDF2F6),
    onSurfaceVariant = Color(0xFF5B7380),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF0F4F7),
    outline = Color(0xFFCAD6DE),
    outlineVariant = Color(0xFFE3EAF0),
)

private val Dark = darkColorScheme(
    primary = Cyan1,
    onPrimary = Navy,
    primaryContainer = Color(0xFF004E66),
    onPrimaryContainer = Color(0xFFBDEFFF),
    secondary = Cyan2,
    onSecondary = Navy,
    secondaryContainer = Color(0xFF12394A),
    onSecondaryContainer = Color(0xFFCFF3FF),
    tertiary = Cyan3,
    background = Color(0xFF0E1419),
    onBackground = Color(0xFFE8F4F8),
    surface = Color(0xFF161D23),
    onSurface = Color(0xFFE8F4F8),
    surfaceVariant = Color(0xFF1F2930),
    onSurfaceVariant = Color(0xFF9AB2BD),
    surfaceContainer = Color(0xFF161D23),
    surfaceContainerLow = Color(0xFF161D23),
    surfaceContainerHigh = Color(0xFF1F2930),
    outline = Color(0xFF3A4852),
    outlineVariant = Color(0xFF243039),
)

private val ZapTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    )
}

@Composable
fun isAppDark(): Boolean {
    val mode by Prefs.theme.collectAsState()
    return when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
}

@Composable
fun ZapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isAppDark()) Dark else Light,
        typography = ZapTypography,
        content = content
    )
}

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp)

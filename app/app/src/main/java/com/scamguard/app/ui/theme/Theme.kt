package com.scamguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Navy700,
    onSecondary = Color.White,
    secondaryContainer = Navy100,
    onSecondaryContainer = Navy900,
    background = Slate50,
    onBackground = Navy900,
    surface = Color.White,
    onSurface = Navy900,
    surfaceVariant = Navy100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedLight,
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Teal300,
    onPrimary = Teal900,
    primaryContainer = Teal700,
    onPrimaryContainer = Teal100,
    secondary = Navy100,
    onSecondary = Navy900,
    secondaryContainer = Navy700,
    onSecondaryContainer = Navy100,
    background = Navy900,
    onBackground = Color(0xFFE6EDF5),
    surface = Navy800,
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Navy700,
    onSurfaceVariant = Slate300,
    outline = Slate600,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = AlertRedLight,
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    )
}

/** Calm navy and teal theme; red appears only on alerts. */
@Composable
fun ScamGuardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

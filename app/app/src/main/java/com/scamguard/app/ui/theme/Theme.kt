package com.scamguard.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScamShieldColors = darkColorScheme(
    primary = AppRose,
    onPrimary = AppInk,
    primaryContainer = AppBurgundy,
    onPrimaryContainer = AppText,
    secondary = AppTextMuted,
    onSecondary = AppInk,
    secondaryContainer = AppSurfaceHighest,
    onSecondaryContainer = AppText,
    tertiary = SafeGreen,
    onTertiary = AppInk,
    background = AppInk,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceHighest,
    onSurfaceVariant = AppTextMuted,
    outline = AppOutline,
    outlineVariant = Color(0xFF30272E),
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRedLight,
)

private val ScamShieldTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val ScamShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(19.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** ScamShield keeps one deliberate dark palette in both system appearance modes. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ScamGuardTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScamShieldColors,
        typography = ScamShieldTypography,
        shapes = ScamShieldShapes,
        content = content,
    )
}

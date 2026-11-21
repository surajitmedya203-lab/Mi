package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KothaDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF00242B),
    primaryContainer = CyanSoftContainer,
    onPrimaryContainer = CyanPulse,
    secondary = CoralBargeIn,
    onSecondary = Color(0xFF2D0A00),
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralBargeIn,
    tertiary = LavenderMemory,
    onTertiary = Color(0xFF170B46),
    tertiaryContainer = LavenderContainer,
    onTertiaryContainer = LavenderMemory,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = MidnightOutline
)

private val KothaLightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = CyanLightContainer,
    onPrimaryContainer = CyanDeep,
    secondary = CoralDeep,
    onSecondary = Color.White,
    secondaryContainer = CoralLightContainer,
    onSecondaryContainer = CoralDeep,
    tertiary = LavenderDeep,
    onTertiary = Color.White,
    tertiaryContainer = LavenderLightContainer,
    onTertiaryContainer = LavenderDeep,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = DaylightOutline
)

@Composable
fun KothaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accessibilityLarge: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KothaDarkColorScheme else KothaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildKothaTypography(accessibilityLarge),
        content = content
    )
}

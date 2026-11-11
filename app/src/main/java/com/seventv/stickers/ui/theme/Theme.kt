package com.seventv.stickers.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val SevenTvDarkColorScheme = darkColorScheme(
    primary = SevenTvCyan,
    onPrimary = Color(0xFF0F081D),
    primaryContainer = Color(0x3501CDFE),
    onPrimaryContainer = SevenTvCyan,
    secondary = NeonFuchsia,
    onSecondary = Color.White,
    secondaryContainer = Color(0x35FF71CE),
    onSecondaryContainer = NeonFuchsia,
    tertiary = WhatsAppGreen,
    onTertiary = Color(0xFF0F081D),
    tertiaryContainer = Color(0x3505FFA1),
    onTertiaryContainer = WhatsAppGreen,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = GlassBorder,
    outlineVariant = GlassBorderCyan,
    error = ErrorRed,
    onError = Color.White
)

val SevenTvLightColorScheme = lightColorScheme(
    primary = SevenTvCyanLight,
    onPrimary = Color.White,
    secondary = TwitchPurpleLight,
    onSecondary = Color.White,
    tertiary = WhatsAppGreenLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    error = ErrorRedLight,
    onError = Color.White
)

@Composable
fun SevenTvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SevenTvDarkColorScheme else SevenTvLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SevenTvShapes,
        content = content
    )
}

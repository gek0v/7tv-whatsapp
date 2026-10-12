package com.seventv.stickers.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val SevenTvDarkColorScheme = darkColorScheme(
    primary = SevenTvCyan,
    onPrimary = Color.Black,
    secondary = TwitchPurple,
    onSecondary = Color.White,
    tertiary = WhatsAppGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = OnSurfaceText,
    surface = DarkSurface,
    onSurface = OnSurfaceText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnSurfaceSubtext,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SevenTvTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = SevenTvDarkColorScheme
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
        content = content
    )
}

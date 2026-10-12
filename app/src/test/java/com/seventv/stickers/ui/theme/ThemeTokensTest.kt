package com.seventv.stickers.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seventv.stickers.ui.components.ChatSimulatorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun verifyColorConstants() {
        // Dark brand tokens
        assertEquals(Color(0xFF00D2FF), SevenTvCyan)
        assertEquals(Color(0xFFB37BF2), TwitchPurple)
        assertEquals(Color(0xFF25D366), WhatsAppGreen)
        assertEquals(Color(0xFFFF4858), ErrorRed)

        // Light brand tokens
        assertEquals(Color(0xFF00728F), SevenTvCyanLight)
        assertEquals(Color(0xFF6D28D9), TwitchPurpleLight)
        assertEquals(Color(0xFF008744), WhatsAppGreenLight)
        assertEquals(Color(0xFFD32F2F), ErrorRedLight)

        // Dark surface container levels
        assertEquals(Color(0xFF0E1015), DarkBackground)
        assertEquals(Color(0xFF141720), DarkSurface)
        assertEquals(Color(0xFF1A1D28), DarkSurfaceContainerLow)
        assertEquals(Color(0xFF202432), DarkSurfaceContainer)
        assertEquals(Color(0xFF272C3E), DarkSurfaceContainerHigh)
        assertEquals(Color(0xFF31374C), DarkSurfaceContainerHighest)
        assertEquals(Color(0xFF272C3E), DarkSurfaceVariant)
        assertEquals(Color(0xFFF1F3F5), DarkOnSurface)
        assertEquals(Color(0xFF9AA0A6), DarkOnSurfaceVariant)

        // Light surface container levels
        assertEquals(Color(0xFFF7F9FC), LightBackground)
        assertEquals(Color(0xFFFFFFFF), LightSurface)
        assertEquals(Color(0xFFF0F3F8), LightSurfaceContainerLow)
        assertEquals(Color(0xFFE7ECF3), LightSurfaceContainer)
        assertEquals(Color(0xFFDEE4EE), LightSurfaceContainerHigh)
        assertEquals(Color(0xFFD4DCE8), LightSurfaceContainerHighest)
        assertEquals(Color(0xFFDEE4EE), LightSurfaceVariant)
        assertEquals(Color(0xFF1A1D23), LightOnSurface)
        assertEquals(Color(0xFF5B6275), LightOnSurfaceVariant)

        // Backward compatibility tokens
        assertEquals(DarkOnSurface, OnSurfaceText)
        assertEquals(DarkOnSurfaceVariant, OnSurfaceSubtext)

        // Chat Simulator preview tokens
        assertEquals(Color(0xFF0B141A), WhatsAppDarkChatBackground)
        assertEquals(Color(0xFF005C4B), WhatsAppDarkBubble)
        assertEquals(Color(0xFFEFEAE2), WhatsAppLightChatBackground)
        assertEquals(Color(0xFFD9FDD3), WhatsAppLightBubble)
        assertEquals(Color(0xFF1E2028), CheckerboardDark)
        assertEquals(Color(0xFF2A2E39), CheckerboardLight)
    }

    @Test
    fun verifyChatSimulatorModes() {
        val modes = ChatSimulatorMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(ChatSimulatorMode.WHATSAPP_DARK))
        assertTrue(modes.contains(ChatSimulatorMode.WHATSAPP_LIGHT))
        assertTrue(modes.contains(ChatSimulatorMode.CHECKERBOARD))
    }

    @Test
    fun testDualColorSchemeRoles() {
        assertNotNull(SevenTvDarkColorScheme.surfaceContainer)
        assertNotNull(SevenTvLightColorScheme.surfaceContainer)
        assertEquals(Color(0xFF00D2FF), SevenTvDarkColorScheme.primary)
        assertEquals(Color(0xFF00728F), SevenTvLightColorScheme.primary)
        assertEquals(Color(0xFF25D366), SevenTvDarkColorScheme.tertiary)
    }

    @Test
    fun verifyDarkColorSchemeTokens() {
        assertEquals(SevenTvCyan, SevenTvDarkColorScheme.primary)
        assertEquals(Color.Black, SevenTvDarkColorScheme.onPrimary)
        assertEquals(TwitchPurple, SevenTvDarkColorScheme.secondary)
        assertEquals(Color.White, SevenTvDarkColorScheme.onSecondary)
        assertEquals(WhatsAppGreen, SevenTvDarkColorScheme.tertiary)
        assertEquals(Color.Black, SevenTvDarkColorScheme.onTertiary)
        assertEquals(DarkBackground, SevenTvDarkColorScheme.background)
        assertEquals(DarkOnSurface, SevenTvDarkColorScheme.onBackground)
        assertEquals(DarkSurface, SevenTvDarkColorScheme.surface)
        assertEquals(DarkOnSurface, SevenTvDarkColorScheme.onSurface)
        assertEquals(DarkSurfaceVariant, SevenTvDarkColorScheme.surfaceVariant)
        assertEquals(DarkOnSurfaceVariant, SevenTvDarkColorScheme.onSurfaceVariant)
        assertEquals(DarkSurfaceContainerLow, SevenTvDarkColorScheme.surfaceContainerLow)
        assertEquals(DarkSurfaceContainer, SevenTvDarkColorScheme.surfaceContainer)
        assertEquals(DarkSurfaceContainerHigh, SevenTvDarkColorScheme.surfaceContainerHigh)
        assertEquals(DarkSurfaceContainerHighest, SevenTvDarkColorScheme.surfaceContainerHighest)
        assertEquals(ErrorRed, SevenTvDarkColorScheme.error)
        assertEquals(Color.White, SevenTvDarkColorScheme.onError)
    }

    @Test
    fun verifyLightColorSchemeTokens() {
        assertEquals(SevenTvCyanLight, SevenTvLightColorScheme.primary)
        assertEquals(Color.White, SevenTvLightColorScheme.onPrimary)
        assertEquals(TwitchPurpleLight, SevenTvLightColorScheme.secondary)
        assertEquals(Color.White, SevenTvLightColorScheme.onSecondary)
        assertEquals(WhatsAppGreenLight, SevenTvLightColorScheme.tertiary)
        assertEquals(Color.White, SevenTvLightColorScheme.onTertiary)
        assertEquals(LightBackground, SevenTvLightColorScheme.background)
        assertEquals(LightOnSurface, SevenTvLightColorScheme.onBackground)
        assertEquals(LightSurface, SevenTvLightColorScheme.surface)
        assertEquals(LightOnSurface, SevenTvLightColorScheme.onSurface)
        assertEquals(LightSurfaceVariant, SevenTvLightColorScheme.surfaceVariant)
        assertEquals(LightOnSurfaceVariant, SevenTvLightColorScheme.onSurfaceVariant)
        assertEquals(LightSurfaceContainerLow, SevenTvLightColorScheme.surfaceContainerLow)
        assertEquals(LightSurfaceContainer, SevenTvLightColorScheme.surfaceContainer)
        assertEquals(LightSurfaceContainerHigh, SevenTvLightColorScheme.surfaceContainerHigh)
        assertEquals(LightSurfaceContainerHighest, SevenTvLightColorScheme.surfaceContainerHighest)
        assertEquals(ErrorRedLight, SevenTvLightColorScheme.error)
        assertEquals(Color.White, SevenTvLightColorScheme.onError)
    }

    @Test
    fun verifyShapeTokens() {
        assertEquals(RoundedCornerShape(8.dp), SevenTvShapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), SevenTvShapes.small)
        assertEquals(RoundedCornerShape(16.dp), SevenTvShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), SevenTvShapes.large)
        assertEquals(RoundedCornerShape(28.dp), SevenTvShapes.extraLarge)
    }

    @Test
    fun verifyTypographyTokens() {
        assertEquals(28.sp, Typography.headlineMedium.fontSize)
        assertEquals(FontWeight.SemiBold, Typography.headlineMedium.fontWeight)
        assertEquals(22.sp, Typography.titleLarge.fontSize)
        assertEquals(FontWeight.SemiBold, Typography.titleLarge.fontWeight)
        assertEquals(16.sp, Typography.titleMedium.fontSize)
        assertEquals(FontWeight.Medium, Typography.titleMedium.fontWeight)
        assertEquals(14.sp, Typography.titleSmall.fontSize)
        assertEquals(FontWeight.Medium, Typography.titleSmall.fontWeight)
        assertEquals(16.sp, Typography.bodyLarge.fontSize)
        assertEquals(FontWeight.Normal, Typography.bodyLarge.fontWeight)
        assertEquals(14.sp, Typography.bodyMedium.fontSize)
        assertEquals(FontWeight.Normal, Typography.bodyMedium.fontWeight)
        assertEquals(14.sp, Typography.labelLarge.fontSize)
        assertEquals(FontWeight.SemiBold, Typography.labelLarge.fontWeight)
        assertEquals(12.sp, Typography.labelMedium.fontSize)
        assertEquals(FontWeight.SemiBold, Typography.labelMedium.fontWeight)
        assertEquals(11.sp, Typography.labelSmall.fontSize)
        assertEquals(FontWeight.Medium, Typography.labelSmall.fontWeight)
        assertEquals(FontFamily.Monospace, Typography.labelSmall.fontFamily)
    }
}

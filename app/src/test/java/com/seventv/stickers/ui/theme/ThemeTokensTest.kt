package com.seventv.stickers.ui.theme

import androidx.compose.ui.graphics.Color
import com.seventv.stickers.ui.components.ChatSimulatorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun verifyColorConstants() {
        assertEquals(Color(0xFF0E0F12), DarkBackground)
        assertEquals(Color(0xFF16181D), DarkSurface)
        assertEquals(Color(0xFF22252C), DarkSurfaceVariant)
        assertEquals(Color(0xFF00D2FF), SevenTvCyan)
        assertEquals(Color(0xFF9B51E0), TwitchPurple)
        assertEquals(Color(0xFF25D366), WhatsAppGreen)
        assertEquals(Color(0xFFFF4858), ErrorRed)
        assertEquals(Color(0xFFF1F3F5), OnSurfaceText)
        assertEquals(Color(0xFF9AA0A6), OnSurfaceSubtext)
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
    fun verifyDarkColorSchemeTokens() {
        assertEquals(SevenTvCyan, SevenTvDarkColorScheme.primary)
        assertEquals(TwitchPurple, SevenTvDarkColorScheme.secondary)
        assertEquals(WhatsAppGreen, SevenTvDarkColorScheme.tertiary)
        assertEquals(DarkBackground, SevenTvDarkColorScheme.background)
        assertEquals(DarkSurface, SevenTvDarkColorScheme.surface)
        assertEquals(DarkSurfaceVariant, SevenTvDarkColorScheme.surfaceVariant)
        assertEquals(ErrorRed, SevenTvDarkColorScheme.error)
        assertEquals(OnSurfaceText, SevenTvDarkColorScheme.onSurface)
        assertEquals(OnSurfaceSubtext, SevenTvDarkColorScheme.onSurfaceVariant)
    }
}

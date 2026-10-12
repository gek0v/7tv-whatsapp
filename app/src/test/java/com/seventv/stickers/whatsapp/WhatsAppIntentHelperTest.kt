package com.seventv.stickers.whatsapp

import android.content.Context
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WhatsAppIntentHelperTest {

    private lateinit var context: Context
    private lateinit var shadowPackageManager: ShadowPackageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowPackageManager = Shadows.shadowOf(context.packageManager)
    }

    @Test
    fun isWhatsAppInstalled_returnsTrueWhenInstalled() {
        val packageInfo = PackageInfo().apply {
            packageName = WhatsAppConstants.PACKAGE_WHATSAPP
        }
        shadowPackageManager.installPackage(packageInfo)

        val result = WhatsAppIntentHelper.isWhatsAppInstalled(context)

        assertTrue(result)
    }

    @Test
    fun isWhatsAppInstalled_returnsTrueWhenSmbInstalled() {
        val packageInfo = PackageInfo().apply {
            packageName = WhatsAppConstants.PACKAGE_WHATSAPP_SMB
        }
        shadowPackageManager.installPackage(packageInfo)

        val result = WhatsAppIntentHelper.isWhatsAppInstalled(context)

        assertTrue(result)
        assertEquals(WhatsAppConstants.PACKAGE_WHATSAPP_SMB, WhatsAppIntentHelper.getInstalledWhatsAppPackage(context))
    }

    @Test
    fun isWhatsAppInstalled_returnsFalseWhenNotInstalled() {
        val result = WhatsAppIntentHelper.isWhatsAppInstalled(context)

        assertFalse(result)
    }

    @Test
    fun createAddPackIntent_setsActionPackageAndExtras() {
        val customAuthority = "com.custom.authority"
        val packId = "pack_123"
        val packName = "Cool Pack"

        val intent = WhatsAppIntentHelper.createAddPackIntent(
            authority = customAuthority,
            packIdentifier = packId,
            packName = packName
        )

        assertEquals(WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK, intent.action)
        assertEquals(WhatsAppConstants.PACKAGE_WHATSAPP, intent.`package`)
        assertEquals(customAuthority, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY))
        assertEquals(packId, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_ID))
        assertEquals(packName, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_NAME))
    }

    @Test
    fun createAddPackIntent_usesDefaultAuthority() {
        val packId = "pack_456"
        val packName = "Default Auth Pack"

        val intent = WhatsAppIntentHelper.createAddPackIntent(
            packIdentifier = packId,
            packName = packName
        )

        assertEquals(WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK, intent.action)
        assertEquals(WhatsAppConstants.PACKAGE_WHATSAPP, intent.`package`)
        assertEquals(StickerContentProvider.AUTHORITY, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY))
        assertEquals(packId, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_ID))
        assertEquals(packName, intent.getStringExtra(WhatsAppConstants.EXTRA_STICKER_PACK_NAME))
    }
}

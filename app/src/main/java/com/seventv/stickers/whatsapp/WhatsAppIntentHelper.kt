package com.seventv.stickers.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object WhatsAppIntentHelper {

    fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WhatsAppConstants.PACKAGE_WHATSAPP, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun createAddPackIntent(
        authority: String = StickerContentProvider.AUTHORITY,
        packIdentifier: String,
        packName: String
    ): Intent {
        return Intent(WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK).apply {
            `package` = WhatsAppConstants.PACKAGE_WHATSAPP
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY, authority)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_ID, packIdentifier)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_NAME, packName)
        }
    }
}

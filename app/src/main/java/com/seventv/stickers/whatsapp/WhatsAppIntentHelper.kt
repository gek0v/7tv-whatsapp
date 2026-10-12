package com.seventv.stickers.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object WhatsAppIntentHelper {

    fun isWhatsAppInstalled(context: Context): Boolean {
        return getInstalledWhatsAppPackage(context) != null
    }

    fun getInstalledWhatsAppPackage(context: Context): String? {
        val packages = listOf(WhatsAppConstants.PACKAGE_WHATSAPP, WhatsAppConstants.PACKAGE_WHATSAPP_SMB)
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return null
    }

    fun createAddPackIntent(
        authority: String = StickerContentProvider.AUTHORITY,
        packIdentifier: String,
        packName: String,
        context: Context? = null
    ): Intent {
        val targetPackage = (context?.let { getInstalledWhatsAppPackage(it) }) ?: WhatsAppConstants.PACKAGE_WHATSAPP
        return Intent(WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK).apply {
            `package` = targetPackage
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY, authority)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_ID, packIdentifier)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_NAME, packName)
        }
    }
}

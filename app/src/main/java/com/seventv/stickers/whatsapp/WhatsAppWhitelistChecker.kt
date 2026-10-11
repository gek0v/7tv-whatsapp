package com.seventv.stickers.whatsapp

import android.content.Context
import android.net.Uri

object WhatsAppWhitelistChecker {

    fun isPackWhitelisted(
        context: Context,
        packIdentifier: String,
        authority: String = StickerContentProvider.AUTHORITY
    ): Boolean {
        return try {
            val queryUri = Uri.Builder()
                .scheme("content")
                .authority(WhatsAppConstants.WHITELIST_AUTHORITY)
                .appendPath(WhatsAppConstants.WHITELIST_QUERY_PATH)
                .appendQueryParameter(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY, authority)
                .appendQueryParameter(WhatsAppConstants.EXTRA_STICKER_PACK_ID, packIdentifier)
                .build()

            val cursor = context.contentResolver.query(queryUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)
                    if (columnIndex != -1) {
                        return@use it.getInt(columnIndex) == 1
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}

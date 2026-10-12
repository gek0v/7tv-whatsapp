package com.seventv.stickers.whatsapp

import android.content.Context
import android.net.Uri

object WhatsAppWhitelistChecker {

    fun isPackWhitelisted(
        context: Context,
        packIdentifier: String,
        authority: String = StickerContentProvider.AUTHORITY
    ): Boolean {
        return checkWhitelistForAuthority(context, WhatsAppConstants.WHITELIST_AUTHORITY, packIdentifier, authority) ||
               checkWhitelistForAuthority(context, WhatsAppConstants.WHITELIST_AUTHORITY_SMB, packIdentifier, authority)
    }

    private fun checkWhitelistForAuthority(
        context: Context,
        whitelistAuthority: String,
        packIdentifier: String,
        authority: String
    ): Boolean {
        return try {
            val queryUri = Uri.Builder()
                .scheme("content")
                .authority(whitelistAuthority)
                .appendPath(WhatsAppConstants.WHITELIST_QUERY_PATH)
                .appendQueryParameter(WhatsAppConstants.WHITELIST_QUERY_PARAM_AUTHORITY, authority)
                .appendQueryParameter(WhatsAppConstants.WHITELIST_QUERY_PARAM_IDENTIFIER, packIdentifier)
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
        } catch (_: Exception) {
            false
        }
    }
}

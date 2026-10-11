package com.seventv.stickers.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppConstantsTest {

    @Test
    fun packageAndIntentConstants() {
        assertEquals("com.whatsapp", WhatsAppConstants.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.intent.action.ENABLE_STICKER_PACK", WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK)
        assertEquals("sticker_pack_id", WhatsAppConstants.EXTRA_STICKER_PACK_ID)
        assertEquals("sticker_pack_authority", WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY)
        assertEquals("sticker_pack_name", WhatsAppConstants.EXTRA_STICKER_PACK_NAME)
    }

    @Test
    fun packMetadataQueryColumns() {
        assertEquals("sticker_pack_identifier", WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)
        assertEquals("sticker_pack_name", WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY)
        assertEquals("sticker_pack_publisher", WhatsAppConstants.STICKER_PACK_PUBLISHER_IN_QUERY)
        assertEquals("sticker_pack_icon", WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY)
        assertEquals("android_play_store_link", WhatsAppConstants.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY)
        assertEquals("ios_app_store_link", WhatsAppConstants.IOS_APP_DOWNLOAD_LINK_IN_QUERY)
        assertEquals("publisher_email", WhatsAppConstants.PUBLISHER_EMAIL)
        assertEquals("publisher_website", WhatsAppConstants.PUBLISHER_WEBSITE)
        assertEquals("privacy_policy_website", WhatsAppConstants.PRIVACY_POLICY_WEBSITE)
        assertEquals("license_agreement_website", WhatsAppConstants.LICENSE_AGREEMENT_WEBSITE)
        assertEquals("image_data_version", WhatsAppConstants.IMAGE_DATA_VERSION)
        assertEquals("avoid_cache", WhatsAppConstants.AVOID_CACHE)
        assertEquals("animated_sticker_pack", WhatsAppConstants.ANIMATED_STICKER_PACK)
    }

    @Test
    fun individualStickerQueryColumns() {
        assertEquals("sticker_file_name", WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY)
        assertEquals("sticker_emojis", WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY)
    }

    @Test
    fun whitelistProviderConstants() {
        assertEquals("com.whatsapp.provider.sticker_whitelist_check", WhatsAppConstants.WHITELIST_AUTHORITY)
        assertEquals("is_whitelisted", WhatsAppConstants.WHITELIST_QUERY_PATH)
        assertEquals("result", WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)
    }
}

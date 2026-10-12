package com.seventv.stickers.whatsapp

object WhatsAppConstants {
    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_SMB = "com.whatsapp.w4b"
    const val INTENT_ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    // Pack Metadata query columns
    const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
    const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
    const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
    const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
    const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
    const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
    const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
    const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
    const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
    const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
    const val IMAGE_DATA_VERSION = "image_data_version"
    const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
    const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

    // Individual Sticker query columns
    const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
    const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
    const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

    // WhatsApp Whitelist Provider constants
    const val WHITELIST_AUTHORITY = "com.whatsapp.provider.sticker_whitelist_check"
    const val WHITELIST_AUTHORITY_SMB = "com.whatsapp.w4b.provider.sticker_whitelist_check"
    const val WHITELIST_QUERY_PATH = "is_whitelisted"
    const val WHITELIST_QUERY_PARAM_AUTHORITY = "authority"
    const val WHITELIST_QUERY_PARAM_IDENTIFIER = "identifier"
    const val WHITELIST_QUERY_RESULT_COLUMN = "result"
}

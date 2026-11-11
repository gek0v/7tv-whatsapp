package com.seventv.stickers.core.network.model

enum class EmoteProvider(val displayName: String, val badgeColorHex: String) {
    SEVENTV("7TV", "#42A5F5"),
    BETTERTTV("BTTV", "#D32F2F"),
    FRANKERFACEZ("FFZ", "#8E24AA"),
    TWITCH("Twitch", "#9146FF");

    companion object {
        fun fromName(raw: String?): EmoteProvider {
            if (raw.isNullOrBlank()) return SEVENTV
            val normalized = raw.trim()
            return entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(normalized, ignoreCase = true)
            } ?: SEVENTV
        }
    }
}

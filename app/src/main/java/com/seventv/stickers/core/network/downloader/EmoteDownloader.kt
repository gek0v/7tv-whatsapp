package com.seventv.stickers.core.network.downloader

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.UnifiedEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.core.network.model.toUnifiedEmote

/**
 * Interface for downloading and caching emote binary assets from any provider.
 */
interface EmoteDownloader {
    /**
     * Downloads a unified emote binary asset, returning a cached file if available,
     * or streaming from the remote server, persisting to disk cache atomically.
     */
    suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> =
        downloadEmote(emote.toSevenTvEmote())

    /**
     * Overload for downloading a [SevenTvEmote].
     */
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> =
        downloadEmote(emote.toUnifiedEmote())

    /**
     * Downloads arbitrary raw binary asset bytes from a direct URL.
     */
    suspend fun downloadFromUrl(url: String): StickerResult<ByteArray>
}

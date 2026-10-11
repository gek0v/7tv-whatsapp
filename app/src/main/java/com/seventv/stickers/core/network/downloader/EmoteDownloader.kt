package com.seventv.stickers.core.network.downloader

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote

/**
 * Interface for downloading and caching emote binary assets.
 */
interface EmoteDownloader {
    /**
     * Downloads an emote binary asset, returning a cached file if available,
     * or streaming from the remote server, persisting to disk cache atomically.
     */
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>

    /**
     * Downloads arbitrary raw binary asset bytes from a direct URL.
     */
    suspend fun downloadFromUrl(url: String): StickerResult<ByteArray>
}

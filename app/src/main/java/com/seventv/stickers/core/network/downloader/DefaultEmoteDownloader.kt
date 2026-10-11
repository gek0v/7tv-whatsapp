package com.seventv.stickers.core.network.downloader

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Production implementation of [EmoteDownloader] with atomic disk caching,
 * streaming safety limits (15 MB ceiling), and comprehensive error mapping.
 */
class DefaultEmoteDownloader(
    private val cacheDir: File,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EmoteDownloader {

    private val emoteCacheDir: File = if (cacheDir.name == CACHE_SUBDIR) cacheDir else File(cacheDir, CACHE_SUBDIR)

    init {
        if (!emoteCacheDir.exists()) {
            emoteCacheDir.mkdirs()
        }
    }

    override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> = withContext(dispatcher) {
        if (emote.bestAssetUrl.isBlank()) {
            return@withContext StickerResult.Failure(
                StickerError.NetworkError(message = "Emote asset URL is empty")
            )
        }

        val fileName = getCacheFileName(emote)
        val cachedFile = File(emoteCacheDir, fileName)

        // Cache hit check
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext try {
                val bytes = cachedFile.readBytes()
                val format = resolveFormat(fileName, null)
                StickerResult.Success(
                    DownloadedEmoteAsset(
                        emoteId = emote.id,
                        emoteName = emote.name,
                        bytes = bytes,
                        isAnimated = emote.isAnimated,
                        format = format
                    )
                )
            } catch (e: Exception) {
                StickerResult.Failure(StickerError.StorageIoError(e))
            }
        }

        // Cache miss: Stream from network to temporary file
        val uniqueSuffix = UUID.randomUUID().toString()
        val tempFile = File(emoteCacheDir, "$fileName.$uniqueSuffix.tmp")
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext StickerResult.Failure(StickerError.StorageIoError(e))
        }

        var responseContentType: String? = null
        val downloadResult = downloadToFile(emote.bestAssetUrl, tempFile) { response ->
            responseContentType = response.header("Content-Type")
        }

        if (downloadResult is StickerResult.Failure) {
            tempFile.delete()
            return@withContext downloadResult
        }

        // Atomic rename / move to cache target file
        try {
            try {
                Files.move(
                    tempFile.toPath(),
                    cachedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    cachedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            val bytes = cachedFile.readBytes()
            val format = resolveFormat(emote.bestAssetUrl, responseContentType)
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = bytes,
                    isAnimated = emote.isAnimated,
                    format = format
                )
            )
        } catch (e: Exception) {
            tempFile.delete()
            if (e is CancellationException) throw e
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun downloadFromUrl(url: String): StickerResult<ByteArray> = withContext(dispatcher) {
        if (url.isBlank()) {
            return@withContext StickerResult.Failure(
                StickerError.NetworkError(message = "URL is empty")
            )
        }

        val outputStream = ByteArrayOutputStream()
        val streamResult = streamPayload(url, outputStream)
        if (streamResult is StickerResult.Failure) {
            return@withContext streamResult
        }

        StickerResult.Success(outputStream.toByteArray())
    }

    private fun downloadToFile(
        url: String,
        targetFile: File,
        onResponse: (Response) -> Unit = {}
    ): StickerResult<Long> {
        val fileOutputStream = try {
            FileOutputStream(targetFile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return StickerResult.Failure(StickerError.StorageIoError(e))
        }

        var streamResult: StickerResult<Long>? = null
        return try {
            fileOutputStream.use { out ->
                streamResult = streamPayload(url, out, onResponse)
            }
            if (streamResult is StickerResult.Failure) {
                targetFile.delete()
            }
            streamResult ?: StickerResult.Failure(StickerError.NetworkError(message = "Download stream incomplete"))
        } catch (e: Exception) {
            targetFile.delete()
            if (e is CancellationException) throw e
            if (e is DiskStorageException) {
                StickerResult.Failure(StickerError.StorageIoError(e.cause ?: e))
            } else {
                StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Download failed"))
            }
        }
    }

    private fun streamPayload(
        url: String,
        outputStream: OutputStream,
        onResponse: (Response) -> Unit = {}
    ): StickerResult<Long> {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            return StickerResult.Failure(
                StickerError.NetworkError(message = e.message ?: "Invalid URL: $url")
            )
        }

        try {
            okHttpClient.newCall(request).execute().use { response ->
                onResponse(response)

                if (response.code == 404) {
                    return StickerResult.Failure(StickerError.ResourceNotFound)
                }
                if (response.code == 429) {
                    return StickerResult.Failure(StickerError.RateLimited)
                }
                if (!response.isSuccessful) {
                    return StickerResult.Failure(
                        StickerError.NetworkError(
                            statusCode = response.code,
                            message = "HTTP ${response.code}: ${response.message}"
                        )
                    )
                }

                val contentLength = response.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_PAYLOAD_BYTES) {
                    return StickerResult.Failure(
                        StickerError.ExceedsSizeLimit(
                            actualBytes = contentLength,
                            maxBytes = MAX_PAYLOAD_BYTES,
                            canFallbackToStatic = false
                        )
                    )
                }

                val body = response.body
                    ?: return StickerResult.Failure(
                        StickerError.NetworkError(
                            statusCode = response.code,
                            message = "Empty response body"
                        )
                    )

                val inputStream = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    if (totalBytesRead > MAX_PAYLOAD_BYTES) {
                        return StickerResult.Failure(
                            StickerError.ExceedsSizeLimit(
                                actualBytes = totalBytesRead,
                                maxBytes = MAX_PAYLOAD_BYTES,
                                canFallbackToStatic = false
                            )
                        )
                    }

                    try {
                        outputStream.write(buffer, 0, bytesRead)
                    } catch (e: IOException) {
                        throw DiskStorageException(e)
                    }
                }

                try {
                    outputStream.flush()
                } catch (e: IOException) {
                    throw DiskStorageException(e)
                }

                return StickerResult.Success(totalBytesRead)
            }
        } catch (e: DiskStorageException) {
            return StickerResult.Failure(StickerError.StorageIoError(e.cause ?: e))
        } catch (e: IOException) {
            return StickerResult.Failure(
                StickerError.NetworkError(message = e.message ?: "Network error")
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return StickerResult.Failure(
                StickerError.NetworkError(message = e.message ?: "Unexpected error")
            )
        }
    }

    private fun getCacheFileName(emote: SevenTvEmote): String {
        val rawName = emote.bestAssetUrl
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
        val safeName = if (rawName.isNotBlank()) rawName else "4x.webp"
        return "${emote.id}_$safeName"
    }

    private fun resolveFormat(url: String, contentType: String?): String {
        val cleanUrl = url.substringBefore('?').substringBefore('#')
        val extension = cleanUrl.substringAfterLast('.', "").uppercase()
        if (extension in KNOWN_EXTENSIONS) {
            return if (extension == "JPG") "JPEG" else extension
        }
        if (contentType != null) {
            when {
                contentType.contains("webp", ignoreCase = true) -> return "WEBP"
                contentType.contains("gif", ignoreCase = true) -> return "GIF"
                contentType.contains("png", ignoreCase = true) -> return "PNG"
                contentType.contains("avif", ignoreCase = true) -> return "AVIF"
                contentType.contains("jpeg", ignoreCase = true) || contentType.contains("jpg", ignoreCase = true) -> return "JPEG"
            }
        }
        return "WEBP"
    }

    private class DiskStorageException(cause: Throwable) : IOException(cause)

    companion object {
        private const val CACHE_SUBDIR = "7tv_emotes_cache"
        const val MAX_PAYLOAD_BYTES = 15 * 1024 * 1024L // 15 MB
        private const val BUFFER_SIZE = 8192
        private val KNOWN_EXTENSIONS = setOf("WEBP", "GIF", "PNG", "AVIF", "JPEG", "JPG")
    }
}

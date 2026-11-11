package com.seventv.stickers.core.updater

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed interface ApkDownloadOutcome {
    data class Success(
        val file: File,
        val verifiedSha256: Boolean
    ) : ApkDownloadOutcome

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ApkDownloadOutcome
}

object ApkDownloadHelper {

    private const val BUFFER_SIZE = 16 * 1024

    suspend fun downloadWithResume(
        okHttpClient: OkHttpClient,
        url: String,
        destinationFile: File,
        expectedSha256: String? = null,
        expectedSizeBytes: Long = 0L,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ApkDownloadOutcome {
        destinationFile.parentFile?.mkdirs()

        if (destinationFile.exists() && destinationFile.length() > 0L) {
            val normalizedExpected = Sha256Checksum.normalizeHex(expectedSha256)
            if (normalizedExpected != null && Sha256Checksum.verify(destinationFile, normalizedExpected)) {
                val size = destinationFile.length()
                onProgress(size, size)
                return ApkDownloadOutcome.Success(file = destinationFile, verifiedSha256 = true)
            }
            if (normalizedExpected == null && expectedSizeBytes > 0L && destinationFile.length() == expectedSizeBytes) {
                val size = destinationFile.length()
                onProgress(size, size)
                return ApkDownloadOutcome.Success(file = destinationFile, verifiedSha256 = false)
            }
            destinationFile.delete()
        }

        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        var attemptFromScratch = false

        while (true) {
            val existingBytes = if (!attemptFromScratch && partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder().url(url).get()
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            try {
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 416 && existingBytes > 0L && !attemptFromScratch) {
                        partFile.delete()
                        attemptFromScratch = true
                        return@use
                    }

                    if (!response.isSuccessful && response.code != 206) {
                        return ApkDownloadOutcome.Failure("Error al descargar APK (HTTP ${response.code})")
                    }

                    val body = response.body
                        ?: return ApkDownloadOutcome.Failure("Respuesta de descarga vacía.")

                    val isResume = response.code == 206 && existingBytes > 0L
                    val contentLength = body.contentLength()
                    val totalBytes = when {
                        isResume -> parseContentRangeTotal(response.header("Content-Range"))
                            ?: if (contentLength > 0L) existingBytes + contentLength else expectedSizeBytes
                        contentLength > 0L -> contentLength
                        else -> expectedSizeBytes
                    }

                    var downloadedBytes = if (isResume) existingBytes else 0L
                    onProgress(downloadedBytes, totalBytes)

                    val append = isResume
                    if (!append && partFile.exists()) {
                        partFile.delete()
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, append).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var lastReportedBytes = downloadedBytes
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (downloadedBytes - lastReportedBytes >= 32 * 1024 ||
                                    (totalBytes > 0L && downloadedBytes >= totalBytes)
                                ) {
                                    lastReportedBytes = downloadedBytes
                                    onProgress(downloadedBytes, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }

                    onProgress(downloadedBytes, totalBytes.coerceAtLeast(downloadedBytes))

                    val normalizedExpected = Sha256Checksum.normalizeHex(expectedSha256)
                    val verified = if (normalizedExpected != null) {
                        if (!Sha256Checksum.verify(partFile, normalizedExpected)) {
                            partFile.delete()
                            return ApkDownloadOutcome.Failure(
                                "La verificación criptográfica SHA-256 falló. Descarga descartada."
                            )
                        }
                        true
                    } else {
                        false
                    }

                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }
                    if (!partFile.renameTo(destinationFile)) {
                        partFile.copyTo(destinationFile, overwrite = true)
                        partFile.delete()
                    }

                    return ApkDownloadOutcome.Success(
                        file = destinationFile,
                        verifiedSha256 = verified
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApkDownloadOutcome.Failure(
                    message = "Error durante la descarga: ${e.message ?: "conexión interrumpida"}",
                    cause = e
                )
            }
        }
    }

    internal fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex < 0 || slashIndex == contentRange.length - 1) return null
        return contentRange.substring(slashIndex + 1).trim().toLongOrNull()
    }
}
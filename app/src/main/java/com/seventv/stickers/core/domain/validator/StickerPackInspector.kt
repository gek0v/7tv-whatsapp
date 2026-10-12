package com.seventv.stickers.core.domain.validator

import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class StickerIssue(
    val stickerId: String,
    val emoteName: String,
    val reason: String,
    val technicalDetails: String? = null
)

data class StickerPackInspectionReport(
    val packIdentifier: String,
    val issues: List<StickerIssue> = emptyList(),
    val packIssues: List<String> = emptyList()
) {
    val isValid: Boolean get() = issues.isEmpty() && packIssues.isEmpty()
}

object StickerPackInspector {
    const val MAX_STATIC_BYTES = 100 * 1024L
    const val MAX_ANIMATED_BYTES = 500 * 1024L
    const val MIN_FRAME_DURATION_MS = 8
    const val MAX_TOTAL_DURATION_MS = 10000
    const val REQUIRED_DIMENSION = 512
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val MIN_EMOJIS = 1
    const val MAX_EMOJIS = 3

    data class WebpMetadata(
        val isAnimated: Boolean,
        val frameCount: Int,
        val minFrameDurationMs: Int,
        val totalDurationMs: Int,
        val width: Int?,
        val height: Int?
    )

    fun inspect(pack: StickerPack, baseDir: File? = null): StickerPackInspectionReport {
        val issues = mutableListOf<StickerIssue>()
        val packIssues = mutableListOf<String>()

        // Pack-level checks
        if (pack.stickers.size < MIN_STICKERS) {
            packIssues.add("El paquete tiene ${pack.stickers.size} stickers. WhatsApp requiere un mínimo de 3 stickers por paquete.")
        } else if (pack.stickers.size > MAX_STICKERS) {
            packIssues.add("El paquete tiene ${pack.stickers.size} stickers. WhatsApp permite un máximo de 30 stickers por paquete.")
        }

        // Check each sticker
        for (sticker in pack.stickers) {
            val file = resolveFile(sticker.filePath, baseDir)
            if (!file.exists()) {
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "El archivo del sticker no existe en el almacenamiento local.",
                        technicalDetails = "Ruta: ${file.absolutePath}"
                    )
                )
                continue
            }

            val length = file.length()
            if (length == 0L) {
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "El archivo del sticker está vacío (0 bytes).",
                        technicalDetails = "0 bytes"
                    )
                )
                continue
            }

            // Size checks
            if (pack.isAnimated && length > MAX_ANIMATED_BYTES) {
                val sizeKb = length / 1024
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "El archivo supera el límite de WhatsApp para stickers animados (${sizeKb} KB > 500 KB).",
                        technicalDetails = "$length bytes"
                    )
                )
                continue
            } else if (!pack.isAnimated && length > MAX_STATIC_BYTES) {
                val sizeKb = length / 1024
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "El archivo supera el límite de WhatsApp para stickers estáticos (${sizeKb} KB > 100 KB).",
                        technicalDetails = "$length bytes"
                    )
                )
                continue
            }

            // Emoji count checks
            if (sticker.emojis.size < MIN_EMOJIS) {
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "No tiene ningún emoji asociado (WhatsApp requiere entre 1 y 3 emojis)."
                    )
                )
                continue
            } else if (sticker.emojis.size > MAX_EMOJIS) {
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "Tiene demasiados emojis asociados (${sticker.emojis.size} > máximo 3)."
                    )
                )
                continue
            }

            // WebP structure checks
            val metadata = parseWebp(file)
            if (metadata == null) {
                issues.add(
                    StickerIssue(
                        stickerId = sticker.id,
                        emoteName = sticker.emoteName,
                        reason = "Formato de imagen corrupto o no compatible con WebP."
                    )
                )
                continue
            }

            if (metadata.width != null && metadata.height != null) {
                if (metadata.width != REQUIRED_DIMENSION || metadata.height != REQUIRED_DIMENSION) {
                    issues.add(
                        StickerIssue(
                            stickerId = sticker.id,
                            emoteName = sticker.emoteName,
                            reason = "Dimensiones no válidas: ${metadata.width}x${metadata.height} px (WhatsApp requiere 512x512 px)."
                        )
                    )
                    continue
                }
            }

            if (pack.isAnimated) {
                if (metadata.frameCount <= 1) {
                    issues.add(
                        StickerIssue(
                            stickerId = sticker.id,
                            emoteName = sticker.emoteName,
                            reason = "El paquete es animado pero este sticker es estático (1 fotograma). WhatsApp rechaza paquetes mixtos."
                        )
                    )
                    continue
                }
                if (metadata.minFrameDurationMs < MIN_FRAME_DURATION_MS) {
                    issues.add(
                        StickerIssue(
                            stickerId = sticker.id,
                            emoteName = sticker.emoteName,
                            reason = "Tiene fotogramas con duración menor a 8 ms (${metadata.minFrameDurationMs} ms). WhatsApp requiere al menos 8 ms por fotograma."
                        )
                    )
                    continue
                }
                if (metadata.totalDurationMs > MAX_TOTAL_DURATION_MS) {
                    issues.add(
                        StickerIssue(
                            stickerId = sticker.id,
                            emoteName = sticker.emoteName,
                            reason = "La duración total supera el máximo de WhatsApp (${metadata.totalDurationMs} ms > 10.000 ms)."
                        )
                    )
                    continue
                }
            } else {
                if (metadata.frameCount > 1) {
                    issues.add(
                        StickerIssue(
                            stickerId = sticker.id,
                            emoteName = sticker.emoteName,
                            reason = "El paquete es estático pero este sticker es animado (${metadata.frameCount} fotogramas). WhatsApp rechaza paquetes mixtos."
                        )
                    )
                    continue
                }
            }
        }

        return StickerPackInspectionReport(
            packIdentifier = pack.identifier,
            issues = issues,
            packIssues = packIssues
        )
    }

    private fun resolveFile(filePath: String, baseDir: File?): File {
        val f = File(filePath)
        return if (f.isAbsolute && f.exists()) {
            f
        } else if (baseDir != null) {
            val relative = File(baseDir, filePath)
            if (relative.exists()) relative else f
        } else {
            f
        }
    }

    fun parseWebp(file: File): WebpMetadata? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 12) return null
            val riff = String(bytes, 0, 4, Charsets.US_ASCII)
            val webp = String(bytes, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || webp != "WEBP") return null

            var offset = 12
            var isAnimated = false
            var frameCount = 0
            var minFrameDuration = Int.MAX_VALUE
            var totalDuration = 0
            var width: Int? = null
            var height: Int? = null

            while (offset + 8 <= bytes.size) {
                val chunkHeader = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val payloadOffset = offset + 8

                if (payloadOffset + chunkSize > bytes.size) {
                    // Chunk goes beyond file bounds; truncated
                    break
                }

                when (chunkHeader) {
                    "VP8X" -> {
                        if (chunkSize >= 10) {
                            val flags = bytes[payloadOffset].toInt()
                            isAnimated = (flags and 0x02) != 0
                            val wMinus1 = (bytes[payloadOffset + 4].toInt() and 0xFF) or
                                ((bytes[payloadOffset + 5].toInt() and 0xFF) shl 8) or
                                ((bytes[payloadOffset + 6].toInt() and 0xFF) shl 16)
                            val hMinus1 = (bytes[payloadOffset + 7].toInt() and 0xFF) or
                                ((bytes[payloadOffset + 8].toInt() and 0xFF) shl 8) or
                                ((bytes[payloadOffset + 9].toInt() and 0xFF) shl 16)
                            width = wMinus1 + 1
                            height = hMinus1 + 1
                        }
                    }
                    "ANMF" -> {
                        frameCount++
                        if (chunkSize >= 16) {
                            val duration = (bytes[payloadOffset + 12].toInt() and 0xFF) or
                                ((bytes[payloadOffset + 13].toInt() and 0xFF) shl 8) or
                                ((bytes[payloadOffset + 14].toInt() and 0xFF) shl 16)
                            totalDuration += duration
                            minFrameDuration = min(minFrameDuration, duration)
                        }
                    }
                    "VP8 " -> {
                        if (width == null && chunkSize >= 10) {
                            // VP8 lossy bitstream starts with 3-byte frame tag, then 3 bytes start code 0x9d 0x01 0x2a
                            if (bytes[payloadOffset + 3] == 0x9D.toByte() &&
                                bytes[payloadOffset + 4] == 0x01.toByte() &&
                                bytes[payloadOffset + 5] == 0x2A.toByte()
                            ) {
                                val wRaw = (bytes[payloadOffset + 6].toInt() and 0xFF) or
                                    ((bytes[payloadOffset + 7].toInt() and 0xFF) shl 8)
                                val hRaw = (bytes[payloadOffset + 8].toInt() and 0xFF) or
                                    ((bytes[payloadOffset + 9].toInt() and 0xFF) shl 8)
                                width = wRaw and 0x3FFF
                                height = hRaw and 0x3FFF
                            }
                        }
                    }
                    "VP8L" -> {
                        if (width == null && chunkSize >= 5) {
                            // 1 byte signature 0x2f, then 14 bits width-1, 14 bits height-1
                            if (bytes[payloadOffset] == 0x2F.toByte()) {
                                val b1 = bytes[payloadOffset + 1].toInt() and 0xFF
                                val b2 = bytes[payloadOffset + 2].toInt() and 0xFF
                                val b3 = bytes[payloadOffset + 3].toInt() and 0xFF
                                val b4 = bytes[payloadOffset + 4].toInt() and 0xFF
                                val w14 = (b1 or (b2 shl 8)) and 0x3FFF
                                val h14 = ((b2 shr 6) or (b3 shl 2) or ((b4 and 0x0F) shl 10)) and 0x3FFF
                                width = w14 + 1
                                height = h14 + 1
                            }
                        }
                    }
                }

                // RIFF chunks are padded to even 2-byte boundaries
                val paddedSize = if (chunkSize % 2L != 0L) chunkSize + 1L else chunkSize
                offset = (payloadOffset + paddedSize).toInt()
            }

            val finalFrameCount = if (frameCount > 0) frameCount else 1
            WebpMetadata(
                isAnimated = isAnimated,
                frameCount = finalFrameCount,
                minFrameDurationMs = if (frameCount > 0 && minFrameDuration != Int.MAX_VALUE) minFrameDuration else 0,
                totalDurationMs = totalDuration,
                width = width,
                height = height
            )
        } catch (_: Exception) {
            null
        }
    }
}

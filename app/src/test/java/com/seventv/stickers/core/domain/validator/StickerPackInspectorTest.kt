package com.seventv.stickers.core.domain.validator

import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class StickerPackInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createWebpBytes(
        isAnimated: Boolean,
        width: Int = 512,
        height: Int = 512,
        frameCount: Int = if (isAnimated) 2 else 1,
        frameDurationsMs: List<Int> = if (isAnimated) List(frameCount) { 100 } else emptyList(),
        padToBytes: Int = 0
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // RIFF header placeholder
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))

        // VP8X chunk
        body.write("VP8X".toByteArray(Charsets.US_ASCII))
        body.write(byteArrayOf(10, 0, 0, 0)) // chunk length 10
        val flags = if (isAnimated) 0x02 else 0x00
        body.write(flags)
        body.write(byteArrayOf(0, 0, 0)) // 3 bytes reserved
        // width minus 1 (3 bytes LE)
        val wMinus1 = width - 1
        body.write(wMinus1 and 0xFF)
        body.write((wMinus1 shr 8) and 0xFF)
        body.write((wMinus1 shr 16) and 0xFF)
        // height minus 1 (3 bytes LE)
        val hMinus1 = height - 1
        body.write(hMinus1 and 0xFF)
        body.write((hMinus1 shr 8) and 0xFF)
        body.write((hMinus1 shr 16) and 0xFF)

        if (isAnimated) {
            // ANIM chunk
            body.write("ANIM".toByteArray(Charsets.US_ASCII))
            body.write(byteArrayOf(6, 0, 0, 0))
            body.write(byteArrayOf(0, 0, 0, 0, 0, 0)) // bg color + loop count

            // ANMF chunks
            for (i in 0 until frameCount) {
                val duration = if (i < frameDurationsMs.size) frameDurationsMs[i] else 100
                body.write("ANMF".toByteArray(Charsets.US_ASCII))
                body.write(byteArrayOf(16, 0, 0, 0)) // 16 bytes payload
                body.write(byteArrayOf(0, 0, 0)) // x
                body.write(byteArrayOf(0, 0, 0)) // y
                body.write(wMinus1 and 0xFF)
                body.write((wMinus1 shr 8) and 0xFF)
                body.write((wMinus1 shr 16) and 0xFF)
                body.write(hMinus1 and 0xFF)
                body.write((hMinus1 shr 8) and 0xFF)
                body.write((hMinus1 shr 16) and 0xFF)
                // duration 3 bytes LE
                body.write(duration and 0xFF)
                body.write((duration shr 8) and 0xFF)
                body.write((duration shr 16) and 0xFF)
                body.write(0) // flags
            }
        }

        val bodyBytes = body.toByteArray()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        val riffLength = bodyBytes.size
        out.write(riffLength and 0xFF)
        out.write((riffLength shr 8) and 0xFF)
        out.write((riffLength shr 16) and 0xFF)
        out.write((riffLength shr 24) and 0xFF)
        out.write(bodyBytes)

        val result = out.toByteArray()
        if (padToBytes > result.size) {
            val padded = ByteArray(padToBytes)
            System.arraycopy(result, 0, padded, 0, result.size)
            return padded
        }
        return result
    }

    private fun createStickerFile(name: String, bytes: ByteArray): File {
        val file = tempFolder.newFile(name)
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun validStaticPackPassesWithoutIssues() {
        val s1 = createStickerFile("s1.webp", createWebpBytes(isAnimated = false))
        val s2 = createStickerFile("s2.webp", createWebpBytes(isAnimated = false))
        val s3 = createStickerFile("s3.webp", createWebpBytes(isAnimated = false))

        val pack = StickerPack(
            identifier = "test_pack",
            name = "Test Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "1", packIdentifier = "test_pack", emoteId = "e1", emoteName = "Emote1", filePath = s1.absolutePath, emojis = listOf("😀")),
                PackSticker(id = "2", packIdentifier = "test_pack", emoteId = "e2", emoteName = "Emote2", filePath = s2.absolutePath, emojis = listOf("😀")),
                PackSticker(id = "3", packIdentifier = "test_pack", emoteId = "e3", emoteName = "Emote3", filePath = s3.absolutePath, emojis = listOf("😀"))
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertTrue("Expected valid report", report.isValid)
        assertTrue(report.issues.isEmpty())
        assertTrue(report.packIssues.isEmpty())
    }

    @Test
    fun validAnimatedPackPassesWithoutIssues() {
        val s1 = createStickerFile("a1.webp", createWebpBytes(isAnimated = true, frameCount = 3, frameDurationsMs = listOf(50, 50, 50)))
        val s2 = createStickerFile("a2.webp", createWebpBytes(isAnimated = true, frameCount = 2, frameDurationsMs = listOf(100, 100)))
        val s3 = createStickerFile("a3.webp", createWebpBytes(isAnimated = true, frameCount = 4, frameDurationsMs = listOf(20, 20, 20, 20)))

        val pack = StickerPack(
            identifier = "anim_pack",
            name = "Anim Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = true,
            stickers = listOf(
                PackSticker(id = "1", packIdentifier = "anim_pack", emoteId = "e1", emoteName = "Anim1", filePath = s1.absolutePath, emojis = listOf("🔥")),
                PackSticker(id = "2", packIdentifier = "anim_pack", emoteId = "e2", emoteName = "Anim2", filePath = s2.absolutePath, emojis = listOf("🔥")),
                PackSticker(id = "3", packIdentifier = "anim_pack", emoteId = "e3", emoteName = "Anim3", filePath = s3.absolutePath, emojis = listOf("🔥"))
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertTrue("Expected valid report", report.isValid)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun packWithFewerThanThreeStickersReportsPackIssue() {
        val s1 = createStickerFile("s1.webp", createWebpBytes(isAnimated = false))
        val pack = StickerPack(
            identifier = "small_pack",
            name = "Small Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "1", packIdentifier = "small_pack", emoteId = "e1", emoteName = "Emote1", filePath = s1.absolutePath, emojis = listOf("😀"))
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        assertTrue(report.packIssues.any { it.contains("mínimo de 3", ignoreCase = true) })
    }

    @Test
    fun missingStickerFileReportsIssue() {
        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "missing_id", packIdentifier = "pack", emoteId = "e1", emoteName = "GhostEmote", filePath = "/non/existent/file.webp", emojis = listOf("👻")),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "E2", filePath = createStickerFile("s2.webp", createWebpBytes(false)).absolutePath),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "E3", filePath = createStickerFile("s3.webp", createWebpBytes(false)).absolutePath)
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "missing_id" }
        assertTrue("Issue should be found for missing sticker", issue != null)
        assertTrue(issue!!.reason.contains("no existe", ignoreCase = true))
    }

    @Test
    fun oversizedStaticStickerReportsIssue() {
        // WhatsApp static limit is 100 KB = 102400 bytes
        val oversizedBytes = createWebpBytes(isAnimated = false, padToBytes = 105 * 1024)
        val oversizedFile = createStickerFile("big.webp", oversizedBytes)

        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "big_id", packIdentifier = "pack", emoteId = "e1", emoteName = "HeavyEmote", filePath = oversizedFile.absolutePath, emojis = listOf("🐘")),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "E2", filePath = createStickerFile("s2.webp", createWebpBytes(false)).absolutePath),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "E3", filePath = createStickerFile("s3.webp", createWebpBytes(false)).absolutePath)
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "big_id" }
        assertTrue(issue != null)
        assertTrue(issue!!.reason.contains("100 KB", ignoreCase = true))
    }

    @Test
    fun staticStickerInAnimatedPackReportsFormatMismatch() {
        // In animated pack, sticker with frameCount <= 1 is rejected by WhatsApp
        val staticBytes = createWebpBytes(isAnimated = false)
        val staticFile = createStickerFile("static_in_anim.webp", staticBytes)

        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = true,
            stickers = listOf(
                PackSticker(id = "static_id", packIdentifier = "pack", emoteId = "e1", emoteName = "FrozenEmote", filePath = staticFile.absolutePath, emojis = listOf("🧊")),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "A2", filePath = createStickerFile("a2.webp", createWebpBytes(true)).absolutePath),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "A3", filePath = createStickerFile("a3.webp", createWebpBytes(true)).absolutePath)
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "static_id" }
        assertTrue(issue != null)
        assertTrue(issue!!.reason.contains("estático", ignoreCase = true))
    }

    @Test
    fun animatedStickerInStaticPackReportsFormatMismatch() {
        val animBytes = createWebpBytes(isAnimated = true)
        val animFile = createStickerFile("anim_in_static.webp", animBytes)

        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "anim_id", packIdentifier = "pack", emoteId = "e1", emoteName = "MovingEmote", filePath = animFile.absolutePath, emojis = listOf("🏃")),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "S2", filePath = createStickerFile("s2.webp", createWebpBytes(false)).absolutePath),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "S3", filePath = createStickerFile("s3.webp", createWebpBytes(false)).absolutePath)
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "anim_id" }
        assertTrue(issue != null)
        assertTrue(issue!!.reason.contains("animado", ignoreCase = true))
    }

    @Test
    fun frameDurationUnder8msReportsIssue() {
        // WhatsApp requires >= 8ms frame duration
        val badAnimBytes = createWebpBytes(isAnimated = true, frameCount = 2, frameDurationsMs = listOf(5, 50))
        val badFile = createStickerFile("fast.webp", badAnimBytes)

        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = true,
            stickers = listOf(
                PackSticker(id = "fast_id", packIdentifier = "pack", emoteId = "e1", emoteName = "FastEmote", filePath = badFile.absolutePath, emojis = listOf("⚡")),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "A2", filePath = createStickerFile("a2.webp", createWebpBytes(true)).absolutePath),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "A3", filePath = createStickerFile("a3.webp", createWebpBytes(true)).absolutePath)
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "fast_id" }
        assertTrue(issue != null)
        assertTrue(issue!!.reason.contains("8 ms", ignoreCase = true))
    }

    @Test
    fun stickerWithoutEmojisReportsIssue() {
        val s1 = createStickerFile("s1.webp", createWebpBytes(isAnimated = false))
        val pack = StickerPack(
            identifier = "pack",
            name = "Pack",
            publisher = "Test",
            trayImagePath = "tray.png",
            isAnimated = false,
            stickers = listOf(
                PackSticker(id = "no_emoji_id", packIdentifier = "pack", emoteId = "e1", emoteName = "NoEmoji", filePath = s1.absolutePath, emojis = emptyList()),
                PackSticker(id = "2", packIdentifier = "pack", emoteId = "e2", emoteName = "S2", filePath = createStickerFile("s2.webp", createWebpBytes(false)).absolutePath, emojis = listOf("😀")),
                PackSticker(id = "3", packIdentifier = "pack", emoteId = "e3", emoteName = "S3", filePath = createStickerFile("s3.webp", createWebpBytes(false)).absolutePath, emojis = listOf("😀"))
            )
        )

        val report = StickerPackInspector.inspect(pack)
        assertFalse(report.isValid)
        val issue = report.issues.find { it.stickerId == "no_emoji_id" }
        assertTrue(issue != null)
        assertTrue(issue!!.reason.contains("emoji", ignoreCase = true))
    }
}

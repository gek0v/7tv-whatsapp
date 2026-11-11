package com.seventv.stickers.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256ChecksumTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // sha256 of empty string

    @Test
    fun computeSha256OfEmptyFile() {
        val emptyFile = tempFolder.newFile("empty.bin")
        val sha = Sha256Checksum.computeSha256(emptyFile)
        assertEquals(sampleHex, sha)
    }

    @Test
    fun computeAndVerifyFileSha256() {
        val file = tempFolder.newFile("hello.txt")
        file.writeText("hello world\n")
        val computed = Sha256Checksum.computeSha256(file)

        assertTrue(Sha256Checksum.verify(file, computed))
        assertTrue(Sha256Checksum.verify(file, computed.uppercase()))
        assertTrue(Sha256Checksum.verify(file, "sha256:$computed"))
        assertFalse(Sha256Checksum.verify(file, "0000000000000000000000000000000000000000000000000000000000000000"))
        assertFalse(Sha256Checksum.verify(File("non_existent"), computed))
    }

    @Test
    fun normalizeHexChecks() {
        assertEquals(sampleHex, Sha256Checksum.normalizeHex(sampleHex))
        assertEquals(sampleHex, Sha256Checksum.normalizeHex(sampleHex.uppercase()))
        assertEquals(sampleHex, Sha256Checksum.normalizeHex("sha256:$sampleHex"))
        assertNull(Sha256Checksum.normalizeHex(null))
        assertNull(Sha256Checksum.normalizeHex(""))
        assertNull(Sha256Checksum.normalizeHex("12345"))
        assertNull(Sha256Checksum.normalizeHex("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"))
    }

    @Test
    fun extractFromChecksumFileWithMultipleEntries() {
        val targetSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val otherSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val content = """
            $otherSha  other-app.apk
            $targetSha  7tv-stickers-v1.2.0.apk
        """.trimIndent()

        val extracted = Sha256Checksum.extractFromChecksumFile(content, "7tv-stickers-v1.2.0.apk")
        assertEquals(targetSha, extracted)

        // Single entry without filename
        val singleContent = "sha256: $targetSha"
        assertEquals(targetSha, Sha256Checksum.extractFromChecksumFile(singleContent))
    }

    @Test
    fun extractFromMarkdownReleaseBody() {
        val targetSha = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val markdown = """
            ## Novedades v1.2.0
            - Añadido actualizador automático
            
            ### Verificación de Integridad
            - **SHA-256**: `$targetSha`
        """.trimIndent()

        val extracted = Sha256Checksum.extractFromMarkdown(markdown, "app-release.apk")
        assertEquals(targetSha, extracted)

        // Null when markdown has no sha256
        assertNull(Sha256Checksum.extractFromMarkdown("Solo notas de versión sin hash."))
        assertNull(Sha256Checksum.extractFromMarkdown(""))
    }
}
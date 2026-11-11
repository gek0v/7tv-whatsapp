package com.seventv.stickers.core.updater

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256Checksum {

    private val HEX_64_REGEX = Regex("""\b([a-fA-F0-9]{64})\b""")
    private val SHA256_PREFIX_REGEX = Regex("""sha256:([a-fA-F0-9]{64})""", RegexOption.IGNORE_CASE)

    fun computeSha256(file: File): String {
        file.inputStream().buffered().use { input ->
            return computeSha256(input)
        }
    }

    fun computeSha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val normalizedExpected = normalizeHex(expectedSha256) ?: return false
        val actual = computeSha256(file)
        return actual.equals(normalizedExpected, ignoreCase = true)
    }

    fun normalizeHex(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().removePrefix("sha256:").removePrefix("SHA256:").trim()
        return if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            trimmed.lowercase()
        } else {
            null
        }
    }

    fun extractFromChecksumFile(content: String, apkFileName: String? = null): String? {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (!apkFileName.isNullOrBlank()) {
            val matchedLine = lines.firstOrNull { line ->
                line.contains(apkFileName, ignoreCase = true) && HEX_64_REGEX.containsMatchIn(line)
            }
            if (matchedLine != null) {
                return HEX_64_REGEX.find(matchedLine)?.groupValues?.get(1)?.lowercase()
            }
        }

        for (line in lines) {
            SHA256_PREFIX_REGEX.find(line)?.let { match ->
                return match.groupValues[1].lowercase()
            }
            HEX_64_REGEX.find(line)?.let { match ->
                return match.groupValues[1].lowercase()
            }
        }
        return null
    }

    fun extractFromMarkdown(markdown: String, apkFileName: String? = null): String? {
        if (markdown.isBlank()) return null

        SHA256_PREFIX_REGEX.find(markdown)?.let { match ->
            return match.groupValues[1].lowercase()
        }

        val lines = markdown.lineSequence().toList()

        if (!apkFileName.isNullOrBlank()) {
            val fileLine = lines.firstOrNull { line ->
                line.contains(apkFileName, ignoreCase = true) && HEX_64_REGEX.containsMatchIn(line)
            }
            if (fileLine != null) {
                return HEX_64_REGEX.find(fileLine)?.groupValues?.get(1)?.lowercase()
            }
        }

        val labeledLine = lines.firstOrNull { line ->
            (line.contains("sha256", ignoreCase = true) ||
                line.contains("sha-256", ignoreCase = true) ||
                line.contains("checksum", ignoreCase = true)) &&
                HEX_64_REGEX.containsMatchIn(line)
        }
        if (labeledLine != null) {
            return HEX_64_REGEX.find(labeledLine)?.groupValues?.get(1)?.lowercase()
        }

        return null
    }
}
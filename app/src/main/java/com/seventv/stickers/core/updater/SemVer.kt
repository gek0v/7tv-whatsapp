package com.seventv.stickers.core.updater

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList()
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val maxLen = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until maxLen) {
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1

            val leftNum = left.toLongOrNull()
            val rightNum = right.toLongOrNull()

            val diff = when {
                leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                leftNum != null -> -1
                rightNum != null -> 1
                else -> left.compareTo(right)
            }
            if (diff != 0) return diff
        }
        return 0
    }

    override fun toString(): String {
        val core = "$major.$minor.$patch"
        return if (preRelease.isEmpty()) core else "$core-${preRelease.joinToString(".")}"
    }

    companion object {
        private val CORE_REGEX = Regex("""^\d+(?:\.\d+){0,2}$""")

        fun parse(raw: String): SemVer? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
                trimmed.substring(1)
            } else {
                trimmed
            }

            val withoutBuild = withoutPrefix.substringBefore('+')
            if (withoutBuild.isEmpty()) return null

            val dashIndex = withoutBuild.indexOf('-')
            val corePart = if (dashIndex >= 0) withoutBuild.substring(0, dashIndex) else withoutBuild
            val preReleasePart = if (dashIndex >= 0) withoutBuild.substring(dashIndex + 1) else null

            if (!CORE_REGEX.matches(corePart)) return null

            val segments = corePart.split('.')
            val major = segments[0].toIntOrNull() ?: return null
            val minor = segments.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = segments.getOrNull(2)?.toIntOrNull() ?: 0

            val preReleaseTokens = if (preReleasePart != null) {
                if (preReleasePart.isEmpty()) return null
                val tokens = preReleasePart.split('.')
                if (tokens.any { it.isEmpty() }) return null
                tokens
            } else {
                emptyList()
            }

            return SemVer(
                major = major,
                minor = minor,
                patch = patch,
                preRelease = preReleaseTokens
            )
        }

        fun isUpdateAvailable(
            currentVersion: String,
            remoteVersion: String,
            currentVersionCode: Int? = null,
            remoteVersionCode: Int? = null
        ): Boolean {
            val current = parse(currentVersion) ?: return false
            val remote = parse(remoteVersion) ?: return false
            val comparison = remote.compareTo(current)
            if (comparison > 0) return true
            if (comparison == 0 && currentVersionCode != null && remoteVersionCode != null) {
                return remoteVersionCode > currentVersionCode
            }
            return false
        }

        fun normalizeDisplayVersion(raw: String): String {
            return parse(raw)?.toString() ?: raw.trim().removePrefix("v").removePrefix("V")
        }
    }
}
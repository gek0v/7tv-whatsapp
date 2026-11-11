package com.seventv.stickers.core.updater

import com.seventv.stickers.BuildConfig

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val changelogMarkdown: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val expectedSha256: String?,
    val publishedAt: String? = null,
    val htmlUrl: String? = null
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Skipped : UpdateCheckResult
    data class Error(val message: String, val cause: Throwable? = null) : UpdateCheckResult
}

class AppUpdateChecker(
    private val releaseService: GitHubReleaseService,
    private val preferences: UpdatePreferencesRepository,
    private val checksumAssetFetcher: ChecksumAssetFetcher,
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val owner: String = GitHubReleaseService.DEFAULT_OWNER,
    private val repo: String = GitHubReleaseService.DEFAULT_REPO
) {

    companion object {
        const val CHECK_THROTTLE_MS = 6L * 60L * 60L * 1000L
        const val SNOOZE_24H_MS = 24L * 60L * 60L * 1000L
        const val SNOOZE_48H_MS = 48L * 60L * 60L * 1000L
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult {
        val snapshot = preferences.getSnapshot()
        val now = currentTimeProvider()

        if (!force) {
            if (!snapshot.autoCheckEnabled) {
                return UpdateCheckResult.Skipped
            }
            if (snapshot.lastCheckTimestampMs > 0L && (now - snapshot.lastCheckTimestampMs) < CHECK_THROTTLE_MS) {
                val cachedDto = decodeRelease(snapshot.cachedReleaseJson)
                if (cachedDto != null) {
                    return evaluateRelease(cachedDto, snapshot, now, force = false)
                }
                return UpdateCheckResult.Skipped
            }
        }

        val response = try {
            releaseService.getLatestRelease(
                owner = owner,
                repo = repo,
                ifNoneMatch = snapshot.etag
            )
        } catch (e: Exception) {
            return UpdateCheckResult.Error(
                message = "No se pudo comprobar actualizaciones: ${e.message ?: "error de red"}",
                cause = e
            )
        }

        if (response.code() == 304) {
            preferences.updateLastCheckTimestamp(now)
            val cachedDto = decodeRelease(snapshot.cachedReleaseJson)
            return if (cachedDto != null) {
                evaluateRelease(cachedDto, snapshot, now, force = force)
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        if (!response.isSuccessful) {
            val message = when (response.code()) {
                403, 429 -> "Límite de peticiones de GitHub alcanzado. Inténtalo más tarde."
                404 -> "No se encontraron releases publicadas en GitHub."
                else -> "Error al consultar GitHub Releases (HTTP ${response.code()})."
            }
            return UpdateCheckResult.Error(message)
        }

        val release = response.body() ?: return UpdateCheckResult.Error("Respuesta vacía de GitHub Releases.")
        val etag = response.headers()["ETag"] ?: snapshot.etag
        val encoded = GitHubReleaseService.json.encodeToString(GitHubReleaseDto.serializer(), release)
        preferences.saveCheckResult(
            etag = etag,
            cachedReleaseJson = encoded,
            checkedAtMs = now
        )

        return evaluateRelease(release, snapshot, now, force = force)
    }

    private suspend fun evaluateRelease(
        release: GitHubReleaseDto,
        snapshot: UpdatePreferencesSnapshot,
        nowMs: Long,
        force: Boolean
    ): UpdateCheckResult {
        if (release.draft) return UpdateCheckResult.UpToDate

        val currentVersion = currentVersionProvider()
        if (!SemVer.isUpdateAvailable(currentVersion = currentVersion, remoteVersion = release.tagName)) {
            return UpdateCheckResult.UpToDate
        }

        val normalizedRemote = SemVer.normalizeDisplayVersion(release.tagName)
        if (!force &&
            snapshot.snoozedUntilMs > nowMs &&
            snapshot.snoozedVersion == normalizedRemote
        ) {
            return UpdateCheckResult.Skipped
        }

        val apkAsset = selectApkAsset(release.assets) ?: return UpdateCheckResult.UpToDate
        val expectedSha256 = resolveExpectedSha256(release, apkAsset)

        val info = AppUpdateInfo(
            currentVersion = SemVer.normalizeDisplayVersion(currentVersion),
            latestVersion = normalizedRemote,
            releaseTitle = release.name?.takeIf { it.isNotBlank() } ?: "Versión $normalizedRemote",
            changelogMarkdown = release.body?.trim().orEmpty(),
            apkUrl = apkAsset.browserDownloadUrl,
            apkFileName = apkAsset.name,
            apkSizeBytes = apkAsset.size,
            expectedSha256 = expectedSha256,
            publishedAt = release.publishedAt,
            htmlUrl = release.htmlUrl
        )
        return UpdateCheckResult.UpdateAvailable(info)
    }

    private fun selectApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null
        return apkAssets.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
            ?: apkAssets.first()
    }

    private suspend fun resolveExpectedSha256(
        release: GitHubReleaseDto,
        apkAsset: GitHubAssetDto
    ): String? {
        Sha256Checksum.normalizeHex(apkAsset.digest)?.let { return it }

        val checksumAsset = release.assets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name == "${apkAsset.name.lowercase()}.sha256" ||
                name.endsWith(".sha256") ||
                name.endsWith(".sha256sum") ||
                name == "checksums.txt" ||
                name == "sha256sums.txt"
        }

        if (checksumAsset != null) {
            val content = checksumAssetFetcher.fetchText(checksumAsset.browserDownloadUrl)
            if (!content.isNullOrBlank()) {
                Sha256Checksum.extractFromChecksumFile(content, apkAsset.name)?.let { return it }
            }
        }

        val body = release.body
        if (!body.isNullOrBlank()) {
            Sha256Checksum.extractFromMarkdown(body, apkAsset.name)?.let { return it }
        }

        return null
    }

    private fun decodeRelease(jsonText: String?): GitHubReleaseDto? {
        if (jsonText.isNullOrBlank()) return null
        return try {
            GitHubReleaseService.json.decodeFromString(GitHubReleaseDto.serializer(), jsonText)
        } catch (_: Exception) {
            null
        }
    }
}
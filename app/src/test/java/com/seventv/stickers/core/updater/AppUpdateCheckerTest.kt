package com.seventv.stickers.core.updater

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeUpdatePreferencesRepository : UpdatePreferencesRepository {
    var snapshot = UpdatePreferencesSnapshot()
    private val _settings = MutableStateFlow(UpdateSettings())
    override val settingsFlow: Flow<UpdateSettings> = _settings.asStateFlow()

    override suspend fun getSnapshot(): UpdatePreferencesSnapshot = snapshot

    override suspend fun saveCheckResult(etag: String?, cachedReleaseJson: String?, checkedAtMs: Long) {
        snapshot = snapshot.copy(
            etag = etag,
            cachedReleaseJson = cachedReleaseJson,
            lastCheckTimestampMs = checkedAtMs
        )
    }

    override suspend fun updateLastCheckTimestamp(checkedAtMs: Long) {
        snapshot = snapshot.copy(lastCheckTimestampMs = checkedAtMs)
    }

    override suspend fun snoozeVersion(version: String, snoozedUntilMs: Long) {
        snapshot = snapshot.copy(snoozedVersion = version, snoozedUntilMs = snoozedUntilMs)
    }

    override suspend fun clearSnooze() {
        snapshot = snapshot.copy(snoozedVersion = null, snoozedUntilMs = 0L)
    }

    override suspend fun setAutoCheckEnabled(enabled: Boolean) {
        snapshot = snapshot.copy(autoCheckEnabled = enabled)
        _settings.value = _settings.value.copy(autoCheckEnabled = enabled)
    }

    override suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        snapshot = snapshot.copy(wifiOnlyDownload = wifiOnly)
        _settings.value = _settings.value.copy(wifiOnlyDownload = wifiOnly)
    }
}

class AppUpdateCheckerTest {

    private val mockWebServer = MockWebServer()
    private lateinit var releaseService: GitHubReleaseService
    private lateinit var fakePrefs: FakeUpdatePreferencesRepository
    private var fakeNowMs: Long = 1_000_000_000L

    @Before
    fun setUp() {
        mockWebServer.start()
        releaseService = GitHubReleaseService.create(baseUrl = mockWebServer.url("/").toString())
        fakePrefs = FakeUpdatePreferencesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun buildChecker(
        currentVersion: String = "1.1.0",
        checksumFetcher: ChecksumAssetFetcher = ChecksumAssetFetcher { null }
    ): AppUpdateChecker {
        return AppUpdateChecker(
            releaseService = releaseService,
            preferences = fakePrefs,
            checksumAssetFetcher = checksumFetcher,
            currentVersionProvider = { currentVersion },
            currentTimeProvider = { fakeNowMs }
        )
    }

    @Test
    fun detectsNewVersionAndExtractsSha256FromChecksumAsset() = runTest {
        val expectedSha = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val apkUrl = mockWebServer.url("/download/7tv.apk").toString()
        val shaUrl = mockWebServer.url("/download/7tv.apk.sha256").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1.2.0-etag\"")
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "name": "Versión 1.2.0",
                      "body": "- Nuevo motor de stickers",
                      "assets": [
                        {
                          "name": "7tv-stickers-v1.2.0.apk",
                          "size": 5000000,
                          "browser_download_url": "$apkUrl"
                        },
                        {
                          "name": "7tv-stickers-v1.2.0.apk.sha256",
                          "size": 100,
                          "browser_download_url": "$shaUrl"
                        }
                      ]
                    }
                """.trimIndent())
        )

        val checker = buildChecker(
            currentVersion = "1.1.0",
            checksumFetcher = ChecksumAssetFetcher { "$expectedSha  7tv-stickers-v1.2.0.apk" }
        )

        val result = checker.checkForUpdate(force = false)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val info = (result as UpdateCheckResult.UpdateAvailable).info
        assertEquals("1.1.0", info.currentVersion)
        assertEquals("1.2.0", info.latestVersion)
        assertEquals(apkUrl, info.apkUrl)
        assertEquals(expectedSha, info.expectedSha256)
        assertEquals("\"v1.2.0-etag\"", fakePrefs.snapshot.etag)
    }

    @Test
    fun handlesMissingSha256Gracefully() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "body": "Sin checksum",
                      "assets": [
                        {
                          "name": "7tv.apk",
                          "size": 1024,
                          "browser_download_url": "https://example.com/7tv.apk"
                        }
                      ]
                    }
                """.trimIndent())
        )

        val result = buildChecker().checkForUpdate(force = true)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertNull((result as UpdateCheckResult.UpdateAvailable).info.expectedSha256)
    }

    @Test
    fun respectsEtagAnd304NotModifiedUsingCachedRelease() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"etag-1\"")
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "body": "Notas",
                      "assets": [
                        { "name": "app.apk", "size": 2048, "browser_download_url": "https://example.com/app.apk" }
                      ]
                    }
                """.trimIndent())
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(304))

        val checker = buildChecker(currentVersion = "1.1.0")
        val first = checker.checkForUpdate(force = true)
        assertTrue(first is UpdateCheckResult.UpdateAvailable)

        fakeNowMs += AppUpdateChecker.CHECK_THROTTLE_MS + 1000L
        val second = checker.checkForUpdate(force = false)
        assertTrue(second is UpdateCheckResult.UpdateAvailable)

        mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("\"etag-1\"", secondRequest.getHeader("If-None-Match"))
    }

    @Test
    fun throttlingSkipsNetworkWithin6HoursUnlessForced() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "tag_name": "v1.1.0",
                      "assets": [
                        { "name": "app.apk", "size": 2048, "browser_download_url": "https://example.com/app.apk" }
                      ]
                    }
                """.trimIndent())
        )

        val checker = buildChecker(currentVersion = "1.1.0")
        val first = checker.checkForUpdate(force = false)
        assertEquals(UpdateCheckResult.UpToDate, first)
        assertEquals(1, mockWebServer.requestCount)

        // Advance only 1 hour -> should NOT hit network
        fakeNowMs += 60 * 60 * 1000L
        val throttled = checker.checkForUpdate(force = false)
        assertEquals(UpdateCheckResult.UpToDate, throttled)
        assertEquals(1, mockWebServer.requestCount)

        // Force check -> hits network
        mockWebServer.enqueue(MockResponse().setResponseCode(304))
        checker.checkForUpdate(force = true)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun snoozeSuppressesNonForcedChecksUntilExpired() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "assets": [
                        { "name": "app.apk", "size": 2048, "browser_download_url": "https://example.com/app.apk" }
                      ]
                    }
                """.trimIndent())
        )

        val checker = buildChecker(currentVersion = "1.1.0")
        fakePrefs.snoozeVersion("1.2.0", fakeNowMs + AppUpdateChecker.SNOOZE_24H_MS)

        val snoozedResult = checker.checkForUpdate(force = false)
        assertEquals(UpdateCheckResult.Skipped, snoozedResult)

        // Force ignores snooze
        mockWebServer.enqueue(MockResponse().setResponseCode(304))
        val forcedResult = checker.checkForUpdate(force = true)
        assertTrue(forcedResult is UpdateCheckResult.UpdateAvailable)

        // After 25h snooze expires
        fakeNowMs += AppUpdateChecker.SNOOZE_24H_MS + 60_000L
        mockWebServer.enqueue(MockResponse().setResponseCode(304))
        val afterExpiry = checker.checkForUpdate(force = false)
        assertTrue(afterExpiry is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun disabledAutoCheckSkipsNonForcedChecks() = runTest {
        fakePrefs.setAutoCheckEnabled(false)
        val result = buildChecker().checkForUpdate(force = false)
        assertEquals(UpdateCheckResult.Skipped, result)
        assertEquals(0, mockWebServer.requestCount)
    }
}
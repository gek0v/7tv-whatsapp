package com.seventv.stickers.core.updater

import android.content.Context
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppUpdateManagerTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private val mockWebServer = MockWebServer()
    private lateinit var context: Context
    private lateinit var fakePrefs: FakeUpdatePreferencesRepository
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer.start()
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
        )
        workManager = WorkManager.getInstance(context)
        fakePrefs = FakeUpdatePreferencesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    private fun createManager(
        currentVersion: String = "1.1.0",
        scope: CoroutineScope
    ): AppUpdateManager {
        val service = GitHubReleaseService.create(baseUrl = mockWebServer.url("/").toString())
        val checker = AppUpdateChecker(
            releaseService = service,
            preferences = fakePrefs,
            checksumAssetFetcher = ChecksumAssetFetcher { null },
            currentVersionProvider = { currentVersion }
        )
        return AppUpdateManager(
            context = context,
            checker = checker,
            preferences = fakePrefs,
            workManagerProvider = { workManager },
            scope = scope,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun checkForUpdatesNowTransitionsToAvailableAndOpensDialog() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "name": "7TV Stickers 1.2.0",
                      "body": "- Nuevo soporte",
                      "assets": [
                        {
                          "name": "7tv.apk",
                          "size": 4096,
                          "browser_download_url": "https://example.com/7tv.apk"
                        }
                      ]
                    }
                """.trimIndent())
        )

        val manager = createManager(scope = backgroundScope)
        manager.checkForUpdatesNow()
        val finalState = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                manager.state.first { it.status is UpdateStatus.Available }
            }
        }

        assertTrue(finalState.status is UpdateStatus.Available)
        assertEquals("1.2.0", (finalState.status as UpdateStatus.Available).info.latestVersion)
        assertTrue(finalState.isChangelogDialogVisible)
    }

    @Test
    fun remindLaterSnoozesVersionAndHidesBanner() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "tag_name": "v1.2.0",
                      "assets": [
                        {
                          "name": "7tv.apk",
                          "size": 4096,
                          "browser_download_url": "https://example.com/7tv.apk"
                        }
                      ]
                    }
                """.trimIndent())
        )

        val manager = createManager(scope = backgroundScope)
        manager.checkForUpdatesNow()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                manager.state.first { it.status is UpdateStatus.Available }
            }
        }

        manager.remindLater(hours = 24L)
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (fakePrefs.snapshot.snoozedVersion == null) {
                    kotlinx.coroutines.delay(10)
                }
            }
        }

        val state = manager.state.value
        assertFalse(state.isChangelogDialogVisible)
        assertTrue(state.isBannerDismissedInSession)
        assertEquals("1.2.0", fakePrefs.snapshot.snoozedVersion)
        assertTrue(fakePrefs.snapshot.snoozedUntilMs > System.currentTimeMillis())
    }

    @Test
    fun startDownloadEnqueuesWorkWithWifiConstraintWhenEnabled() = runTest(testDispatcher) {
        val manager = createManager(scope = backgroundScope)
        manager.setWifiOnlyDownload(true)
        advanceUntilIdle()

        val info = AppUpdateInfo(
            currentVersion = "1.1.0",
            latestVersion = "1.2.0",
            releaseTitle = "v1.2.0",
            changelogMarkdown = "Notes",
            apkUrl = mockWebServer.url("/7tv.apk").toString(),
            apkFileName = "7tv.apk",
            apkSizeBytes = 2048L,
            expectedSha256 = null
        )

        manager.startDownload(info)
        advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(ApkDownloadWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
        val workInfo = workInfos.first()
        assertEquals(NetworkType.UNMETERED, workInfo.constraints.requiredNetworkType)
        assertTrue(
            workInfo.state == WorkInfo.State.ENQUEUED ||
                workInfo.state == WorkInfo.State.BLOCKED ||
                workInfo.state == WorkInfo.State.RUNNING
        )
    }

    @Test
    fun toggleSettingsPersistInPreferences() = runTest(testDispatcher) {
        val manager = createManager(scope = backgroundScope)
        manager.setAutoCheckEnabled(false)
        manager.setWifiOnlyDownload(true)
        advanceUntilIdle()

        assertFalse(manager.state.value.settings.autoCheckEnabled)
        assertTrue(manager.state.value.settings.wifiOnlyDownload)
    }
}
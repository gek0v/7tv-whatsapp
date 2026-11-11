package com.seventv.stickers.core.updater

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ApkDownloadHelperAndWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockWebServer = MockWebServer()
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockWebServer.start()
        okHttpClient = OkHttpClient.Builder().build()
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun downloadWithResumeDownloadsFullApkAndVerifiesSha256() = runTest {
        val payload = "FAKE_APK_BINARY_PAYLOAD_1234567890".toByteArray()
        val expectedSha = sha256Of(payload)
        val destFile = File(tempFolder.root, "update.apk")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
        )

        var lastDownloaded = 0L
        var lastTotal = 0L
        val outcome = ApkDownloadHelper.downloadWithResume(
            okHttpClient = okHttpClient,
            url = mockWebServer.url("/update.apk").toString(),
            destinationFile = destFile,
            expectedSha256 = expectedSha,
            onProgress = { downloaded, total ->
                lastDownloaded = downloaded
                lastTotal = total
            }
        )

        assertTrue(outcome is ApkDownloadOutcome.Success)
        val success = outcome as ApkDownloadOutcome.Success
        assertTrue(success.verifiedSha256)
        assertTrue(destFile.exists())
        assertEquals(payload.size.toLong(), destFile.length())
        assertEquals(payload.size.toLong(), lastDownloaded)
        assertEquals(payload.size.toLong(), lastTotal)
    }

    @Test
    fun downloadWithResumeResumesPartialFileWithRange206() = runTest {
        val fullPayload = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
        val expectedSha = sha256Of(fullPayload)
        val splitPoint = 10
        val firstPart = fullPayload.copyOfRange(0, splitPoint)
        val secondPart = fullPayload.copyOfRange(splitPoint, fullPayload.size)

        val destFile = File(tempFolder.root, "resumed.apk")
        val partFile = File(tempFolder.root, "resumed.apk.part")
        partFile.writeBytes(firstPart)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $splitPoint-${fullPayload.size - 1}/${fullPayload.size}")
                .setBody(Buffer().write(secondPart))
        )

        val outcome = ApkDownloadHelper.downloadWithResume(
            okHttpClient = okHttpClient,
            url = mockWebServer.url("/resumed.apk").toString(),
            destinationFile = destFile,
            expectedSha256 = expectedSha
        )

        val request = mockWebServer.takeRequest()
        assertEquals("bytes=$splitPoint-", request.getHeader("Range"))

        assertTrue(outcome is ApkDownloadOutcome.Success)
        assertTrue((outcome as ApkDownloadOutcome.Success).verifiedSha256)
        assertEquals(fullPayload.toList(), destFile.readBytes().toList())
        assertFalse(partFile.exists())
    }

    @Test
    fun downloadWithResumeFailsAndDeletesFileOnSha256Mismatch() = runTest {
        val payload = "CORRUPTED_APK_BYTES".toByteArray()
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
        val destFile = File(tempFolder.root, "corrupt.apk")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
        )

        val outcome = ApkDownloadHelper.downloadWithResume(
            okHttpClient = okHttpClient,
            url = mockWebServer.url("/corrupt.apk").toString(),
            destinationFile = destFile,
            expectedSha256 = wrongSha
        )

        assertTrue(outcome is ApkDownloadOutcome.Failure)
        assertFalse(destFile.exists())
        assertFalse(File(tempFolder.root, "corrupt.apk.part").exists())
    }

    @Test
    fun apkDownloadWorkerSucceedsAndReturnsApkPath() = runTest {
        val payload = "WORKER_APK_PAYLOAD_CONTENT".toByteArray()
        val expectedSha = sha256Of(payload)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
        )

        val input = workDataOf(
            ApkDownloadWorker.KEY_APK_URL to mockWebServer.url("/release.apk").toString(),
            ApkDownloadWorker.KEY_APK_FILENAME to "release-v1.2.0.apk",
            ApkDownloadWorker.KEY_VERSION_NAME to "1.2.0",
            ApkDownloadWorker.KEY_EXPECTED_SHA256 to expectedSha,
            ApkDownloadWorker.KEY_EXPECTED_SIZE to payload.size.toLong()
        )

        val worker = TestListenableWorkerBuilder<ApkDownloadWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        val apkPath = output.getString(ApkDownloadWorker.KEY_APK_PATH)!!
        assertTrue(File(apkPath).exists())
        assertTrue(output.getBoolean(ApkDownloadWorker.KEY_VERIFIED_SHA256, false))
    }

    @Test
    fun apkDownloadWorkerFailsWhenSha256Mismatches() = runTest {
        val payload = "BAD_PAYLOAD".toByteArray()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
        )

        val input = workDataOf(
            ApkDownloadWorker.KEY_APK_URL to mockWebServer.url("/bad.apk").toString(),
            ApkDownloadWorker.KEY_APK_FILENAME to "bad.apk",
            ApkDownloadWorker.KEY_VERSION_NAME to "1.2.0",
            ApkDownloadWorker.KEY_EXPECTED_SHA256 to "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        )

        val worker = TestListenableWorkerBuilder<ApkDownloadWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        val error = (result as ListenableWorker.Result.Failure).outputData.getString(ApkDownloadWorker.KEY_ERROR_MESSAGE)
        assertTrue(error!!.contains("SHA-256"))
    }
}
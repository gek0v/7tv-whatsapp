package com.seventv.stickers.core.network.downloader

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultEmoteDownloaderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tempDir: File
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var downloader: DefaultEmoteDownloader

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tempDir = Files.createTempDirectory("downloader_test").toFile()
        okHttpClient = OkHttpClient()
        downloader = DefaultEmoteDownloader(
            cacheDir = tempDir,
            okHttpClient = okHttpClient
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun downloadEmoteFetchesFromNetworkAndSavesToCache() = runTest {
        val payload = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x01, 0x02, 0x03, 0x04)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/webp")
                .setBody(Buffer().write(payload))
        )

        val emote = SevenTvEmote(
            id = "emote1",
            name = "KEKW",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/emote1/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        // First download: network hit + cached
        val result = downloader.downloadEmote(emote)
        assertTrue("Expected Success on first download", result is StickerResult.Success<*>)
        val asset = (result as StickerResult.Success<DownloadedEmoteAsset>).data
        assertEquals("emote1", asset.emoteId)
        assertEquals("KEKW", asset.emoteName)
        assertFalse(asset.isAnimated)
        assertEquals("WEBP", asset.format)
        assertArrayEquals(payload, asset.bytes)

        val cacheFolder = File(tempDir, "7tv_emotes_cache")
        assertTrue(cacheFolder.exists())
        val cachedFiles = cacheFolder.listFiles { file -> file.isFile && file.name.startsWith("emote1") }
        assertTrue(cachedFiles != null && cachedFiles.isNotEmpty())
        assertArrayEquals(payload, cachedFiles!!.first().readBytes())

        // Second download: cache hit, no network request
        val secondResult = downloader.downloadEmote(emote)
        assertTrue("Expected Success on cache hit", secondResult is StickerResult.Success<*>)
        val secondAsset = (secondResult as StickerResult.Success<DownloadedEmoteAsset>).data
        assertArrayEquals(payload, secondAsset.bytes)

        // Only 1 network request was made to MockWebServer
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun downloadFromUrlReturnsBytes() = runTest {
        val payload = byteArrayOf(10, 20, 30, 40, 50)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
        )

        val url = mockWebServer.url("/raw-asset.bin").toString()
        val result = downloader.downloadFromUrl(url)

        assertTrue(result is StickerResult.Success<*>)
        assertArrayEquals(payload, (result as StickerResult.Success<ByteArray>).data)
    }

    @Test
    fun http404ReturnsResourceNotFound() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val emote = SevenTvEmote(
            id = "missing",
            name = "Missing",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/missing/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        val emoteResult = downloader.downloadEmote(emote)
        assertTrue(emoteResult is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (emoteResult as StickerResult.Failure).error)

        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        val urlResult = downloader.downloadFromUrl(mockWebServer.url("/notfound").toString())
        assertTrue(urlResult is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (urlResult as StickerResult.Failure).error)
    }

    @Test
    fun http429ReturnsRateLimited() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val emote = SevenTvEmote(
            id = "ratelimited",
            name = "RateLimited",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/ratelimited/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.RateLimited, (result as StickerResult.Failure).error)
    }

    @Test
    fun http500ReturnsNetworkError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val emote = SevenTvEmote(
            id = "server_error",
            name = "ServerError",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/server_error/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue(error is StickerError.NetworkError)
        assertEquals(500, (error as StickerError.NetworkError).statusCode)
    }

    @Test
    fun exceeds15MbViaContentLengthFailsSafely() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("too large")
                .setHeader("Content-Length", (16 * 1024 * 1024).toString())
        )

        val emote = SevenTvEmote(
            id = "huge1",
            name = "HugeHeader",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/huge1/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Error should be ExceedsSizeLimit", error is StickerError.ExceedsSizeLimit)
        assertEquals(16 * 1024 * 1024L, (error as StickerError.ExceedsSizeLimit).actualBytes)

        val cacheFolder = File(tempDir, "7tv_emotes_cache")
        val cacheFiles = cacheFolder.listFiles() ?: emptyArray()
        assertTrue("No cached or tmp file should be retained", cacheFiles.isEmpty())
    }

    @Test
    fun exceeds15MbStreamingFailsSafely() = runTest {
        val chunk = ByteArray(1024 * 1024) { 0x42 }
        val buffer = Buffer()
        for (i in 0 until 15) {
            buffer.write(chunk)
        }
        buffer.write(ByteArray(2048) { 0x42 })

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(buffer, 64 * 1024)
        )

        val emote = SevenTvEmote(
            id = "huge2",
            name = "HugeStream",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/huge2/4x.webp").toString(),
            availableScales = listOf("4x.webp")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Error should be ExceedsSizeLimit", error is StickerError.ExceedsSizeLimit)
        assertTrue((error as StickerError.ExceedsSizeLimit).actualBytes > 15 * 1024 * 1024L)

        val cacheFolder = File(tempDir, "7tv_emotes_cache")
        val cacheFiles = cacheFolder.listFiles() ?: emptyArray()
        assertTrue("No partial or tmp file should remain in cache", cacheFiles.none { it.name.startsWith("huge2") })
    }

    @Test
    fun networkFailureReturnsNetworkError() = runTest {
        mockWebServer.shutdown() // forcibly kill server

        val emote = SevenTvEmote(
            id = "offline",
            name = "Offline",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "http://localhost:${mockWebServer.port}/offline/4x.webp",
            availableScales = listOf("4x.webp")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue(error is StickerError.NetworkError)
    }

    @Test
    fun resolvesFormatCorrectlyForGifAndWebp() = runTest {
        val gifPayload = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/gif")
                .setBody(Buffer().write(gifPayload))
        )

        val emoteGif = SevenTvEmote(
            id = "gif_emote",
            name = "DancingDuck",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/gif_emote/4x.gif").toString(),
            availableScales = listOf("4x.gif")
        )

        val result = downloader.downloadEmote(emoteGif)
        assertTrue(result is StickerResult.Success<*>)
        val asset = (result as StickerResult.Success<DownloadedEmoteAsset>).data
        assertEquals("GIF", asset.format)
        assertTrue(asset.isAnimated)
    }
}

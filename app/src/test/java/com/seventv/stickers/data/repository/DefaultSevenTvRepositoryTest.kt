package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import com.seventv.stickers.core.network.dto.SevenTvFileDto
import com.seventv.stickers.core.network.dto.SevenTvHostDto
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSevenTvRepositoryTest {

    private lateinit var fakeApi: FakeSevenTvApi
    private lateinit var fakeDownloader: FakeEmoteDownloader
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SevenTvRepository

    @Before
    fun setUp() {
        fakeApi = FakeSevenTvApi()
        fakeDownloader = FakeEmoteDownloader()
        repository = DefaultSevenTvRepository(
            api = fakeApi,
            downloader = fakeDownloader,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun searchEmotesSuccess() = runTest(testDispatcher) {
        val emoteDto = SevenTvEmoteDto(
            id = "emote1",
            name = "KEKW",
            animated = false,
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/emote1",
                files = listOf(SevenTvFileDto(name = "4x.webp", format = "WEBP"))
            )
        )
        fakeApi.searchEmotesHandler = { query, page, limit ->
            assertEquals("kekw", query)
            assertEquals(1, page)
            assertEquals(30, limit)
            Response.success(listOf(emoteDto))
        }

        val result = repository.searchEmotes(query = "kekw", page = 1, limit = 30)

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val emotes = (result as StickerResult.Success<List<SevenTvEmote>>).data
        assertEquals(1, emotes.size)
        val emote = emotes.first()
        assertEquals("emote1", emote.id)
        assertEquals("KEKW", emote.name)
        assertFalse(emote.isAnimated)
        assertEquals("https://cdn.7tv.app/emote/emote1/4x.webp", emote.bestAssetUrl)
    }

    @Test
    fun getEmoteSuccess() = runTest(testDispatcher) {
        val emoteDto = SevenTvEmoteDto(
            id = "emote2",
            name = "catJAM",
            animated = true,
            host = SevenTvHostDto(
                url = "https://cdn.7tv.app/emote/emote2",
                files = listOf(SevenTvFileDto(name = "4x.webp", format = "WEBP"))
            )
        )
        fakeApi.getEmoteHandler = { emoteId ->
            assertEquals("emote2", emoteId)
            Response.success(emoteDto)
        }

        val result = repository.getEmote("emote2")

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val emote = (result as StickerResult.Success<SevenTvEmote>).data
        assertEquals("emote2", emote.id)
        assertEquals("catJAM", emote.name)
        assertTrue(emote.isAnimated)
        assertEquals("https://cdn.7tv.app/emote/emote2/4x.webp", emote.bestAssetUrl)
    }

    @Test
    fun getEmoteSetSuccess() = runTest(testDispatcher) {
        val setDto = SevenTvEmoteSetDto(
            id = "set1",
            name = "Global Emotes",
            ownerName = "7TV Team",
            emotes = listOf(
                SevenTvEmoteDto(
                    id = "emote1",
                    name = "KEKW",
                    animated = false
                )
            )
        )
        fakeApi.getEmoteSetHandler = { setId ->
            assertEquals("set1", setId)
            Response.success(setDto)
        }

        val result = repository.getEmoteSet("set1")

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val set = (result as StickerResult.Success<SevenTvEmoteSet>).data
        assertEquals("set1", set.id)
        assertEquals("Global Emotes", set.name)
        assertEquals("7TV Team", set.ownerName)
        assertEquals(1, set.emotes.size)
        assertEquals("KEKW", set.emotes.first().name)
    }

    @Test
    fun http404ReturnsResourceNotFound() = runTest(testDispatcher) {
        fakeApi.getEmoteHandler = {
            errorResponse(404, "Not Found")
        }

        val result = repository.getEmote("missing")

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (result as StickerResult.Failure).error)
    }

    @Test
    fun http429ReturnsRateLimited() = runTest(testDispatcher) {
        fakeApi.searchEmotesHandler = { _, _, _ ->
            errorResponse(429, "Too Many Requests")
        }

        val result = repository.searchEmotes("spam")

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        assertEquals(StickerError.RateLimited, (result as StickerResult.Failure).error)
    }

    @Test
    fun http500ReturnsNetworkError() = runTest(testDispatcher) {
        fakeApi.getEmoteSetHandler = {
            errorResponse(500, "Internal Server Error")
        }

        val result = repository.getEmoteSet("broken_set")

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected NetworkError, got $error", error is StickerError.NetworkError)
        val networkError = error as StickerError.NetworkError
        assertEquals(500, networkError.statusCode)
    }

    @Test
    fun networkExceptionReturnsNetworkError() = runTest(testDispatcher) {
        fakeApi.searchEmotesHandler = { _, _, _ ->
            throw IOException("Socket timeout")
        }

        val result = repository.searchEmotes("timeout")

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected NetworkError, got $error", error is StickerError.NetworkError)
        val networkError = error as StickerError.NetworkError
        assertEquals("Socket timeout", networkError.message)
    }

    @Test
    fun downloadEmoteDelegatesToDownloader() = runTest(testDispatcher) {
        val emote = SevenTvEmote(
            id = "emote_dl",
            name = "PepeLaugh",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/emote_dl/4x.webp",
            availableScales = listOf("4x.webp")
        )
        val expectedAsset = DownloadedEmoteAsset(
            emoteId = "emote_dl",
            emoteName = "PepeLaugh",
            bytes = byteArrayOf(10, 20, 30),
            isAnimated = false,
            format = "WEBP"
        )

        fakeDownloader.downloadEmoteHandler = { dlEmote ->
            assertEquals(emote, dlEmote)
            StickerResult.Success(expectedAsset)
        }

        val result = repository.downloadEmote(emote)

        assertTrue(result is StickerResult.Success<*>)
        assertEquals(expectedAsset, (result as StickerResult.Success<DownloadedEmoteAsset>).data)
        assertEquals(emote, fakeDownloader.lastEmote)

        // Downloader failure pass-through
        fakeDownloader.downloadEmoteHandler = {
            StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val failResult = repository.downloadEmote(emote)
        assertTrue(failResult is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (failResult as StickerResult.Failure).error)
    }

    @Test
    fun emptyResponseBodyReturnsNetworkError() = runTest(testDispatcher) {
        fakeApi.getEmoteHandler = {
            Response.success(null)
        }

        val result = repository.getEmote("null_body")

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected NetworkError, got $error", error is StickerError.NetworkError)
        val networkError = error as StickerError.NetworkError
        assertEquals(200, networkError.statusCode)
        assertEquals("Empty response body", networkError.message)
    }

    @Test
    fun coroutineCancellationExceptionIsRethrown() = runTest(testDispatcher) {
        fakeApi.getEmoteHandler = {
            throw CancellationException("Job was cancelled")
        }

        try {
            repository.getEmote("cancelled")
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled", e.message)
        }
    }

    private fun <T> errorResponse(code: Int, message: String): Response<T> {
        val mediaType = "application/json".toMediaType()
        val body = "{\"error\": \"$message\"}".toResponseBody(mediaType)
        return Response.error(code, body)
    }

    private class FakeSevenTvApi : SevenTvApi {
        var searchEmotesHandler: (suspend (String, Int, Int) -> Response<List<SevenTvEmoteDto>>)? = null
        var getEmoteHandler: (suspend (String) -> Response<SevenTvEmoteDto>)? = null
        var getEmoteSetHandler: (suspend (String) -> Response<SevenTvEmoteSetDto>)? = null

        override suspend fun searchEmotes(query: String, page: Int, limit: Int): Response<List<SevenTvEmoteDto>> {
            return searchEmotesHandler?.invoke(query, page, limit)
                ?: Response.success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): Response<SevenTvEmoteDto> {
            return getEmoteHandler?.invoke(emoteId)
                ?: Response.success(SevenTvEmoteDto(id = emoteId, name = "default"))
        }

        override suspend fun getEmoteSet(setId: String): Response<SevenTvEmoteSetDto> {
            return getEmoteSetHandler?.invoke(setId)
                ?: Response.success(SevenTvEmoteSetDto(id = setId, name = "default"))
        }
    }

    private class FakeEmoteDownloader : EmoteDownloader {
        var downloadEmoteHandler: (suspend (SevenTvEmote) -> StickerResult<DownloadedEmoteAsset>)? = null
        var downloadFromUrlHandler: (suspend (String) -> StickerResult<ByteArray>)? = null
        var lastEmote: SevenTvEmote? = null

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            lastEmote = emote
            return downloadEmoteHandler?.invoke(emote)
                ?: StickerResult.Success(
                    DownloadedEmoteAsset(
                        emoteId = emote.id,
                        emoteName = emote.name,
                        bytes = byteArrayOf(1, 2, 3),
                        isAnimated = emote.isAnimated,
                        format = "WEBP"
                    )
                )
        }

        override suspend fun downloadFromUrl(url: String): StickerResult<ByteArray> {
            return downloadFromUrlHandler?.invoke(url) ?: StickerResult.Success(byteArrayOf(1, 2, 3))
        }
    }
}

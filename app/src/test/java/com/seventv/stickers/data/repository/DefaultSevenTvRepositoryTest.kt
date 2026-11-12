package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import com.seventv.stickers.core.network.dto.SevenTvFileDto
import com.seventv.stickers.core.network.dto.SevenTvGqlEmoteSetRefDto
import com.seventv.stickers.core.network.dto.SevenTvGqlEmotesConnection
import com.seventv.stickers.core.network.dto.SevenTvGqlEmotesData
import com.seventv.stickers.core.network.dto.SevenTvGqlRequest
import com.seventv.stickers.core.network.dto.SevenTvGqlResponse
import com.seventv.stickers.core.network.dto.SevenTvGqlUserConnectionDto
import com.seventv.stickers.core.network.dto.SevenTvGqlUserDto
import com.seventv.stickers.core.network.dto.SevenTvGqlUsersData
import com.seventv.stickers.core.network.dto.SevenTvHostDto
import com.seventv.stickers.core.network.model.ChannelSearchResult
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
import org.junit.Assert.assertNotNull
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
        fakeApi.searchEmotesHandler = { request ->
            assertTrue(request.query.contains("SearchEmotes"))
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlEmotesData(
                        emotes = SevenTvGqlEmotesConnection(
                            count = 1,
                            items = listOf(emoteDto)
                        )
                    )
                )
            )
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
    fun searchEmotesWithCategorySendsFilterVariable() = runTest(testDispatcher) {
        var capturedRequest: SevenTvGqlRequest? = null
        fakeApi.searchEmotesHandler = { request ->
            capturedRequest = request
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlEmotesData(
                        emotes = SevenTvGqlEmotesConnection(count = 0, items = emptyList())
                    )
                )
            )
        }

        repository.searchEmotes(query = "", category = "TRENDING_DAY")

        assertNotNull(capturedRequest)
        val filterJson = capturedRequest?.variables?.get("filter")
        assertNotNull(filterJson)
        assertTrue(filterJson.toString().contains("TRENDING_DAY"))
    }

    @Test
    fun getStreamerEmoteSetDirectSuccess() = runTest(testDispatcher) {
        val setDto = SevenTvEmoteSetDto(
            id = "set_direct",
            name = "Direct Set",
            ownerName = "Streamer",
            emotes = listOf(SevenTvEmoteDto(id = "e1", name = "Pog"))
        )
        fakeApi.getEmoteSetHandler = { setId ->
            assertEquals("set_direct", setId)
            Response.success(setDto)
        }

        val result = repository.getStreamerEmoteSet("set_direct")

        assertTrue(result is StickerResult.Success<*>)
        val set = (result as StickerResult.Success<SevenTvEmoteSet>).data
        assertEquals("set_direct", set.id)
        assertEquals("Direct Set", set.name)
    }

    @Test
    fun getStreamerEmoteSetUserSearchSuccess() = runTest(testDispatcher) {
        // Direct set call fails (query is username, not set id)
        fakeApi.getEmoteSetHandler = { errorResponse(404, "Set not found") }

        // User search returns streamer with active emote set
        fakeApi.searchUsersHandler = { _ ->
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlUsersData(
                        users = listOf(
                            SevenTvGqlUserDto(
                                id = "u_ibai",
                                username = "ibai",
                                displayName = "Ibai",
                                emoteSets = listOf(SevenTvGqlEmoteSetRefDto(id = "set_ibai_1", name = "Ibai Set"))
                            )
                        )
                    )
                )
            )
        }

        // Subsequent getEmoteSet with resolved set_ibai_1 succeeds
        fakeApi.getEmoteSetHandler = { setId ->
            if (setId == "set_ibai_1") {
                Response.success(
                    SevenTvEmoteSetDto(
                        id = "set_ibai_1",
                        name = "Ibai Set",
                        ownerName = "Ibai",
                        emotes = listOf(SevenTvEmoteDto(id = "e1", name = "Sisi"))
                    )
                )
            } else {
                errorResponse(404, "Not found")
            }
        }

        val result = repository.getStreamerEmoteSet("ibai")

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val set = (result as StickerResult.Success<SevenTvEmoteSet>).data
        assertEquals("set_ibai_1", set.id)
        assertEquals("Ibai Set", set.name)
        assertEquals(1, set.emotes.size)
        assertEquals("Sisi", set.emotes.first().name)
    }

    @Test
    fun getStreamerEmoteSetFallbackEmotesSuccess() = runTest(testDispatcher) {
        // Direct set call fails
        fakeApi.getEmoteSetHandler = { errorResponse(404, "Set not found") }
        // User search returns empty
        fakeApi.searchUsersHandler = {
            Response.success(SevenTvGqlResponse(data = SevenTvGqlUsersData(users = emptyList())))
        }
        // Fallback emote search finds matching emotes
        fakeApi.searchEmotesHandler = {
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlEmotesData(
                        emotes = SevenTvGqlEmotesConnection(
                            count = 1,
                            items = listOf(SevenTvEmoteDto(id = "e1", name = "cat"))
                        )
                    )
                )
            )
        }

        val result = repository.getStreamerEmoteSet("cat")

        assertTrue(result is StickerResult.Success<*>)
        val set = (result as StickerResult.Success<SevenTvEmoteSet>).data
        assertEquals("cat", set.id)
        assertEquals("cat", set.name)
        assertEquals(1, set.emotes.size)
    }

    @Test
    fun getChannelDataMultipleEmoteSetsSelectsConnectionActiveSet() = runTest(testDispatcher) {
        fakeApi.getEmoteSetHandler = { errorResponse(404, "Set not found") }

        fakeApi.searchUsersHandler = { _ ->
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlUsersData(
                        users = listOf(
                            SevenTvGqlUserDto(
                                id = "u_streamer",
                                username = "streamer",
                                displayName = "StreamerChannel",
                                connections = listOf(
                                    SevenTvGqlUserConnectionDto(
                                        platform = "TWITCH",
                                        username = "streamer",
                                        emoteSetId = "set_stream_active"
                                    )
                                ),
                                emoteSets = listOf(
                                    SevenTvGqlEmoteSetRefDto(id = "set_other", name = "Other Emotes", emoteCount = 30),
                                    SevenTvGqlEmoteSetRefDto(id = "set_stream_active", name = "Main Stream Set", emoteCount = 800),
                                    SevenTvGqlEmoteSetRefDto(id = "set_empty", name = "Empty Set", emoteCount = 0)
                                )
                            )
                        )
                    )
                )
            )
        }

        fakeApi.getEmoteSetHandler = { setId ->
            if (setId == "set_stream_active") {
                Response.success(
                    SevenTvEmoteSetDto(
                        id = "set_stream_active",
                        name = "Main Stream Set",
                        ownerName = "StreamerChannel",
                        emotes = listOf(SevenTvEmoteDto(id = "e1", name = "PogU"))
                    )
                )
            } else {
                errorResponse(404, "Not found")
            }
        }

        val result = repository.getChannelData("streamer")

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val channel = (result as StickerResult.Success<ChannelSearchResult>).data
        assertEquals("StreamerChannel", channel.streamerName)
        assertEquals("streamer", channel.username)
        // Empty set filtered out, leaving 2 sets
        assertEquals(2, channel.availableSets.size)
        assertEquals("set_stream_active", channel.activeSet.id)

        val activeSummary = channel.availableSets.first { it.id == "set_stream_active" }
        assertTrue(activeSummary.isActive)
        val otherSummary = channel.availableSets.first { it.id == "set_other" }
        assertFalse(otherSummary.isActive)
    }

    @Test
    fun getChannelDataMultipleEmoteSetsFallsBackToHighestCountWhenNoConnection() = runTest(testDispatcher) {
        fakeApi.getEmoteSetHandler = { errorResponse(404, "Set not found") }

        fakeApi.searchUsersHandler = { _ ->
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlUsersData(
                        users = listOf(
                            SevenTvGqlUserDto(
                                id = "u_streamer",
                                username = "streamer",
                                displayName = "StreamerChannel",
                                connections = emptyList(),
                                emoteSets = listOf(
                                    SevenTvGqlEmoteSetRefDto(id = "set_small", name = "Small Set", emoteCount = 10),
                                    SevenTvGqlEmoteSetRefDto(id = "set_huge", name = "Huge Set", emoteCount = 950)
                                )
                            )
                        )
                    )
                )
            )
        }

        fakeApi.getEmoteSetHandler = { setId ->
            if (setId == "set_huge") {
                Response.success(
                    SevenTvEmoteSetDto(
                        id = "set_huge",
                        name = "Huge Set",
                        ownerName = "StreamerChannel",
                        emotes = listOf(SevenTvEmoteDto(id = "e_huge", name = "HugeEmote"))
                    )
                )
            } else {
                errorResponse(404, "Not found")
            }
        }

        val result = repository.getChannelData("streamer")

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val channel = (result as StickerResult.Success<ChannelSearchResult>).data
        assertEquals("set_huge", channel.activeSet.id)
        val hugeSummary = channel.availableSets.first { it.id == "set_huge" }
        assertTrue(hugeSummary.isActive)
    }

    @Test
    fun getChannelDataUsesLimit15AndPrioritizesExactMatchOverPartialFirstResult() = runTest(testDispatcher) {
        fakeApi.getEmoteSetHandler = { errorResponse(404, "Set not found") }

        var capturedLimit: Int? = null
        fakeApi.searchUsersHandler = { request ->
            capturedLimit = request.variables?.get("limit")?.toString()?.toIntOrNull()
            Response.success(
                SevenTvGqlResponse(
                    data = SevenTvGqlUsersData(
                        users = listOf(
                            SevenTvGqlUserDto(
                                id = "u_unrelated",
                                username = "other_streamer_fan",
                                displayName = "OtherStreamerFan",
                                emoteSets = listOf(
                                    SevenTvGqlEmoteSetRefDto(id = "set_unrelated", name = "Main", emoteCount = 10)
                                )
                            ),
                            SevenTvGqlUserDto(
                                id = "u_partial",
                                username = "illojuan_clips",
                                displayName = "IlloJuanClips",
                                emoteSets = listOf(
                                    SevenTvGqlEmoteSetRefDto(id = "set_partial", name = "Main", emoteCount = 10)
                                )
                            ),
                            SevenTvGqlUserDto(
                                id = "u_exact",
                                username = "illojuan_tv",
                                displayName = "IlloJuan",
                                emoteSets = listOf(
                                    SevenTvGqlEmoteSetRefDto(id = "set_exact", name = "Main", emoteCount = 25)
                                )
                            )
                        )
                    )
                )
            )
        }

        fakeApi.getEmoteSetHandler = { setId ->
            if (setId == "set_exact") {
                Response.success(
                    SevenTvEmoteSetDto(
                        id = "set_exact",
                        name = "Main",
                        emotes = listOf(SevenTvEmoteDto(id = "e1", name = "KEKW", animated = false))
                    )
                )
            } else {
                errorResponse(404, "Not found")
            }
        }

        val result = repository.getChannelData("illojuan")

        assertEquals(15, capturedLimit)
        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val channel = (result as StickerResult.Success<ChannelSearchResult>).data
        assertEquals("IlloJuan", channel.streamerName)
        assertEquals("set_exact", channel.activeSet.id)
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
        fakeApi.searchEmotesHandler = { _ ->
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
        fakeApi.searchEmotesHandler = { _ ->
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
    fun downloadEmoteGifDelegatesToDownloader() = runTest(testDispatcher) {
        val gifEmote = SevenTvEmote(
            id = "catjam_gif",
            name = "catJAM",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/catjam_gif/4x.webp",
            availableScales = listOf("4x.webp", "4x.gif"),
            gifUrl = "https://cdn.7tv.app/emote/catjam_gif/4x.gif"
        )
        val expectedGifBytes = byteArrayOf(71, 73, 70, 56, 57, 97) // "GIF89a"
        var requestedUrl: String? = null
        fakeDownloader.downloadFromUrlHandler = { url ->
            requestedUrl = url
            StickerResult.Success(expectedGifBytes)
        }

        val result = repository.downloadEmoteGif(gifEmote)
        assertTrue(result is StickerResult.Success<*>)
        assertEquals(expectedGifBytes, (result as StickerResult.Success<ByteArray>).data)
        assertEquals("https://cdn.7tv.app/emote/catjam_gif/4x.gif", requestedUrl)
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
        var searchEmotesHandler: (suspend (SevenTvGqlRequest) -> Response<SevenTvGqlResponse<SevenTvGqlEmotesData>>)? = null
        var searchUsersHandler: (suspend (SevenTvGqlRequest) -> Response<SevenTvGqlResponse<SevenTvGqlUsersData>>)? = null
        var getEmoteHandler: (suspend (String) -> Response<SevenTvEmoteDto>)? = null
        var getEmoteSetHandler: (suspend (String) -> Response<SevenTvEmoteSetDto>)? = null

        override suspend fun searchEmotes(request: SevenTvGqlRequest): Response<SevenTvGqlResponse<SevenTvGqlEmotesData>> {
            return searchEmotesHandler?.invoke(request)
                ?: Response.success(SevenTvGqlResponse(data = SevenTvGqlEmotesData()))
        }

        override suspend fun searchUsers(request: SevenTvGqlRequest): Response<SevenTvGqlResponse<SevenTvGqlUsersData>> {
            return searchUsersHandler?.invoke(request)
                ?: Response.success(SevenTvGqlResponse(data = SevenTvGqlUsersData()))
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

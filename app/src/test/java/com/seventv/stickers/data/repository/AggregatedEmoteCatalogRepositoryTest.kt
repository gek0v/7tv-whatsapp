package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatedEmoteCatalogRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun getUnifiedChannelEmotes_aggregatesAllProvidersConcurrently() = runTest(testDispatcher) {
        val sevenTv = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            channelResult = {
                delay(50)
                StickerResult.Success(listOf(sampleEmote("7tv1", "Pepega", EmoteProvider.SEVENTV, "Ibai")))
            }
        }
        val bttv = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            channelResult = {
                delay(50)
                StickerResult.Success(listOf(sampleEmote("bttv1", "catJAM", EmoteProvider.BETTERTTV, "Ibai")))
            }
        }
        val ffz = FakeProviderRepo(EmoteProvider.FRANKERFACEZ).apply {
            channelResult = {
                delay(50)
                StickerResult.Success(listOf(sampleEmote("ffz1", "KEKW", EmoteProvider.FRANKERFACEZ, "Ibai")))
            }
        }
        val twitch = FakeProviderRepo(EmoteProvider.TWITCH).apply {
            channelResult = {
                delay(50)
                StickerResult.Success(listOf(sampleEmote("tw1", "ibaiL", EmoteProvider.TWITCH, "Ibai")))
            }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(sevenTv, bttv, ffz, twitch),
            dispatcher = testDispatcher
        )

        val result = aggregated.getUnifiedChannelEmotes("ibai")
        assertTrue(result is StickerResult.Success)
        val data = (result as StickerResult.Success).data
        assertEquals("Ibai", data.channelName)
        assertEquals(4, data.totalEmotesCount)
        assertEquals(4, data.emotesByProvider.size)
        assertTrue(data.failedProviders.isEmpty())
    }

    @Test
    fun getUnifiedChannelEmotes_toleratesPartialProviderFailures() = runTest(testDispatcher) {
        val sevenTv = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            channelResult = {
                StickerResult.Success(listOf(sampleEmote("7tv1", "GIGACHAD", EmoteProvider.SEVENTV, "IlloJuan")))
            }
        }
        val bttvFailing = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            channelResult = { StickerResult.Failure(StickerError.ResourceNotFound) }
        }
        val ffzThrowing = FakeProviderRepo(EmoteProvider.FRANKERFACEZ).apply {
            channelResult = { throw RuntimeException("Timeout") }
        }
        val twitch = FakeProviderRepo(EmoteProvider.TWITCH).apply {
            channelResult = {
                StickerResult.Success(listOf(sampleEmote("tw1", "illo1", EmoteProvider.TWITCH, "IlloJuan")))
            }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(sevenTv, bttvFailing, ffzThrowing, twitch),
            dispatcher = testDispatcher
        )

        val result = aggregated.getUnifiedChannelEmotes("illojuan")
        assertTrue(result is StickerResult.Success)
        val data = (result as StickerResult.Success).data
        assertEquals(2, data.totalEmotesCount)
        assertEquals(setOf(EmoteProvider.SEVENTV, EmoteProvider.TWITCH), data.emotesByProvider.keys)
        assertEquals(setOf(EmoteProvider.BETTERTTV, EmoteProvider.FRANKERFACEZ), data.failedProviders)
    }

    @Test
    fun getUnifiedChannelEmotes_returnsFailureWhenAllProvidersFail() = runTest(testDispatcher) {
        val p1 = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            channelResult = { StickerResult.Failure(StickerError.ResourceNotFound) }
        }
        val p2 = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            channelResult = { StickerResult.Failure(StickerError.NetworkError(503, "Down")) }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(p1, p2),
            dispatcher = testDispatcher
        )

        val result = aggregated.getUnifiedChannelEmotes("nobody")
        assertTrue(result is StickerResult.Failure)
    }

    @Test
    fun getUnifiedChannelEmotes_blankQueryReturnsResourceNotFound() = runTest(testDispatcher) {
        val p1 = FakeProviderRepo(EmoteProvider.SEVENTV)
        val aggregated = DefaultAggregatedEmoteCatalogRepository(listOf(p1), dispatcher = testDispatcher)

        val result = aggregated.getUnifiedChannelEmotes("   ")
        assertEquals(StickerError.ResourceNotFound, (result as StickerResult.Failure).error)
    }

    @Test
    fun searchAllProviders_aggregatesAndToleratesPartialFailure() = runTest(testDispatcher) {
        val sevenTv = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            searchResult = {
                StickerResult.Success(listOf(sampleEmote("1", "pepeLaugh", EmoteProvider.SEVENTV)))
            }
        }
        val bttv = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            searchResult = { StickerResult.Failure(StickerError.RateLimited) }
        }
        val ffz = FakeProviderRepo(EmoteProvider.FRANKERFACEZ).apply {
            searchResult = {
                StickerResult.Success(listOf(sampleEmote("2", "pepeD", EmoteProvider.FRANKERFACEZ)))
            }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(sevenTv, bttv, ffz),
            dispatcher = testDispatcher
        )

        val result = aggregated.searchAllProviders("pepe")
        assertTrue(result is StickerResult.Success)
        val map = (result as StickerResult.Success).data
        assertEquals(2, map.size)
        assertEquals("pepeLaugh", map[EmoteProvider.SEVENTV]?.first()?.name)
        assertEquals("pepeD", map[EmoteProvider.FRANKERFACEZ]?.first()?.name)
    }

    @Test
    fun downloadEmote_routesToMatchingProviderRepository() = runTest(testDispatcher) {
        val bttvRepo = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            downloadResult = { emote ->
                StickerResult.Success(
                    DownloadedEmoteAsset(emote.id, emote.name, byteArrayOf(9, 8, 7), emote.isAnimated, "WEBP")
                )
            }
        }
        val ffzRepo = FakeProviderRepo(EmoteProvider.FRANKERFACEZ)

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(bttvRepo, ffzRepo),
            dispatcher = testDispatcher
        )

        val emote = sampleEmote("b1", "catJAM", EmoteProvider.BETTERTTV)
        val result = aggregated.downloadEmote(emote)
        assertTrue(result is StickerResult.Success)
        assertEquals("b1", (result as StickerResult.Success).data.emoteId)
        assertEquals(1, bttvRepo.downloadCalls)
        assertEquals(0, ffzRepo.downloadCalls)
    }

    @Test
    fun searchAllProvidersProgressive_emitsIncrementalResultsAndSkipsUnrequestedProviders() = runTest(testDispatcher) {
        var bttvCalls = 0
        val sevenTv = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            searchResult = {
                delay(20)
                StickerResult.Success(listOf(sampleEmote("7_1", "pepeFast", EmoteProvider.SEVENTV)))
            }
        }
        val bttv = FakeProviderRepo(EmoteProvider.BETTERTTV).apply {
            searchResult = {
                bttvCalls++
                delay(500)
                StickerResult.Success(listOf(sampleEmote("b_1", "pepeSlow", EmoteProvider.BETTERTTV)))
            }
        }
        val ffz = FakeProviderRepo(EmoteProvider.FRANKERFACEZ).apply {
            searchResult = {
                delay(100)
                StickerResult.Success(listOf(sampleEmote("f_1", "pepeMid", EmoteProvider.FRANKERFACEZ)))
            }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(sevenTv, bttv, ffz),
            dispatcher = testDispatcher
        )

        // Progressive emission with all providers should emit incrementally (3 emissions)
        val emissions = aggregated.searchAllProvidersProgressive("pepe").toList()
        assertEquals(3, emissions.size)
        val firstMap = (emissions.first() as StickerResult.Success).data
        assertEquals(setOf(EmoteProvider.SEVENTV), firstMap.keys)
        val lastMap = (emissions.last() as StickerResult.Success).data
        assertEquals(setOf(EmoteProvider.SEVENTV, EmoteProvider.FRANKERFACEZ, EmoteProvider.BETTERTTV), lastMap.keys)

        // Provider-filtered search should skip unrequested providers (BTTV not called again)
        val callsBefore = bttvCalls
        val filteredEmissions = aggregated.searchAllProvidersProgressive(
            query = "pepe2",
            targetProviders = setOf(EmoteProvider.SEVENTV)
        ).toList()
        assertEquals(1, filteredEmissions.size)
        assertEquals(callsBefore, bttvCalls)
        assertEquals(setOf(EmoteProvider.SEVENTV), (filteredEmissions.first() as StickerResult.Success).data.keys)
    }

    @Test
    fun searchAllProviders_andPrefetch_usesInMemoryCacheOnRepeatCalls() = runTest(testDispatcher) {
        var callCount = 0
        val sevenTv = FakeProviderRepo(EmoteProvider.SEVENTV).apply {
            searchResult = {
                callCount++
                StickerResult.Success(listOf(sampleEmote("1", "cachedEmote", EmoteProvider.SEVENTV)))
            }
        }

        val aggregated = DefaultAggregatedEmoteCatalogRepository(
            providers = listOf(sevenTv),
            dispatcher = testDispatcher
        )

        val res1 = aggregated.searchAllProviders("kekw", page = 1, limit = 50)
        val res2 = aggregated.searchAllProviders("KEKW", page = 1, limit = 50)
        assertTrue(res1 is StickerResult.Success && res2 is StickerResult.Success)
        assertEquals(1, callCount)

        aggregated.prefetchSearchPage("kekw", page = 2, limit = 50)
        assertEquals(2, callCount)
        val page2 = aggregated.searchAllProviders("kekw", page = 2, limit = 50)
        assertTrue(page2 is StickerResult.Success)
        assertEquals(2, callCount)
    }

    private fun sampleEmote(
        id: String,
        name: String,
        provider: EmoteProvider,
        owner: String? = null
    ) = UnifiedEmote(
        id = id,
        name = name,
        provider = provider,
        isAnimated = false,
        bestAssetUrl = "https://cdn.example.com/$id.webp",
        previewUrl = "https://cdn.example.com/$id.webp",
        originalOwner = owner
    )

    private class FakeProviderRepo(
        override val provider: EmoteProvider
    ) : EmoteProviderRepository {
        var searchResult: suspend () -> StickerResult<List<UnifiedEmote>> = { StickerResult.Success(emptyList()) }
        var channelResult: suspend () -> StickerResult<List<UnifiedEmote>> = { StickerResult.Success(emptyList()) }
        var downloadResult: suspend (UnifiedEmote) -> StickerResult<DownloadedEmoteAsset> = {
            StickerResult.Failure(StickerError.ResourceNotFound)
        }
        var downloadCalls = 0

        override suspend fun searchEmotes(query: String, page: Int, limit: Int): StickerResult<List<UnifiedEmote>> {
            return searchResult()
        }

        override suspend fun getChannelEmotes(channelQuery: String): StickerResult<List<UnifiedEmote>> {
            return channelResult()
        }

        override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
            downloadCalls++
            return downloadResult(emote)
        }
    }
}

package com.seventv.stickers.data.repository

import android.graphics.Bitmap
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.media.DefaultStickerMediaPipeline
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.media.model.StickerSpecs
import com.seventv.stickers.core.network.downloader.DefaultEmoteDownloader
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.ChannelSearchResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.core.network.model.UnifiedEmote
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.di.DefaultAppContainer
import com.seventv.stickers.ui.channel.ChannelFilter
import com.seventv.stickers.ui.channel.ChannelLoadState
import com.seventv.stickers.ui.channel.ChannelProviderFilter
import com.seventv.stickers.ui.channel.ChannelViewModel
import com.seventv.stickers.ui.components.EmoteCardDefaults
import com.seventv.stickers.ui.explore.ExploreViewModel
import com.seventv.stickers.ui.explore.SearchResultsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MultiProviderIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        cacheDir = tempFolder.newFolder("cache")
        outputDir = tempFolder.newFolder("output")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppDatabase.setTestInstance(null)
        mockWebServer.shutdown()
    }

    private fun samplePngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        return baos.toByteArray()
    }

    @Test
    fun downloader_detectsPngFormatWithoutExtensionAndIsolatesProviderCache() = runTest {
        val pngBytes = samplePngBytes()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(pngBytes))
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/webp")
                .setBody(Buffer().write(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)))
        )

        val downloader = DefaultEmoteDownloader(cacheDir, OkHttpClient(), testDispatcher)

        val ffzEmote = UnifiedEmote(
            id = "42",
            name = "KEKW",
            provider = EmoteProvider.FRANKERFACEZ,
            isAnimated = false,
            bestAssetUrl = mockWebServer.url("/emoticon/42/4").toString()
        )
        val bttvEmote = UnifiedEmote(
            id = "42",
            name = "BttvEmote",
            provider = EmoteProvider.BETTERTTV,
            isAnimated = false,
            bestAssetUrl = mockWebServer.url("/emote/42/3x.webp").toString()
        )

        val ffzFirst = downloader.downloadEmote(ffzEmote)
        assertTrue(ffzFirst is StickerResult.Success)
        assertEquals("PNG", (ffzFirst as StickerResult.Success).data.format)

        // Cache hit without HTTP Content-Type should still sniff PNG magic bytes
        val ffzSecond = downloader.downloadEmote(ffzEmote)
        assertTrue(ffzSecond is StickerResult.Success)
        assertEquals("PNG", (ffzSecond as StickerResult.Success).data.format)

        // Same ID "42" on BTTV should NOT collide with FFZ cache
        val bttvRes = downloader.downloadEmote(bttvEmote)
        assertTrue(bttvRes is StickerResult.Success)
        assertEquals("WEBP", (bttvRes as StickerResult.Success).data.format)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun bridge_convertsUnifiedEmotesFromBttvAndFfzIntoCompliantStickers() = runTest(testDispatcher) {
        val pngBytes = samplePngBytes()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(pngBytes))
        )

        val downloader = DefaultEmoteDownloader(cacheDir, OkHttpClient(), testDispatcher)
        val pipeline = DefaultStickerMediaPipeline(dispatcher = testDispatcher)
        val bridge = EmoteStickerBridge(
            repository = DummySevenTvRepo(),
            mediaPipeline = pipeline,
            emoteDownloader = downloader
        )

        val ffzEmote = UnifiedEmote(
            id = "999",
            name = "LULW",
            provider = EmoteProvider.FRANKERFACEZ,
            isAnimated = false,
            bestAssetUrl = mockWebServer.url("/emoticon/999/4").toString()
        )

        val result = bridge.convertEmoteToSticker(ffzEmote, outputDir)
        assertTrue("Expected Success, got $result", result is StickerResult.Success)
        val processed = (result as StickerResult.Success).data
        assertEquals(StickerSpecs.STICKER_SIZE, processed.width)
        assertEquals(StickerSpecs.STICKER_SIZE, processed.height)
        assertFalse(processed.isAnimated)
        assertTrue(processed.byteSize <= StickerSpecs.STATIC_MAX_BYTES)
        assertTrue(processed.file.exists())
    }

    @Test
    fun channelViewModel_federatedSearchAndProviderFilters() = runTest(testDispatcher) {
        val fakeAggregated = FakeAggregatedRepo().apply {
            channelResult = StickerResult.Success(
                UnifiedChannelResult(
                    channelName = "IlloJuan",
                    avatarUrl = null,
                    emotesByProvider = mapOf(
                        EmoteProvider.SEVENTV to listOf(
                            unified("7tv_1", "illoHappy", EmoteProvider.SEVENTV, animated = false),
                            unified("7tv_2", "illoDance", EmoteProvider.SEVENTV, animated = true)
                        ),
                        EmoteProvider.BETTERTTV to listOf(
                            unified("bttv_1", "catJAM", EmoteProvider.BETTERTTV, animated = true)
                        ),
                        EmoteProvider.FRANKERFACEZ to listOf(
                            unified("ffz_1", "KEKW", EmoteProvider.FRANKERFACEZ, animated = false)
                        )
                    ),
                    totalEmotesCount = 4
                )
            )
        }

        val vm = ChannelViewModel(
            sevenTvRepository = DummySevenTvRepo(),
            stickerPackRepository = DummyPackRepo(),
            aggregatedRepository = fakeAggregated
        )

        vm.onSearchStreamer("illojuan")
        advanceUntilIdle()

        val load = vm.uiState.value.loadState
        assertTrue(load is ChannelLoadState.Success)
        val success = load as ChannelLoadState.Success
        assertEquals("IlloJuan", success.streamerName)
        assertEquals(4, success.allEmotes.size)
        assertEquals(3, success.emotesByProvider.size)

        // Filter by BTTV
        vm.onProviderFilterSelected(ChannelProviderFilter.BETTERTTV)
        assertEquals(listOf("catJAM"), vm.getFilteredEmotes().map { it.name })

        // Filter by 7TV + STATIC
        vm.onProviderFilterSelected(ChannelProviderFilter.SEVENTV)
        vm.onFilterSelected(ChannelFilter.STATIC)
        assertEquals(listOf("illoHappy"), vm.getFilteredEmotes().map { it.name })

        // Filter by ALL + ANIMATED
        vm.onProviderFilterSelected(ChannelProviderFilter.ALL)
        vm.onFilterSelected(ChannelFilter.ANIMATED)
        assertEquals(2, vm.getFilteredEmotes().size)
    }

    @Test
    fun exploreViewModel_multiProviderSearchAndProviderFilter() = runTest(testDispatcher) {
        val fakeAggregated = FakeAggregatedRepo().apply {
            searchResult = StickerResult.Success(
                mapOf(
                    EmoteProvider.SEVENTV to listOf(unified("1", "pepe7", EmoteProvider.SEVENTV)),
                    EmoteProvider.BETTERTTV to listOf(unified("2", "pepeB", EmoteProvider.BETTERTTV)),
                    EmoteProvider.FRANKERFACEZ to listOf(unified("3", "pepeF", EmoteProvider.FRANKERFACEZ))
                )
            )
        }

        val vm = ExploreViewModel(
            sevenTvRepository = DummySevenTvRepo(),
            stickerPackRepository = DummyPackRepo(),
            aggregatedRepository = fakeAggregated
        )

        vm.onQueryChanged("pepe")
        advanceTimeBy(ExploreViewModel.SEARCH_DEBOUNCE_MS + 1)
        runCurrent()

        val results = vm.uiState.value.searchResults
        assertTrue(results is SearchResultsState.Success)
        assertEquals(3, vm.uiState.value.filteredEmotes.size)

        vm.onProviderFilterSelected(ChannelProviderFilter.FRANKERFACEZ)
        assertEquals(listOf("pepeF"), vm.uiState.value.filteredEmotes.map { it.name })
    }

    @Test
    fun emoteCardDefaults_exposesDistinctBadgesAndColorsPerProvider() {
        assertEquals("7TV", EmoteCardDefaults.providerBadgeText(EmoteProvider.SEVENTV))
        assertEquals("BTTV", EmoteCardDefaults.providerBadgeText(EmoteProvider.BETTERTTV))
        assertEquals("FFZ", EmoteCardDefaults.providerBadgeText(EmoteProvider.FRANKERFACEZ))
        assertEquals("Twitch", EmoteCardDefaults.providerBadgeText(EmoteProvider.TWITCH))

        val colors = EmoteProvider.entries.map { EmoteCardDefaults.providerColor(it) }.toSet()
        assertEquals(4, colors.size)
        assertNotEquals(
            EmoteCardDefaults.providerColor(EmoteProvider.SEVENTV),
            EmoteCardDefaults.providerColor(EmoteProvider.BETTERTTV)
        )
    }

    @Test
    fun appContainer_providesAggregatedEmoteCatalogRepositorySingleton() {
        val context = RuntimeEnvironment.getApplication()
        val container = DefaultAppContainer(context)
        assertNotNull(container.aggregatedEmoteCatalogRepository)
        assertSame(container.aggregatedEmoteCatalogRepository, container.aggregatedEmoteCatalogRepository)
    }

    private fun unified(
        id: String,
        name: String,
        provider: EmoteProvider,
        animated: Boolean = false
    ) = UnifiedEmote(
        id = id,
        name = name,
        provider = provider,
        isAnimated = animated,
        bestAssetUrl = "https://cdn.example.com/$id.webp"
    )

    private class FakeAggregatedRepo : AggregatedEmoteCatalogRepository {
        var searchResult: StickerResult<Map<EmoteProvider, List<UnifiedEmote>>> =
            StickerResult.Success(emptyMap())
        var channelResult: StickerResult<UnifiedChannelResult> =
            StickerResult.Success(UnifiedChannelResult("Test", null, emptyMap(), 0))

        override suspend fun searchAllProviders(
            query: String,
            page: Int,
            limit: Int
        ): StickerResult<Map<EmoteProvider, List<UnifiedEmote>>> = searchResult

        override suspend fun getUnifiedChannelEmotes(twitchChannel: String): StickerResult<UnifiedChannelResult> =
            channelResult

        override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
            return StickerResult.Success(DownloadedEmoteAsset(emote.id, emote.name, byteArrayOf(), emote.isAnimated, "WEBP"))
        }
    }

    private class DummySevenTvRepo : SevenTvRepository {
        override suspend fun searchEmotes(query: String, page: Int, limit: Int, category: String?) =
            StickerResult.Success(emptyList<SevenTvEmote>())

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> =
            throw UnsupportedOperationException()

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> =
            throw UnsupportedOperationException()

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> =
            throw UnsupportedOperationException()
    }

    private class DummyPackRepo : StickerPackRepository {
        override fun observeAllPacks(): Flow<List<StickerPack>> = MutableStateFlow(emptyList())
        override suspend fun getPack(identifier: String) = throw UnsupportedOperationException()
        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((Int, Int) -> Unit)?,
            originSetId: String?,
            originStreamerName: String?
        ) = StickerResult.Success(emptyList<StickerPack>())
        override suspend fun deletePack(identifier: String) = StickerResult.Success(Unit)
        override suspend fun deletePacks(identifiers: List<String>) = StickerResult.Success(Unit)
        override suspend fun renamePack(identifier: String, newName: String) = StickerResult.Success(Unit)
        override suspend fun mergePacks(sourcePackIdentifiers: List<String>, basePackName: String) =
            StickerResult.Success(emptyList<StickerPack>())
        override suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String) = StickerResult.Success(Unit)
        override suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>) = StickerResult.Success(Unit)
        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>) = StickerResult.Success(Unit)
        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean) = StickerResult.Success(Unit)
        override suspend fun syncWhitelistStatus(context: android.content.Context, packIdentifier: String) = StickerResult.Success(false)
        override suspend fun checkForPackUpdates(packIdentifier: String) = StickerResult.Success(emptyList<SevenTvEmote>())
    }
}

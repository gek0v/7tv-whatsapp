package com.seventv.stickers.benchmark

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvNetworkFactory
import com.seventv.stickers.core.network.downloader.DefaultEmoteDownloader
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.data.repository.BttvProviderRepository
import com.seventv.stickers.data.repository.DefaultAggregatedEmoteCatalogRepository
import com.seventv.stickers.data.repository.DefaultSevenTvRepository
import com.seventv.stickers.data.repository.FfzProviderRepository
import com.seventv.stickers.data.repository.SevenTvProviderRepository
import com.seventv.stickers.data.repository.TwitchIdResolver
import com.seventv.stickers.data.repository.TwitchProviderRepository
import com.seventv.stickers.ui.channel.ChannelProviderFilter
import com.seventv.stickers.ui.explore.EmoteFilter
import com.seventv.stickers.ui.explore.ExploreUiState
import com.seventv.stickers.ui.explore.SearchResultsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

class EmoteSearchBenchmarkTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tempDir: File
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var aggregatedRepo: DefaultAggregatedEmoteCatalogRepository
    private lateinit var sevenTvRepo: DefaultSevenTvRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("emote_bench_").toFile()
        mockWebServer = MockWebServer()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val body = request.body.readUtf8()

                // 7TV GraphQL
                if (path.contains("v3/gql")) {
                    return if (body.contains("SearchUsers")) {
                        MockResponse()
                            .setHeadersDelay(45, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setBody(SEVENTV_USERS_JSON)
                    } else {
                        MockResponse()
                            .setHeadersDelay(45, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setBody(SEVENTV_EMOTES_JSON)
                    }
                }
                if (path.contains("v3/emote-sets/")) {
                    return if (path.endsWith("/set_123")) {
                        MockResponse()
                            .setHeadersDelay(40, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setBody(SEVENTV_EMOTE_SET_JSON)
                    } else {
                        MockResponse()
                            .setHeadersDelay(40, TimeUnit.MILLISECONDS)
                            .setResponseCode(404)
                            .setBody("""{"error":"not found"}""")
                    }
                }

                // BTTV
                if (path.contains("3/emotes/shared/search") || path.contains("3/emotes/shared/trending")) {
                    return MockResponse()
                        .setHeadersDelay(65, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(BTTV_SEARCH_JSON)
                }
                if (path.contains("3/cached/users/twitch/")) {
                    return MockResponse()
                        .setHeadersDelay(60, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(BTTV_USER_JSON)
                }
                if (path.contains("3/cached/emotes/global")) {
                    return MockResponse()
                        .setHeadersDelay(55, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(BTTV_GLOBAL_JSON)
                }

                // FFZ
                if (path.contains("v1/emoticons")) {
                    return MockResponse()
                        .setHeadersDelay(70, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(FFZ_SEARCH_JSON)
                }
                if (path.contains("v1/room/")) {
                    return MockResponse()
                        .setHeadersDelay(65, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(FFZ_ROOM_JSON)
                }

                // Twitch IVR
                if (path.contains("v2/twitch/emotes/sets")) {
                    return MockResponse()
                        .setHeadersDelay(120, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(TWITCH_GLOBAL_JSON)
                }
                if (path.contains("v2/twitch/emotes/channel/")) {
                    return MockResponse()
                        .setHeadersDelay(110, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(TWITCH_CHANNEL_JSON)
                }
                if (path.contains("v2/twitch/user")) {
                    return MockResponse()
                        .setHeadersDelay(50, TimeUnit.MILLISECONDS)
                        .setResponseCode(200)
                        .setBody(TWITCH_USER_JSON)
                }

                return MockResponse().setResponseCode(404).setBody("{}")
            }
        }
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val sevenTvApi = SevenTvNetworkFactory.createApi(baseUrl, okHttpClient)
        val bttvApi = SevenTvNetworkFactory.createBttvApi(baseUrl, okHttpClient)
        val ffzApi = SevenTvNetworkFactory.createFfzApi(baseUrl, okHttpClient)
        val twitchApi = SevenTvNetworkFactory.createTwitchEmotesApi(baseUrl, okHttpClient)
        val downloader = DefaultEmoteDownloader(tempDir, okHttpClient, Dispatchers.IO)

        sevenTvRepo = DefaultSevenTvRepository(sevenTvApi, downloader, Dispatchers.IO)
        val twitchIdResolver = TwitchIdResolver(
            sevenTvApi = sevenTvApi,
            ffzApi = ffzApi,
            twitchApi = twitchApi
        )
        val providers = listOf(
            SevenTvProviderRepository(sevenTvRepo),
            BttvProviderRepository(
                bttvApi = bttvApi,
                downloader = downloader,
                twitchIdResolver = { q -> twitchIdResolver.resolve(q) },
                dispatcher = Dispatchers.IO
            ),
            FfzProviderRepository(ffzApi, downloader, Dispatchers.IO),
            TwitchProviderRepository(twitchApi, downloader, Dispatchers.IO)
        )

        aggregatedRepo = DefaultAggregatedEmoteCatalogRepository(
            providers = providers,
            sevenTvRepository = sevenTvRepo,
            downloader = downloader,
            dispatcher = Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun runBenchmark() = runBlocking {
        println("=== INICIANDO BENCHMARK DE BUSQUEDA Y CARGA DE EMOTES ===")

        // Warmup
        repeat(2) {
            aggregatedRepo.searchAllProviders("warmup_$it", 1, 50)
            aggregatedRepo.getUnifiedChannelEmotes("warmup_channel_$it")
        }

        val iterations = 5

        // Scenario 1: Multi-Provider Search Cold + Model Mapping
        val coldSearchTimes = LongArray(iterations) { iter ->
            measureNanoTime {
                runBlocking {
                    val res = aggregatedRepo.searchAllProviders("pepe_$iter", 1, 50)
                    check(res is StickerResult.Success)
                    val mapped = res.data.mapValues { (_, list) -> list.map { it.toSevenTvEmote() } }
                    check(mapped.isNotEmpty())
                }
            }
        }
        val avgColdSearchMs = coldSearchTimes.average() / 1_000_000.0

        // Scenario 2: First Paint / TTFB (time until first provider batch is emitted via progressive Flow)
        val firstPaintTimes = LongArray(iterations) { iter ->
            measureNanoTime {
                runBlocking {
                    val res = aggregatedRepo.searchAllProvidersProgressive("ttfb_$iter", 1, 50).first()
                    check(res is StickerResult.Success)
                }
            }
        }
        val avgFirstPaintMs = firstPaintTimes.average() / 1_000_000.0

        // Scenario 3: Channel Emotes Cold
        val channelTimes = LongArray(iterations) { iter ->
            measureNanoTime {
                runBlocking {
                    val res = aggregatedRepo.getUnifiedChannelEmotes("streamer_$iter")
                    check(res is StickerResult.Success)
                }
            }
        }
        val avgChannelMs = channelTimes.average() / 1_000_000.0

        // Scenario 4: Warm Search / Cache-Hit + Repeat Channel
        aggregatedRepo.searchAllProviders("cache_target", 1, 50)
        aggregatedRepo.getUnifiedChannelEmotes("ibai")
        val warmSearchTimes = LongArray(iterations) {
            measureNanoTime {
                runBlocking {
                    val res1 = aggregatedRepo.searchAllProviders("cache_target", 1, 50)
                    val res2 = aggregatedRepo.getUnifiedChannelEmotes("ibai")
                    check(res1 is StickerResult.Success && res2 is StickerResult.Success)
                }
            }
        }
        val avgWarmSearchMs = warmSearchTimes.average() / 1_000_000.0

        // Scenario 5: Pagination (Page 2)
        val paginationTimes = LongArray(iterations) { iter ->
            measureNanoTime {
                runBlocking {
                    val res = aggregatedRepo.searchAllProviders("pepe_page_$iter", 2, 50)
                    check(res is StickerResult.Success)
                }
            }
        }
        val avgPaginationMs = paginationTimes.average() / 1_000_000.0

        // Scenario 6: UI State Projection & Recomposition Overhead
        val sampleEmotes = (1..200).map { i ->
            val provider = EmoteProvider.entries[i % EmoteProvider.entries.size]
            SevenTvEmote(
                id = "emote_$i",
                name = "Emote$i",
                isAnimated = i % 2 == 0,
                isZeroWidth = false,
                isNsfw = false,
                bestAssetUrl = "https://cdn.example.com/$i.webp",
                availableScales = listOf("1x.webp", "2x.webp"),
                provider = provider
            )
        }
        val sampleDraft = sampleEmotes.take(25)
        val initialExploreState = ExploreUiState(
            searchResults = SearchResultsState.Success(
                emotes = sampleEmotes,
                emotesByProvider = sampleEmotes.groupBy { it.provider }
            ),
            draftEmotes = sampleDraft
        )

        val uiProjectionTimes = LongArray(iterations) {
            measureNanoTime {
                repeat(500) { cycle ->
                    val filter = when (cycle % 3) {
                        0 -> EmoteFilter.ALL
                        1 -> EmoteFilter.ANIMATED
                        else -> EmoteFilter.STATIC
                    }
                    val providerFilter = when (cycle % 5) {
                        0 -> ChannelProviderFilter.ALL
                        1 -> ChannelProviderFilter.SEVENTV
                        2 -> ChannelProviderFilter.BETTERTTV
                        3 -> ChannelProviderFilter.FRANKERFACEZ
                        else -> ChannelProviderFilter.TWITCH
                    }
                    val updatedState = initialExploreState.copy(
                        activeFilter = filter,
                        activeProviderFilter = providerFilter
                    )
                    val filtered = updatedState.filteredEmotes
                    var draftedCount = 0
                    for (emote in filtered) {
                        if (updatedState.draftEmotes.any { it.id == emote.id }) {
                            draftedCount++
                        }
                    }
                    check(draftedCount >= 0)
                }
            }
        }
        val avgUiProjectionMs = uiProjectionTimes.average() / 1_000_000.0

        val compositeLatencyMs = (avgColdSearchMs * 0.20) +
            (avgFirstPaintMs * 0.20) +
            (avgChannelMs * 0.25) +
            (avgWarmSearchMs * 0.15) +
            (avgPaginationMs * 0.10) +
            (avgUiProjectionMs * 0.10)

        println("--------------------------------------------------")
        println("BENCHMARK RESULTS (Averages over $iterations iterations):")
        println("  1. Multi-Provider Search Cold : ${String.format(java.util.Locale.US, "%.2f", avgColdSearchMs)} ms")
        println("  2. First Paint / TTFB         : ${String.format(java.util.Locale.US, "%.2f", avgFirstPaintMs)} ms")
        println("  3. Channel Emotes Cold        : ${String.format(java.util.Locale.US, "%.2f", avgChannelMs)} ms")
        println("  4. Warm Search / Cache-Hit    : ${String.format(java.util.Locale.US, "%.2f", avgWarmSearchMs)} ms")
        println("  5. Pagination (Page 2)        : ${String.format(java.util.Locale.US, "%.2f", avgPaginationMs)} ms")
        println("  6. UI State Projection (500x) : ${String.format(java.util.Locale.US, "%.2f", avgUiProjectionMs)} ms")
        println("--------------------------------------------------")
        println("METRIC search_latency_ms=${String.format(java.util.Locale.US, "%.2f", compositeLatencyMs)}")
        println("METRIC cold_search_ms=${String.format(java.util.Locale.US, "%.2f", avgColdSearchMs)}")
        println("METRIC first_paint_ms=${String.format(java.util.Locale.US, "%.2f", avgFirstPaintMs)}")
        println("METRIC channel_search_ms=${String.format(java.util.Locale.US, "%.2f", avgChannelMs)}")
        println("METRIC warm_search_ms=${String.format(java.util.Locale.US, "%.2f", avgWarmSearchMs)}")
        println("METRIC pagination_ms=${String.format(java.util.Locale.US, "%.2f", avgPaginationMs)}")
        println("METRIC ui_projection_ms=${String.format(java.util.Locale.US, "%.2f", avgUiProjectionMs)}")
        println("--------------------------------------------------")
    }

    companion object {
        private val SEVENTV_EMOTES_JSON = buildString {
            append("""{"data":{"emotes":{"count":50,"items":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":"7tv_$i","name":"7tvEmote_$i","animated":${i % 2 == 0},"host":{"url":"//cdn.7tv.app/emote/7tv_$i","files":[{"name":"1x.webp","format":"WEBP"},{"name":"2x.webp","format":"WEBP"},{"name":"3x.webp","format":"WEBP"},{"name":"4x.webp","format":"WEBP"}]}}""")
            }
            append("""]}}}""")
        }

        private val SEVENTV_EMOTE_SET_JSON = buildString {
            append("""{"id":"set_123","name":"Ibai Set","flags":0,"capacity":500,"owner_name":"Ibai","emotes":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":"7tv_set_$i","name":"7tvSetEmote_$i","animated":${i % 2 == 0},"host":{"url":"//cdn.7tv.app/emote/7tv_set_$i","files":[{"name":"2x.webp","format":"WEBP"},{"name":"4x.webp","format":"WEBP"}]}}""")
            }
            append("""]}""")
        }

        private val SEVENTV_USERS_JSON = """
            {"data":{"users":[{"id":"u_ibai","username":"ibai","display_name":"Ibai","avatar_url":"https://cdn.7tv.app/avatar/ibai.png","connections":[{"id":"83232866","platform":"TWITCH","username":"ibai","display_name":"Ibai","emote_set_id":"set_123"}],"emote_sets":[{"id":"set_123","name":"Ibai Set","capacity":500,"emote_count":50}]}]}}
        """.trimIndent()

        private val BTTV_SEARCH_JSON = buildString {
            append("[")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":"bttv_$i","code":"bttvEmote_$i","imageType":"webp","animated":${i % 2 == 0},"user":{"id":"u1","name":"ibai","displayName":"Ibai"}}""")
            }
            append("]")
        }

        private val BTTV_GLOBAL_JSON = BTTV_SEARCH_JSON

        private val BTTV_USER_JSON = buildString {
            append("""{"id":"83232866","bots":[],"channelEmotes":[""")
            for (i in 1..25) {
                if (i > 1) append(",")
                append("""{"id":"bttv_c_$i","code":"bttvChannel_$i","imageType":"webp","animated":${i % 2 == 0}}""")
            }
            append("""],"sharedEmotes":[""")
            for (i in 26..50) {
                if (i > 26) append(",")
                append("""{"id":"bttv_s_$i","code":"bttvShared_$i","imageType":"webp","animated":${i % 2 == 0}}""")
            }
            append("""]}""")
        }

        private val FFZ_SEARCH_JSON = buildString {
            append("""{"_total":50,"_pages":1,"emoticons":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":$i,"name":"ffzEmote_$i","width":32,"height":32,"modifier":false,"hidden":false,"urls":{"1":"//cdn.frankerfacez.com/emoticon/$i/1","2":"//cdn.frankerfacez.com/emoticon/$i/2","4":"//cdn.frankerfacez.com/emoticon/$i/4"},"owner":{"id":10,"name":"ibai","display_name":"Ibai"}}""")
            }
            append("""]}""")
        }

        private val FFZ_ROOM_JSON = buildString {
            append("""{"room":{"id":"ibai","_id":10,"twitch_id":83232866,"display_name":"Ibai","set":100},"sets":{"100":{"id":100,"title":"Channel","emoticons":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":${1000 + i},"name":"ffzRoom_$i","width":32,"height":32,"modifier":false,"hidden":false,"urls":{"2":"//cdn.frankerfacez.com/emoticon/${1000 + i}/2","4":"//cdn.frankerfacez.com/emoticon/${1000 + i}/4"},"owner":{"id":10,"name":"ibai","display_name":"Ibai"}}""")
            }
            append("""]}}}""")
        }

        private val TWITCH_GLOBAL_JSON = buildString {
            append("""[{"setID":"0","emotes":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":"tw_$i","code":"twEmote_$i","assetType":"${if (i % 2 == 0) "ANIMATED" else "STATIC"}"}""")
            }
            append("""],"emoteList":[]}]""")
        }

        private val TWITCH_CHANNEL_JSON = buildString {
            append("""{"channelName":"Ibai","channelLogin":"ibai","channelID":"83232866","emoteSets":[],"subscriberEmotes":[{"setID":"sub1","emotes":[""")
            for (i in 1..50) {
                if (i > 1) append(",")
                append("""{"id":"tw_sub_$i","code":"twSub_$i","assetType":"${if (i % 2 == 0) "ANIMATED" else "STATIC"}"}""")
            }
            append("""],"emoteList":[]}],"subProducts":[],"localEmotes":[],"bitEmotes":[]}""")
        }

        private val TWITCH_USER_JSON = """[{"id":"83232866","login":"ibai","displayName":"Ibai","logo":"https://static-cdn.jtvnw.net/logo.png"}]"""
    }
}

package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvNetworkFactory
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiProviderRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var fakeDownloader: FakeUnifiedDownloader

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient()
        fakeDownloader = FakeUnifiedDownloader()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun bttvRepository_getChannelEmotes_resolvesTwitchIdAndParsesChannelAndShared() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "bttv123",
                  "bots": [],
                  "channelEmotes": [
                    { "id": "c1", "code": "ibaiHappy", "imageType": "png", "animated": false }
                  ],
                  "sharedEmotes": [
                    { "id": "s1", "code": "catJAM", "imageType": "gif", "animated": true }
                  ]
                }
                """.trimIndent()
            )
        )

        val api = SevenTvNetworkFactory.createBttvApi(mockWebServer.url("/").toString(), okHttpClient)
        val repo = BttvProviderRepository(
            bttvApi = api,
            downloader = fakeDownloader,
            twitchIdResolver = { query -> if (query == "ibai") "83232866" else null }
        )

        val result = repo.getChannelEmotes("ibai")
        assertTrue(result is StickerResult.Success)
        val emotes = (result as StickerResult.Success).data
        assertEquals(2, emotes.size)
        assertEquals("c1", emotes[0].id)
        assertEquals(EmoteProvider.BETTERTTV, emotes[0].provider)
        assertTrue(emotes[1].isAnimated)

        val req = mockWebServer.takeRequest()
        assertEquals("/3/cached/users/twitch/83232866", req.path)
    }

    @Test
    fun bttvRepository_searchEmotes_handlesTrendingShortQueryAndFullSearch() = runTest {
        // 1. Blank query -> trending
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"emote":{"id":"tr1","code":"pepeJAM","imageType":"gif","animated":true},"total":500}]"""
            )
        )
        // 2. Short query (<3 chars) -> global filter
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"g1","code":"EZ","imageType":"png","animated":false},{"id":"g2","code":"KEKW","imageType":"png","animated":false}]"""
            )
        )
        // 3. Normal query -> search endpoint
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"sr1","code":"peepoSad","imageType":"png","animated":false}]"""
            )
        )

        val api = SevenTvNetworkFactory.createBttvApi(mockWebServer.url("/").toString(), okHttpClient)
        val repo = BttvProviderRepository(api, fakeDownloader)

        val trending = repo.searchEmotes("")
        assertEquals(1, (trending as StickerResult.Success).data.size)
        val trendingReq = mockWebServer.takeRequest()
        assertEquals("/3/emotes/shared/trending?limit=100", trendingReq.path)
        assertEquals("https://betterttv.com", trendingReq.getHeader("Origin"))
        assertEquals("https://betterttv.com/", trendingReq.getHeader("Referer"))

        val shortRes = repo.searchEmotes("EZ")
        assertEquals(listOf("EZ"), (shortRes as StickerResult.Success).data.map { it.name })
        assertEquals("/3/cached/emotes/global", mockWebServer.takeRequest().path)

        val searchRes = repo.searchEmotes("peepo")
        assertEquals(listOf("peepoSad"), (searchRes as StickerResult.Success).data.map { it.name })
        val searchReq = mockWebServer.takeRequest()
        assertEquals("/3/emotes/shared/search?query=peepo&limit=100", searchReq.path)
        assertEquals("https://betterttv.com", searchReq.getHeader("Origin"))
        assertEquals("https://betterttv.com/", searchReq.getHeader("Referer"))

        // Page 2 includes positive offset
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        repo.searchEmotes("", page = 2, limit = 50)
        assertEquals("/3/emotes/shared/trending?offset=50&limit=50", mockWebServer.takeRequest().path)

        // Direct API call with offset=0 strips offset=0
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.getTrendingEmotes(offset = 0, limit = 50)
        assertEquals("/3/emotes/shared/trending?limit=50", mockWebServer.takeRequest().path)
    }

    @Test
    fun ffzRepository_getChannelEmotes_handlesRoomAndHttpErrors() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "room": { "id": "illojuan", "_id": 1, "twitch_id": 9001, "display_name": "IlloJuan", "set": 100 },
                  "sets": {
                    "100": {
                      "id": 100,
                      "emoticons": [
                        { "id": 500, "name": "illoLUL", "urls": { "4": "//cdn.frankerfacez.com/emoticon/500/4" } }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val api = SevenTvNetworkFactory.createFfzApi(mockWebServer.url("/").toString(), okHttpClient)
        val repo = FfzProviderRepository(api, fakeDownloader)

        val result = repo.getChannelEmotes("IlloJuan")
        assertTrue(result is StickerResult.Success)
        val emotes = (result as StickerResult.Success).data
        assertEquals(1, emotes.size)
        assertEquals("500", emotes[0].id)
        assertEquals(EmoteProvider.FRANKERFACEZ, emotes[0].provider)
        assertEquals("https://cdn.frankerfacez.com/emoticon/500/4", emotes[0].bestAssetUrl)
        assertEquals("/v1/room/illojuan", mockWebServer.takeRequest().path)

        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        val notFound = repo.getChannelEmotes("noexist")
        assertEquals(StickerError.ResourceNotFound, (notFound as StickerResult.Failure).error)

        mockWebServer.enqueue(MockResponse().setResponseCode(429))
        val rateLimited = repo.getChannelEmotes("ratelimit")
        assertEquals(StickerError.RateLimited, (rateLimited as StickerResult.Failure).error)
    }

    @Test
    fun twitchRepository_getChannelAndSearchEmotes() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "channelName": "xQc",
                  "subProducts": [
                    {
                      "id": "1000",
                      "emotes": [
                        { "id": "300", "code": "xqcL", "assetType": "STATIC" }
                      ]
                    }
                  ],
                  "localEmotes": [
                    {
                      "id": "follower",
                      "emotes": [
                        { "id": "301", "code": "xqcDance", "assetType": "ANIMATED" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val api = SevenTvNetworkFactory.createTwitchEmotesApi(mockWebServer.url("/").toString(), okHttpClient)
        val repo = TwitchProviderRepository(api, fakeDownloader)

        val res = repo.getChannelEmotes("xQc")
        assertTrue(res is StickerResult.Success)
        val data = (res as StickerResult.Success).data
        assertEquals(2, data.size)
        assertEquals(EmoteProvider.TWITCH, data[0].provider)
        assertEquals("/v2/twitch/emotes/channel/xqc", mockWebServer.takeRequest().path)

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"setID":"0","emoteList":[{"id":"25","code":"Kappa","assetType":"STATIC"},{"id":"354","code":"4Head","assetType":"STATIC"}]}]"""
            )
        )
        val globalSearch = repo.searchEmotes("kap")
        assertEquals(listOf("Kappa"), (globalSearch as StickerResult.Success).data.map { it.name })
        assertEquals("/v2/twitch/emotes/sets?set_id=0", mockWebServer.takeRequest().path)
    }

    @Test
    fun twitchIdResolver_fallsBackThroughProvidersAndCachesResult() = runTest {
        // 1. Twitch IVR fails (404) -> 2. FFZ succeeds with twitch_id
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"room":{"id":"auronplay","_id":10,"twitch_id":778899,"display_name":"AuronPlay","set":1},"sets":{}}"""
            )
        )

        val base = mockWebServer.url("/").toString()
        val resolver = TwitchIdResolver(
            ffzApi = SevenTvNetworkFactory.createFfzApi(base, okHttpClient),
            twitchApi = SevenTvNetworkFactory.createTwitchEmotesApi(base, okHttpClient)
        )

        val id1 = resolver.resolve("AuronPlay")
        val id2 = resolver.resolve("auronplay") // cached
        assertEquals("778899", id1)
        assertEquals("778899", id2)
        assertEquals(2, mockWebServer.requestCount)
    }

    private class FakeUnifiedDownloader : EmoteDownloader {
        override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
            return StickerResult.Success(
                DownloadedEmoteAsset(emote.id, emote.name, byteArrayOf(1, 2, 3), emote.isAnimated, "WEBP")
            )
        }

        override suspend fun downloadFromUrl(url: String): StickerResult<ByteArray> {
            return StickerResult.Success(byteArrayOf(1, 2, 3))
        }
    }
}

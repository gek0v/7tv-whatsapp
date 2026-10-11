package com.seventv.stickers.core.network.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SevenTvApiTest {
    private val mockWebServer = MockWebServer()
    private lateinit var api: SevenTvApi

    @Before
    fun setUp() {
        mockWebServer.start()
        api = SevenTvNetworkFactory.createApi(mockWebServer.url("/").toString())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun searchEmotesReturnsEmoteListOnHttp200() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":"1","name":"KEKW","animated":false}]""")
        )
        val response = api.searchEmotes("KEKW")
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()?.size)
        assertEquals("KEKW", response.body()?.first()?.name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertTrue(recordedRequest.path?.contains("/v3/emotes") == true)
        assertTrue(recordedRequest.path?.contains("query=KEKW") == true)
        assertEquals("7TV-WhatsApp-Android/1.0", recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun getEmoteSetReturnsSetOnHttp200() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"set1","name":"Test Set","emotes":[]}""")
        )
        val response = api.getEmoteSet("set1")
        assertTrue(response.isSuccessful)
        assertEquals("Test Set", response.body()?.name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/v3/emote-sets/set1", recordedRequest.path)
    }

    @Test
    fun getEmoteReturnsSingleEmoteOnHttp200() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"60ccf4479f59298d1a7b4698","name":"catJAM","animated":true}""")
        )
        val response = api.getEmote("60ccf4479f59298d1a7b4698")
        assertTrue(response.isSuccessful)
        assertEquals("catJAM", response.body()?.name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/v3/emotes/60ccf4479f59298d1a7b4698", recordedRequest.path)
    }

    @Test
    fun searchEmotesWithPaginationSendsCorrectQueryParams() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        val response = api.searchEmotes(query = "monkaS", page = 2, limit = 50)
        assertTrue(response.isSuccessful)

        val recordedRequest = mockWebServer.takeRequest()
        val path = recordedRequest.path ?: ""
        assertTrue(path.contains("query=monkaS"))
        assertTrue(path.contains("page=2"))
        assertTrue(path.contains("limit=50"))
    }

    @Test
    fun networkFactoryConfiguresTimeoutsAndCache() {
        val tempDir = File.createTempFile("cache_unit_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val clientWithCache = SevenTvNetworkFactory.createOkHttpClient(tempDir)
            assertEquals(15_000, clientWithCache.connectTimeoutMillis)
            assertEquals(15_000, clientWithCache.readTimeoutMillis)
            assertEquals(15_000, clientWithCache.writeTimeoutMillis)
            assertNotNull(clientWithCache.cache)
            assertEquals(10L * 1024 * 1024, clientWithCache.cache?.maxSize())

            val clientWithoutCache = SevenTvNetworkFactory.createOkHttpClient(null)
            org.junit.Assert.assertNull(clientWithoutCache.cache)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

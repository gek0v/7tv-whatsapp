package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.SevenTvGqlQueries
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
    fun searchEmotesSendsPostGqlAndParsesEmotes() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "data": {
                        "emotes": {
                          "count": 1,
                          "items": [
                            {"id":"1","name":"KEKW","animated":false}
                          ]
                        }
                      }
                    }
                """.trimIndent())
        )
        val response = api.searchEmotes(SevenTvGqlQueries.searchEmotesRequest("KEKW", page = 1, limit = 30))
        assertTrue(response.isSuccessful)
        val items = response.body()?.data?.emotes?.items.orEmpty()
        assertEquals(1, items.size)
        assertEquals("KEKW", items.first().name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v3/gql", recordedRequest.path)
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("SearchEmotes"))
        assertTrue(bodyText.contains("KEKW"))
        assertEquals("7TV-WhatsApp-Android/1.0", recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun searchUsersSendsPostGqlAndParsesUsers() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "data": {
                        "users": [
                          {
                            "id": "u1",
                            "username": "ibai",
                            "display_name": "Ibai",
                            "emote_sets": [
                              {"id": "s1", "name": "Ibai Set"}
                            ]
                          }
                        ]
                      }
                    }
                """.trimIndent())
        )
        val response = api.searchUsers(SevenTvGqlQueries.searchUsersRequest("ibai", limit = 1))
        assertTrue(response.isSuccessful)
        val users = response.body()?.data?.users.orEmpty()
        assertEquals(1, users.size)
        assertEquals("ibai", users.first().username)
        assertEquals("Ibai Set", users.first().emoteSets.first().name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v3/gql", recordedRequest.path)
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("SearchUsers"))
        assertTrue(bodyText.contains("ibai"))
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

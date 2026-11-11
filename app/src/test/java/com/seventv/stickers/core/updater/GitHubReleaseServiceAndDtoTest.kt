package com.seventv.stickers.core.updater

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubReleaseServiceAndDtoTest {

    private val mockWebServer = MockWebServer()
    private lateinit var service: GitHubReleaseService

    @Before
    fun setUp() {
        mockWebServer.start()
        service = GitHubReleaseService.create(baseUrl = mockWebServer.url("/").toString())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun parsesGitHubReleaseDtoAndSendsIfNoneMatchHeader() = runTest {
        val bodyJson = """
            {
              "tag_name": "v1.2.0",
              "name": "7TV Stickers 1.2.0",
              "body": "### Mejoras\n- Descarga automática",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-11T10:00:00Z",
              "html_url": "https://github.com/gek0v/7tv-whatsapp/releases/tag/v1.2.0",
              "assets": [
                {
                  "id": 101,
                  "name": "7tv-stickers-1.2.0.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 12345678,
                  "browser_download_url": "https://github.com/gek0v/7tv-whatsapp/releases/download/v1.2.0/7tv-stickers-1.2.0.apk",
                  "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ],
              "unknown_field": "ignored_safely"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"etag-123\"")
                .setHeader("Content-Type", "application/json")
                .setBody(bodyJson)
        )

        val response = service.getLatestRelease(ifNoneMatch = "\"etag-old\"")
        assertTrue(response.isSuccessful)
        assertEquals("\"etag-123\"", response.headers()["ETag"])

        val dto = response.body()!!
        assertEquals("v1.2.0", dto.tagName)
        assertEquals("7TV Stickers 1.2.0", dto.name)
        assertFalse(dto.draft)
        assertFalse(dto.prerelease)
        assertEquals(1, dto.assets.size)
        val asset = dto.assets.first()
        assertEquals("7tv-stickers-1.2.0.apk", asset.name)
        assertEquals(12345678L, asset.size)
        assertEquals("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", asset.digest)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/repos/gek0v/7tv-whatsapp/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("\"etag-old\"", request.getHeader("If-None-Match"))
    }

    @Test
    fun handlesHttp304NotModified() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(304))

        val response = service.getLatestRelease(ifNoneMatch = "\"etag-123\"")
        assertEquals(304, response.code())
        assertNull(response.body())
    }
}
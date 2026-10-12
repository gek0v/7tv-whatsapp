# Subproject 2: 7TV Network API & Emote Downloader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the network client, serialization, asset downloader with local disk caching, repository layer, and the media pipeline integration bridge (`EmoteStickerBridge`) for 7TV emotes and emote sets.

**Architecture:** Clean architecture layer separating remote DTOs (`core.network.dto`), Retrofit service (`SevenTvApi`), progressive asset downloader with local disk caching (`DefaultEmoteDownloader`), clean domain models (`SevenTvEmote`, `SevenTvEmoteSet`), repository (`DefaultSevenTvRepository`), and media bridge (`EmoteStickerBridge`) routing into `StickerMediaPipeline`.

**Tech Stack:** Retrofit 2.11.0, OkHttp 4.12.0, Kotlinx Serialization 1.7.1, MockWebServer 4.12.0, Robolectric 4.12.2, Coroutines 1.8.1.

**Spec:** [docs/superpowers/specs/2026-10-11-7tv-network-api-downloader-design.md](file:///D:/Workspaces/7tv-whatsapp/docs/superpowers/specs/2026-10-11-7tv-network-api-downloader-design.md)

## Global Constraints

- Android Min SDK: 26, Target SDK: 34, JVM Toolchain: Java 17
- Base API URL: `https://7tv.io/v3/`
- CDN URL Schema: `https://cdn.7tv.app/emote/{id}/`
- CDN Quality Prioritization: `4x.webp` -> `3x.webp` -> `2x.webp` -> `1x.webp`
- Max Payload Safety: Max 15 MB for image asset downloads
- HTTP Cache: 10 MB for JSON responses via OkHttp Cache
- Local Asset Cache: Directory `context.cacheDir/7tv_emotes_cache/`
- Error Hierarchy: Pure functional return `StickerResult<T>` and `StickerError`

## Review Focus

1. **Protocol-relative CDN URLs**: URLs starting with `//cdn.7tv.app/...` must prepend `https:` correctly without malformed URI crashes.
2. **Missing Host / Files in Emote DTO**: Some 7TV emote payloads may have null or empty files; must safely return `StickerError.CorruptedSourceData` instead of `NullPointerException`.
3. **HTTP 429 Rate Limiting**: When 7TV responds with HTTP 429, the repository must translate it into `StickerError.RateLimited` rather than a generic network exception.
4. **Disk Cache Concurrency**: Concurrent calls for the same emote must not corrupt files or produce half-written files.
5. **Memory Safety during Asset Downloads**: Direct streaming of response bytes must avoid unbounded heap allocation.

---

### Task 1: Gradle Dependencies & Serialization Plugin Setup

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: Retrofit, OkHttp, Kotlinx Serialization, and MockWebServer dependencies available in `:app`.

- [ ] **Step 1: Update `gradle/libs.versions.toml` with networking dependencies**

Add to `[versions]`:
- `retrofit = "2.11.0"`
- `okhttp = "4.12.0"`
- `kotlinx-serialization = "1.7.1"`

Add to `[libraries]`:
- `retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }`
- `retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }`
- `okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }`
- `okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }`
- `okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }`
- `kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }`

Add to `[plugins]`:
- `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`

- [ ] **Step 2: Apply serialization plugin in `build.gradle.kts` and `app/build.gradle.kts`**

In `build.gradle.kts`:
`alias(libs.plugins.kotlin.serialization) apply false`

In `app/build.gradle.kts`:
`alias(libs.plugins.kotlin.serialization)`
And add dependencies:
```kotlin
implementation(libs.retrofit.core)
implementation(libs.retrofit.converter.kotlinx.serialization)
implementation(libs.okhttp.core)
implementation(libs.okhttp.logging)
implementation(libs.kotlinx.serialization.json)
testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Verify build and dependencies sync**

Run: `gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build(deps): add retrofit, okhttp, and kotlinx serialization dependencies"
```

---

### Task 2: Data Transfer Objects (DTOs) & JSON Serialization

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/network/dto/SevenTvEmoteDto.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/dto/SevenTvEmoteDataDto.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/dto/SevenTvHostDto.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/dto/SevenTvFileDto.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/dto/SevenTvEmoteSetDto.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/network/dto/SevenTvDtoSerializationTest.kt`

**Interfaces:**
- Produces: `@Serializable` DTO models for 7TV v3 API responses.

- [ ] **Step 1: Write failing unit test for DTO serialization**

```kotlin
class SevenTvDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseEmoteDtoWithNestedHostAndFiles() {
        val payload = """
        {
            "id": "60ccf4479f59298d1a7b4698",
            "name": "catJAM",
            "flags": 1,
            "animated": true,
            "host": {
                "url": "//cdn.7tv.app/emote/60ccf4479f59298d1a7b4698",
                "files": [
                    {"name": "1x.webp", "format": "WEBP", "width": 32, "height": 32, "size": 1200},
                    {"name": "4x.webp", "format": "WEBP", "width": 128, "height": 128, "size": 8400}
                ]
            }
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteDto>(payload)
        assertEquals("60ccf4479f59298d1a7b4698", dto.id)
        assertEquals("catJAM", dto.name)
        assertTrue(dto.animated)
        assertEquals(2, dto.host?.files?.size)
    }

    @Test
    fun parseEmoteSetDtoWithEmoteList() {
        val payload = """
        {
            "id": "60b0e3be5b1f9f2571fa088b",
            "name": "Global Emotes",
            "flags": 0,
            "emotes": [
                {"id": "60ccf4479f59298d1a7b4698", "name": "catJAM", "animated": true}
            ]
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteSetDto>(payload)
        assertEquals("Global Emotes", dto.name)
        assertEquals(1, dto.emotes.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.dto.SevenTvDtoSerializationTest`
Expected: FAIL (unresolved references)

- [ ] **Step 3: Implement DTO classes with `@Serializable` and default values**

Create `SevenTvEmoteDto`, `SevenTvEmoteDataDto`, `SevenTvHostDto`, `SevenTvFileDto`, and `SevenTvEmoteSetDto` under `com.seventv.stickers.core.network.dto`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.dto.SevenTvDtoSerializationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/network/dto/ app/src/test/java/com/seventv/stickers/core/network/dto/
git commit -m "feat(network): implement 7TV v3 DTOs with kotlinx serialization"
```

---

### Task 3: Retrofit Service (`SevenTvApi`) & Network Factory

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/network/api/SevenTvApi.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/api/SevenTvNetworkFactory.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/network/api/SevenTvApiTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmoteDto`, `SevenTvEmoteSetDto`
- Produces: `SevenTvApi`, `SevenTvNetworkFactory`

- [ ] **Step 1: Write failing unit test for `SevenTvApi` using `MockWebServer`**

```kotlin
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
                .setBody("""[{"id":"1","name":"KEKW","animated":false}]""")
        )
        val response = api.searchEmotes("KEKW")
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()?.size)
        assertEquals("KEKW", response.body()?.first()?.name)
    }

    @Test
    fun getEmoteSetReturnsSetOnHttp200() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"set1","name":"Test Set","emotes":[]}""")
        )
        val response = api.getEmoteSet("set1")
        assertTrue(response.isSuccessful)
        assertEquals("Test Set", response.body()?.name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.api.SevenTvApiTest`
Expected: FAIL

- [ ] **Step 3: Implement `SevenTvApi` and `SevenTvNetworkFactory`**

- `SevenTvApi` interface with Retrofit annotations:
  `searchEmotes(@Query("query") query: String, @Query("page") page: Int = 1, @Query("limit") limit: Int = 30): Response<List<SevenTvEmoteDto>>`
  `getEmote(@Path("id") emoteId: String): Response<SevenTvEmoteDto>`
  `getEmoteSet(@Path("id") setId: String): Response<SevenTvEmoteSetDto>`
- `SevenTvNetworkFactory` building OkHttpClient with timeouts, User-Agent header, and `asConverterFactory("application/json".toMediaType())`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.api.SevenTvApiTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/network/api/ app/src/test/java/com/seventv/stickers/core/network/api/
git commit -m "feat(network): implement SevenTvApi retrofit service and network factory"
```

---

### Task 4: Domain Models, Error Extensions & Mapper

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/core/common/result/StickerError.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/model/SevenTvEmote.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/model/SevenTvEmoteSet.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/model/DownloadedEmoteAsset.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/mapper/SevenTvMapper.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/network/mapper/SevenTvMapperTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmoteDto`, `SevenTvEmoteSetDto`, `StickerError`
- Produces: `SevenTvEmote`, `SevenTvEmoteSet`, `DownloadedEmoteAsset`, `SevenTvMapper`

- [ ] **Step 1: Write failing unit test for `SevenTvMapper`**

```kotlin
class SevenTvMapperTest {
    @Test
    fun mapsDtoToDomainWithCdnUrlNormalization() {
        val dto = SevenTvEmoteDto(
            id = "60ccf4479f59298d1a7b4698",
            name = "catJAM",
            flags = 1,
            animated = true,
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/60ccf4479f59298d1a7b4698",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100),
                    SevenTvFileDto("4x.webp", "WEBP", 128, 128, 400)
                )
            )
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertEquals("60ccf4479f59298d1a7b4698", domain.id)
        assertEquals("catJAM", domain.name)
        assertTrue(domain.isAnimated)
        assertEquals("https://cdn.7tv.app/emote/60ccf4479f59298d1a7b4698/4x.webp", domain.bestAssetUrl)
    }

    @Test
    fun handlesProtocolRelativeUrlWithoutCrashing() {
        val url = SevenTvMapper.normalizeCdnUrl("//cdn.7tv.app/emote/test", "4x.webp")
        assertEquals("https://cdn.7tv.app/emote/test/4x.webp", url)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.mapper.SevenTvMapperTest`
Expected: FAIL

- [ ] **Step 3: Implement domain models, error extensions, and mapper**

- Add `NetworkError(statusCode: Int?, message: String)`, `ResourceNotFound`, `RateLimited` to `StickerError`.
- Implement `SevenTvEmote`, `SevenTvEmoteSet`, and `DownloadedEmoteAsset`.
- Implement `SevenTvMapper.toDomain(dto: SevenTvEmoteDto)` selecting highest quality scale (`4x` > `3x` > `2x` > `1x`) and prepending `https:` to protocol-relative URLs.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.mapper.SevenTvMapperTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/common/result/StickerError.kt app/src/main/java/com/seventv/stickers/core/network/model/ app/src/main/java/com/seventv/stickers/core/network/mapper/ app/src/test/java/com/seventv/stickers/core/network/mapper/
git commit -m "feat(network): implement domain models, error extensions, and SevenTvMapper"
```

---

### Task 5: Emote Downloader & Disk Cache Engine (`DefaultEmoteDownloader`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/network/downloader/EmoteDownloader.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/network/downloader/DefaultEmoteDownloader.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/network/downloader/DefaultEmoteDownloaderTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmote`, `DownloadedEmoteAsset`, `StickerResult`, `StickerError`
- Produces: `EmoteDownloader`, `DefaultEmoteDownloader`
  - `suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>`
  - `suspend fun downloadFromUrl(url: String): StickerResult<ByteArray>`

- [ ] **Step 1: Write failing unit test for `DefaultEmoteDownloader`**

```kotlin
class DefaultEmoteDownloaderTest {
    private val mockWebServer = MockWebServer()
    private val tempDir = File.createTempFile("cache_test", "").apply { delete(); mkdirs() }
    private lateinit var downloader: EmoteDownloader

    @Before
    fun setUp() {
        mockWebServer.start()
        downloader = DefaultEmoteDownloader(
            cacheDir = tempDir,
            okHttpClient = OkHttpClient()
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun downloadEmoteFetchesFromNetworkAndSavesToCache() = runTest {
        val sampleBytes = byteArrayOf(1, 2, 3, 4, 5)
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(sampleBytes)))

        val emote = SevenTvEmote(
            id = "test_emote",
            name = "Test",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = mockWebServer.url("/test_emote/4x.webp").toString(),
            availableScales = listOf("4x")
        )

        val result = downloader.downloadEmote(emote)
        assertTrue(result is StickerResult.Success)
        val asset = (result as StickerResult.Success).data
        assertTrue(asset.bytes.contentEquals(sampleBytes))

        // Second call should hit disk cache without network request
        val cacheResult = downloader.downloadEmote(emote)
        assertTrue(cacheResult is StickerResult.Success)
        assertEquals(1, mockWebServer.requestCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.downloader.DefaultEmoteDownloaderTest`
Expected: FAIL

- [ ] **Step 3: Implement `DefaultEmoteDownloader`**

Implement streaming download with OkHttp, file writing to `cacheDir`, max size check ($15\text{ MB}$), and disk cache lookup.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.network.downloader.DefaultEmoteDownloaderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/network/downloader/ app/src/test/java/com/seventv/stickers/core/network/downloader/
git commit -m "feat(network): implement DefaultEmoteDownloader with local disk caching"
```

---

### Task 6: SevenTvRepository Implementation

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/data/repository/SevenTvRepository.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/repository/DefaultSevenTvRepository.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/repository/DefaultSevenTvRepositoryTest.kt`

**Interfaces:**
- Consumes: `SevenTvApi`, `EmoteDownloader`, `SevenTvMapper`, `StickerResult`, `StickerError`
- Produces: `SevenTvRepository`, `DefaultSevenTvRepository`
  - `suspend fun searchEmotes(query: String, page: Int = 1, limit: Int = 30): StickerResult<List<SevenTvEmote>>`
  - `suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote>`
  - `suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet>`
  - `suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>`

- [ ] **Step 1: Write failing unit test for `DefaultSevenTvRepository`**

```kotlin
class DefaultSevenTvRepositoryTest {
    private val mockApi = mockk<SevenTvApi>()
    private val mockDownloader = mockk<EmoteDownloader>()
    private lateinit var repository: SevenTvRepository

    @Test
    fun searchEmotesMapsDtoListToDomainList() = runTest {
        // Mock successful API response and verify repository returns StickerResult.Success
    }

    @Test
    fun http404ReturnsResourceNotFound() = runTest {
        // Mock 404 response and verify repository returns StickerResult.Failure(StickerError.ResourceNotFound)
    }

    @Test
    fun http429ReturnsRateLimited() = runTest {
        // Mock 429 response and verify repository returns StickerResult.Failure(StickerError.RateLimited)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.DefaultSevenTvRepositoryTest`
Expected: FAIL

- [ ] **Step 3: Implement `DefaultSevenTvRepository`**

Implement search, getEmote, getEmoteSet, downloadEmote mapping HTTP status codes (200 $\to$ Success, 404 $\to$ ResourceNotFound, 429 $\to$ RateLimited, exceptions $\to$ NetworkError).

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.DefaultSevenTvRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/data/repository/ app/src/test/java/com/seventv/stickers/data/repository/DefaultSevenTvRepositoryTest.kt
git commit -m "feat(data): implement DefaultSevenTvRepository with error mapping"
```

---

### Task 7: EmoteStickerBridge & Media Pipeline Integration

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/data/repository/EmoteStickerBridge.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/repository/EmoteStickerBridgeTest.kt`

**Interfaces:**
- Consumes: `SevenTvRepository`, `StickerMediaPipeline`, `SevenTvEmote`, `ProcessedSticker`
- Produces: `EmoteStickerBridge`
  - `suspend fun convertEmoteToSticker(emote: SevenTvEmote, outputDir: File, allowStaticFallback: Boolean = true): StickerResult<ProcessedSticker>`

- [ ] **Step 1: Write unit test for `EmoteStickerBridge`**

```kotlin
@RunWith(RobolectricTestRunner::class)
class EmoteStickerBridgeTest {
    @Test
    fun convertEmoteToStickerDownloadsAndProcessesStickerUnderLimit() = runTest {
        // Mock repository returning valid sample PNG/WebP bytes
        // Verify bridge invokes pipeline and returns ProcessedSticker with 512x512 dimensions <= 95 KB
    }

    @Test
    fun downloadFailureReturnsEarlyWithoutInvokingPipeline() = runTest {
        // Mock repository returning StickerResult.Failure(StickerError.ResourceNotFound)
        // Verify bridge returns Failure immediately
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.EmoteStickerBridgeTest`
Expected: FAIL

- [ ] **Step 3: Implement `EmoteStickerBridge`**

Orchestrate emote downloading $\to$ `RawMediaInput` packaging $\to$ `mediaPipeline.processSticker(...)` with optional static fallback.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.EmoteStickerBridgeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/data/repository/EmoteStickerBridge.kt app/src/test/java/com/seventv/stickers/data/repository/EmoteStickerBridgeTest.kt
git commit -m "feat(data): implement EmoteStickerBridge integrating repository with media pipeline"
```

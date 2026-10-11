# Media Pipeline & Android Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the Android project base (`:app` module with Gradle Kotlin DSL and CMake/NDK) and implement the media processing engine (`core.media`) that normalizes and compresses 7TV emotes into WhatsApp-compliant WebP stickers (static $<100\text{ KB}$ and animated $<500\text{ KB}$).

**Architecture:** A clean single-module Android app structured by decoupled Clean Architecture packages. Geometric transformation and quality stepping run in Kotlin using Android Graphics (`Canvas`, `Bitmap`), while multi-frame Animated WebP assembly delegates through a lightweight JNI wrapper to native `libwebp` (`WebPAnimEncoder`).

**Tech Stack:** Kotlin 2.0+, Android Gradle Plugin 8.5+, Gradle Kotlin DSL (`libs.versions.toml`), Android SDK 34 (Min SDK 26), CMake 3.22.1+, NDK r25b+, C++17 (`libwebp`), JUnit 4 / AndroidX Test.

**Spec:** [docs/superpowers/specs/2026-10-11-media-pipeline-scaffolding-design.md](file:///D:/Workspaces/7tv-whatsapp/docs/superpowers/specs/2026-10-11-media-pipeline-scaffolding-design.md)

## Global Constraints

- Android Min SDK: `26` (Android 8.0 Oreo).
- Android Compile/Target SDK: `34` (Android 14).
- JVM Toolchain: Java `17`.
- Sticker Dimensions: Exactly `512 x 512 px`.
- Sticker Transparent Margin: Exactly `16 px` (usable centered area: `480 x 480 px`).
- Static Sticker File Size: Strictly `< 100 KB` (target ceiling `95 KB`).
- Tray Icon Dimensions: Exactly `96 x 96 px`, `1 px` transparent margin (usable area: `94 x 94 px`), `< 50 KB`.
- Animated Sticker File Size: Strictly `< 500 KB` (target ceiling `480 KB`).
- Animated Sticker Max Duration: Strictly `≤ 3000 ms` (`3.0 s`).
- Animated Sticker Max Framerate: Strictly `≤ 30 fps`.
- Animated WebP Loop: Infinite loop (`loop_count = 0`).
- Error Handling: Sealed functional returns (`StickerResult<T>`), zero unhandled exceptions escaping the pipeline.

## Review Focus

- Emote with extreme aspect ratio (e.g., $128 \times 32\text{ px}$ banner or $32 \times 128\text{ px}$ tall column): Must center accurately within the $480 \times 480\text{ px}$ usable area leaving clean transparent borders on all sides.
- Animated emote exceeding $3000\text{ ms}$: Must cleanly truncate at $3000\text{ ms}$ without crashing or leaving trailing frames.
- Animated emote with high complexity exceeding $480\text{ KB}$ at quality 80: Must automatically step down quality ($60 \to 45$) and decimate framerate ($30 \to 18 \to 15\text{ fps}$) before failing or offering static fallback.
- Emote with alpha transparency: Must preserve transparency with `ARGB_8888` and bilinear filtering without black border halos.
- Corrupted byte input: Must return `StickerResult.Failure(StickerError.CorruptedSourceData)` without throwing `OutOfMemoryError` or unhandled exceptions.

---

### Task 1: Android Project Scaffolding & Gradle Version Catalog

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: Runnable Gradle project structure targeting SDK 34, Min SDK 26, Java 17, and CMake build wiring.

- [ ] **Step 1: Create `gradle/libs.versions.toml`**

Define Version Catalog with AGP `8.5.0`, Kotlin `2.0.0`, Coroutines `1.8.1`, AndroidX Core `1.13.1`, AndroidX Test `1.6.1`, and JUnit `4.13.2`.

- [ ] **Step 2: Create root build files**

Create `settings.gradle.kts` with plugin management and `include(":app")`, `build.gradle.kts` with root plugins, and `gradle.properties` with AndroidX and JVM args.

- [ ] **Step 3: Create `app/build.gradle.kts` and `AndroidManifest.xml`**

Configure `com.android.application` plugin, `namespace = "com.seventv.stickers"`, compileSdk 34, minSdk 26, Java 17 toolchain, and external CMake link (`src/main/cpp/CMakeLists.txt`).

- [ ] **Step 4: Run Gradle build check**

Run: `cmd.exe /c "gradlew.bat --version"` or verify project files syntax.
Expected: Valid Gradle build structure without configuration errors.

- [ ] **Step 5: Commit scaffolding**

```bash
git add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts gradle.properties app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: scaffold android project base with gradle kotlin dsl"
```

---

### Task 2: Core Media Models & Result Types

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/common/result/StickerResult.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/common/result/StickerError.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/model/StickerSpecs.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/model/RawMediaInput.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/model/AnimationFrame.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/model/ProcessedSticker.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/common/result/StickerResultTest.kt`

**Interfaces:**
- Produces: `StickerResult<T>`, `StickerError`, `StickerSpecs`, `RawMediaInput`, `AnimationFrame`, `ProcessedSticker`.

- [ ] **Step 1: Write unit test for `StickerResult`**

```kotlin
class StickerResultTest {
    @Test
    fun successReturnsData() {
        val result: StickerResult<String> = StickerResult.Success("ok")
        assertTrue(result is StickerResult.Success && result.data == "ok")
    }

    @Test
    fun failureReturnsError() {
        val result: StickerResult<String> = StickerResult.Failure(StickerError.CorruptedSourceData)
        assertTrue(result is StickerResult.Failure && result.error == StickerError.CorruptedSourceData)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.common.result.StickerResultTest`
Expected: FAIL (unresolved reference)

- [ ] **Step 3: Implement domain models and result types**

Implement `StickerResult<T>`, `StickerError`, `StickerSpecs` with constants:
- `STICKER_SIZE = 512`
- `STICKER_PADDING = 16`
- `USABLE_AREA = 480`
- `STATIC_MAX_BYTES = 100 * 1024L`
- `STATIC_SAFE_CEILING_BYTES = 95 * 1024L`
- `ANIMATED_MAX_BYTES = 500 * 1024L`
- `ANIMATED_SAFE_CEILING_BYTES = 480 * 1024L`
- `ANIMATED_MAX_DURATION_MS = 3000`
- `ANIMATED_MAX_FPS = 30`
- `TRAY_ICON_SIZE = 96`
- `TRAY_ICON_PADDING = 1`
- `TRAY_ICON_MAX_BYTES = 50 * 1024L`

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.common.result.StickerResultTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/common/ app/src/main/java/com/seventv/stickers/core/media/model/ app/src/test/
git commit -m "feat(media): add domain models, specs constants and StickerResult types"
```

---

### Task 3: Geometric Normalization Engine (`CanvasStickerNormalizer`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/media/normalizer/StickerNormalizer.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/normalizer/CanvasStickerNormalizer.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/media/normalizer/CanvasStickerNormalizerTest.kt`

**Interfaces:**
- Consumes: `StickerSpecs`
- Produces: `StickerNormalizer`
  - `fun normalizeToStickerCanvas(source: Bitmap): Bitmap`
  - `fun normalizeToTrayIcon(source: Bitmap): Bitmap`

- [ ] **Step 1: Write failing unit test for `CanvasStickerNormalizer`**

```kotlin
class CanvasStickerNormalizerTest {
    private val normalizer = CanvasStickerNormalizer()

    @Test
    fun normalizeWideBitmapMaintainsPaddingAndCenters() {
        val wideBitmap = Bitmap.createBitmap(128, 32, Bitmap.Config.ARGB_8888)
        val normalized = normalizer.normalizeToStickerCanvas(wideBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
        // Usable width is 480, height should be 480 * (32/128) = 120, centered vertically
    }

    @Test
    fun normalizeToTrayIconProduces96x96() {
        val squareBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val tray = normalizer.normalizeToTrayIcon(squareBitmap)
        assertEquals(96, tray.width)
        assertEquals(96, tray.height)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.normalizer.CanvasStickerNormalizerTest`
Expected: FAIL (unresolved reference)

- [ ] **Step 3: Implement `CanvasStickerNormalizer`**

Implement scaling algorithm preserving aspect ratio:
`scale = min(usableSize / source.width, usableSize / source.height)`.
Draw centered onto `ARGB_8888` `Canvas` with `Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.normalizer.CanvasStickerNormalizerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/media/normalizer/ app/src/test/java/com/seventv/stickers/core/media/normalizer/
git commit -m "feat(media): implement CanvasStickerNormalizer for 512x512 and 96x96 tray icon"
```

---

### Task 4: Static Sticker Size Optimizer & Compression

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/media/optimizer/StickerSizeOptimizer.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/media/optimizer/StickerSizeOptimizerTest.kt`

**Interfaces:**
- Consumes: `StickerSpecs`, `StickerResult`, `StickerError`
- Produces: `StickerSizeOptimizer`
  - `fun compressStaticWebP(bitmap: Bitmap, outputFile: File): StickerResult<Long>`

- [ ] **Step 1: Write failing unit test for `StickerSizeOptimizer`**

```kotlin
class StickerSizeOptimizerTest {
    private val optimizer = StickerSizeOptimizer()

    @Test
    fun compressStaticWebPSucceedsUnder95KB() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val outFile = File.createTempFile("test_static", ".webp")
        val result = optimizer.compressStaticWebP(bitmap, outFile)
        assertTrue(result is StickerResult.Success)
        assertTrue(outFile.length() <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.optimizer.StickerSizeOptimizerTest`
Expected: FAIL

- [ ] **Step 3: Implement `compressStaticWebP` with progressive quality loop**

Loop qualities `[90, 80, 70, 60, 50]`:
Compress with `Bitmap.CompressFormat.WEBP_LOSSY`. If byte size $\le 95\text{ KB}$, write to `outputFile` and return `StickerResult.Success(bytes)`. If still exceeding $95\text{ KB}$, scale down bounding box by 10% and retry.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.optimizer.StickerSizeOptimizerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/media/optimizer/ app/src/test/java/com/seventv/stickers/core/media/optimizer/
git commit -m "feat(media): implement static WebP progressive size optimizer"
```

---

### Task 5: Frame Extractor & Duration / FPS Throttler

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/media/animated/FrameThrottler.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/animated/FrameExtractor.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/media/animated/FrameThrottlerTest.kt`

**Interfaces:**
- Consumes: `AnimationFrame`, `StickerSpecs`
- Produces: `FrameThrottler`
  - `fun truncateDuration(frames: List<AnimationFrame>, maxDurationMs: Int = 3000): List<AnimationFrame>`
  - `fun decimateFramerate(frames: List<AnimationFrame>, targetFps: Int): List<AnimationFrame>`
- Produces: `FrameExtractor`
  - `fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>>`

- [ ] **Step 1: Write failing unit test for `FrameThrottler`**

```kotlin
class FrameThrottlerTest {
    private val throttler = FrameThrottler()

    @Test
    fun truncateDurationCutsAt3000Ms() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 1000, 1000),
            AnimationFrame(mockBitmap(), 1000, 2000),
            AnimationFrame(mockBitmap(), 1000, 3000),
            AnimationFrame(mockBitmap(), 1000, 4000)
        )
        val truncated = throttler.truncateDuration(frames, 3000)
        assertEquals(3, truncated.size)
        assertTrue(truncated.last().timestampMs <= 3000)
    }

    @Test
    fun decimateFramerateMergesDroppedFrameDurations() {
        val frames = (0 until 30).map { i ->
            AnimationFrame(mockBitmap(), 33, (i + 1) * 33)
        }
        val decimated = throttler.decimateFramerate(frames, targetFps = 15)
        assertEquals(15, decimated.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.animated.FrameThrottlerTest`
Expected: FAIL

- [ ] **Step 3: Implement `FrameThrottler` and `FrameExtractor`**

Implement `truncateDuration` accumulating `durationMs` up to `maxDurationMs`.
Implement `decimateFramerate` skipping intermediate frames and accumulating their durations onto retained frames.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.animated.FrameThrottlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/media/animated/ app/src/test/java/com/seventv/stickers/core/media/animated/
git commit -m "feat(media): implement frame throttler for duration truncation and fps decimation"
```

---

### Task 6: Native WebP Anim Encoder (C++ CMake & JNI)

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/webp_anim_encoder.cpp`
- Create: `app/src/main/java/com/seventv/stickers/core/media/animated/AnimatedWebPEncoder.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/media/animated/NativeWebPAnimEncoder.kt`
- Create: `app/src/androidTest/java/com/seventv/stickers/core/media/animated/NativeWebPAnimEncoderTest.kt`

**Interfaces:**
- Produces: `AnimatedWebPEncoder`
  - `fun encode(bitmaps: Array<Bitmap>, timestampsMs: IntArray, quality: Float, outputPath: String): Boolean`

- [ ] **Step 1: Write instrumented test for `NativeWebPAnimEncoder`**

```kotlin
@RunWith(AndroidJUnit4::class)
class NativeWebPAnimEncoderTest {
    @Test
    fun nativeLibraryLoadsSuccessfully() {
        val encoder = NativeWebPAnimEncoder()
        assertNotNull(encoder)
    }

    @Test
    fun encodeGeneratesValidWebPFile() {
        val encoder = NativeWebPAnimEncoder()
        val frames = Array(3) { Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888) }
        val timestamps = intArrayOf(0, 200, 400)
        val tempFile = File.createTempFile("anim_test", ".webp")
        val success = encoder.encode(frames, timestamps, 75f, tempFile.absolutePath)
        assertTrue(success)
        assertTrue(tempFile.length() > 0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew connectedDebugAndroidTest --tests com.seventv.stickers.core.media.animated.NativeWebPAnimEncoderTest`
Expected: FAIL (library or class missing)

- [ ] **Step 3: Implement `CMakeLists.txt` and `webp_anim_encoder.cpp`**

Configure `CMakeLists.txt` linking `jnigraphics`, `log`, and `libwebp`.
Implement JNI method:
`Java_com_seventv_stickers_core_media_animated_NativeWebPAnimEncoder_encodeAnimation`
using `AndroidBitmap_lockPixels`, `WebPPictureImportRGBA`, `WebPAnimEncoderAdd`, `WebPAnimEncoderAssemble`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew connectedDebugAndroidTest --tests com.seventv.stickers.core.media.animated.NativeWebPAnimEncoderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/ app/src/main/java/com/seventv/stickers/core/media/animated/ app/src/androidTest/
git commit -m "feat(media): implement native animated WebP encoder using libwebp and JNI"
```

---

### Task 7: StickerMediaPipeline Facade & Integration

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/media/StickerMediaPipeline.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/media/StickerMediaPipelineTest.kt`

**Interfaces:**
- Consumes: `StickerNormalizer`, `StickerSizeOptimizer`, `FrameExtractor`, `FrameThrottler`, `AnimatedWebPEncoder`
- Produces: `StickerMediaPipeline`
  - `suspend fun processSticker(input: RawMediaInput, outputDir: File): StickerResult<ProcessedSticker>`
  - `suspend fun processTrayIcon(sourceBitmap: Bitmap, outputFile: File): StickerResult<File>`
  - `suspend fun processAsStaticFallback(input: RawMediaInput, outputDir: File): StickerResult<ProcessedSticker>`

- [ ] **Step 1: Write unit test for `StickerMediaPipeline`**

```kotlin
class StickerMediaPipelineTest {
    @Test
    fun processStickerStaticReturnsProcessedStickerUnderLimit() = runTest {
        val pipeline = StickerMediaPipeline(...)
        val input = RawMediaInput(bytes = sampleStaticBytes, isAnimated = false)
        val result = pipeline.processSticker(input, tempDir)
        assertTrue(result is StickerResult.Success)
        assertEquals(512, result.data.width)
        assertEquals(512, result.data.height)
        assertTrue(result.data.byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
    }

    @Test
    fun corruptedDataReturnsCorruptedSourceDataError() = runTest {
        val pipeline = StickerMediaPipeline(...)
        val input = RawMediaInput(bytes = byteArrayOf(0, 1, 2), isAnimated = false)
        val result = pipeline.processSticker(input, tempDir)
        assertTrue(result is StickerResult.Failure && result.error == StickerError.CorruptedSourceData)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.StickerMediaPipelineTest`
Expected: FAIL

- [ ] **Step 3: Implement `StickerMediaPipeline` facade**

Orchestrate normalization, frame throttling, optimization loop, and fallback logic:
- Route static vs animated.
- Enforce safe limits ($\le 95\text{ KB}$ static, $\le 480\text{ KB}$ animated).
- Return `ProcessedSticker` with final dimensions and byte size.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew testDebugUnitTest --tests com.seventv.stickers.core.media.StickerMediaPipelineTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/core/media/StickerMediaPipeline.kt app/src/test/java/com/seventv/stickers/core/media/StickerMediaPipelineTest.kt
git commit -m "feat(media): implement StickerMediaPipeline facade and end-to-end integration"
```

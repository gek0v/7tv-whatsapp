# Subproject 3: Local Persistence & Pack Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

This implementation plan details the step-by-step construction of **Subproject 3: Local Persistence & Pack Management Engine** for the 7TV Stickers WhatsApp Android application.

---

## Architecture & Interfaces Reference

- **Design Spec:** [`docs/superpowers/specs/2026-10-12-local-persistence-pack-management-design.md`](file:///D:/Workspaces/7tv-whatsapp/docs/superpowers/specs/2026-10-12-local-persistence-pack-management-design.md)
- **Target Android SDK:** Compile SDK 34, Min SDK 26, Kotlin 2.0.0
- **Key Modules & Packages:**
  - `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
  - `app/src/main/java/com/seventv/stickers/core/domain/model/StickerPack.kt`
  - `app/src/main/java/com/seventv/stickers/core/domain/model/PackSticker.kt`
  - `app/src/main/java/com/seventv/stickers/core/domain/engine/PackSplittingEngine.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/converter/EmojiListConverter.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/entity/StickerPackEntity.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/entity/StickerEntity.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/entity/PackWithStickers.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/dao/StickerPackDao.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/dao/StickerDao.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/AppDatabase.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/storage/StickerStorageManager.kt`
  - `app/src/main/java/com/seventv/stickers/data/local/storage/DefaultStickerStorageManager.kt`
  - `app/src/main/java/com/seventv/stickers/data/repository/StickerPackRepository.kt`
  - `app/src/main/java/com/seventv/stickers/data/repository/DefaultStickerPackRepository.kt`

---

## Tasks Breakdown

### Task 1: Room 2.6.1 & KSP Plugin Setup

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Supplies: KSP `2.0.0-1.0.24`, Room `2.6.1` dependencies (`room-runtime`, `room-ktx`, `room-compiler`, `room-testing`).

- [ ] **Step 1: Update `gradle/libs.versions.toml`**
  Add versions:
  `ksp = "2.0.0-1.0.24"`
  `room = "2.6.1"`
  Add libraries:
  `room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }`
  `room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }`
  `room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }`
  `room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }`
  Add plugin:
  `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`

- [ ] **Step 2: Update `build.gradle.kts` and `app/build.gradle.kts`**
  Apply KSP plugin in root (`alias(libs.plugins.ksp) apply false`) and `:app` (`alias(libs.plugins.ksp)`).
  Add `room-runtime`, `room-ktx`, `ksp(libs.room.compiler)`, and `testImplementation(libs.room.testing)`.

- [ ] **Step 3: Verify compilation**
  Run: `cmd.exe /c "gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin"`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
  ```bash
  git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
  git commit -m "build(deps): add KSP and Room 2.6.1 dependencies"
  ```

---

### Task 2: Domain Models & Error Extensions

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/core/common/result/StickerError.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/domain/model/StickerPack.kt`
- Create: `app/src/main/java/com/seventv/stickers/core/domain/model/PackSticker.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/domain/model/DomainModelsTest.kt`

**Interfaces:**
- Consumes: `StickerError`
- Produces: `StickerPack`, `PackSticker`, `InsufficientStickers`, `PackNotFound`

- [ ] **Step 1: Write unit tests in `DomainModelsTest.kt`**
  Test creation, copy, and equality of `StickerPack` and `PackSticker`.
  Test error type instances (`InsufficientStickers`, `PackNotFound`).

- [ ] **Step 2: Run test to verify it fails (RED)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.domain.model.DomainModelsTest"`

- [ ] **Step 3: Implement domain models and error extensions**
  Add `data class InsufficientStickers(val actual: Int, val min: Int = 3) : StickerError` and `data class PackNotFound(val identifier: String) : StickerError` to `StickerError.kt`.
  Implement `StickerPack` and `PackSticker`.

- [ ] **Step 4: Run test to verify it passes (GREEN)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.domain.model.DomainModelsTest"`

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/seventv/stickers/core/common/result/StickerError.kt app/src/main/java/com/seventv/stickers/core/domain/model/ app/src/test/java/com/seventv/stickers/core/domain/model/
  git commit -m "feat(domain): implement pack domain models and persistence error types"
  ```

---

### Task 3: PackSplittingEngine (Balanced Partitioning & Format Segregation)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/core/domain/engine/PackSplittingEngine.kt`
- Create: `app/src/test/java/com/seventv/stickers/core/domain/engine/PackSplittingEngineTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmote`, `StickerResult`, `StickerError.InsufficientStickers`
- Produces: `PackSplittingEngine`, `SplitPlan(packName: String, isAnimated: Boolean, emotes: List<SevenTvEmote>)`

- [ ] **Step 1: Write unit tests in `PackSplittingEngineTest.kt`**
  - `rejectsLessThan3Emotes`: 0, 1, 2 emotes return `StickerResult.Failure(InsufficientStickers)`.
  - `keeps3To30EmotesInSinglePack`: 3 emotes $\to$ 1 pack with 3 items; 30 emotes $\to$ 1 pack with 30 items.
  - `splits31EmotesIntoBalanced16And15`: 31 emotes $\to$ 2 packs of sizes 16 and 15, named "Part 1" and "Part 2".
  - `splits65EmotesIntoBalanced22_22_21`: 65 emotes $\to$ 3 packs of sizes 22, 22, 21.
  - `splits90EmotesInto30_30_30`: 90 emotes $\to$ 3 packs of sizes 30, 30, 30.
  - `segregatesMixedAnimatedAndStaticEmotes`: 10 static + 10 animated $\to$ 2 packs ("(Estáticos)" and "(Animados)").
  - `segregatesMixedWithMultiPartSplitting`: 35 static + 10 animated $\to$ 2 static packs (18, 17) + 1 animated pack (10).

- [ ] **Step 2: Run test to verify it fails (RED)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.domain.engine.PackSplittingEngineTest"`

- [ ] **Step 3: Implement `PackSplittingEngine`**
  Implement balanced chunk algorithm ($K = \lceil N/30 \rceil$, $B = \lfloor N/K \rfloor$, $R = N \pmod K$) and format segregation logic.

- [ ] **Step 4: Run test to verify it passes (GREEN)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.core.domain.engine.PackSplittingEngineTest"`

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/seventv/stickers/core/domain/engine/ app/src/test/java/com/seventv/stickers/core/domain/engine/
  git commit -m "feat(domain): implement PackSplittingEngine with balanced partitioning"
  ```

---

### Task 4: Room Entities, TypeConverter, DAOs & AppDatabase

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/data/local/converter/EmojiListConverter.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/entity/StickerPackEntity.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/entity/StickerEntity.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/entity/PackWithStickers.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/dao/StickerPackDao.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/dao/StickerDao.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/AppDatabase.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/local/EmojiListConverterTest.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/local/StickerDatabaseTest.kt`

**Interfaces:**
- Produces: `EmojiListConverter`, `StickerPackEntity`, `StickerEntity`, `PackWithStickers`, `StickerPackDao`, `StickerDao`, `AppDatabase`

- [ ] **Step 1: Write unit tests in `EmojiListConverterTest.kt` and `StickerDatabaseTest.kt`**
  - `EmojiListConverterTest`: converts empty, single, multi-emoji, limits to 3.
  - `StickerDatabaseTest` (Robolectric with `Room.inMemoryDatabaseBuilder`):
    - Insert pack with stickers.
    - Query pack with stickers relation.
    - Update whitelist status.
    - Cascade deletion: deleting pack deletes all child stickers automatically.
    - Observe flow updates.

- [ ] **Step 2: Run test to verify it fails (RED)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.StickerDatabaseTest"`

- [ ] **Step 3: Implement entities, DAOs, converter, and database**
  Implement `EmojiListConverter`, `StickerPackEntity`, `StickerEntity`, `PackWithStickers`, `StickerPackDao`, `StickerDao`, `AppDatabase`.

- [ ] **Step 4: Run test to verify it passes (GREEN)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.StickerDatabaseTest"`

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/seventv/stickers/data/local/ app/src/test/java/com/seventv/stickers/data/local/
  git commit -m "feat(data): implement Room entities, DAOs, type converters, and AppDatabase"
  ```

---

### Task 5: StickerStorageManager (Internal Disk Storage & Atomic Writes)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/data/local/storage/StickerStorageManager.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/local/storage/DefaultStickerStorageManager.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/local/storage/StickerStorageManagerTest.kt`

**Interfaces:**
- Produces: `StickerStorageManager`, `DefaultStickerStorageManager`

- [ ] **Step 1: Write unit tests in `StickerStorageManagerTest.kt` (Robolectric)**
  - `saveStickerFileWritesBytesAtomically`: Writes sticker file to `sticker_packs/{packId}/sticker_{id}.webp`.
  - `saveTrayIconWritesTrayFile`: Writes `tray_icon.webp`.
  - `deletePackDirectoryRemovesAllFiles`: Deletes directory and files recursively.
  - `deleteStickerFileRemovesSingleFile`: Deletes individual sticker.
  - `getAbsoluteFileResolvesCorrectly`: Returns correct `File` handle.

- [ ] **Step 2: Run test to verify it fails (RED)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.storage.StickerStorageManagerTest"`

- [ ] **Step 3: Implement `DefaultStickerStorageManager`**
  Implement directory creation, atomic file writing with `.tmp` and `Files.move`, and safe recursive deletion.

- [ ] **Step 4: Run test to verify it passes (GREEN)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.storage.StickerStorageManagerTest"`

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/seventv/stickers/data/local/storage/ app/src/test/java/com/seventv/stickers/data/local/storage/
  git commit -m "feat(data): implement DefaultStickerStorageManager for atomic disk storage"
  ```

---

### Task 6: StickerPackRepository Implementation & Integration

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/data/repository/StickerPackRepository.kt`
- Create: `app/src/main/java/com/seventv/stickers/data/repository/DefaultStickerPackRepository.kt`
- Create: `app/src/test/java/com/seventv/stickers/data/repository/DefaultStickerPackRepositoryTest.kt`

**Interfaces:**
- Consumes: `StickerPackDao`, `StickerDao`, `StickerStorageManager`, `EmoteStickerBridge`, `StickerMediaPipeline`, `PackSplittingEngine`
- Produces: `StickerPackRepository`, `DefaultStickerPackRepository`

- [ ] **Step 1: Write unit tests in `DefaultStickerPackRepositoryTest.kt` (Robolectric)**
  - `createPacksFromEmotesSplitsAndPersistsPacks`: Takes 35 emotes, splits into 2 packs, processes stickers and tray icons, persists to Room and disk, returns `StickerResult.Success`.
  - `observeAllPacksEmitsChanges`: Verifies `Flow` emits updated pack lists.
  - `deletePackRemovesDatabaseAndDiskFiles`: Verifies deleting pack deletes Room entity and removes disk folder.
  - `updateStickerEmojisUpdatesDatabase`: Verifies emoji updates.
  - `updatePackWhitelistedUpdatesStatus`: Verifies WhatsApp whitelisted status flag.

- [ ] **Step 2: Run test to verify it fails (RED)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.DefaultStickerPackRepositoryTest"`

- [ ] **Step 3: Implement `DefaultStickerPackRepository`**
  Coordinate splitting plan, media processing via `EmoteStickerBridge`, tray icon generation via `mediaPipeline.processTrayIcon`, storage via `storageManager`, and transactional persistence in `stickerPackDao`.

- [ ] **Step 4: Run test to verify it passes (GREEN)**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.DefaultStickerPackRepositoryTest"`

- [ ] **Step 5: Run full project regression test**
  Run: `cmd.exe /c "gradlew.bat testDebugUnitTest"`
  Expected: All tests pass across the entire codebase.

- [ ] **Step 6: Commit**
  ```bash
  git add app/src/main/java/com/seventv/stickers/data/repository/ app/src/test/java/com/seventv/stickers/data/repository/
  git commit -m "feat(data): implement DefaultStickerPackRepository coordinating pipeline and Room"
  ```

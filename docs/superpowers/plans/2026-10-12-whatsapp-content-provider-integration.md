# Subproject 4: WhatsApp Native Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement native integration with WhatsApp Standard (`com.whatsapp`), exposing stored sticker packs and assets via `StickerContentProvider`, supporting IPC MatrixCursors, `AssetFileDescriptor` streaming, intent launching, and whitelist verification.

**Architecture:** A custom `StickerContentProvider` (`com.seventv.stickers.stickercontentprovider`) backed by Room (`StickerPackDao`) and `StickerStorageManager` serves sticker metadata and raw WebP files to WhatsApp via standard IPC. `WhatsAppIntentHelper` and `WhatsAppWhitelistChecker` provide package visibility, installation detection, intent building, and installed-state queries.

**Tech Stack:** Kotlin 2.0, Android ContentProvider IPC, Android UriMatcher, MatrixCursor, AssetFileDescriptor / ParcelFileDescriptor, Room 2.6.1, Robolectric 4.12.2, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-12-whatsapp-content-provider-integration-design.md`

## Global Constraints

- Authority string: `com.seventv.stickers.stickercontentprovider`
- Read permission: `com.whatsapp.sticker.READ`
- Target WhatsApp package: `com.whatsapp`
- Intent action: `com.whatsapp.intent.action.ENABLE_STICKER_PACK`
- Whitelist authority: `com.whatsapp.provider.sticker_whitelist_check`
- Whitelist path: `is_whitelisted`
- Whitelist result column: `result`
- Tray icon file name: `tray_icon.webp`
- Sticker file prefix: `sticker_`
- Image data version: `"1"`
- Avoid cache: `0`
- Animated sticker pack flag: `1` if animated else `0`
- Package visibility: `<queries>` in `AndroidManifest.xml` targeting `com.whatsapp` and `com.whatsapp.intent.action.ENABLE_STICKER_PACK`

## Review Focus

1. Path traversal attempt in `openAssetFile` (e.g. `../` in file name): must be prevented and throw `FileNotFoundException` or `SecurityException`.
2. Unsupported open mode in `openAssetFile` (e.g. `"w"`, `"rw"`): must throw `IllegalArgumentException` or `SecurityException`.
3. Unknown or malformed URIs queried in `StickerContentProvider`: must throw `IllegalArgumentException` or return empty/null without crashing.
4. Non-existent pack queried in `/metadata/{packId}` or `/stickers/{packId}`: returns empty `MatrixCursor` (0 rows) gracefully.
5. Whitelist query when WhatsApp is missing or provider throws: returns `false` without crashing or throwing unhandled exceptions.

---

### Task 1: Package Visibility (`<queries>`), Manifest Provider Declaration & `WhatsAppConstants`

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppConstants.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppConstantsTest.kt`

**Interfaces:**
- Consumes: Nothing
- Produces: `WhatsAppConstants` containing all protocol authority strings, column names, intent actions, and extras.

- [ ] **Step 1: Write failing test in `WhatsAppConstantsTest.kt`**

```kotlin
package com.seventv.stickers.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppConstantsTest {
    @Test
    fun testConstantsValues() {
        assertEquals("com.whatsapp", WhatsAppConstants.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.intent.action.ENABLE_STICKER_PACK", WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK)
        assertEquals("sticker_pack_id", WhatsAppConstants.EXTRA_STICKER_PACK_ID)
        assertEquals("sticker_pack_authority", WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY)
        assertEquals("sticker_pack_name", WhatsAppConstants.EXTRA_STICKER_PACK_NAME)
        assertEquals("sticker_pack_identifier", WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)
        assertEquals("sticker_pack_name", WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY)
        assertEquals("sticker_pack_publisher", WhatsAppConstants.STICKER_PACK_PUBLISHER_IN_QUERY)
        assertEquals("sticker_pack_icon", WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY)
        assertEquals("animated_sticker_pack", WhatsAppConstants.ANIMATED_STICKER_PACK)
        assertEquals("sticker_file_name", WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY)
        assertEquals("sticker_emojis", WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY)
        assertEquals("com.whatsapp.provider.sticker_whitelist_check", WhatsAppConstants.WHITELIST_AUTHORITY)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppConstantsTest"`
Expected: FAIL (unresolved reference `WhatsAppConstants`)

- [ ] **Step 3: Implement `WhatsAppConstants.kt` and update `AndroidManifest.xml`**

Create `app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppConstants.kt` with all 13 metadata column names, sticker columns, whitelist URI constants, and intent action strings.
Update `app/src/main/AndroidManifest.xml` to include:
```xml
    <queries>
        <package android:name="com.whatsapp" />
        <intent>
            <action android:name="com.whatsapp.intent.action.ENABLE_STICKER_PACK" />
        </intent>
    </queries>
```
and declare `<provider>` for `StickerContentProvider` with `android:authorities="com.seventv.stickers.stickercontentprovider"`, `android:exported="true"`, `android:readPermission="com.whatsapp.sticker.READ"`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppConstantsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppConstants.kt app/src/main/AndroidManifest.xml app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppConstantsTest.kt
git commit -m "feat(whatsapp): add WhatsAppConstants and manifest provider configuration"
```

---

### Task 2: Synchronous Direct Access DAO Queries & `AppDatabase` Singleton

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/data/local/dao/StickerPackDao.kt`
- Modify: `app/src/main/java/com/seventv/stickers/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/seventv/stickers/data/local/StickerDatabaseTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `PackWithStickers`, `StickerPackEntity`, `StickerEntity`
- Produces:
  - `StickerPackDao.getAllPacksDirect(): List<PackWithStickers>`
  - `StickerPackDao.getPackByIdentifierDirect(identifier: String): PackWithStickers?`
  - `AppDatabase.getInstance(context: Context): AppDatabase`
  - `AppDatabase.setTestInstance(testInstance: AppDatabase?)`

- [ ] **Step 1: Write failing tests in `StickerDatabaseTest.kt`**

Add tests exercising `getAllPacksDirect()` and `getPackByIdentifierDirect(identifier)` verifying synchronous retrieval of packs with their associated stickers ordered by `sortOrder`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.StickerDatabaseTest"`
Expected: FAIL (unresolved reference `getAllPacksDirect`)

- [ ] **Step 3: Implement synchronous DAO methods and singleton pattern in `AppDatabase`**

In `StickerPackDao.kt`:
```kotlin
@Transaction
@Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
fun getAllPacksDirect(): List<PackWithStickers>

@Transaction
@Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
fun getPackByIdentifierDirect(identifier: String): PackWithStickers?
```

In `AppDatabase.kt`:
Add thread-safe singleton `getInstance(context: Context): AppDatabase` and `@VisibleForTesting fun setTestInstance(testInstance: AppDatabase?)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.local.StickerDatabaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/data/local/dao/StickerPackDao.kt app/src/main/java/com/seventv/stickers/data/local/AppDatabase.kt app/src/test/java/com/seventv/stickers/data/local/StickerDatabaseTest.kt
git commit -m "feat(data): add synchronous DAO queries and AppDatabase singleton"
```

---

### Task 3: `StickerContentProvider` Core Implementation & Tests

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/whatsapp/StickerContentProvider.kt`
- Create: `app/src/test/java/com/seventv/stickers/whatsapp/StickerContentProviderTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `StickerStorageManager`, `WhatsAppConstants`
- Produces: Fully functional `StickerContentProvider` serving:
  - `content://com.seventv.stickers.stickercontentprovider/metadata`
  - `content://com.seventv.stickers.stickercontentprovider/metadata/{packId}`
  - `content://com.seventv.stickers.stickercontentprovider/stickers/{packId}`
  - `content://com.seventv.stickers.stickercontentprovider/stickers_asset/{packId}/{fileName}`
  - `getType(uri)` for each URI pattern
  - `openAssetFile(uri, mode)` with path-traversal and mode validation

- [ ] **Step 1: Write failing tests in `StickerContentProviderTest.kt`**

Write Robolectric unit tests covering:
- Query `/metadata`: verifies `MatrixCursor` with all 13 columns, correct row count, and `animated_sticker_pack` (1 for animated, 0 for static).
- Query `/metadata/{packId}`: returns single row matching pack or empty cursor if not found.
- Query `/stickers/{packId}`: returns `sticker_file_name` and comma-separated `sticker_emojis` ordered by `sortOrder`.
- `getType(uri)`: returns directory/item MIME types and `image/webp`.
- `openAssetFile(uri, "r")`: returns `AssetFileDescriptor` for existing file.
- Review Focus 1: `openAssetFile(uri, "r")` with path traversal (e.g. `../../secret.txt`) throws `FileNotFoundException` or `SecurityException`.
- Review Focus 2: `openAssetFile(uri, "w")` throws `IllegalArgumentException`.
- Review Focus 3: Query on invalid URI throws `IllegalArgumentException`.
- Non-supported operations: `insert`, `update`, `delete` throw `UnsupportedOperationException`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.StickerContentProviderTest"`
Expected: FAIL (unresolved reference `StickerContentProvider`)

- [ ] **Step 3: Implement `StickerContentProvider.kt`**

Implement `StickerContentProvider` extending `ContentProvider`:
- `UriMatcher` registering `metadata` (1), `metadata/*` (2), `stickers/*` (3), `stickers_asset/*/*` (4).
- `onCreate()` initializes `database = AppDatabase.getInstance(context)` and `storageManager = DefaultStickerStorageManager(context.filesDir)`. Allow test overrides via internal/visible-for-testing properties or `AppDatabase.setTestInstance`.
- `query()` dispatches to `getPackMetadataCursor`, `getSinglePackMetadataCursor`, `getStickersCursor`.
- `openAssetFile()` checks `mode == "r"`, sanitizes path against traversal using `file.canonicalFile.parentFile == packDir.canonicalFile`, and returns `AssetFileDescriptor(ParcelFileDescriptor.open(file, MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)`.
- `getType()` returns MIME types.
- `insert()`, `update()`, `delete()` throw `UnsupportedOperationException`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.StickerContentProviderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/whatsapp/StickerContentProvider.kt app/src/test/java/com/seventv/stickers/whatsapp/StickerContentProviderTest.kt
git commit -m "feat(whatsapp): implement StickerContentProvider with IPC cursors and AssetFileDescriptor"
```

---

### Task 4: `WhatsAppIntentHelper` (Package Detection & Add Pack Intent) & Tests

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppIntentHelper.kt`
- Create: `app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppIntentHelperTest.kt`

**Interfaces:**
- Consumes: `WhatsAppConstants`, `StickerContentProvider.AUTHORITY`
- Produces:
  - `WhatsAppIntentHelper.isWhatsAppInstalled(context: Context): Boolean`
  - `WhatsAppIntentHelper.createAddPackIntent(authority: String, packIdentifier: String, packName: String): Intent`

- [ ] **Step 1: Write failing tests in `WhatsAppIntentHelperTest.kt`**

Write Robolectric unit tests covering:
- `isWhatsAppInstalled(context)` returns `true` when package `com.whatsapp` is installed (using Robolectric `packageManager.installPackage`).
- `isWhatsAppInstalled(context)` returns `false` when package `com.whatsapp` is not installed.
- `createAddPackIntent(...)` builds `Intent` with action `ENABLE_STICKER_PACK`, target package `com.whatsapp`, and extras `sticker_pack_authority`, `sticker_pack_id`, `sticker_pack_name`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppIntentHelperTest"`
Expected: FAIL (unresolved reference `WhatsAppIntentHelper`)

- [ ] **Step 3: Implement `WhatsAppIntentHelper.kt`**

Implement `WhatsAppIntentHelper`:
- `isWhatsAppInstalled(context: Context): Boolean` wraps `packageManager.getPackageInfo(WhatsAppConstants.PACKAGE_WHATSAPP, 0)` in `try/catch (NameNotFoundException)`.
- `createAddPackIntent(authority: String = StickerContentProvider.AUTHORITY, packIdentifier: String, packName: String): Intent` constructs the required explicit intent.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppIntentHelperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppIntentHelper.kt app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppIntentHelperTest.kt
git commit -m "feat(whatsapp): implement WhatsAppIntentHelper for package check and intent creation"
```

---

### Task 5: `WhatsAppWhitelistChecker` (Provider Query & State Verification) & Tests

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppWhitelistChecker.kt`
- Create: `app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppWhitelistCheckerTest.kt`

**Interfaces:**
- Consumes: `WhatsAppConstants`, `StickerContentProvider.AUTHORITY`
- Produces: `WhatsAppWhitelistChecker.isPackWhitelisted(context: Context, packIdentifier: String, authority: String = StickerContentProvider.AUTHORITY): Boolean`

- [ ] **Step 1: Write failing tests in `WhatsAppWhitelistCheckerTest.kt`**

Write Robolectric tests:
- Returns `true` when ContentResolver returns a cursor with `result = 1`.
- Returns `false` when ContentResolver returns a cursor with `result = 0`.
- Returns `false` when ContentResolver returns `null` (WhatsApp not installed or provider absent).
- Review Focus 5: Returns `false` when ContentResolver query throws an exception (SecurityException / IllegalArgumentException) without bubbling the exception.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppWhitelistCheckerTest"`
Expected: FAIL (unresolved reference `WhatsAppWhitelistChecker`)

- [ ] **Step 3: Implement `WhatsAppWhitelistChecker.kt`**

Implement `WhatsAppWhitelistChecker.isPackWhitelisted(context, packIdentifier, authority)`:
- Builds query URI `content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted?sticker_pack_authority=<authority>&sticker_pack_id=<identifier>`.
- Queries content resolver safely inside `try/catch`.
- Extracts `result` column, returning `result == 1`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.whatsapp.WhatsAppWhitelistCheckerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppWhitelistChecker.kt app/src/test/java/com/seventv/stickers/whatsapp/WhatsAppWhitelistCheckerTest.kt
git commit -m "feat(whatsapp): implement WhatsAppWhitelistChecker with safe IPC query"
```

---

### Task 6: Repository Integration, Diagnostic Verification & End-to-End Build

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/data/repository/StickerPackRepository.kt`
- Modify: `app/src/main/java/com/seventv/stickers/data/repository/DefaultStickerPackRepository.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/MainActivity.kt`
- Modify: `app/src/test/java/com/seventv/stickers/data/repository/DefaultStickerPackRepositoryTest.kt`

**Interfaces:**
- Consumes: `StickerPackRepository`, `WhatsAppWhitelistChecker`, `WhatsAppIntentHelper`
- Produces:
  - `StickerPackRepository.syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean>`
  - Updated diagnostic status in `MainActivity` verifying WhatsApp installation and provider endpoints.

- [ ] **Step 1: Write failing test in `DefaultStickerPackRepositoryTest.kt`**

Add unit test for `syncWhitelistStatus(...)` verifying that querying whitelist and updating Room updates `StickerPackEntity.isWhitelisted` and emits the updated state via `observeAllPacks()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.data.repository.DefaultStickerPackRepositoryTest"`
Expected: FAIL (unresolved reference `syncWhitelistStatus`)

- [ ] **Step 3: Implement `syncWhitelistStatus` and update `MainActivity.kt`**

Implement `syncWhitelistStatus` in `StickerPackRepository` and `DefaultStickerPackRepository`.
Update `MainActivity.kt` diagnostic report to display:
- WhatsApp installation status (`WhatsAppIntentHelper.isWhatsAppInstalled(this)`)
- Number of packs exposed to `StickerContentProvider`
- Ability to test adding a pack via intent.

- [ ] **Step 4: Run full test suite to verify everything passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest"`
Expected: All tests PASS across the entire project.

- [ ] **Step 5: Build Debug APK**

Run: `cmd.exe /c "gradlew.bat assembleDebug"`
Expected: BUILD SUCCESSFUL producing `app-debug.apk`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/data/repository/StickerPackRepository.kt app/src/main/java/com/seventv/stickers/data/repository/DefaultStickerPackRepository.kt app/src/main/java/com/seventv/stickers/ui/MainActivity.kt app/src/test/java/com/seventv/stickers/data/repository/DefaultStickerPackRepositoryTest.kt
git commit -m "feat(whatsapp): integrate whitelist sync in repository and update MainActivity diagnostic"
```

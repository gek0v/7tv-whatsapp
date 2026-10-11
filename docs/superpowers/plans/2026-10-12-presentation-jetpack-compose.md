# Subproject 5: Presentation Layer & Jetpack Compose UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a responsive native Android UI in Jetpack Compose and Material 3 for the 7TV to WhatsApp sticker app, featuring 3-tab navigation (Explorar, Canales, Mis Packs), real-time emote search with debounce, animated WebP rendering via Coil, active draft pack banner, channel import with automatic pack splitting, and direct WhatsApp export.

**Architecture:** MVI/MVVM architecture built with Jetpack Compose, Material 3 Dark Theme tokens, Navigation Compose, and StateFlow. ViewModels use constructor injection via `ViewModelProvider.Factory` powered by an `AppContainer` service locator in `SevenTvApplication`. Repositories (`SevenTvRepository`, `StickerPackRepository`) supply data to reactive screens.

**Tech Stack:** Kotlin 2.0.0, Jetpack Compose (BOM 2024.06.00), Material 3, Navigation Compose 2.7.7, Coil 2.6.0 (with Animated WebP support), Robolectric 4.12.2, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-12-presentation-jetpack-compose-design.md`

## Global Constraints

- Kotlin: 2.0.0
- Compose Compiler: `org.jetbrains.kotlin.plugin.compose` version 2.0.0
- Compose BOM: `2024.06.00`
- Min SDK: 26, Compile / Target SDK: 34
- Theme: Dark Theme prioritized with tokens from `Design.md`:
  - Background: `#0E0F12`
  - Surface: `#16181D`
  - Surface Variant: `#22252C`
  - Primary (7TV Cyan): `#00D2FF`
  - Secondary (Twitch Purple): `#9B51E0`
  - Tertiary (WhatsApp Green): `#25D366`
  - Error: `#FF4858`
  - OnSurface: `#F1F3F5`
  - OnSurfaceVariant: `#9AA0A6`
- Chat Background Simulator modes: WhatsApp Dark (`#0B141A`), WhatsApp Light (`#EFEAE2`), Checkerboard (`#2A2E39`/`#1E2028`)
- WhatsApp Pack limits: minimum 3, maximum 30 stickers per pack
- Image loading: Coil 2.6.0 with `ImageDecoderDecoder` / `GifDecoder` for continuous WebP animation playback

## Review Focus

1. **State mutation on draft pack:** adding an emote already present in the draft pack should not create duplicates; adding when draft pack size reaches 30 should reject further additions with appropriate feedback.
2. **Channel import splitting verification:** importing a channel set with mixed static and animated emotes or $>30$ emotes must invoke `PackSplittingEngine` and produce homogeneous, balanced packs without dropping any emotes.
3. **WhatsApp installation check before intent:** when user taps "Añadir a WhatsApp" and WhatsApp is not installed, the UI must show an informative warning/dialog rather than crashing with `ActivityNotFoundException`.
4. **Search debounce behavior:** typing in the search bar must debounce queries by 400ms to prevent flooding the 7TV API with intermediate keystrokes.
5. **Coroutine cancellation and lifecycle:** navigation away from a screen or cancelling a search must immediately cancel ongoing network requests without leaking jobs or crashing.

---

### Task 1: Compose Dependencies, Compiler Plugin & Build Configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: Nothing
- Produces: Gradle configuration supporting Jetpack Compose, Material 3, Navigation, ViewModels, and Coil image loading.

- [ ] **Step 1: Update `gradle/libs.versions.toml`**

Add version catalog entries:
```toml
[versions]
compose-bom = "2024.06.00"
activity-compose = "1.9.0"
lifecycle = "2.8.3"
navigation-compose = "2.7.7"
coil = "2.6.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
coil-gif = { group = "io.coil-kt", name = "coil-gif", version.ref = "coil" }

[plugins]
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Update `build.gradle.kts` and `app/build.gradle.kts`**

In root `build.gradle.kts`:
`alias(libs.plugins.compose.compiler) apply false`

In `app/build.gradle.kts`:
Add plugin `alias(libs.plugins.compose.compiler)`.
In `android`:
```kotlin
buildFeatures {
    compose = true
}
```
In `dependencies`:
```kotlin
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.ui.graphics)
implementation(libs.compose.ui.tooling.preview)
implementation(libs.compose.material3)
implementation(libs.compose.material.icons.extended)
implementation(libs.activity.compose)
implementation(libs.lifecycle.viewmodel.compose)
implementation(libs.lifecycle.runtime.compose)
implementation(libs.navigation.compose)
implementation(libs.coil.compose)
implementation(libs.coil.gif)
debugImplementation(libs.compose.ui.tooling)
```

- [ ] **Step 3: Run build to verify compilation**

Run: `cmd.exe /c "gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build(deps): add Jetpack Compose, Material 3, Navigation, and Coil dependencies"
```

---

### Task 2: Design System, Tokens, Chat Simulator & `AppContainer`

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/theme/Color.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/theme/Type.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/ChatSimulatorBackground.kt`
- Create: `app/src/main/java/com/seventv/stickers/di/AppContainer.kt`
- Create: `app/src/main/java/com/seventv/stickers/SevenTvApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/seventv/stickers/ui/theme/ThemeTokensTest.kt`
- Test: `app/src/test/java/com/seventv/stickers/di/AppContainerTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `SevenTvNetworkFactory`, `DefaultSevenTvRepository`, `DefaultStickerPackRepository`, `DefaultStickerStorageManager`
- Produces:
  - `SevenTvTheme` Composable theme applying Material 3 dark color scheme
  - `ChatSimulatorMode` enum (`WHATSAPP_DARK`, `WHATSAPP_LIGHT`, `CHECKERBOARD`) and `ChatSimulatorBackground` composable
  - `AppContainer` providing `sevenTvRepository: SevenTvRepository` and `stickerPackRepository: StickerPackRepository`
  - `SevenTvApplication.appContainer` accessible from Application context

- [ ] **Step 1: Write failing tests in `ThemeTokensTest.kt` and `AppContainerTest.kt`**

Verifies exact color token values and `AppContainer` dependency wiring.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.theme.ThemeTokensTest"`
Expected: FAIL

- [ ] **Step 3: Implement Theme, ChatSimulatorBackground, AppContainer, and SevenTvApplication**

Implement color tokens, typography, `SevenTvTheme`, `ChatSimulatorBackground`, `AppContainer` and `SevenTvApplication`. Update `AndroidManifest.xml` with `android:name=".SevenTvApplication"`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.theme.ThemeTokensTest"`
Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.di.AppContainerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/theme/ app/src/main/java/com/seventv/stickers/ui/components/ChatSimulatorBackground.kt app/src/main/java/com/seventv/stickers/di/ app/src/main/java/com/seventv/stickers/SevenTvApplication.kt app/src/main/AndroidManifest.xml app/src/test/java/com/seventv/stickers/ui/theme/ app/src/test/java/com/seventv/stickers/di/
git commit -m "feat(ui): implement design system tokens, chat simulator backgrounds, and AppContainer"
```

---

### Task 3: Reusable UI Components & Coil Animated WebP Loader

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/components/CoilSetup.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/EmoteCard.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/ActiveDraftBanner.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/WhatsAppAddButton.kt`
- Test: `app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmote`, `SevenTvTheme`, Coil ImageLoader
- Produces:
  - `createCoilImageLoader(context: Context): ImageLoader` with `ImageDecoderDecoder.Factory()` for animated WebP
  - `EmoteCard`: composable rendering emote with animated preview, type badge (`GIF`/`WebP`), name, and toggle add action
  - `ActiveDraftBanner`: floating bar with count `X/30`, minicard previews, and "Crear Pack" CTA
  - `WhatsAppAddButton`: branded green button with WhatsApp icon, loading state, and whitelisted badge (🟢 Añadido / ⚪ Añadir a WhatsApp)

- [ ] **Step 1: Write unit tests in `ComponentsTest.kt`**

Verifies state rendering and formatting (count formatting, disabled state when draft is empty).

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest"`
Expected: FAIL

- [ ] **Step 3: Implement components**

Implement `CoilSetup.kt`, `EmoteCard.kt`, `ActiveDraftBanner.kt`, `WhatsAppAddButton.kt`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/components/ app/src/test/java/com/seventv/stickers/ui/components/
git commit -m "feat(ui): implement EmoteCard, ActiveDraftBanner, WhatsAppAddButton, and Coil image loader"
```

---

### Task 4: Explore Feature (`ExploreViewModel` & `ExploreScreen`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/explore/ExploreUiState.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/explore/ExploreViewModel.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/explore/ExploreScreen.kt`
- Test: `app/src/test/java/com/seventv/stickers/ui/explore/ExploreViewModelTest.kt`

**Interfaces:**
- Consumes: `SevenTvRepository`, `StickerPackRepository`, `EmoteCard`, `ActiveDraftBanner`
- Produces:
  - `ExploreViewModel`: search with 400ms debounce (Review Focus 4), filter chips (ALL, ANIMATED, STATIC), draft pack state (prevent duplicates and enforce max 30 limit - Review Focus 1), create pack action
  - `ExploreScreen`: Composable screen with search field, filter row, grid of emotes, empty/error/loading views, and draft banner

- [ ] **Step 1: Write failing tests in `ExploreViewModelTest.kt`**

Write tests covering:
- Search debounce and result updating
- Filter by animated / static emotes
- Add/remove emote to draft (Review Focus 1: rejects duplicates, enforces 30 limit)
- Create pack from draft (minimum 3 check, invokes `createPacksFromEmotes`, clears draft on success)

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.explore.ExploreViewModelTest"`
Expected: FAIL

- [ ] **Step 3: Implement `ExploreUiState.kt`, `ExploreViewModel.kt`, and `ExploreScreen.kt`**

Implement search debounce, filter state, draft management, and Compose UI layout.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.explore.ExploreViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/explore/ app/src/test/java/com/seventv/stickers/ui/explore/
git commit -m "feat(ui): implement ExploreViewModel with debounce, draft management, and ExploreScreen"
```

---

### Task 5: Channel Explorer Feature (`ChannelViewModel` & `ChannelScreen`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/channel/ChannelUiState.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/channel/ChannelViewModel.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/channel/ChannelScreen.kt`
- Test: `app/src/test/java/com/seventv/stickers/ui/channel/ChannelViewModelTest.kt`

**Interfaces:**
- Consumes: `SevenTvRepository`, `StickerPackRepository`, `PackSplittingEngine`, `EmoteCard`
- Produces:
  - `ChannelViewModel`: loads streamer emote set, filter tabs, "Importar Canal Completo" invoking `PackSplittingEngine` (Review Focus 2: balanced splitting into 3..30 packs), and transactional pack persistence
  - `ChannelScreen`: Composable screen with streamer search, channel header, emote grid, and import modal

- [ ] **Step 1: Write failing tests in `ChannelViewModelTest.kt`**

Write tests covering:
- Load channel emote set successfully
- Channel import invoking `createPacksFromEmotes` with split plan (Review Focus 2: splits $>30$ emotes into balanced packs)
- Error handling on unknown channel or network failure

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.channel.ChannelViewModelTest"`
Expected: FAIL

- [ ] **Step 3: Implement `ChannelUiState.kt`, `ChannelViewModel.kt`, and `ChannelScreen.kt`**

Implement channel lookup, splitting integration, and Compose UI.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.channel.ChannelViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/channel/ app/src/test/java/com/seventv/stickers/ui/channel/
git commit -m "feat(ui): implement ChannelViewModel with pack splitting import and ChannelScreen"
```

---

### Task 6: Local Packs & Pack Detail Features (`PacksViewModel`, `PacksScreen`, `PackDetailScreen`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/packs/PacksUiState.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/packs/PacksViewModel.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/packs/PacksScreen.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/packs/PackDetailScreen.kt`
- Test: `app/src/test/java/com/seventv/stickers/ui/packs/PacksViewModelTest.kt`

**Interfaces:**
- Consumes: `StickerPackRepository`, `WhatsAppIntentHelper`, `WhatsAppWhitelistChecker`, `WhatsAppAddButton`, `ChatSimulatorBackground`
- Produces:
  - `PacksViewModel`: observes `observeAllPacks()`, synchronizes whitelist state on load, delete pack action, add pack to WhatsApp trigger (Review Focus 3: verifies WhatsApp installation before launching)
  - `PacksScreen`: lists user sticker packs with tray icon, sticker count, and direct WhatsApp export button
  - `PackDetailScreen`: grid of pack stickers, chat simulator background toggle, and export CTA

- [ ] **Step 1: Write failing tests in `PacksViewModelTest.kt`**

Write tests covering:
- Packs observation emitting loaded packs from Room
- Whitelist synchronization updating whitelisted flag
- Delete pack removing pack
- Add to WhatsApp intent creation (Review Focus 3: checks `isWhatsAppInstalled`)

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.packs.PacksViewModelTest"`
Expected: FAIL

- [ ] **Step 3: Implement `PacksUiState.kt`, `PacksViewModel.kt`, `PacksScreen.kt`, and `PackDetailScreen.kt`**

Implement pack management, detail view with chat background simulation, and WhatsApp intent integration.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest --tests com.seventv.stickers.ui.packs.PacksViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/packs/ app/src/test/java/com/seventv/stickers/ui/packs/
git commit -m "feat(ui): implement PacksViewModel, PacksScreen, PackDetailScreen with chat simulation"
```

---

### Task 7: Root Navigation, MainActivity Integration & End-to-End Build

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/MainActivity.kt`
- Test: `app/src/test/java/com/seventv/stickers/ui/navigation/AppNavigationTest.kt`

**Interfaces:**
- Consumes: `ExploreScreen`, `ChannelScreen`, `PacksScreen`, `PackDetailScreen`, `AppContainer`
- Produces:
  - Complete Android app entry point in `MainActivity` running Jetpack Compose with full 3-tab navigation
  - Production-ready Debug APK

- [ ] **Step 1: Write navigation routes test in `AppNavigationTest.kt`**

Verifies route string constants and destinations (`explore`, `channel`, `packs`, `pack_detail/{packId}`).

- [ ] **Step 2: Implement `AppNavigation.kt` and update `MainActivity.kt`**

Implement `AppNavigation` with bottom `NavigationBar` and `NavHost`.
Update `MainActivity.kt` to extend `ComponentActivity()`, initialize Coil `ImageLoader`, and set content with `SevenTvTheme` and `AppNavigation`.

- [ ] **Step 3: Run full project test suite**

Run: `cmd.exe /c "gradlew.bat testDebugUnitTest"`
Expected: ALL tests pass across the entire codebase.

- [ ] **Step 4: Build Debug APK**

Run: `cmd.exe /c "gradlew.bat assembleDebug"`
Expected: BUILD SUCCESSFUL producing `app-debug.apk`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/seventv/stickers/ui/navigation/ app/src/main/java/com/seventv/stickers/ui/MainActivity.kt app/src/test/java/com/seventv/stickers/ui/navigation/
git commit -m "feat(ui): implement AppNavigation and set Compose content in MainActivity"
```

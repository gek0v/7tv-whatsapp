# M3 Expressive UI/UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rediseñar por completo la interfaz y experiencia de usuario (UI/UX) de la aplicación 7TV Stickers para WhatsApp, adaptándola a las guías de diseño de Material Design 3 Expressive con soporte dual (Modo Claro y Oscuro).

**Architecture:** Se desacopla la capa de presentación mediante un sistema de tokens y temas M3 unificado (`ui/theme/`), componentes modulares reutilizables y comprobables unitariamente (`ui/components/`), y la modernización de las cuatro pantallas principales y el árbol de navegación (`ui/explore/`, `ui/channel/`, `ui/packs/`, `ui/navigation/`) sin alterar la lógica de negocio ni la integración del ContentProvider de WhatsApp.

**Tech Stack:** Kotlin, Jetpack Compose (`compose-bom 2024.06.00`), Material Design 3 (Material You), Coil 3 (renderizado animado WebP/GIF), Android SDK 34 (minSdk 26).

**Spec:** `docs/superpowers/specs/2026-10-12-m3-expressive-ui-redesign.md`

## Global Constraints

- Android minSdk 26, targetSdk 34, compileSdk 34, Java/Kotlin JVM target 17.
- Sin dependencias de terceros adicionales para UI: uso exclusivo de las APIs nativas de Compose Material 3 1.2+.
- Tokens de marca exactos: 7TV Cyan `#00D2FF` (Dark) / `#00728F` (Light), Twitch Purple `#B37BF2` (Dark) / `#6D28D9` (Light), WhatsApp Green `#25D366` (Dark) / `#008744` (Light), Error Red `#FF4858` (Dark) / `#D32F2F` (Light).
- Superficies tonales M3 implementadas rigurosamente: `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`.
- Restricciones técnicas de WhatsApp garantizadas: paquetes de 3 a 30 stickers; $\le 100\text{ KB}$ estático, $\le 500\text{ KB}$ animado; dimensiones de sticker $512 \times 512\text{ px}$.

## Review Focus

1. **Contraste de accesibilidad en Modo Claro:** Verificar que los textos sobre contenedores tonales y botones con acento cian/verde cumplan con WCAG AA.
2. **Conteo y consistencia del Borrador Activo:** Agregar o quitar stickers rápidamente desde `Explore` y `Channel` debe mantener la reactividad sin estados huérfanos en `DockedDraftPill`.
3. **Validación visual de límites de WhatsApp:** Un paquete con menos de 3 stickers debe inhabilitar la exportación con aviso explícito; más de 30 debe activar la división asistida.
4. **Rendimiento de renderizado en canales masivos:** Sets de creadores con más de 100 emotes deben mantener desplazamiento fluido a 60 fps mediante solicitudes de miniaturas a $128 \times 128\text{ px}$ y claves estables (`key = { it.id }`).
5. **Fidelidad del Inspector y Simulador:** El cambio de fondo en vivo (burbuja oscura, clara y cuadrícula alfa) debe reflejarse instantáneamente sin reiniciar la animación WebP.

---

### Task 1: M3 Theme & Design Tokens (Color, Typography, Shape, Theme)

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/theme/Type.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/theme/Theme.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/theme/ThemeTokensTest.kt`

**Interfaces:**
- Produces: `SevenTvDarkColorScheme: ColorScheme`, `SevenTvLightColorScheme: ColorScheme`, `SevenTvShapes: Shapes`, `Typography: Typography`, `SevenTvTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.

- [ ] **Step 1: Escribir el test que falla para verificar los tokens del tema dual M3**

Actualizar `ThemeTokensTest.kt` para verificar los esquemas de color claro y oscuro, roles de `surfaceContainer` y contraste básico:

```kotlin
@Test
fun testDualColorSchemeRoles() {
    assertNotNull(SevenTvDarkColorScheme.surfaceContainer)
    assertNotNull(SevenTvLightColorScheme.surfaceContainer)
    assertEquals(Color(0xFF00D2FF), SevenTvDarkColorScheme.primary)
    assertEquals(Color(0xFF00728F), SevenTvLightColorScheme.primary)
    assertEquals(Color(0xFF25D366), SevenTvDarkColorScheme.tertiary)
}
```

- [ ] **Step 2: Ejecutar el test para comprobar que falla**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.theme.ThemeTokensTest`  
Expected: FAIL por símbolos no resueltos o aserciones fallidas.

- [ ] **Step 3: Implementar la arquitectura de tokens en `Color.kt`, `Type.kt`, `Shape.kt` y `Theme.kt`**

Definir `SevenTvDarkColorScheme` y `SevenTvLightColorScheme` con los roles de color semánticos y de superficies tonales de M3. Configurar la escala tipográfica completa en `Type.kt` y definir las formas redondeadas en `Shape.kt`. En `Theme.kt`, soportar la selección dinámica `darkTheme: Boolean = isSystemInDarkTheme()`.

- [ ] **Step 4: Ejecutar el test para comprobar que pasa**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.theme.ThemeTokensTest`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 1**

```bash
git add app/src/main/java/com/seventv/stickers/ui/theme/ app/src/test/java/com/seventv/stickers/ui/theme/ThemeTokensTest.kt
git commit -m "feat(ui): implement dual M3 color schemes, typography, and expressive shapes"
```

---

### Task 2: Core Emote Components (`SevenTvSearchBar` y `EmoteCard`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/components/SevenTvSearchBar.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/components/EmoteCard.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: `SevenTvTheme` tokens, `SevenTvEmote`.
- Produces:
  - `SevenTvSearchBar(query: String, onQueryChanged: (String) -> Unit, selectedFilter: String, onFilterSelected: (String) -> Unit, onPasteUrl: () -> Unit, modifier: Modifier)`
  - `EmoteCard(emote: SevenTvEmote, isInDraft: Boolean, onToggleDraft: () -> Unit, modifier: Modifier, onClick: (() -> Unit)?)`

- [ ] **Step 1: Escribir el test que falla para `SevenTvSearchBar` y `EmoteCard` M3**

En `ComponentsTest.kt`, validar que `EmoteCardDefaults.badgeText(isAnimated)` y los filtros predeterminados de búsqueda responden según lo esperado.

- [ ] **Step 2: Ejecutar el test para comprobar que falla**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: FAIL si faltan métodos o defaults esperados.

- [ ] **Step 3: Implementar `SevenTvSearchBar.kt` y modernizar `EmoteCard.kt`**

Crear `SevenTvSearchBar` con contenedor tonal `surfaceContainerLow`, esquinas de `28dp`, leading icon de lupa, trailing icon para limpiar o pegar enlace, y fila deslizable de `FilterChip`. En `EmoteCard.kt`, aplicar `ElevatedCard`, esquinas de `16dp`, badge con fondo semántico y botón con transición animada (`+` cian $\to$ `✓` verde).

- [ ] **Step 4: Ejecutar el test para comprobar que pasa**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 2**

```bash
git add app/src/main/java/com/seventv/stickers/ui/components/SevenTvSearchBar.kt app/src/main/java/com/seventv/stickers/ui/components/EmoteCard.kt app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt
git commit -m "feat(ui): add SevenTvSearchBar and M3 ElevatedCard EmoteCard"
```

---

### Task 3: Borrador Activo y Creación (`DockedDraftPill` y `DraftBottomSheet`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/components/DockedDraftPill.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/DraftBottomSheet.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/components/ActiveDraftBanner.kt` (delegar a `DockedDraftPill`)
- Modify: `app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: `SevenTvEmote`, `SevenTvTheme`.
- Produces:
  - `DockedDraftPill(draftEmotes: List<SevenTvEmote>, onClickReview: () -> Unit, modifier: Modifier)`
  - `DraftBottomSheet(draftEmotes: List<SevenTvEmote>, packName: String, onPackNameChanged: (String) -> Unit, onRemoveEmote: (SevenTvEmote) -> Unit, onCreatePack: () -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Escribir el test que falla para los estados del borrador**

En `ComponentsTest.kt`, añadir pruebas de la lógica de presentación del borrador (validación de estados: `< 3` insuficiente, `3..30` válido, `> 30` excedido).

- [ ] **Step 2: Ejecutar el test para comprobar que falla**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: FAIL.

- [ ] **Step 3: Implementar `DockedDraftPill.kt` y `DraftBottomSheet.kt`**

Construir la píldora flotante animada con contador `X / 30`, barra de progreso tonal lineal con color semántico, y el `ModalBottomSheet` con campo de texto para el nombre del paquete, cuadrícula de miniaturas y botón primario M3 `Crear Paquete`.

- [ ] **Step 4: Ejecutar el test para comprobar que pasa**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 3**

```bash
git add app/src/main/java/com/seventv/stickers/ui/components/DockedDraftPill.kt app/src/main/java/com/seventv/stickers/ui/components/DraftBottomSheet.kt app/src/main/java/com/seventv/stickers/ui/components/ActiveDraftBanner.kt app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt
git commit -m "feat(ui): implement DockedDraftPill and DraftBottomSheet components"
```

---

### Task 4: Gestión de Packs e Inspector (`PackHeroCard`, `StickerInspectorSheet`)

**Files:**
- Create: `app/src/main/java/com/seventv/stickers/ui/components/PackHeroCard.kt`
- Create: `app/src/main/java/com/seventv/stickers/ui/components/StickerInspectorSheet.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/components/ChatSimulatorBackground.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/components/WhatsAppAddButton.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: `StickerPack`, `PackSticker`, `SevenTvTheme`.
- Produces:
  - `PackHeroCard(pack: StickerPack, stickers: List<PackSticker>, onExportToWhatsApp: () -> Unit, onPackClick: () -> Unit, onMenuAction: (PackMenuAction) -> Unit, modifier: Modifier)`
  - `StickerInspectorSheet(sticker: PackSticker, onDismiss: () -> Unit, onUpdateEmojis: (List<String>) -> Unit, onDeleteSticker: () -> Unit, onSetAsTrayIcon: () -> Unit)`

- [ ] **Step 1: Escribir el test que falla para la lógica de inspector y chips de estado**

Añadir tests en `ComponentsTest.kt` para validar los modos del simulador (Dark, Light, Checkerboard) y el estado del chip de WhatsApp (Sincronizado, Pendiente, No exportado).

- [ ] **Step 2: Ejecutar el test para comprobar que falla**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: FAIL.

- [ ] **Step 3: Implementar `PackHeroCard.kt` y `StickerInspectorSheet.kt`**

Crear `PackHeroCard` con esquinas de `24dp`, tira de stickers en scroll horizontal, chip de estado de WhatsApp y menú desplegable. Implementar `StickerInspectorSheet` con cambio de fondo en tiempo real, selector de emojis asociados al teclado de WhatsApp y ficha técnica (KB, duración s, FPS).

- [ ] **Step 4: Ejecutar el test para comprobar que pasa**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.components.ComponentsTest`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 4**

```bash
git add app/src/main/java/com/seventv/stickers/ui/components/PackHeroCard.kt app/src/main/java/com/seventv/stickers/ui/components/StickerInspectorSheet.kt app/src/main/java/com/seventv/stickers/ui/components/ChatSimulatorBackground.kt app/src/main/java/com/seventv/stickers/ui/components/WhatsAppAddButton.kt app/src/test/java/com/seventv/stickers/ui/components/ComponentsTest.kt
git commit -m "feat(ui): add PackHeroCard and StickerInspectorSheet with live chat preview"
```

---

### Task 5: Rediseño de Pantallas: Explorador y Canales (`ExploreScreen` & `ChannelScreen`)

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/ui/explore/ExploreScreen.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/channel/ChannelScreen.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/explore/ExploreViewModelTest.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/channel/ChannelViewModelTest.kt`

**Interfaces:**
- Consumes: `SevenTvSearchBar`, `EmoteCard`, `DockedDraftPill`, `DraftBottomSheet`.
- Modifies UI layouts while retaining existing ViewModel interaction contracts.

- [ ] **Step 1: Escribir el test que falla para verificar los flujos de UI de Explore y Channel**

Verificar que las acciones de selección de borrador y cambio de consulta siguen actualizando el `ExploreUiState` y `ChannelUiState`.

- [ ] **Step 2: Ejecutar los tests de ViewModels para verificar la línea base**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.explore.ExploreViewModelTest --tests com.seventv.stickers.ui.channel.ChannelViewModelTest`  
Expected: PASS inicial.

- [ ] **Step 3: Refactorizar `ExploreScreen.kt` y `ChannelScreen.kt` a Material 3 Expressive**

Integrar `SevenTvSearchBar` en la cabecera, `LazyVerticalGrid` adaptativo (3 columnas en móvil), estados vacíos expresivos con iconos vectoriales, cabecera hero del streamer en `ChannelScreen`, `PrimaryTabRow` de M3 y anclaje continuo de `DockedDraftPill`.

- [ ] **Step 4: Ejecutar los tests de ViewModels y componentes**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.explore.* --tests com.seventv.stickers.ui.channel.*`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 5**

```bash
git add app/src/main/java/com/seventv/stickers/ui/explore/ExploreScreen.kt app/src/main/java/com/seventv/stickers/ui/channel/ChannelScreen.kt
git commit -m "feat(ui): redesign ExploreScreen and ChannelScreen with M3 Expressive layouts"
```

---

### Task 6: Rediseño de Gestión de Packs, Detalle y Navegación (`PacksScreen`, `PackDetailScreen`, `AppNavigation`)

**Files:**
- Modify: `app/src/main/java/com/seventv/stickers/ui/packs/PacksScreen.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/packs/PackDetailScreen.kt`
- Modify: `app/src/main/java/com/seventv/stickers/ui/navigation/AppNavigation.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/packs/PacksViewModelTest.kt`
- Modify: `app/src/test/java/com/seventv/stickers/ui/navigation/AppNavigationTest.kt`

**Interfaces:**
- Consumes: `PackHeroCard`, `StickerInspectorSheet`, M3 `NavigationBar`, `ExtendedFloatingActionButton`.

- [ ] **Step 1: Escribir el test que falla para la navegación y acciones de packs**

En `AppNavigationTest.kt` y `PacksViewModelTest.kt`, validar que las rutas de navegación mantienen sus destinos y que las acciones de exportación a WhatsApp se invocan correctamente.

- [ ] **Step 2: Ejecutar los tests para comprobar el comportamiento**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.packs.PacksViewModelTest --tests com.seventv.stickers.ui.navigation.AppNavigationTest`  
Expected: PASS inicial.

- [ ] **Step 3: Refactorizar `PacksScreen.kt`, `PackDetailScreen.kt` y `AppNavigation.kt`**

Reemplazar tarjetas antiguas por `PackHeroCard`, implementar `LargeTopAppBar` y `ExtendedFloatingActionButton` en `PacksScreen`, integrar `StickerInspectorSheet` al tocar cualquier sticker en `PackDetailScreen` con badge de Tray Icon, y estilizar la `NavigationBar` de M3 con indicadores de acento tonal en `AppNavigation`.

- [ ] **Step 4: Ejecutar los tests de navegación y packs**

Run: `./gradlew testDebugUnitTest --tests com.seventv.stickers.ui.packs.* --tests com.seventv.stickers.ui.navigation.*`  
Expected: PASS.

- [ ] **Step 5: Commit de Task 6**

```bash
git add app/src/main/java/com/seventv/stickers/ui/packs/ app/src/main/java/com/seventv/stickers/ui/navigation/
git commit -m "feat(ui): modernize PacksScreen, PackDetailScreen and AppNavigation with M3 components"
```

---

### Task 7: Verificación Integral y Suite de Regresión Completa

**Files:**
- Test: Toda la suite de tests del proyecto (`app/src/test/...`)

- [ ] **Step 1: Ejecutar la suite completa de tests unitarios y de integración**

Run: `./gradlew testDebugUnitTest`  
Expected: Todos los tests pasando (cero fallos en repositorios, content provider, compresión de WebP, ViewModels y componentes de UI).

- [ ] **Step 2: Ejecutar la tarea de lint y comprobación de ensamblado**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL con APK generado sin errores de compilación ni advertencias críticas.

- [ ] **Step 3: Commit final de verificación y limpieza**

```bash
git commit --allow-empty -m "chore: verify 100% test pass on M3 Expressive UI refactor"
```

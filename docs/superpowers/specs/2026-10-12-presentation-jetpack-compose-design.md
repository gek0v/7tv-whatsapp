# Especificación Técnica: Capa de Presentación & UI en Jetpack Compose (Subproyecto 5)

- **Fecha:** 2026-10-12
- **Estado:** Propuesto
- **Rama:** `feature/media-pipeline-scaffolding`
- **Subproyecto:** 5 (Capa de Presentación: Jetpack Compose, Material 3, Navegación, ViewModels, Coil 3 y Flujo de WhatsApp)

---

## 1. Resumen Ejecutivo

Este documento define la arquitectura técnica y la especificación de diseño del **Subproyecto 5: Capa de Presentación & UI**.
El objetivo es transformar la aplicación en una experiencia nativa moderna, rápida y reactiva en **Jetpack Compose** con **Material 3**, priorizando el modo oscuro conforme a [`Design.md`](file:///D:/Workspaces/7tv-whatsapp/Design.md) y [`ARCHITECURE.md`](file:///D:/Workspaces/7tv-whatsapp/ARCHITECURE.md).

La interfaz conectará los motores desarrollados en los subproyectos anteriores:
1. **Subproyecto 1 (Media Pipeline):** Conversión y optimización a WebP estático/animado de 512x512 px.
2. **Subproyecto 2 (7TV Network API & Downloader):** Búsqueda de emotes, consulta de canales de streamers y descarga con caché.
3. **Subproyecto 3 (Room & PackSplittingEngine):** Persistencia reactiva de paquetes locales y partición automática en rangos válidos (3 a 30 stickers).
4. **Subproyecto 4 (Native WhatsApp Integration):** Publicación mediante `StickerContentProvider`, verificación de lista blanca y lanzamiento de intents `ENABLE_STICKER_PACK`.

---

## 2. Decisiones de Arquitectura y Dependencias

### 2.1 Tecnologías y Versiones
- **Kotlin:** `2.0.0`
- **Compose Compiler:** Plugin oficial `org.jetbrains.kotlin.plugin.compose` (integrado en Kotlin 2.0).
- **Compose BOM:** `androidx.compose:compose-bom:2024.06.00`
- **UI & Material 3:** `androidx.compose.material3:material3`, `androidx.compose.ui:ui`, `androidx.compose.ui:ui-tooling-preview`
- **Navegación:** `androidx.navigation:navigation-compose:2.7.7`
- **Ciclo de Vida & ViewModels:** `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.3`
- **Carga de Imágenes & Animaciones:** `io.coil-kt:coil-compose:2.6.0` y `io.coil-kt:coil-gif:2.6.0` (soporte nativo con `ImageDecoderDecoder` para Animated WebP y GIF con bucles continuos).
- **Inyección de Dependencias:** Inyección limpia mediante `ViewModelProvider.Factory` y un contenedor de dependencias (`AppContainer` / `ServiceLocator`) a nivel de aplicación (`SevenTvApplication`), manteniendo tiempos de compilación inmediatos y desacoplamiento para pruebas unitarias y de Compose.

---

## 3. Sistema de Diseño (Design System Tokens)

Implementado en el paquete `com.seventv.stickers.ui.theme`:

### 3.1 Paleta de Color (Dark Theme Principal)
```kotlin
val DarkBackground = Color(0xFF0E0F12)
val DarkSurface = Color(0xFF16181D)
val DarkSurfaceVariant = Color(0xFF22252C)
val SevenTvCyan = Color(0xFF00D2FF)      // Primary
val TwitchPurple = Color(0xFF9B51E0)    // Secondary
val WhatsAppGreen = Color(0xFF25D366)   // Tertiary (Acción WhatsApp)
val ErrorRed = Color(0xFFFF4858)        // Alertas de límite o error
val OnSurfaceText = Color(0xFFF1F3F5)
val OnSurfaceSubtext = Color(0xFF9AA0A6)
```

### 3.2 Simuladores de Fondo de Chat (Chat Background Simulator)
Para comprobar el contraste y la transparencia alfa de los emotes antes de exportar a WhatsApp:
- **WhatsApp Dark Chat:** `#0B141A` (burbuja `#005C4B`)
- **WhatsApp Light Chat:** `#EFEAE2` (burbuja `#D9FDD3`)
- **Checkerboard:** Cuadrícula de transparencia `#2A2E39` / `#1E2028`

---

## 4. Arquitectura de Navegación y Pantallas

La aplicación contará con un `Scaffold` raíz con una barra de navegación inferior (`NavigationBar`) y `NavHost`:

```
┌────────────────────────────────────────────────────────┐
│                      Pantalla Activa                   │
│                                                        │
│  [Explorar]        |  [Canales]       |  [Mis Packs]   │
│  ExploreScreen     |  ChannelScreen   |  PacksScreen   │
│                    │                  │                │
├────────────────────────────────────────────────────────┤
│  Banner Flotante: "Pack Borrador (5/30)" [Crear Pack]  │ (Solo en Explore/Channel si hay selecciones)
├────────────────────────────────────────────────────────┤
│           Barra Inferior de Navegación                 │
│       🔍 Explorar   |   📺 Canales   |   📦 Mis Packs  │
└────────────────────────────────────────────────────────┘
```

Rutas de navegación:
- `Screen.Explore.route` ("explore")
- `Screen.Channel.route` ("channel")
- `Screen.Packs.route` ("packs")
- `Screen.PackDetail.route` ("pack_detail/{packId}")

---

## 5. Especificación Detallada de Pantallas y ViewModels

### 5.1 Pantalla 1: Explorar / Búsqueda (`ExploreScreen`) & `ExploreViewModel`
- **Funcionalidades:**
  - Búsqueda global de emotes de 7TV con debounce (400ms).
  - Filtros en chips: *Todos*, *Animados*, *Estáticos*.
  - Cuadrícula adaptativa (`LazyVerticalGrid` de 3 columnas en teléfono) mostrando tarjeta de emote: miniatura con animación WebP vía Coil, badge GIF/WebP, nombre y botón rápido `+` para añadir al borrador activo.
  - Paginación o recarga al final de la lista.
- **Borrador de Pack Activo (`DraftPack`):**
  - Estado en memoria en el ViewModel (`draftEmotes: List<SevenTvEmote>`).
  - Límite dinámico de 30 emotes por paquete.
  - Banner inferior flotante persistente cuando `draftEmotes.isNotEmpty()` mostrando miniaturas, contador `X/30` y botón **"Guardar Pack"**.
  - Diálogo para asignar nombre al pack y pulsar "Crear", que invoca a `StickerPackRepository.createPacksFromEmotes` mostrando un indicador de progreso.

### 5.2 Pantalla 2: Explorador de Canales (`ChannelScreen`) & `ChannelViewModel`
- **Funcionalidades:**
  - Búsqueda de streamer por nombre o ID de usuario de Twitch/7TV (ej. "ibai", "auronplay").
  - Cabecera del canal: nombre, avatar y número total de emotes activos en su emote-set.
  - Pestañas de categoría: *Todos*, *Animados*, *Estáticos*.
  - Botón primario prominente: **"Importar Canal a WhatsApp"**:
    - Ejecuta `PackSplittingEngine` para particionar automáticamente el canal en bloques homogéneos y balanceados (de 3 a 30 stickers).
    - Muestra modal de confirmación con el plan de partición (ej. "Se crearán 4 paquetes: Ibai (Animados 1/2), Ibai (Animados 2/2), Ibai (Estáticos 1/2)...").
    - Procesamiento por lotes y guardado transaccional en Room y almacenamiento interno.

### 5.3 Pantalla 3: Mis Packs (`PacksScreen`) & `PacksViewModel`
- **Funcionalidades:**
  - Observa en tiempo real `StickerPackRepository.observeAllPacks()`.
  - Tarjetas de paquete mostrando:
    - Icono de bandeja (*Tray Icon*).
    - Nombre del pack y publicador.
    - Conteo de stickers y badge animado/estático.
    - Badge de estado de WhatsApp:
      - 🟢 *Añadido a WhatsApp* (si `isWhitelisted == true`).
      - ⚪ *Pendiente de añadir* (si `isWhitelisted == false`).
    - Botón primario directo **"Añadir a WhatsApp"** (con icono de WhatsApp) que construye y lanza el intent `ENABLE_STICKER_PACK`. Si WhatsApp no está instalado, abre diálogo informativo.
    - Menú contextual para eliminar el paquete (liberando archivos de disco en cascada).
  - Al pulsar una tarjeta, navega a `PackDetailScreen`.

### 5.4 Pantalla 4: Detalle de Pack (`PackDetailScreen`) & `PackDetailViewModel`
- **Funcionalidades:**
  - Previsualización completa de los 3 a 30 stickers en cuadrícula.
  - Selector de fondo de simulación de chat (WhatsApp Oscuro, WhatsApp Claro, Transparente) para inspeccionar la legibilidad y bordes.
  - Edición de emojis asociados a cada sticker mediante diálogo modal.
  - Botón fijo inferior para añadir o sincronizar con WhatsApp.

---

## 6. Manejo de Estado y Flujo de Datos (MVI / Unidirectional Data Flow)

Cada ViewModel expone un `StateFlow<UiState>` inmutable y procesa eventos unidireccionales (`UiEvent`):

```kotlin
sealed interface ExploreUiState {
    data object Initial : ExploreUiState
    data object Loading : ExploreUiState
    data class Success(val emotes: List<SevenTvEmote>) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}
```

---

## 7. Plan de Pruebas y Validación

1. **Pruebas Unitarias de ViewModels (Robolectric + Coroutines Test):**
   - `ExploreViewModelTest`: búsqueda, debounce, filtrado por tipo, adición y remoción en borrador, creación de pack.
   - `ChannelViewModelTest`: resolución de canal, emote sets, invocación de `PackSplittingEngine` y creación de lotes.
   - `PacksViewModelTest`: observación reactiva de Room, sincronización de `syncWhitelistStatus`, eliminación de packs.
2. **Pruebas de Integración y Renderizado Compose:**
   - Pruebas de componentes clave: tarjetas de emote, banner de borrador activo, badges de estado y simulación de fondo.
3. **Verificación en APK:**
   - Compilación completa `gradlew assembleDebug` y verificación del tamaño y funcionamiento en dispositivo físico.

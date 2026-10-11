# ARCHITECTURE.md - 7TV to WhatsApp Stickers for Android

Este documento detalla la arquitectura técnica, especificaciones del sistema, estructura modular, procesamiento de medios e interfaz de usuario para el desarrollo de la aplicación nativa en Android.

---

## 1. Stack Tecnológico &amp; Requisitos

### 1.1 Entorno y Plataforma

- **Lenguaje:** Kotlin (100% nativo).
- **Min SDK:** 26 (Android 8.0 Oreo) — *Garantiza compatibilidad con APIs de decodificación WebP y manejo eficiente de concurrencia.*
- **Target / Compile SDK:** 34+ (Android 14+).
- **Build System:** Gradle (Kotlin DSL con Version Catalogs `libs.versions.toml`).

### 1.2 Librerías y Frameworks Principales

- **UI &amp; Estado:** Jetpack Compose, Material Design 3 (Material You), Navigation Compose.
- **Inyección de Dependencias:** Hilt (Dagger).
- **Asincronía &amp; Flujos:** Kotlin Coroutines &amp; StateFlow / SharedFlow.
- **Persistencia Local:** Room Database + SQLCipher (opcional para metadata) y DataStore Preferences (configuraciones de usuario).
- **Networking:** Ktor Client o Retrofit 2 + OkHttp 4 (Ktor recomendado para multiplatform futuro, Retrofit para madurez).
- **Serialización:** Kotlinx Serialization (JSON).
- **Carga de Imágenes &amp; Animaciones:** Coil 3 (con soporte nativo para Animated WebP y caché en disco).
- **Procesamiento WebP &amp; Gráficos:** 
  - Android Graphics `Bitmap` / `ImageDecoder` / `Canvas`.
  - `libwebp` (vía JNI / NDK) o librerías optimizadas como `glide-transformations` / FFmpeg-kit (modo ligero) en caso de requerir re-ensamblado de WebP animados con control exacto de duración de frames.

---

## 2. Requisitos Técnicos de WhatsApp Stickers

WhatsApp define un protocolo estricto basado en un `ContentProvider` personalizado y un contrato de datos:


| Atributo             | Sticker Estático                   | Sticker Animado                    | Icono de Bandeja (*Tray*)        |
| :-------------------- | :---------------------------------- | :---------------------------------- | :-------------------------------- |
| **Formato**          | WebP (.webp)                       | Animated WebP (.webp)              | WebP (.webp) o PNG               |
| **Dimensiones**      | $512 \times 512\text{ px}$ exactos | $512 \times 512\text{ px}$ exactos | $96 \times 96\text{ px}$ exactos |
| **Margen (Padding)** | $16\text{ px}$ transparente        | $16\text{ px}$ transparente        | $1\text{ px}$ transparente       |
| **Peso Máximo**      | $< 100\text{ KB}$                  | $< 500\text{ KB}$                  | $< 50\text{ KB}$                 |
| **Límites de Pack**  | Mín. 3 — Máx. 30                   | Mín. 3 — Máx. 30                   | 1 por paquete                    |
| **Frame rate**       | N/A                                | $\le 20-30\text{ fps}$             | Estático                         |
| **Duración Anim.**   | N/A                                | Máximo 3 segundos                  | N/A                              |


---

## 3. Arquitectura del Sistema (Clean Architecture + MVI/MVVM)

La aplicación sigue los principios de **Clean Architecture** estructurada en capas desacopladas unidireccionales:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│     (Compose UI -> ViewModels -> State / UI Events)         │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                           │
│     (UseCases, Domain Models, Repository Contracts)          │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│          Data Layer          ││      Media Pipeline         │
│  - Remote (7TV REST/GQL)     ││  - 512x512 Canvas Scaler    │
│  - Local (Room, FileStorage) ││  - WebP Compressor / Parser │
│  - Repositories Impl         ││  - Frame Rate Throttler     │
└──────────────┬───────────────┘└─────────────┬───────────────┘
               │                              │
               └──────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│               WhatsApp Integration Layer                    │
│    (Custom ContentProvider + Intent Action Handlers)        │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Estructura de Directorios del Proyecto

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/seventv/stickers/
│   ├── core/
│   │   ├── common/              # Extensiones, Dispatchers, Result wrapper
│   │   ├── designsystem/        # Themes, Typography, Color schemes, Atoms
│   │   └── network/             # Clientes HTTP, Interceptores, Base URLs
│   │
│   ├── data/
│   │   ├── local/
│   │   │   ├── dao/             # PackDao, StickerDao
│   │   │   ├── entity/          # PackEntity, StickerEntity
│   │   │   ├── storage/         # Manejador de archivos internos (WebP files)
│   │   │   └── AppDatabase.kt
│   │   ├── remote/
│   │   │   ├── api/             # SevenTVService (Endpoints REST/GraphQL)
│   │   │   └── model/           # DTOs de 7TV (EmoteDTO, UserDTO, EmoteSetDTO)
│   │   └── repository/          # Implementaciones de Repositorios
│   │
│   ├── domain/
│   │   ├── model/               # Modelos limpios: Sticker, StickerPack, EmoteSource
│   │   ├── repository/          # Interfaces: StickerPackRepository, SevenTVRepository
│   │   └── usecase/             # Casos de uso:
│   │       ├── SearchEmotesUseCase.kt
│   │       ├── FetchChannelEmotesUseCase.kt
│   │       ├── ProcessAndSaveStickerUseCase.kt
│   │       ├── SplitPackIfNeededUseCase.kt
│   │       └── UpdateWhatsAppPackUseCase.kt
│   │
│   ├── media/                   # Pipeline de procesamiento de imagen/vídeo
│   │   ├── WebPTransformer.kt   # Redimensionado a 512x512 con padding
│   │   ├── WebPCompressor.kt    # Control de peso (<100KB / <500KB)
│   │   └── AnimatedOptimizer.kt # Recorte de duración y drop de frames
│   │
│   ├── whatsapp/                # Integración nativa de stickers
│   │   ├── StickerContentProvider.kt  # Expone los archivos hacia WhatsApp
│   │   ├── WhatsAppIntentHelper.kt    # Intents hacia com.whatsapp
│   │   └── WhitelistCheck.kt          # Comprobación de instalación del pack
│   │
│   └── presentation/            # UI con Jetpack Compose
│       ├── search/              # Búsqueda global de emotes 7TV
│       ├── channel/             # Explorador por canal de streamer
│       ├── pack_manager/        # Listado y gestión de packs locales
│       ├── pack_detail/         # Editor de un pack (reordenar, borrar, añadir)
│       └── components/          # Componentes reutilizables (AsyncImage, Chips)
│
└── res/
    ├── xml/
    │   └── provider_paths.xml   # FileProvider / ContentProvider paths
    └── values/
```

---

## 5. Pipeline de Procesamiento de Emotes

```
[7TV API URL (WebP/AVIF/GIF)]
            │
            ▼
┌──────────────────────────────────────┐
│  1. Descarga & Decodificación        │ -> Obtención del stream binario
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│  2. Determinación de Tipo            │
└───────┬──────────────────────┬───────┘
        │ (Estático)           │ (Animado)
        ▼                      ▼
┌──────────────────────┐┌──────────────────────────────────────┐
│ Canvas 512x512       ││ Decodificación de fotogramas         │
│ Aspect Fit centrado  ││ Limitación a máx. 3s y máx. 30 fps   │
│ Padding >= 16px      ││ Canvas 512x512 centrado por frame    │
└──────────┬───────────┘└──────────────────┬───────────────────┘
           │                               │
           ▼                               ▼
┌──────────────────────┐┌──────────────────────────────────────┐
│ Compresión a WebP    ││ Codificación a Animated WebP         │
│ (Target < 100 KB)    ││ (Target < 500 KB con ajuste gradual) │
└──────────┬───────────┘└──────────────────┬───────────────────┘
           │                               │
           └───────────────┬───────────────┘
                           ▼
┌──────────────────────────────────────┐
│  3. Guardado en Cache/Internal Dir   │ -> Listos para StickerContentProvider
└──────────────────────────────────────┘
```

---

## 6. Integración con WhatsApp (`ContentProvider`)

WhatsApp requiere que los stickers no se transmitan por un simple intent con binarios directos, sino que lee los metadatos y el flujo de bytes desde un `ContentProvider`:

1. **Definición en AndroidManifest:**
   ```xml
    <provider
        android:name=".whatsapp.StickerContentProvider"
        android:authorities="${applicationId}.stickercontentprovider"
        android:enabled="true"
        android:exported="true"
        android:readPermission="com.whatsapp.sticker.READ" />
   ```
2. **Métodos clave a sobrescribir:**
   - `query()`: Expone la lista de stickers y la metadata de cada paquete en formato Cursor (`sticker_packs`, `stickers`).
   - `openAssetFile()`: Entrega el descriptor de archivo (`AssetFileDescriptor`) con el stream del archivo `.webp` almacenado en memoria interna.
3. **Intent de adición:**
   - Lanzar Intent implícito hacia WhatsApp con:
     - `action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"`
     - Extras: `EXTRA_STICKER_PACK_ID`, `EXTRA_STICKER_PACK_AUTHORITY`, `EXTRA_STICKER_PACK_NAME`.

---

## 7. Guía de Diseño (UI/UX)

[Guia de diseño](./Design.md)
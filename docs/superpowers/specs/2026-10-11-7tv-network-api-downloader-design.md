# Especificación de Diseño: Subproyecto 2 — Cliente de Red & API de 7TV (`core.network` & `data.repository`)

- **Fecha:** 2026-10-11
- **Subproyecto:** Subproyecto 2 de la suite 7TV Stickers para WhatsApp
- **Estado:** Aprobado / Listo para Planificación
- **Dependencia previa:** Subproyecto 1 (`core.media` / `StickerMediaPipeline`) completado y verificado.

---

## 1. Visión General del Subsistema

El propósito del Subproyecto 2 es proporcionar la infraestructura de red y datos necesaria para:
1. Conectarse a la API pública v3 de 7TV (`https://7tv.io/v3/`).
2. Realizar búsquedas globales de emotes por texto/etiquetas con soporte de paginación.
3. Consultar Emote Sets completos de canales y creadores de contenido populares.
4. Resolver y descargar los archivos de imagen de mayor calidad en el CDN de 7TV (`https://cdn.7tv.app/emote/{id}/4x.webp`).
5. Gestionar una caché local en disco para los emotes descargados.
6. Proporcionar un puente de integración directo (`EmoteStickerBridge`) que transmita los activos descargados a través del motor `StickerMediaPipeline` ya validado en el Subproyecto 1, generando stickers listos para WhatsApp.

---

## 2. Decisiones Arquitectónicas y Stack Tecnológico

1. **Cliente HTTP & REST:** Retrofit `2.11.0` con OkHttp `4.12.0`.
2. **Serialización JSON:** Kotlinx Serialization `1.7.1` (`kotlinx-serialization-json`) con convertidor `retrofit2-kotlinx-serialization-converter`.
   - `Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }` para resistir adiciones o variaciones no documentadas en el esquema JSON de 7TV.
3. **Manejo de Errores Funcional:** Extensión de `StickerResult<T>` y `StickerError` de Clean Architecture para garantizar cero excepciones no capturadas.
4. **Caché en Disco:**
   - Caché HTTP de OkHttp ($10\text{ MB}$) para respuestas JSON de la API.
   - Directorio de caché dedicado (`context.cacheDir/7tv_emotes_cache/`) para activos binarios de emotes indexados por `{emote_id}_{scale}.webp`.
5. **Aislamiento de Pruebas:** `MockWebServer` para pruebas unitarias de red 100% locales en JVM sin necesidad de emulador ni conexión a internet.

---

## 3. Especificación de la API de 7TV v3

### 3.1 Endpoints REST
- **Base URL:** `https://7tv.io/v3/`
- **Búsqueda Global de Emotes:**
  - `GET /v3/emotes`
  - Parámetros:
    - `query`: `String` (palabras clave del emote, ej. `"catJAM"`).
    - `page`: `Int` (índice 1-based, por defecto `1`).
    - `limit`: `Int` (tamaño de página, por defecto `30`, máx. `100`).
  - Respuesta HTTP 200: JSON con lista de emotes o array de resultados.
- **Detalle de Emote por ID:**
  - `GET /v3/emotes/{id}`
  - Respuesta HTTP 200: Objeto emote completo.
- **Emote Set por ID:**
  - `GET /v3/emote-sets/{id}`
  - Respuesta HTTP 200: Objeto emote set con metadatos del autor y lista de emotes activos.
- **Usuario de Twitch por ID:**
  - `GET /v3/users/twitch/{twitch_id}`
  - Respuesta HTTP 200: Perfil de usuario con `emote_set_id` activo.

### 3.2 Formato de URLs del CDN de 7TV
- **Base CDN:** `https://cdn.7tv.app/emote/{id}/`
- **Variantes de Escala:**
  - `4x.webp` (Máxima resolución disponible, óptima para el lienzo de $512\times512\text{ px}$).
  - `3x.webp` (Resolución intermedia fallback).
  - `2x.webp` (Resolución fallback secundaria).
  - `1x.webp` ($32\times32\text{ px}$, fallback final).

---

## 4. Contratos y Modelos de Datos

### 4.1 Data Transfer Objects (DTOs) en `core.network.dto`

```kotlin
package com.seventv.stickers.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SevenTvSearchResponseDto(
    val items: List<SevenTvEmoteDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SevenTvEmoteDto(
    val id: String,
    val name: String,
    val flags: Int = 0,
    val animated: Boolean = false,
    val data: SevenTvEmoteDataDto? = null,
    val host: SevenTvHostDto? = null
)

@Serializable
data class SevenTvEmoteDataDto(
    val id: String? = null,
    val name: String? = null,
    val flags: Int = 0,
    val animated: Boolean = false,
    val host: SevenTvHostDto? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class SevenTvHostDto(
    val url: String,
    val files: List<SevenTvFileDto> = emptyList()
)

@Serializable
data class SevenTvFileDto(
    val name: String,
    val format: String = "WEBP",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L
)

@Serializable
data class SevenTvEmoteSetDto(
    val id: String,
    val name: String,
    val flags: Int = 0,
    val emotes: List<SevenTvEmoteDto> = emptyList(),
    val capacity: Int = 0,
    @SerialName("owner_name")
    val ownerName: String? = null
)
```

### 4.2 Modelos de Dominio Limpios en `core.network.model`

```kotlin
package com.seventv.stickers.core.network.model

data class SevenTvEmote(
    val id: String,
    val name: String,
    val isAnimated: Boolean,
    val isZeroWidth: Boolean,
    val isNsfw: Boolean,
    val bestAssetUrl: String,
    val availableScales: List<String>
)

data class SevenTvEmoteSet(
    val id: String,
    val name: String,
    val ownerName: String?,
    val emotes: List<SevenTvEmote>
)

data class DownloadedEmoteAsset(
    val emoteId: String,
    val emoteName: String,
    val bytes: ByteArray,
    val isAnimated: Boolean,
    val format: String
)
```

### 4.3 Extensiones de Error en `core.common.result.StickerError`

```kotlin
sealed interface StickerError {
    // Errores preexistentes del motor de medios
    data object CorruptedSourceData : StickerError
    data class UnsupportedFormat(val mimeType: String) : StickerError
    data class ExceedsSizeLimit(val actualBytes: Long, val maxBytes: Long, val canFallbackToStatic: Boolean) : StickerError
    data class NativeEncodingFailed(val details: String) : StickerError
    data class StorageIoError(val cause: Throwable) : StickerError

    // Nuevos errores de red para Subproyecto 2
    data class NetworkError(val statusCode: Int? = null, val message: String) : StickerError
    data object ResourceNotFound : StickerError
    data object RateLimited : StickerError
}
```

---

## 5. Componentes e Interfaces

### 5.1 Interfaz de Servicio Retrofit (`SevenTvApi`)
```kotlin
package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SevenTvApi {
    @GET("v3/emotes")
    suspend fun searchEmotes(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<List<SevenTvEmoteDto>>

    @GET("v3/emotes/{id}")
    suspend fun getEmote(
        @Path("id") emoteId: String
    ): Response<SevenTvEmoteDto>

    @GET("v3/emote-sets/{id}")
    suspend fun getEmoteSet(
        @Path("id") setId: String
    ): Response<SevenTvEmoteSetDto>
}
```

### 5.2 Descargador de Activos (`EmoteDownloader`)
```kotlin
package com.seventv.stickers.core.network.downloader

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote

interface EmoteDownloader {
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>
    suspend fun downloadFromUrl(url: String): StickerResult<ByteArray>
}
```

**Reglas de `EmoteDownloader`:**
1. Si el archivo local en `cacheDir/7tv_emotes_cache/{id}_{scale}.webp` ya existe y no está vacío, lo lee de disco sin consumir red.
2. Si no existe, realiza la petición HTTP GET con OkHttp validando que el `Content-Length` o bytes leídos no superen $15\text{ MB}$.
3. Escribe de forma atómica en disco y devuelve `DownloadedEmoteAsset`.
4. Si la descarga falla, elimina el archivo parcial y devuelve `StickerError.NetworkError` o `StickerError.StorageIoError`.

### 5.3 Repositorio de 7TV (`SevenTvRepository`)
```kotlin
package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet

interface SevenTvRepository {
    suspend fun searchEmotes(query: String, page: Int = 1, limit: Int = 30): StickerResult<List<SevenTvEmote>>
    suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote>
    suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet>
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>
}
```

### 5.4 Puente hacia el Motor de Stickers (`EmoteStickerBridge`)
```kotlin
package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.network.model.SevenTvEmote
import java.io.File

class EmoteStickerBridge(
    private val repository: SevenTvRepository,
    private val mediaPipeline: StickerMediaPipeline
) {
    suspend fun convertEmoteToSticker(
        emote: SevenTvEmote,
        outputDir: File,
        allowStaticFallback: Boolean = true
    ): StickerResult<ProcessedSticker> {
        val downloadResult = repository.downloadEmote(emote)
        val asset = when (downloadResult) {
            is StickerResult.Success -> downloadResult.data
            is StickerResult.Failure -> return downloadResult
        }

        val rawInput = RawMediaInput(
            bytes = asset.bytes,
            isAnimated = asset.isAnimated,
            mimeType = "image/webp"
        )

        val processResult = mediaPipeline.processSticker(rawInput, outputDir)
        return if (processResult is StickerResult.Failure && allowStaticFallback && asset.isAnimated) {
            mediaPipeline.processAsStaticFallback(rawInput, outputDir)
        } else {
            processResult
        }
    }
}
```

---

## 6. Plan de Pruebas y Validación

1. **Pruebas de Serialización (`SevenTvDtoTest`):**
   - Parseo de respuestas JSON reales de 7TV (`search_response.json`, `emote_set.json`).
   - Mapeo adecuado de flags (zero-width, animated, NSFW).
2. **Pruebas de API con MockWebServer (`SevenTvApiTest`):**
   - Éxito HTTP 200 en búsqueda de emotes y sets.
   - HTTP 404 $\to$ `StickerError.ResourceNotFound`.
   - HTTP 429 $\to$ `StickerError.RateLimited`.
   - Error 500 / Fallo de red $\to$ `StickerError.NetworkError`.
3. **Pruebas de Descarga y Caché (`EmoteDownloaderTest`):**
   - Descarga exitosa de `4x.webp`.
   - Acierto de caché en disco (sin llamada HTTP secundaria).
   - Manejo seguro de descargas interrumpidas o corruptas.
4. **Pruebas del Puente Integrado (`EmoteStickerBridgeTest`):**
   - Descarga de emote estático $\to$ Sticker WebP de $512\times512\text{ px}$ $\le 95\text{ KB}$.
   - Descarga de emote animado $\to$ Sticker WebP animado $\le 480\text{ KB}$ ($\le 30\text{ fps}$, $\le 3000\text{ ms}$).
   - Fallback estático activado cuando un emote animado excede el límite de peso.

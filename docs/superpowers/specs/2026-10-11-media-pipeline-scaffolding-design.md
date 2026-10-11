# Especificación de Diseño Técnico: Andamiaje del Proyecto y Motor de Procesamiento de Medios (WebP)

- **Fecha:** 2026-10-11
- **Estado:** Validado y Aprobado
- **Alcance:** Subproyecto 1 — Andamiaje base de Android (`:app`) y motor de normalización/compresión de WebP estático y animado para WhatsApp.
- **Autor/Diseñador:** Antigravity & Pair Programming Partner

---

## 1. Resumen Ejecutivo y Metas

Este documento define la arquitectura, contratos, algoritmos de procesamiento de imagen e integración nativa con `libwebp` para el primer hito de la aplicación **7TV Stickers for WhatsApp**.

### 1.1 Metas Principales
1. Establecer el andamiaje del proyecto Android con Gradle (Kotlin DSL), Version Catalogs (`libs.versions.toml`) y soporte NDK/CMake bajo un modelo monomódulo (`:app`) con Clean Architecture por paquetes.
2. Implementar el motor de normalización geométrica en Kotlin (`CanvasNormalizer`), asegurando dimensiones exactas de $512\times512\text{ px}$ con margen de seguridad transparente de $16\text{ px}$ (área útil centrada de $480\times480\text{ px}$) para stickers y $96\times96\text{ px}$ (margen $1\text{ px}$) para el icono de bandeja (*Tray Icon*).
3. Implementar el compresor adaptativo para stickers estáticos con objetivo de peso $<100\text{ KB}$ (techo seguro $\le 95\text{ KB}$).
4. Implementar el motor de WebP animado mediante extracción de fotogramas, truncamiento de duración a $\le 3000\text{ ms}$, decimación adaptativa de fotogramas ($\le 30\text{ fps}$) y empaquetado nativo mediante JNI con `WebPAnimEncoder` de `libwebp`, garantizando un tamaño final $<500\text{ KB}$ (techo seguro $\le 480\text{ KB}$).
5. Proporcionar una fachada desacoplada (`StickerMediaPipeline`) con tipado estricto de resultados y errores (`StickerResult`), junto con una suite completa de pruebas unitarias en JVM y pruebas de integración NDK.

---

## 2. Requisitos y Especificaciones Técnicas de WhatsApp

WhatsApp impone validaciones binarias no negociables antes de aceptar la instalación de un paquete de stickers:

| Parámetro | Sticker Estático | Sticker Animado | Icono de Bandeja (*Tray Icon*) |
| :--- | :--- | :--- | :--- |
| **Formato** | WebP (`.webp`) | Animated WebP (`.webp`) | WebP (`.webp`) o PNG |
| **Dimensiones** | $512 \times 512\text{ px}$ exactos | $512 \times 512\text{ px}$ exactos | $96 \times 96\text{ px}$ exactos |
| **Margen transparente** | $16\text{ px}$ en los 4 bordes | $16\text{ px}$ en los 4 bordes | $1\text{ px}$ en los 4 bordes |
| **Área útil de dibujo** | $480 \times 480\text{ px}$ centrada | $480 \times 480\text{ px}$ centrada | $94 \times 94\text{ px}$ centrada |
| **Límite de peso** | $< 100\text{ KB}$ (objetivo $\le 95\text{ KB}$) | $< 500\text{ KB}$ (objetivo $\le 480\text{ KB}$) | $< 50\text{ KB}$ |
| **Duración máxima** | N/A | $\le 3.0\text{ s}$ ($3000\text{ ms}$) | Estático |
| **Tasa de fotogramas** | N/A | $\le 30\text{ fps}$ (recomendado $15\text{ - }24\text{ fps}$) | N/A |
| **Bucle de animación** | N/A | Bucle infinito (`loop_count = 0`) | N/A |

---

## 3. Arquitectura del Proyecto y Organización de Paquetes

### 3.1 Configuración de Plataforma y Build
- **Lenguaje:** Kotlin 2.0+ (JVM 17).
- **Min SDK:** 26 (Android 8.0 Oreo) para compatibilidad nativa con WebP y decodificadores gráficos.
- **Compile / Target SDK:** 34+.
- **Build System:** Gradle Kotlin DSL con Version Catalog (`gradle/libs.versions.toml`).
- **CMake & NDK:** CMake 3.22.1+, NDK r25b+ con filtros ABI `arm64-v8a`, `armeabi-v7a`, `x86_64`.

### 3.2 Estructura de Directorios

```text
app/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt              # Script de compilación de libwebp y wrapper JNI
│   │   │   ├── webp_anim_encoder.cpp       # Implementación JNI del codificador animado
│   │   │   └── libwebp/                    # Código fuente C de libwebp (src/ e include/)
│   │   │
│   │   └── java/com/seventv/stickers/
│   │       └── core/
│   │           ├── common/
│   │           │   ├── result/
│   │           │   │   ├── StickerResult.kt
│   │           │   │   └── StickerError.kt
│   │           │   └── dispatcher/
│   │           │       └── CoroutineDispatchers.kt
│   │           │
│   │           └── media/
│   │               ├── model/
│   │               │   ├── RawMediaInput.kt
│   │               │   ├── ProcessedSticker.kt
│   │               │   ├── AnimationFrame.kt
│   │               │   └── StickerSpecs.kt
│   │               │
│   │               ├── normalizer/
│   │               │   ├── StickerNormalizer.kt
│   │               │   └── CanvasStickerNormalizer.kt
│   │               │
│   │               ├── animated/
│   │               │   ├── FrameExtractor.kt
│   │               │   ├── AnimatedWebPEncoder.kt
│   │               │   └── NativeWebPAnimEncoder.kt
│   │               │
│   │               ├── optimizer/
│   │               │   └── StickerSizeOptimizer.kt
│   │               │
│   │               └── StickerMediaPipeline.kt
│   │
│   ├── test/                               # Tests unitarios en JVM
│   │   └── java/com/seventv/stickers/core/media/
│   │       ├── CanvasStickerNormalizerTest.kt
│   │       └── StickerSizeOptimizerTest.kt
│   │
│   └── androidTest/                        # Tests instrumentados en emulador/dispositivo
│       └── java/com/seventv/stickers/core/media/
│           └── NativeWebPAnimEncoderTest.kt
```

---

## 4. Motor de Normalización de Lienzo (Canvas Normalizer)

### 4.1 Contrato del Normalizador

```kotlin
interface StickerNormalizer {
    fun normalizeToStickerCanvas(source: Bitmap): Bitmap
    fun normalizeToTrayIcon(source: Bitmap): Bitmap
}
```

### 4.2 Algoritmo de Escalado y Centrado (`CanvasStickerNormalizer`)

1. **Sticker ($512\times512\text{ px}$ con $16\text{ px}$ de margen):**
   - Espacio útil: $W_{\text{usable}} = 512 - 2 \times 16 = 480\text{ px}$, $H_{\text{usable}} = 480\text{ px}$.
   - Factor de escala con conservación de aspect ratio:
     $$s = \min\left(\frac{480}{W_{\text{src}}}, \frac{480}{H_{\text{src}}}\right)$$
   - Dimensiones escaladas:
     $$W_{\text{dst}} = \text{round}(W_{\text{src}} \times s), \quad H_{\text{dst}} = \text{round}(H_{\text{src}} \times s)$$
   - Offsets de centrado:
     $$X_{\text{offset}} = \frac{512 - W_{\text{dst}}}{2f}, \quad Y_{\text{offset}} = \frac{512 - H_{\text{dst}}}{2f}$$
   - Creación del lienzo destino: `Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)`.
   - Dibujo en `Canvas`:
     - `Paint` configurado con `isAntiAlias = true` e `isFilterBitmap = true`.
     - `canvas.drawBitmap(source, null, RectF(X_offset, Y_offset, X_offset + W_dst, Y_offset + H_dst), paint)`.

2. **Tray Icon ($96\times96\text{ px}$ con $1\text{ px}$ de margen):**
   - Espacio útil: $94\times94\text{ px}$.
   - Se aplica la misma lógica matemática proyectando sobre un `Bitmap` de $96\times96\text{ px}$ con $1\text{ px}$ de margen transparente perimetral.

---

## 5. Compresor de Stickers Estáticos

La compresión de stickers estáticos se apoya en el soporte nativo de WebP del framework de Android (`Bitmap.compress`):

1. **Calidad Inicial:** Iniciar en calidad lossy $90\%$.
2. **Evaluación de Tamaño:**
   - Si $\text{bytes} \le 95\text{ KB}$, se acepta de inmediato y se escribe a disco.
   - Si $\text{bytes} > 95\text{ KB}$, se reduce la calidad en decrementos: $80\% \to 70\% \to 60\% \to 50\%$.
3. **Reducción Geométrica Extrema (Fallback secundario):**
   - Si a calidad $50\%$ persiste el exceso de peso (situación residual en imágenes con alto ruido de alta frecuencia), se reescala el área útil un $10\%$ adicional ($432\times432\text{ px}$) dentro del canvas de $512\times512\text{ px}$ para reducir la entropía visual y se recomprime.

---

## 6. Pipeline de WebP Animado & Integración NDK (`libwebp`)

### 6.1 Extracción de Fotogramas (`FrameExtractor`)
- Recibe un `ByteArray` o `InputStream` (formato GIF o WebP animado).
- Extrae cada fotograma en un `Bitmap` ARGB_8888 junto con su duración individual en milisegundos (`durationMs`).
- **Truncamiento de duración:** Se acumula el tiempo total $\sum \text{durationMs}$. Si excede $3000\text{ ms}$, la secuencia se trunca en el fotograma que alcance los $3000\text{ ms}$ y se descartan los fotogramas excedentes.

### 6.2 Decimación de Fotogramas y Control de FPS
- Si la tasa de refresco detectada es $> 30\text{ fps}$ (o si el archivo excede los $480\text{ KB}$ en la primera pasada de compresión):
  - Se deciman fotogramas combinando frames adyacentes:
    $$\text{frame}[i].\text{durationMs} = \text{frame}[i].\text{durationMs} + \text{frame}[i+1].\text{durationMs}$$
  - Escalas de decimación: $30\text{ fps} \to 20\text{ fps} \to 15\text{ fps}$.

### 6.3 Interfaz JNI y Puente Nativo C++

#### Declaración en Kotlin:
```kotlin
class NativeWebPAnimEncoder : AnimatedWebPEncoder {
    external override fun encode(
        bitmaps: Array<Bitmap>,
        timestampsMs: IntArray,
        quality: Float,
        outputPath: String
    ): Boolean

    companion object {
        init {
            System.loadLibrary("seven_tv_webp")
        }
    }
}
```

#### Operaciones en C++ (`src/main/cpp/webp_anim_encoder.cpp`):
1. **Configuración del Encoder:**
   ```cpp
   WebPAnimEncoderOptions anim_config;
   WebPAnimEncoderOptionsInit(&anim_config);
   anim_config.anim_params.loop_count = 0; // Bucle infinito requerido por WhatsApp

   WebPAnimEncoder* enc = WebPAnimEncoderNew(512, 512, &anim_config);
   ```
2. **Iteración y Transferencia de Píxeles:**
   - Para cada frame $k \in [0, N-1]$:
     - Bloquear memoria del Bitmap: `AndroidBitmap_lockPixels(env, bitmapObj, &pixels)`.
     - Importar buffer RGBA: `WebPPictureImportRGBA(&pic, (uint8_t*)pixels, stride)`.
     - Configurar compresión: `WebPConfigInit(&config); config.quality = quality; config.method = 4;`.
     - Agregar frame con marca de tiempo: `WebPAnimEncoderAdd(enc, &pic, timestampsMs[k], &config)`.
     - Desbloquear memoria: `AndroidBitmap_unlockPixels(env, bitmapObj)`.
     - Liberar estructura: `WebPPictureFree(&pic)`.
3. **Ensamblado y Escritura:**
   - `WebPAnimEncoderAssemble(enc, &webp_data)`.
   - Escribir el buffer de bytes resultante directamente a `outputPath`.
   - Liberar encoder: `WebPDataClear(&webp_data); WebPAnimEncoderDelete(enc);`.

---

## 7. Modelado de Datos y Manejo de Errores

### 7.1 Tipos de Dominio

```kotlin
sealed interface StickerResult<out T> {
    data class Success<out T>(val data: T) : StickerResult<T>
    data class Failure(val error: StickerError) : StickerResult<Nothing>
}

sealed interface StickerError {
    data object CorruptedSourceData : StickerError
    data class UnsupportedFormat(val mimeType: String) : StickerError
    data class ExceedsSizeLimit(val actualBytes: Long, val maxBytes: Long, val canFallbackToStatic: Boolean) : StickerError
    data class NativeEncodingFailed(val details: String) : StickerError
    data class StorageIoError(val cause: Throwable) : StickerError
}

data class ProcessedSticker(
    val file: File,
    val width: Int = 512,
    val height: Int = 512,
    val byteSize: Long,
    val isAnimated: Boolean,
    val durationMs: Int = 0,
    val frameCount: Int = 1
)
```

### 7.2 Política de Fallback a Estático
Si una animación compleja no logra descender de los $480\text{ KB}$ tras la decimación a $15\text{ fps}$ y calidad $40\%$:
- El pipeline devuelve `StickerError.ExceedsSizeLimit(canFallbackToStatic = true)`.
- El consumidor (`StickerMediaPipeline`) expone el método alternativo `processAsStaticFallback()`, el cual toma el primer fotograma de la animación y lo procesa a través del pipeline estático, produciendo un sticker de $<100\text{ KB}$ nítido sin detener el flujo de trabajo del usuario.

---

## 8. Estrategia de Testing

| Nivel | Componente | Tipo de Prueba | Verificación |
| :--- | :--- | :--- | :--- |
| **JVM Unit Test** | `CanvasStickerNormalizerTest` | Unitario (Robolectric / Mock) | - Relación de aspecto 1:1, 1:3 y 3:1 centradas en $512\times512$.<br>- Padding perimetral de $16\text{ px}$ con alfa cero.<br>- Tray icon de $96\times96\text{ px}$ con $1\text{ px}$ de margen. |
| **JVM Unit Test** | `StickerSizeOptimizerTest` | Unitario | - Bucle adaptativo de calidad.<br>- Respeto del límite estático $<95\text{ KB}$.<br>- Decimación de frames y suma de timestamps acumulados. |
| **JVM Unit Test** | `FrameThrottlerTest` | Unitario | - Truncamiento estricto a $\le 3000\text{ ms}$. |
| **NDK Instrumented** | `NativeWebPAnimEncoderTest` | Instrumented (Emulador/Dispositivo) | - Carga exitosa de `libseven_tv_webp.so`.<br>- Ensamblado de secuencia animada de 5 frames.<br>- Header `VP8X` y chunks `ANIM`/`ANMF` presentes.<br>- `ImageDecoder` nativo lee el sticker generado.<br>- Peso $<480\text{ KB}$. |

---

## 9. Criterios de Aceptación y Próximos Pasos

### 9.1 Criterios de Aceptación de este Hito
1. El proyecto compila limpiamente en Android Studio con Gradle Kotlin DSL y CMake.
2. Todo emote estático se exporta a `.webp` de exactamente $512\times512\text{ px}$, con $16\text{ px}$ de margen y $<100\text{ KB}$.
3. Todo emote animado se exporta a WebP animado de $512\times512\text{ px}$, $16\text{ px}$ de margen, $\le 3\text{ s}$, $\le 30\text{ fps}$ y $<500\text{ KB}$.
4. Los iconos de bandeja se generan a $96\times96\text{ px}$ y $<50\text{ KB}$.
5. El 100% de las pruebas unitarias y de integración pasan satisfactoriamente.

### 9.2 Próximos Subproyectos (Hitos Posteriores)
- **Subproyecto 2:** Integración con WhatsApp (`StickerContentProvider`, intents de registro de packs, validación con la app de WhatsApp).
- **Subproyecto 3:** Cliente de API de 7TV y persistencia local (Room Database, DataStore, gestión de packs locales).
- **Subproyecto 4:** Interfaz de usuario en Jetpack Compose (Buscador, Explorador de canales, Gestor de paquetes y Simulador de contraste).

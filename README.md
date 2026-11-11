<p align="center">
  <img src="docs/assets/logo.png" alt="7TV Stickers Logo" width="160" />
</p>

<h1 align="center">7TV Stickers for WhatsApp</h1>

<p align="center">
  Lleva los mejores emotes de <b>7TV</b> (Twitch, Kick y catálogo global) directamente a <b>WhatsApp</b> en Android de forma nativa, rápida y sin pérdida de calidad.
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.0" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack-Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License" /></a>
  <a href="https://github.com/gek0v/7tv-whatsapp/actions/workflows/android-ci.yml"><img src="https://img.shields.io/badge/CI-Passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white" alt="CI Status" /></a>
</p>

---

## 📖 Descripción

**7TV Stickers for WhatsApp** es una aplicación nativa para Android diseñada para conectar la vibrante cultura de emotes de streaming con las conversaciones diarias de WhatsApp.

Desarrollada con **Kotlin** y **Jetpack Compose**, la aplicación permite explorar el catálogo global de 7TV, consultar canales de streamers populares de Twitch o Kick, y transformar automáticamente cualquier emote estático o animado en paquetes de stickers 100% compatibles con las estrictas normativas técnicas de WhatsApp.

Gracias a un pipeline de procesamiento multimedia de alto rendimiento respaldado por **libwebp** nativo (C++ mediante JNI / NDK), la aplicación redimensiona, aplica márgenes transparentes de seguridad, optimiza la compresión y valida los límites de framerate y peso en cuestión de milisegundos.

---

## ✨ Características

* 🔍 **Buscador global de emotes:** Explora millones de emotes en la red de 7TV con filtros avanzados (estáticos, animados, populares, globales o búsqueda directa por ID/URL).
* 📺 **Importación por canal de streamer:** Introduce el nombre de usuario de cualquier creador en Twitch o Kick para obtener al instante todos los emotes activos de su comunidad.
* 📦 **División inteligente de packs (Pack Splitting):** Si un canal excede el límite máximo de WhatsApp (30 stickers), la aplicación sugiere y distribuye automáticamente los emotes en paquetes balanceados de 3 a 30 stickers listos para exportar.
* 🗂️ **Gestor de paquetes locales:** Organiza tu colección privada: crea nuevos packs, renómbralos, reordena los stickers, actualiza packs ya añadidos a WhatsApp y comparte emotes animados como GIF directamente en otras aplicaciones.
* 🎭 **Simulador de chat / Inspector de stickers:** Previsualiza cada sticker sobre fondos oscuros (#121B22), claros (#ECE5DD) y con el fondo clásico de WhatsApp para detectar imperfecciones de transparencia o bordes residuales.
* 😃 **Mapeo contextual de emojis:** Asocia hasta 3 emojis a cada sticker para habilitar la sugerencia predictiva automática desde el teclado nativo de WhatsApp.
* ⚡ **Pipeline nativo C++ (libwebp):** Procesamiento de imágenes optimizado a bajo nivel para codificar Animated WebP respetando el límite estricto de 500 KB sin perder fluidez ni fidelidad.

---

## ⚙️ Requisitos de WhatsApp

WhatsApp impone directrices técnicas muy estrictas para aceptar la importación de stickers mediante su API oficial de `ContentProvider`:

| Parámetro | Stickers Estáticos | Stickers Animados | Icono de Bandeja (Tray Icon) |
| :--- | :--- | :--- | :--- |
| **Formato de archivo** | WebP (.webp) estático | WebP animado (.webp) | WebP / PNG estático |
| **Dimensiones de imagen** | $512 \times 512\text{ px}$ exactos | $512 \times 512\text{ px}$ exactos | $96 \times 96\text{ px}$ exactos |
| **Tamaño máximo de archivo** | $< 100\text{ KB}$ | $< 500\text{ KB}$ | $< 50\text{ KB}$ |
| **Margen de seguridad (Padding)** | $16\text{ px}$ transparente por borde | $16\text{ px}$ transparente por borde | $1\text{ px}$ transparente por borde |
| **Duración / Framerate** | N/A | $\le 3\text{ s}$ / $\le 30\text{ fps}$ | Estático |
| **Capacidad del paquete** | Mínimo 3 — Máximo 30 stickers | Mínimo 3 — Máximo 30 stickers | 1 icono representativo por pack |
| **Mapeo de emojis** | Máximo 3 emojis por sticker | Máximo 3 emojis por sticker | N/A |

> [!NOTE]
> 7TV Stickers normaliza, añade el margen perimetral transparente y ajusta la tasa de bits y resolución automáticamente para que nunca tengas que preocuparte por rechazos en WhatsApp.

---

## 📥 Descarga del APK

Puedes obtener la última versión instalable lista para usar:

1. Ve a la sección de **[Releases](../../releases)** en este repositorio de GitHub.
2. Descarga el archivo `app-debug.apk` o la versión más reciente en formato `.apk`.
3. En tu dispositivo Android (Android 8.0 o superior):
   - Abre el archivo descargado.
   - Habilita la instalación desde fuentes desconocidas en caso de que el sistema lo solicite.
   - ¡Listo! Abre 7TV Stickers y comienza a añadir tus packs favoritos.

---

## 💻 Compilación Local

Si eres desarrollador y deseas compilar o depurar la aplicación en tu entorno local:

### Requisitos Previos
* **Android Studio Iguana (2023.2.1)** o superior.
* **JDK 17** (configurado como Gradle JDK).
* **Android SDK 34** (Android 14) instalado.
* **Android NDK & CMake 3.22.1** (para compilar `libwebp` en C++).

### Pasos de Compilación

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/gek0v/7tv-whatsapp.git
   cd 7tv-whatsapp
   ```

2. **Ejecutar las pruebas unitarias:**
   ```bash
   # Linux / macOS
   ./gradlew test

   # Windows
   .\gradlew.bat test
   ```

3. **Compilar el APK en modo depuración (Debug):**
   ```bash
   # Linux / macOS
   ./gradlew assembleDebug

   # Windows
   .\gradlew.bat assembleDebug
   ```
   El binario compilado estará disponible en:  
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🏗️ Arquitectura

El proyecto está diseñado siguiendo los principios de **Clean Architecture**, **MVI/MVVM reactivo** y modularidad:

```text
┌─────────────────────────────────────────────────────────────┐
│                       UI / PRESENTATION                     │
│    Jetpack Compose • Material 3 • Navigation • ViewModels   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                            DOMAIN                           │
│       UseCases • Modelos de Dominio • Validadores WhatsApp  │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                             DATA                            │
│  7TV API Client (Retrofit/OkHttp) • Room Database • Coil    │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                     MEDIA & NATIVE PIPELINE                 │
│         libwebp C++ (CMake JNI) • Canvas / ImageDecoder     │
│             WhatsApp StickerContentProvider (IPC)           │
└─────────────────────────────────────────────────────────────┘
```

* **Capa de Presentación:** Construida íntegramente con **Jetpack Compose** y **Material You (Material 3)**. Utiliza estados inmutables con `StateFlow` y eventos unidireccionales.
* **Capa de Dominio:** Casos de uso (`GetChannelEmotesUseCase`, `ImportPackUseCase`, `ValidatePackUseCase`) encargados de orquestar la lógica de negocio y las restricciones de stickers.
* **Capa de Datos:** Repositorios que integran la API v3 de 7TV (`https://7tv.io/v3/`), base de datos local con **Room** para persistir colecciones offline y preferencias con **DataStore**.
* **Integración WhatsApp:** Implementación de `StickerContentProvider` con la autoridad `com.seventv.stickers.stickercontentprovider` y lectura autorizada para `com.whatsapp.sticker.READ`.
* **Motor Nativo C++:** Código fuente en `app/src/main/cpp/` con `libwebp` para codificación eficiente de fotogramas y reducción de latencia de exportación.

Para más detalles técnicos y guías de diseño, consulta:
* 📐 [**ARCHITECTURE.md**](ARCHITECURE.md): Arquitectura detallada, diagramas de secuencia, esquema de bases de datos y especificación de intents.
* 🎨 [**DESIGN.md**](DESIGN.md): Guía de diseño visual, paleta de colores, tokens de contraste y flujos de usuario.
* 🤝 [**CONTRIBUTING.md**](CONTRIBUTING.md): Pautas de colaboración, estilos de código y convención de commits.

---

## 📄 Licencia

Este proyecto está bajo la Licencia **MIT**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Copyright (c) 2026 **gek0v / contribuyentes de 7TV Stickers**.
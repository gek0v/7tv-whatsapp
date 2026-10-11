# 7TV Stickers for WhatsApp (Android)

Una aplicación nativa para Android desarrollada con Kotlin y Jetpack Compose que permite buscar, seleccionar y exportar emotes de **7TV** (de canales de Twitch, Kick o del catálogo global) directamente como paquetes de stickers estáticos y animados en **WhatsApp**.

---

## 🚀 Características Principales

* **Buscador global de emotes:** Explora el catálogo de 7TV con filtros por tipo (estáticos, animados, populares, globales) o pegando directamente URLs.
* **Importación por canal de streamer:** Busca cualquier creador de contenido y extrae su conjunto completo de emotes.
* **División inteligente de packs (Pack Splitting):** Si un canal tiene más de 30 emotes, la aplicación sugiere y divide automáticamente el catálogo en paquetes de 3 a 30 stickers listos para exportar.
* **Gestor de paquetes locales:** Crea, edita, reordena y actualiza tus paquetes sin necesidad de recrearlos en WhatsApp.
* **Simulador de chat / Inspector:** Previsualiza el sticker en fondos oscuros, claros y de chat de WhatsApp para detectar bordes sucios o artefactos de transparencia.
* **Asignación de emojis:** Vincula hasta 3 emojis por sticker para habilitar la sugerencia contextual en el teclado de WhatsApp.

---

## ⚙️ Reglas y Restricciones Técnicas (WhatsApp)

WhatsApp exige requisitos estrictos para aceptar paquetes de stickers:

| Requisito | Stickers Estáticos | Stickers Animados | Tray Icon (Icono de pack) |
| :--- | :--- | :--- | :--- |
| **Formato** | WebP (.webp) | Animated WebP (.webp) | WebP / PNG |
| **Dimensiones** | $512 \times 512\text{ px}$ exactos | $512 \times 512\text{ px}$ exactos | $96 \times 96\text{ px}$ exactos |
| **Límite de peso** | $< 100\text{ KB}$ | $< 500\text{ KB}$ | $< 50\text{ KB}$ |
| **Padding** | $16\text{ px}$ transparente | $16\text{ px}$ transparente | $1\text{ px}$ transparente |
| **Duración / FPS** | N/A | $\le 3\text{ s}$ / $\le 30\text{ fps}$ | Estático |
| **Límites de pack** | Mínimo 3 — Máximo 30 | Mínimo 3 — Máximo 30 | 1 icono por pack |

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** Kotlin (100% nativo).
* **UI Framework:** Jetpack Compose + Material Design 3 (Material You).
* **Inyección de dependencias:** Hilt.
* **Asincronía:** Kotlin Coroutines & Flow.
* **Persistencia:** Room Database + Jetpack DataStore.
* **Red:** Ktor Client / Retrofit 2 + Kotlinx Serialization.
* **Carga de imágenes:** Coil 3 (soporte para Animated WebP).
* **Integración con WhatsApp:** `ContentProvider` personalizado (`com.whatsapp.sticker.READ`).

---

## 📂 Documentación del Proyecto

El proyecto cuenta con especificaciones detalladas en la raíz del repositorio:

* [`ARCHITECTURE.md`](ARCHITECTURE.md): Diagramas de capas, clean architecture, pipeline de procesamiento de WebP, esquema del `ContentProvider` e intents de WhatsApp.
* [`DESIGN.md`](DESIGN.md): Guía de estilo UI/UX, tokens de color (Dark Theme), diseño de pantallas, simulador de contraste y flujos de usuario.

---

## 🔄 Flujo de Trabajo (Pipeline de Medios)

1. **Descarga:** Se consulta la API v3 de 7TV (`https://7tv.io/v3/`) y se obtiene el recurso WebP/GIF/AVIF.
2. **Normalización:** Se ajusta el tamaño en un `Canvas` de $512 \times 512\text{ px}$ manteniendo el aspect ratio y aplicando el margen de seguridad de $16\text{ px}$.
3. **Optimización de peso:**
   * **Estáticos:** Compresión por debajo de 100 KB.
   * **Animados:** Control de framerate y duración ($\le 3\text{ s}$, $< 500\text{ KB}$).
4. **Almacenamiento y exposición:** Los archivos se guardan en el almacenamiento interno de la app y se exponen a WhatsApp mediante `StickerContentProvider`.
5. **Registro en WhatsApp:** Se dispara el intent `com.whatsapp.intent.action.ENABLE_STICKER_PACK`.

---

## 🗺️ Roadmap de Funcionalidades

- [x] Planificación y arquitectura técnica (`ARCHITECTURE.md`).
- [x] Especificación de interfaz de usuario y experiencia (`DESIGN.md`).
- [ ] Implementación del cliente de API de 7TV (búsqueda y canales).
- [ ] Módulo de procesamiento y compresión de WebP nativo.
- [ ] Implementación y pruebas del `StickerContentProvider`.
- [ ] Exportación/importación de paquetes en formato de copia de seguridad (ZIP/JSON).
- [ ] Integración con el menú "Compartir" de Android para URLs directas de 7TV.

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia MIT. Consulta el archivo `LICENSE` para más detalles.
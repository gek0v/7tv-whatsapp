# DESIGN.md - UI/UX Design System & Experience Specification

Este documento define la dirección de arte, el sistema de diseño visual, los flujos de interacción y las pantallas clave para la aplicación Android de emotes de 7TV a WhatsApp.

---

## 1. Filosofía de Diseño y Principios Rectores

* **Velocidad sin fricción:** Añadir emotes debe requerir la menor cantidad de toques posible (máximo 3 toques desde la búsqueda hasta tener el pack listo para exportar).
* **Fidelidad y Transparencia:** Mostrar siempre cómo se verá el emote en el entorno real de chat antes de que el usuario lo exporte a WhatsApp.
* **Identidad híbrida (Twitch/7TV meets Material You):** Combina el lenguaje visual de streaming moderno (tonos oscuros de alto contraste, acentos neón) con la ergonomía táctil y fluidez de Material Design 3.
* **Control informativo en tiempo real:** WhatsApp impone límites duros (3 a 30 stickers, <100 KB/<500 KB). La interfaz debe educar y prevenir errores de forma proactiva, no bloquear mediante alertas tardías.

---

## 2. Sistema de Diseño (Design Tokens)

### 2.1 Paleta de Color (Modo Oscuro Principal)

La aplicación está concebida con prioridad en **Dark Theme** debido al perfil de los usuarios de Twitch/7TV.

| Token | Hex | Rol / Uso |
| :--- | :--- | :--- |
| `md_theme_dark_background` | `#0E0F12` | Fondo principal (negro neutro profundo) |
| `md_theme_dark_surface` | `#16181D` | Superficie de tarjetas y bottom sheets |
| `md_theme_dark_surfaceVariant` | `#22252C` | Elementos interactivos secundarios / Inputs |
| `md_theme_dark_primary` | `#00D2FF` | Acento cian eléctrico 7TV (CTA principal, badges activos) |
| `md_theme_dark_secondary` | `#9B51E0` | Púrpura Twitch (acciones secundarias, animaciones) |
| `md_theme_dark_tertiary` | `#25D366` | Verde WhatsApp (botón de exportación / sincronización) |
| `md_theme_dark_error` | `#FF4858` | Alertas de tamaño excedido, pack incompleto (<3) |
| `md_theme_dark_onSurface` | `#F1F3F5` | Texto principal de alto contraste |
| `md_theme_dark_onSurfaceVariant` | `#9AA0A6` | Metadatos, alias de emotes, placeholders |

### 2.2 Fondos de Previsualización (Simulador de Chat)

Para comprobar el recorte de bordes y canales alfa:
* **WhatsApp Dark Chat:** `#0B141A` (Fondo de burbuja: `#005C4B`)
* **WhatsApp Light Chat:** `#EFEAE2` (Fondo de burbuja: `#D9FDD3`)
* **Checkerboard (Transparencia pura):** Cuadrícula gris claro `#2A2E39` y `#1E2028`.

### 2.3 Tipografía

Utiliza la escala tipográfica estándar de **Material 3** usando `Roboto` o `Inter`:

* **Display Small / Headline Medium:** Títulos de vista y nombre del pack (`24sp`, Medium / Bold).
* **Title Small:** Nombres de emotes (`14sp`, SemiBold, Monospace opcional para nombres de emotes técnicos).
* **Body Medium / Small:** Autores, tamaño de archivo, estadísticas (`12sp` - `14sp`, Regular).
* **Label Large:** Botones de acción y chips (`14sp`, Medium).

---

## 3. Mapa de Navegación y Arquitectura de Pantallas

La aplicación utiliza una estructura de **Bottom Navigation Bar** con 3 pestañas principales:

```
[Main Navigation Bar]
  ├── 1. Explorar / Búsqueda (Emotes populares, globales, búsqueda por nombre)
  ├── 2. Canales (Buscar streamer, sets de emotes de Twitch/Kick)
  └── 3. Mis Packs (Gestión local, estado de sincronización con WhatsApp)
```

---

## 4. Especificación Detallada de Pantallas

### 4.1 Pantalla 1: Explorador & Búsqueda (Home)

#### Componentes:
* **Barra de Búsqueda Superior (Persistent Search Bar):**
  * Campo de texto con icono de lupa y botón para pegar enlace directo de 7TV (`7tv.app/emotes/...`).
  * Filtros en chips deslizables horizontalmente: `Todos`, `Animados`, `Estáticos`, `Populares hoy`, `Globales`.
* **Cuadrícula de Emotes (Staggered/Adaptive Grid):**
  * Columnas: 3 en móvil vertical, 5 en horizontal o tablet.
  * Cada celda contiene:
    * Contenedor cuadrado con fondo contrastado tenue.
    * Imagen del emote (renderizado animado mediante Coil con loop continuo).
    * Badge en esquina superior derecha: etiqueta `GIF` o `WebP`.
    * Nombre recortado con elipsis a 1 línea.
    * Botón flotante `+` para añadir rápidamente al "Pack Activo".
* **Banner Flotante Inferior ("Pack Activo en Edición"):**
  * Aparece cuando el usuario añade al menos 1 emote.
  * Muestra: Miniaturas de los últimos 3 emotes, contador `12/30` y botón `Ver Pack`.

---

### 4.2 Pantalla 2: Explorador de Canal (Streamer Hub)

#### Componentes:
* **Buscador de Usuario:**
  * Sugerencias autocompletadas conectadas al resolver de usuarios de 7TV/Twitch.
* **Cabecera del Canal:**
  * Avatar circular, nombre del streamer y total de emotes habilitados (ej. `Ibai - 114 emotes`).
* **Acción Global Destacada ("Importar Canal Completo"):**
  * Botón primario prominente con icono de WhatsApp.
  * Abre el modal interactivo de **División Inteligente (Pack Splitting)**.
* **Listado por Categorías:**
  * Pestañas: `Todos`, `Estáticos`, `Animados`.
  * Selección múltiple mediante pulsación prolongada (*Long Press*) o botón `Seleccionar varios`.

---

### 4.3 Pantalla 3: Gestor de Paquetes ("Mis Packs")

#### Componentes:
* **Tarjetas de Paquetes:**
  * **Cabecera:** Nombre del paquete, autor, número de stickers (`18/30`).
  * **Previsualización en tira:** Cuadrícula de 4x1 o 5x1 mostrando los primeros emotes del pack.
  * **Chip de Estado de WhatsApp:**
    * 🟢 *Sincronizado* (Instalado en WhatsApp sin cambios pendientes).
    * 🟡 *Pendiente de actualizar* (Se han añadido/quitado emotes tras enviarlo a WhatsApp).
    * ⚪ *No exportado*.
* **Acciones Rápidas:**
  * Botón verde `Añadir a WhatsApp` (o `Actualizar en WhatsApp`).
  * Menú de tres puntos: `Duplicar`, `Renombrar`, `Exportar respaldo (ZIP)`, `Eliminar`.
* **Botón Flotante (FAB):**
  * `Crear nuevo pack vacío`.

---

### 4.4 Pantalla 4: Editor de Paquete & Inspector de Stickers

#### Componentes:
* **Indicador de Salud del Pack (Top Bar):**
  * Barra de progreso con 3 estados visuales:
    * **Rojo (< 3 stickers):** "Faltan $X$ para el mínimo permitido". Botón exportar inhabilitado.
    * **Verde (3 a 30 stickers):** "Listo para WhatsApp".
    * **Naranja (> 30 stickers):** "Límite excedido. Se sugiere dividir el paquete".
* **Reordenación Drag & Drop:**
  * Cuadrícula con interacción de arrastre para definir el orden en WhatsApp.
  * El primer sticker se define automáticamente como **Tray Icon** (icono de bandeja), con opción de cambiarlo con un toque.
* **Modal Inspector de Sticker (Al pulsar sobre un emote):**
  * Visualizador ampliado ($512 \times 512$).
  * Selector de fondo: `Blanco` | `Oscuro` | `Burbuja WhatsApp`.
  * Asignador de Emojis: Asignar hasta 3 emojis estándar (ej. 😂, 🔥) para que WhatsApp sugiera el sticker al escribir esos emojis.
  * Métricas técnicas: Peso optimizado (ej. `184 KB / 500 KB`), duración de animación (`2.1s`), FPS (`24 fps`).
  * Acciones: `Eliminar del pack`, `Convertir a estático (congelar frame)`.

---

## 5. Microinteracciones y Patrones de UX Críticos

### 5.1 Flujo de División de Canal (>30 Emotes)

```
[Usuario pulsa "Importar Canal" (84 emotes)]
                   │
                   ▼
┌────────────────────────────────────────────────────────┐
│ Modal: "Canal demasiado grande para un solo paquete"    │
│ WhatsApp permite máx. 30 stickers por lote.            │
├────────────────────────────────────────────────────────┤
│ Opciones:                                              │
│  (•) Crear 3 paquetes automáticos:                     │
│      - [x] Streamer Pack 1 (30 emotes)                 │
│      - [x] Streamer Pack 2 (30 emotes)                 │
│      - [x] Streamer Pack 3 (24 emotes)                 │
│  ( ) Seleccionar manualmente los 30 mejores            │
├────────────────────────────────────────────────────────┤
│ [Cancelar]                 [Crear y Preparar Exportación]
└────────────────────────────────────────────────────────┘
```

### 5.2 Estado de Carga y Optimización de Medios

* **Transición de procesamiento:** Cuando se agregan múltiples emotes animados, un indicador no bloqueante en segundo plano procesa los WebP.
* Si un sticker animado excede 500 KB tras el primer pase:
  * Mostrar badge de advertencia `Optimizando peso...`.
  * La app reduce progresivamente la tasa de frames (ej. de 30 fps a 18 fps) de forma transparente para el usuario.

### 5.3 Feedback háptico (Vibración)

* `HapticFeedbackType.LongPress`: Al entrar en modo de selección múltiple o iniciar un arrastre.
* `HapticFeedbackType.TextHandleMove`: Al reordenar posiciones dentro del pack.
* `HapticFeedbackType.Success`: Al completar la importación exitosa hacia WhatsApp.

---

## 6. Accesibilidad e Internacionalización

* **Contraste de Color:** Ratios mínimos de contraste de texto de $4.5:1$ cumpliendo WCAG 2.1 AA.
* **Soporte de TalkBack / Screen Readers:**
  * Cada celda de emote tiene `contentDescription` con el nombre fonético del emote (ej. *"Emote PepeLa, sticker animado"*).
* **Internacionalización:**
  * Soporte inicial en Español (`es-ES`, `es-419`) e Inglés (`en-US`).
  * Los nombres propios de los emotes de 7TV nunca se traducen ni alteran.
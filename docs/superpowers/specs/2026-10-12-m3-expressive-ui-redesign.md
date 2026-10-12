# Especificación de Diseño: Rediseño UI/UX con Material Design 3 Expressive

**Fecha:** 2026-10-12  
**Estado:** Aprobado para Planificación  
**Proyecto:** 7TV Stickers for WhatsApp (Android)  
**Alcance:** Remodelación completa de la interfaz de usuario (UI), experiencia de usuario (UX) y sistema de diseño adaptado a Material Design 3 (M3).

---

## 1. Resumen Ejecutivo y Metas

### 1.1 Objetivo
Modernizar la interfaz y experiencia de la aplicación de stickers de 7TV para WhatsApp, transformándola en un producto nativo de alta calidad visual y ergonomía táctil bajo las directrices de **Material Design 3 Expressive**.

### 1.2 Principios Rectores
1. **Ergonomía sin fricción:** Explorar y recopilar emotes para crear un pack de WhatsApp en 3 toques o menos.
2. **Identidad híbrida M3 Expressive:** Fusión de la cultura de streaming (Cian 7TV, Púrpura Twitch, Verde WhatsApp) con la sofisticación tonal, curvaturas expresivas y fluidez de Material You.
3. **Soporte Dual Completo:** Implementación nativa de temas Claro (Light Theme) y Oscuro (Dark Theme) con alternancia automática según el sistema Android.
4. **Validación Proactiva de WhatsApp:** Exposición clara de los límites estrictos de WhatsApp (mínimo 3, máximo 30 stickers; $\le 100\text{ KB}$ estático / $\le 500\text{ KB}$ animado) mediante indicadores visuales continuos, previniendo errores antes de exportar.

---

## 2. Sistema de Tokens y Temas (Design Tokens)

### 2.1 Paleta de Color (M3 Dual ColorScheme)

La aplicación implementa dos esquemas de color completos en `ui/theme/` respetando los roles semánticos de Material 3:

| Token Semántico M3 | Rol en la App | Valor Modo Oscuro (Dark) | Valor Modo Claro (Light) |
| :--- | :--- | :--- | :--- |
| `primary` | Acento principal 7TV (Buscador, CTA, selección) | `#00D2FF` | `#00728F` |
| `onPrimary` | Texto sobre botón primario | `#000000` | `#FFFFFF` |
| `secondary` | Acento Twitch (Canales, Streamers) | `#B37BF2` | `#6D28D9` |
| `onSecondary` | Texto sobre acento secundario | `#FFFFFF` | `#FFFFFF` |
| `tertiary` | Acento WhatsApp (Acciones de exportación) | `#25D366` | `#008744` |
| `onTertiary` | Texto sobre botón de WhatsApp | `#000000` | `#FFFFFF` |
| `background` | Fondo principal de la ventana | `#0E1015` | `#F7F9FC` |
| `surface` | Superficie base de vista | `#141720` | `#FFFFFF` |
| `surfaceContainerLow` | Tarjetas de emotes y chips base | `#1A1D28` | `#F0F3F8` |
| `surfaceContainer` | Elevación intermedia (tarjetas de packs) | `#202432` | `#E7ECF3` |
| `surfaceContainerHigh` | Píldora flotante y bottom sheets | `#272C3E` | `#DEE4EE` |
| `surfaceContainerHighest`| Estados de foco e inputs activos | `#31374C` | `#D4DCE8` |
| `error` | Advertencias de límites de pack ($<3$ o $>30$) | `#FF4858` | `#D32F2F` |
| `onSurface` | Texto principal de alto contraste | `#F1F3F5` | `#1A1D23` |
| `onSurfaceVariant` | Metadatos secundarios y alias de emotes | `#9AA0A6` | `#5B6275` |

### 2.2 Tokens del Simulador de WhatsApp
Tokens dedicados para la previsualización fidedigna de stickers:
* **WhatsApp Dark Chat:** `#0B141A` (Burbuja de mensaje: `#005C4B`)
* **WhatsApp Light Chat:** `#EFEAE2` (Burbuja de mensaje: `#D9FDD3`)
* **Checkerboard (Canal Alfa puro):** Cuadrícula gris alternada `#202432` / `#161922` (Dark) y `#E2E6EE` / `#F0F3F8` (Light).

### 2.3 Escala Tipográfica (M3 Typography)
* **`headlineMedium` / `titleLarge`:** Nombres de paquetes, nombres de streamers y cabeceras principales (`22sp` - `28sp`, SemiBold).
* **`titleMedium` / `titleSmall`:** Nombres de emotes y cabeceras de categorías (`14sp` - `16sp`, Medium).
* **`bodyLarge` / `bodyMedium`:** Textos descriptivos, avisos de división de packs y diálogos (`14sp` - `16sp`, Normal).
* **`labelLarge` / `labelMedium`:** Botones de acción, chips de filtro y pestañas (`12sp` - `14sp`, SemiBold).
* **`labelSmall`:** Metadatos técnicos (KB, duración s, FPS, badge ANIM/STATIC) (`10sp` - `11sp`, Monospace/Medium).

### 2.4 Formas Expresivas (M3 Shapes)
* **`extraSmall` (`8dp`):** Badges de formato (`ANIM` / `STATIC`).
* **`small` (`12dp`):** Chips de filtro y campos de texto de búsqueda.
* **`medium` (`16dp`):** Tarjetas de emotes individuales (`EmoteCard`).
* **`large` (`24dp`):** Tarjetas de packs (`PackHeroCard`) y botones primarios redondeados.
* **`extraLarge` (`28dp`):** Píldora interactiva flotante (`DockedDraftPill`) y esquinas superiores de `ModalBottomSheet`.

---

## 3. Arquitectura de Componentes Reutilizables

Los componentes se centralizan en `ui/components/` con aislamiento de responsabilidades y soporte `@Preview`:

1. **`SevenTvSearchBar`:**
   * Basado en `SearchBar` de M3 con contenedor tonal (`surfaceContainerLow`) y esquinas de `28dp`.
   * Icono de búsqueda a la izquierda, botón para limpiar texto y botón directo para pegar URLs de 7TV (`7tv.app/emotes/...`).
   * Fila inferior de `FilterChip`s deslizables horizontalmente: `Todos`, `Animados`, `Estáticos`, `Populares hoy`.

2. **`EmoteCard`:**
   * Basado en `ElevatedCard` con esquinas de `16dp`.
   * Renderizado optimizado con Coil (`previewUrl`, tamaño solicitado a $128 \times 128\text{ px}$).
   * Badge superior flotante con tonal background: `ANIM` (Cian) o `STATIC` (Gris tenue).
   * Botón circular interactivo con transición animada (`+` cian $\to$ `✓` verde esmeralda) al añadirse al borrador.
   * Al pulsar la tarjeta se dispara el inspector de stickers.

3. **`DockedDraftPill` (Píldora Flotante del Borrador):**
   * Píldora flotante anclada sobre la barra de navegación con animación fluida (`AnimatedVisibility`).
   * Visualización continua del estado: `X / 30 stickers`, barra de progreso lineal M3 con color semántico (Rojo $<3$, Verde $3\text{ a }30$, Naranja $>30$) y botón de acción rápida `Revisar Pack ▶`.
   * Al pulsar, despliega el `DraftBottomSheet`.

4. **`DraftBottomSheet`:**
   * M3 `ModalBottomSheet` con arrastre gestual (*drag handle*).
   * Editor del nombre del paquete con `OutlinedTextField`.
   * Tira de stickers añadidos con opción de eliminar en un toque.
   * Botón de acción principal M3: `Crear Paquete y Exportar a WhatsApp`.

5. **`PackHeroCard`:**
   * Tarjeta para la lista de "Mis Packs" con esquinas de `24dp` y jerarquía clara.
   * Cabecera con nombre del paquete, contador de stickers y chip de estado M3 (`Sincronizado`, `Pendiente`, `No exportado`).
   * Carrusel horizontal de previsualización de los primeros stickers del pack.
   * Botón tonal verde de exportación rápida a WhatsApp y menú desplegable de 3 puntos (`Renombrar`, `Duplicar`, `Eliminar`).

6. **`StickerInspectorSheet`:**
   * M3 `ModalBottomSheet` con el simulador de chat de WhatsApp en tiempo real.
   * Selector instantáneo de fondo: Burbuja WhatsApp Oscuro, Burbuja WhatsApp Claro y Cuadrícula de Transparencia.
   * Mapeo de hasta 3 emojis asociados al teclado de WhatsApp.
   * Métricas técnicas en vivo: peso en KB ($\le 500\text{ KB}$ animado / $\le 100\text{ KB}$ estático), duración en segundos ($\le 3\text{s}$) y tasa de refresco (FPS $\le 30$).

---

## 4. Arquitectura de Pantallas y Navegación

### 4.1 Navegación Global (`AppNavigation`)
* `NavigationBar` de M3 con superficie tonal `surfaceContainer`.
* 3 pestañas principales con iconos M3 vectoriales (Outlined/Filled según selección):
  1. **Explorar:** Acento cian 7TV (`ExploreScreen`).
  2. **Canales:** Acento púrpura Twitch (`ChannelScreen`).
  3. **Mis Packs:** Acento verde WhatsApp (`PacksScreen`).
* Integración fluida de la píldora flotante `DockedDraftPill` por encima de la barra de navegación.

### 4.2 Pantalla 1: Explorador (`ExploreScreen`)
* Barra superior integrada `SevenTvSearchBar`.
* Cuadrícula adaptativa `LazyVerticalGrid` (3 columnas en móvil, 5 en tablet/horizontal).
* Shimmer de carga tonal para transiciones fluidas.
* Estado vacío expresivo con ilustración vectorial M3 y botón para reiniciar la búsqueda.

### 4.3 Pantalla 2: Explorador de Canales (`ChannelScreen`)
* Cabecera hero del creador con avatar circular, nombre y badge púrpura con el número total de emotes.
* Botón de acción principal M3: `Importar Canal Completo` con icono de WhatsApp.
* Diálogo de División Inteligente (*Pack Splitting Engine*) que se dispara automáticamente si el canal tiene más de 30 emotes, dividiendo en subpaquetes de 3 a 30 listos para WhatsApp.
* `PrimaryTabRow` de M3 para alternar entre `Todos`, `Animados` y `Estáticos`.

### 4.4 Pantalla 3: Gestor de Paquetes (`PacksScreen`)
* `LargeTopAppBar` M3 con colapso dinámico al desplazarse ("Mis Packs" + contador total).
* Lista vertical con espaciado consistente de `PackHeroCard`.
* `ExtendedFloatingActionButton` M3 (`+ Crear Pack`) que se contrae a icono flotante circular durante el scroll.
* Vista de bienvenida si la lista está vacía con acceso directo a Explorar.

### 4.5 Pantalla 4: Detalle del Paquete (`PackDetailScreen`)
* Barra superior con botón de navegación hacia atrás, título editable y botón primario de WhatsApp.
* Barra de estado de salud del paquete con alertas en tiempo real sobre los requisitos de WhatsApp.
* Cuadrícula de stickers con insignia visible `★ Tray Icon` en el primer slot.
* Apertura del `StickerInspectorSheet` al interactuar con cualquier sticker.

---

## 5. Rendimiento, Accesibilidad y Testing

### 5.1 Rendimiento
* Solicitudes de miniaturas optimizadas con Coil a $128 \times 128\text{ px}$ en listas y cuadrículas.
* Asignación de claves estables `key = { it.id }` en todas las colecciones `LazyColumn` y `LazyVerticalGrid` para garantizar tasa de refresco a 60/120 fps constantes.
* Previsualización a máxima resolución ($512 \times 512\text{ px}$) restringida exclusivamente al inspector modal.

### 5.2 Estrategia de Verificación y Testing
* **Tests de Tokens y Temas (`ThemeTokensTest`):** Verificación de contraste WCAG AA en modo claro y oscuro para todos los roles de color y tonalidades de superficie.
* **Tests de Componentes (`ComponentsTest`):** Pruebas unitarias de Compose para `EmoteCard`, `DockedDraftPill`, `PackHeroCard` y `StickerInspectorSheet`.
* **Pruebas de Regresión Completa:** Ejecución de toda la suite existente en `app/src/test/...` (Room Database, repositorios, cliente Retrofit/Ktor, `PackSplittingEngine`, `StickerMediaPipeline` y `StickerContentProvider`) asegurando 0 regresiones funcionales.

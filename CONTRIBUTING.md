# Guía de Contribución a 7TV Stickers for WhatsApp

¡Gracias por tu interés en colaborar con **7TV Stickers for WhatsApp**! Este es un proyecto de código abierto enfocado en brindar una experiencia fluida, rápida y nativa para llevar los emotes de 7TV a WhatsApp en Android.

Para asegurar un desarrollo ordenado y consistente, solicitamos a todas las personas colaboradoras seguir las pautas detalladas en este documento.

---

## 📋 Tabla de Contenidos

1. [Requisitos de Entorno](#-requisitos-de-entorno)
2. [Configuración Inicial del Proyecto](#-configuración-inicial-del-proyecto)
3. [Comandos de Compilación y Pruebas](#-comandos-de-compilación-y-pruebas)
4. [Flujo de Trabajo y Ramas](#-flujo-de-trabajo-y-ramas)
5. [Convención de Commits](#-convención-de-commits)
6. [Estilo de Código y Buenas Prácticas](#-estilo-de-código-y-buenas-prácticas)
7. [Proceso de Envío de Pull Requests (PR)](#-proceso-de-envío-de-pull-requests-pr)

---

## 💻 Requisitos de Entorno

Antes de comenzar a trabajar en el repositorio, asegúrate de contar con las siguientes herramientas instaladas y configuradas:

| Herramienta | Versión mínima / requerida | Observaciones |
| :--- | :--- | :--- |
| **Android Studio** | **Iguana (2023.2.1)** o superior | Con soporte completo para Kotlin 2.0 y Compose |
| **JDK (Java Development Kit)** | **JDK 17** | Azul Zulu, Eclipse Temurin o el JDK 17 integrado en Android Studio |
| **Android SDK** | **SDK 34 (Android 14)** | `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26` (Android 8.0) |
| **Android NDK** | Compatible con el SDK 34 (Side-by-side) | Requerido para la compilación de código C++ |
| **CMake** | **3.22.1** | Requerido por el módulo nativo libwebp (`app/src/main/cpp/CMakeLists.txt`) |

> [!NOTE]
> Puedes instalar el NDK y CMake 3.22.1 directamente desde Android Studio en:  
> **Settings / Preferences** > **Languages & Frameworks** > **Android SDK** > pestaña **SDK Tools** (marca *Show Package Details* y selecciona CMake `3.22.1`).

---

## 🚀 Configuración Inicial del Proyecto

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/gek0v/7tv-whatsapp.git
   cd 7tv-whatsapp
   ```

2. **Abrir en Android Studio:**
   - Inicia Android Studio.
   - Selecciona **Open** y abre la carpeta raíz del proyecto.
   - Espera a que Gradle descargue las dependencias y sincronice el proyecto.

---

## 🛠️ Comandos de Compilación y Pruebas

Se utiliza el wrapper de Gradle (`./gradlew` en Linux/macOS o `gradlew.bat` en Windows):

### Ejecución de Pruebas Unitarias
Ejecuta todas las pruebas unitarias locales (incluyendo JUnit 4, Robolectric y MockWebServer):
```bash
# Linux / macOS
./gradlew test

# Windows (PowerShell / CMD)
.\gradlew.bat test
```

### Compilación del APK de Depuración (Debug)
Compila el ejecutable APK en modo debug (incluyendo la compilación nativa con CMake):
```bash
# Linux / macOS
./gradlew assembleDebug

# Windows (PowerShell / CMD)
.\gradlew.bat assembleDebug
```
El APK resultante se generará en: `app/build/outputs/apk/debug/app-debug.apk`.

### Verificación de Calidad y Linting
Para ejecutar comprobaciones de análisis estático:
```bash
./gradlew lintDebug
# o verificación completa:
./gradlew check
```

---

## 🌿 Flujo de Trabajo y Ramas

1. La rama principal y de producción es `master`.
2. Para cualquier funcionalidad, corrección o documentación, crea una rama específica derivada de `master`:
   - `feat/nombre-de-la-funcionalidad` para nuevas características.
   - `fix/descripcion-del-bug` para correcciones de errores.
   - `docs/tema-documentado` para cambios exclusivos de documentación.
   - `perf/area-optimizada` para mejoras de rendimiento.
   - `refactor/componente` para refactorización de código sin alterar comportamiento.

Ejemplo:
```bash
git checkout master
git pull origin master
git checkout -b feat/sticker-pack-reorder
```

---

## 📝 Convención de Commits

Seguimos la especificación de **[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)**:

```text
<tipo>(<ámbito opcional>): <descripción concisa en minúsculas y modo imperativo>
```

### Tipos admitidos:
- **`feat`**: Nueva característica o funcionalidad para el usuario.
- **`fix`**: Corrección de un defecto o bug.
- **`docs`**: Cambios exclusivos en documentación o archivos Markdown.
- **`style`**: Formato de código, espacios, punto y coma (sin cambios en la lógica).
- **`refactor`**: Reorganización o simplificación de código sin añadir features ni corregir bugs.
- **`perf`**: Cambios orientados a optimizar el rendimiento (latencia, uso de memoria, etc.).
- **`test`**: Incorporación o ajuste de pruebas automatizadas.
- **`build`**: Modificaciones en el sistema de compilación o dependencias Gradle.
- **`ci`**: Cambios en flujos de integración continua (GitHub Actions, etc.).
- **`chore`**: Tareas de mantenimiento o configuración general.

### Ejemplos válidos:
- `feat(explore): uncap search results and add infinite scroll pagination`
- `fix(provider): resolve cursor leak in StickerContentProvider query`
- `docs: add LICENSE, CONTRIBUTING, GitHub templates, CI and update README`
- `perf(media): accelerate animated sticker encoding with libwebp method 1`

---

## 📐 Estilo de Código y Buenas Prácticas

- **Kotlin Idiomático:** Sigue las directrices oficiales de [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Jetpack Compose:**
  - Mantén los composables sin estado (*stateless*) siempre que sea posible elevando el estado (*hoisting*).
  - Utiliza nombres claros en funciones `@Composable` en PascalCase.
  - Asegura que los componentes interactivos respeten un objetivo táctil mínimo de $48 \times 48\text{ dp}$.
- **Arquitectura Limpia:**
  - Respeta la separación de capas: `presentation`, `domain`, `data` y `native`.
  - El acceso a datos remotos o bases de datos no debe ejecutarse en el hilo principal (`Dispatchers.IO`).
- **Restricciones de Stickers:**
  - Ten siempre presente la especificación estricta de WhatsApp: resolución exacta de $512 \times 512\text{ px}$, márgenes transparentes de $16\text{ px}$, límites de peso (<100 KB estático, <500 KB animado) y duración ($\le 3\text{ s}$).

---

## 🚀 Proceso de Envío de Pull Requests (PR)

1. **Asegúrate de que las pruebas pasen:** Ejecuta `./gradlew test` localmente antes de hacer push.
2. **Haz push de tu rama:** `git push origin <tu-rama>`.
3. **Abre un Pull Request:** Dirígete al repositorio en GitHub y abre un PR apuntando a `master`.
4. **Completa la plantilla de PR:** Rellena la descripción, el tipo de cambio, el issue vinculado y la lista de comprobación.
5. **Revisión de código:** Los mantenedores revisarán tu propuesta y es posible que sugieran ajustes o comentarios. Una vez aprobado y con los flujos de CI en verde, tu código será integrado.

¡Muchas gracias por contribuir a hacer que los stickers de 7TV en WhatsApp sean rápidos, eficientes y accesibles para todos!

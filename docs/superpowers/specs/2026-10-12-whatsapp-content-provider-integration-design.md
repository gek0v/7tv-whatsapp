# Especificación Técnica: Integración Nativa con WhatsApp (Subproyecto 4)

- **Fecha:** 2026-10-12
- **Estado:** Propuesto
- **Rama:** `feature/media-pipeline-scaffolding`
- **Subproyecto:** 4 (Integración Nativa con WhatsApp: ContentProvider, Cursors IPC, AssetFileDescriptor, Intents y Whitelist)

---

## 1. Resumen Ejecutivo

Este documento define la arquitectura y especificación técnica del **Subproyecto 4: Integración Nativa con WhatsApp**. El objetivo es permitir que la aplicación exponga los paquetes y stickers locales almacenados en Room y en el sistema de archivos interno hacia **WhatsApp Estándar** (`com.whatsapp`) cumpliendo estrictamente con el protocolo IPC de WhatsApp:
1. Un `ContentProvider` personalizado ([`StickerContentProvider`](file:///D:/Workspaces/7tv-whatsapp/app/src/main/java/com/seventv/stickers/whatsapp/StickerContentProvider.kt)) con la autoridad `${applicationId}.stickercontentprovider` y permiso de lectura `com.whatsapp.sticker.READ`.
2. Mapeo de consultas `query()` a cursores de WhatsApp (`MatrixCursor`) con las columnas y metadatos oficiales para paquetes y stickers individuales.
3. Entrega eficiente y segura de flujos binarios WebP mediante `openAssetFile()` devolviendo `AssetFileDescriptor`.
4. Un helper de intents ([`WhatsAppIntentHelper`](file:///D:/Workspaces/7tv-whatsapp/app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppIntentHelper.kt)) para verificar la instalación de WhatsApp y lanzar el intent `com.whatsapp.intent.action.ENABLE_STICKER_PACK`.
5. Un verificador de whitelist ([`WhatsAppWhitelistChecker`](file:///D:/Workspaces/7tv-whatsapp/app/src/main/java/com/seventv/stickers/whatsapp/WhatsAppWhitelistChecker.kt)) para consultar a WhatsApp si un paquete sigue instalado.
6. Configuración de visibilidad de paquetes en `AndroidManifest.xml` (`<queries>`) para compatibilidad completa con Android 11+ (API 30+).

---

## 2. Requisitos y Restricciones Técnicas de WhatsApp

| Requisito | Detalle del Protocolo de WhatsApp | Implementación en Subproyecto 4 |
| :--- | :--- | :--- |
| **Autoridad** | `${applicationId}.stickercontentprovider` | Declarada en el Manifest y referenciada dinámicamente o fija (`com.seventv.stickers.stickercontentprovider`). |
| **Permiso de Lectura** | `android:readPermission="com.whatsapp.sticker.READ"` | Declarado en `<provider>` en `AndroidManifest.xml`. |
| **Visibilidad de Paquete (API 30+)** | Se requiere consultar `com.whatsapp` | `<queries><package android:name="com.whatsapp"/></queries>` en el Manifest. |
| **Endpoint Metadata** | `content://{authority}/metadata` | Devuelve cursor con columnas oficiales de metadata de todos los packs. |
| **Endpoint Single Pack** | `content://{authority}/metadata/{id}` | Devuelve cursor con metadata de un único pack. |
| **Endpoint Stickers** | `content://{authority}/stickers/{id}` | Devuelve cursor con la lista de stickers de un pack (`sticker_file_name`, `sticker_emojis`). |
| **Endpoint Binarios** | `content://{authority}/stickers_asset/{id}/{file}` | `openAssetFile()` con modo `"r"` entregando `AssetFileDescriptor`. |
| **Acción de Intent** | `com.whatsapp.intent.action.ENABLE_STICKER_PACK` | Lanzado hacia `package = "com.whatsapp"` con extras `sticker_pack_id`, `sticker_pack_authority`, `sticker_pack_name`. |
| **Consulta Whitelist** | `content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted` | `query()` con parámetros `authority` y `identifier`. Retorna columna `result` (1 si añadido, 0 si no). |

---

## 3. Arquitectura del Subsistema

```
┌────────────────────────────────────────────────────────┐
│                   WhatsApp Client                      │
│                  (com.whatsapp)                        │
└───────┬────────────────────────┬───────────────▲───────┘
        │ 1. Launch Intent       │ 2. Query      │ 3. Read
        │ (ENABLE_STICKER_PACK)  │    metadata & │    Asset
        ▼                        │    stickers   │    File
┌──────────────────────┐         │               │
│ WhatsAppIntentHelper │         │               │
└──────────────────────┘         ▼               │
┌────────────────────────────────────────────────┴───────┐
│              StickerContentProvider                    │
│   (Authority: com.seventv.stickers.stickercontentprovider)│
└───────────────┬────────────────────────┬───────────────┘
                │                        │
                ▼                        ▼
┌──────────────────────────────┐ ┌──────────────────────┐
│         AppDatabase          │ │ StickerStorageManager│
│  (StickerPackDao / Room)     │ │ (Archivos WebP)      │
└──────────────────────────────┘ └──────────────────────┘
```

---

## 4. Especificación Detallada de Componentes

### 4.1 Constantes del Protocolo de WhatsApp (`WhatsAppConstants`)

```kotlin
package com.seventv.stickers.whatsapp

object WhatsAppConstants {
    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val INTENT_ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    // Columnas de Metadata del Paquete
    const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
    const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
    const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
    const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
    const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
    const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_store_link"
    const val PUBLISHER_EMAIL = "publisher_email"
    const val PUBLISHER_WEBSITE = "publisher_website"
    const val PRIVACY_POLICY_WEBSITE = "privacy_policy_website"
    const val LICENSE_AGREEMENT_WEBSITE = "license_agreement_website"
    const val IMAGE_DATA_VERSION = "image_data_version"
    const val AVOID_CACHE = "avoid_cache"
    const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

    // Columnas de Stickers individuales
    const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
    const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emojis"

    // Whitelist Provider de WhatsApp
    const val WHITELIST_AUTHORITY = "com.whatsapp.provider.sticker_whitelist_check"
    const val WHITELIST_QUERY_PATH = "is_whitelisted"
    const val WHITELIST_QUERY_RESULT_COLUMN = "result"
}
```

### 4.2 `StickerContentProvider`

```kotlin
package com.seventv.stickers.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.data.local.storage.StickerStorageManager
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.seventv.stickers.stickercontentprovider"
        const val METADATA_CODE = 1
        const val METADATA_PACK_CODE = 2
        const val STICKERS_CODE = 3
        const val STICKERS_ASSET_CODE = 4

        val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA_CODE)
            addURI(AUTHORITY, "metadata/*", METADATA_PACK_CODE)
            addURI(AUTHORITY, "stickers/*", STICKERS_CODE)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var storageManager: StickerStorageManager

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        database = AppDatabase.getInstance(ctx) // o inyección de instancia
        storageManager = DefaultStickerStorageManager(ctx.filesDir)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor?

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor?

    override fun getType(uri: Uri): String?
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
```

#### Reglas de consulta:
1. `METADATA_CODE` (`/metadata`):
   - Consulta `database.stickerPackDao().getAllPacksDirect()` (consulta síncrona para ContentProvider).
   - Crea un `MatrixCursor` con las 13 columnas requeridas por WhatsApp.
   - Si `pack.isAnimated == true`, asigna `animated_sticker_pack = 1`, sino `0`.
   - `sticker_pack_icon = "tray_icon.webp"`.
2. `METADATA_PACK_CODE` (`/metadata/{packId}`):
   - Consulta el paquete por identificador y devuelve un cursor de 1 fila.
3. `STICKERS_CODE` (`/stickers/{packId}`):
   - Consulta el paquete y extrae sus stickers ordenados por `sortOrder`.
   - Crea un `MatrixCursor` con columnas `sticker_file_name` (ej. `"sticker_${sticker.id}.webp"`) y `sticker_emojis` (cadena de emojis separados por comas).
4. `STICKERS_ASSET_CODE` (`/stickers_asset/{packId}/{fileName}`):
   - Manejado por `openAssetFile`.
   - Valida `mode == "r"`.
   - Resuelve el archivo en disco: `File(storageManager.getPackDirectory(packId), fileName)`.
   - Si no existe, lanza `FileNotFoundException`.
   - Retorna `AssetFileDescriptor(ParcelFileDescriptor.open(file, MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)`.

### 4.3 `WhatsAppIntentHelper`

```kotlin
package com.seventv.stickers.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object WhatsAppIntentHelper {
    fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WhatsAppConstants.PACKAGE_WHATSAPP, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun createAddPackIntent(
        authority: String = StickerContentProvider.AUTHORITY,
        packIdentifier: String,
        packName: String
    ): Intent {
        return Intent(WhatsAppConstants.INTENT_ACTION_ENABLE_STICKER_PACK).apply {
            `package` = WhatsAppConstants.PACKAGE_WHATSAPP
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY, authority)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_ID, packIdentifier)
            putExtra(WhatsAppConstants.EXTRA_STICKER_PACK_NAME, packName)
        }
    }
}
```

### 4.4 `WhatsAppWhitelistChecker`

```kotlin
package com.seventv.stickers.whatsapp

import android.content.Context
import android.net.Uri

object WhatsAppWhitelistChecker {
    fun isPackWhitelisted(
        context: Context,
        packIdentifier: String,
        authority: String = StickerContentProvider.AUTHORITY
    ): Boolean {
        val queryUri = Uri.Builder()
            .scheme("content")
            .authority(WhatsAppConstants.WHITELIST_AUTHORITY)
            .appendPath(WhatsAppConstants.WHITELIST_QUERY_PATH)
            .appendQueryParameter(WhatsAppConstants.EXTRA_STICKER_PACK_AUTHORITY, authority)
            .appendQueryParameter(WhatsAppConstants.EXTRA_STICKER_PACK_ID, packIdentifier)
            .build()

        return try {
            val cursor = context.contentResolver.query(queryUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)
                    if (columnIndex >= 0) {
                        return it.getInt(columnIndex) == 1
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
```

### 4.5 Configuración en `AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Visibilidad de WhatsApp para Android 11+ (API 30+) -->
    <queries>
        <package android:name="com.whatsapp" />
        <intent>
            <action android:name="com.whatsapp.intent.action.ENABLE_STICKER_PACK" />
        </intent>
    </queries>

    <application ...>
        ...
        <provider
            android:name="com.seventv.stickers.whatsapp.StickerContentProvider"
            android:authorities="com.seventv.stickers.stickercontentprovider"
            android:enabled="true"
            android:exported="true"
            android:readPermission="com.whatsapp.sticker.READ" />
    </application>
</manifest>
```

---

## 5. Estrategia de Pruebas y Validación

1. **Pruebas de `StickerContentProvider` (`StickerContentProviderTest`):**
   - Ejecución bajo Robolectric.
   - Creación de base de datos en memoria con paquetes y stickers de prueba.
   - Validación del cursor `/metadata`: verificar presencia de las 13 columnas y valores correctos de `animated_sticker_pack`.
   - Validación del cursor `/stickers/{id}`: verificar columnas `sticker_file_name` y `sticker_emojis`.
   - Validación de `openAssetFile()`: verificar entrega de `AssetFileDescriptor` para archivos existentes y `FileNotFoundException` para inexistentes.
   - Validación de operaciones no permitidas: `insert()`, `update()`, `delete()` deben arrojar `UnsupportedOperationException`.
2. **Pruebas de `WhatsAppIntentHelper` (`WhatsAppIntentHelperTest`):**
   - Verificación de la construcción del Intent: action, package y extras exactos.
   - Detección de instalación de WhatsApp con mock/shadow de `PackageManager`.
3. **Pruebas de `WhatsAppWhitelistChecker` (`WhatsAppWhitelistCheckerTest`):**
   - Verificación de consulta segura retornando `false` ante ausencia del proveedor y `true` cuando el cursor devuelve 1.

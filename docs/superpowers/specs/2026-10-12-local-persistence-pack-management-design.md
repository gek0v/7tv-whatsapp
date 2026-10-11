# Especificación Técnica: Persistencia Local y Motor de Gestión/División de Packs (Subproyecto 3)

- **Fecha:** 2026-10-12
- **Estado:** Propuesto
- **Rama:** `feature/media-pipeline-scaffolding`
- **Subproyecto:** 3 (Persistencia Local, Room Database, Ciclo de Vida de Archivos y Pack Splitting Engine)

---

## 1. Resumen Ejecutivo

Este documento define la arquitectura y especificación técnica del **Subproyecto 3: Persistencia Local y Gestión de Paquetes**. El objetivo es dotar a la aplicación de:
1. Una base de datos relacional local basada en **Room** para persistir metadatos de paquetes y stickers con reactividad mediante Kotlin `Flow`.
2. Un gestor de archivos interno (`StickerStorageManager`) que administre los archivos WebP y el icono de bandeja (*tray icon*) de forma transaccional y limpia en `context.filesDir/sticker_packs/{identifier}/`.
3. Un motor de división balanceada (`PackSplittingEngine`) que garantice que ningún conjunto de emotes viole las restricciones estrictas de WhatsApp ($3 \le N \le 30$ stickers por pack), particionando proporcionalmente catálogos de canales grandes (ej. 31 $\to$ 16 + 15) y segregando formatos (estáticos vs. animados).
4. Un repositorio de dominio (`StickerPackRepository`) que coordine la descarga y procesamiento de medios vía `EmoteStickerBridge`, la escritura en disco y el registro atómico en Room.

---

## 2. Restricciones Técnicas del Sistema

| Parámetro | Requisito de WhatsApp | Implementación en Subproyecto 3 |
| :--- | :--- | :--- |
| **Tamaño de Pack** | Mínimo 3, Máximo 30 stickers | Validado por `PackSplittingEngine`. Falla si $N < 3$. |
| **Homogeneidad de Formato** | Packs 100% estáticos o 100% animados | Segregación automática en packs distintos si el conjunto es mixto. |
| **Icono de Bandeja (*Tray*)** | Exactamente 1 por pack ($96 \times 96$ px, $\le 50$ KB) | Generado automáticamente con el primer sticker del paquete vía `StickerMediaPipeline.processTrayIcon`. |
| **Emojis por Sticker** | Entre 1 y 3 emojis | Campo `emojis: List<String>` (máx. 3), por defecto `["💬"]`. |
| **Persistencia** | Debe residir en almacenamiento interno | `context.filesDir/sticker_packs/{packIdentifier}/` para exposición segura vía `ContentProvider`. |
| **Base de Datos** | SQLite relacional con transacciones | Room 2.6.1 + KSP con Kotlin 2.0. |

---

## 3. Arquitectura del Subsistema

```
┌────────────────────────────────────────────────────────┐
│                   Domain Layer                         │
│  - StickerPack, PackSticker (Modelos limpios)          │
│  - PackSplittingEngine (Lógica pura de partición)      │
│  - StickerPackRepository (Contrato de repositorio)     │
└───────────────┬────────────────────────┬───────────────┘
                │                        │
                ▼                        ▼
┌──────────────────────────────┐ ┌──────────────────────┐
│         Data Layer           │ │    Media Bridge      │
│  - DefaultStickerPackRepo    │ │  - EmoteStickerBridge│
│  - StickerStorageManager     │ └──────────────────────┘
└───────────────┬──────────────┘
                │
        ┌───────┴───────┐
        ▼               ▼
┌──────────────┐ ┌──────────────┐
│  Room DB     │ │  FilesDir    │
│  (Metadatos) │ │ (WebP files) │
└──────────────┘ └──────────────┘
```

---

## 4. Esquema de Base de Datos (Room)

### 4.1 Entidades

#### `StickerPackEntity`
Representa un paquete de stickers registrado en la base de datos local.
```kotlin
package com.seventv.stickers.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey
    val identifier: String,             // Identificador único (UUID o slug)
    val name: String,                   // Nombre del pack (máx. 128 caracteres)
    val publisher: String,              // Autor/publicador (ej. nombre de streamer o 7TV)
    val trayImageRelativePath: String,  // Ruta relativa al icono de bandeja (96x96 px)
    val isAnimated: Boolean,            // true si contiene WebP animado, false si es estático
    val isWhitelisted: Boolean = false, // true si el pack ya fue añadido a WhatsApp
    val createdAt: Long = System.currentTimeMillis()
)
```

#### `StickerEntity`
Representa un sticker individual perteneciente a un paquete.
```kotlin
package com.seventv.stickers.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["identifier"],
            childColumns = ["packIdentifier"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("packIdentifier"),
        Index("emoteId")
    ]
)
data class StickerEntity(
    @PrimaryKey
    val id: String,                     // UUID único del sticker local
    val packIdentifier: String,         // Clave foránea al paquete contenedor
    val emoteId: String,                // ID de 7TV original
    val emoteName: String,              // Nombre del emote (ej. "catJAM")
    val relativeFilePath: String,       // Ruta relativa al archivo WebP (512x512 px)
    val emojis: List<String>,           // Lista de 1 a 3 emojis
    val fileLength: Long,               // Tamaño en bytes del archivo WebP
    val sortOrder: Int                  // Posición ordinal en el paquete (0..29)
)
```

#### `PackWithStickers`
Relación 1 a $N$ para consultas compuestas.
```kotlin
package com.seventv.stickers.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PackWithStickers(
    @Embedded
    val pack: StickerPackEntity,
    @Relation(
        parentColumn = "identifier",
        entityColumn = "packIdentifier"
    )
    val stickers: List<StickerEntity>
)
```

### 4.2 TypeConverter
```kotlin
package com.seventv.stickers.data.local.converter

import androidx.room.TypeConverter

class EmojiListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toString(list: List<String>): String {
        return list.take(3).joinToString(",")
    }
}
```

### 4.3 Data Access Objects (DAOs)

#### `StickerPackDao`
```kotlin
package com.seventv.stickers.data.local.dao

import androidx.room.*
import com.seventv.stickers.data.local.entity.PackWithStickers
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerPackDao {
    @Transaction
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    fun observeAllPacks(): Flow<List<PackWithStickers>>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
    suspend fun getPackByIdentifier(identifier: String): PackWithStickers?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Transaction
    suspend fun insertPackWithStickers(pack: StickerPackEntity, stickers: List<StickerEntity>) {
        insertPack(pack)
        insertStickers(stickers)
    }

    @Query("UPDATE sticker_packs SET isWhitelisted = :isWhitelisted WHERE identifier = :identifier")
    suspend fun updateWhitelisted(identifier: String, isWhitelisted: Boolean)

    @Query("DELETE FROM sticker_packs WHERE identifier = :identifier")
    suspend fun deletePack(identifier: String)
}
```

#### `StickerDao`
```kotlin
package com.seventv.stickers.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface StickerDao {
    @Query("UPDATE stickers SET emojis = :emojis WHERE id = :stickerId")
    suspend fun updateEmojis(stickerId: String, emojis: String)

    @Query("UPDATE stickers SET sortOrder = :newOrder WHERE id = :stickerId")
    suspend fun updateSortOrder(stickerId: String, newOrder: Int)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: String)
}
```

### 4.4 AppDatabase
```kotlin
package com.seventv.stickers.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.seventv.stickers.data.local.converter.EmojiListConverter
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity

@Database(
    entities = [StickerPackEntity::class, StickerEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(EmojiListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stickerPackDao(): StickerPackDao
    abstract fun stickerDao(): StickerDao
}
```

---

## 5. Gestor de Archivos en Almacenamiento Interno (`StickerStorageManager`)

Para satisfacer la lectura de archivos por parte de WhatsApp y prevenir archivos huérfanos:

- **Estructura en disco:**
  `context.filesDir/sticker_packs/{packIdentifier}/`
  - `tray_icon.webp` ($96 \times 96$ px, $\le 50$ KB)
  - `sticker_{stickerId}.webp` ($512 \times 512$ px, estático o animado)

- **Contrato de `StickerStorageManager`:**
```kotlin
package com.seventv.stickers.data.local.storage

import java.io.File

interface StickerStorageManager {
    fun getPackDirectory(packIdentifier: String): File
    suspend fun saveStickerFile(packIdentifier: String, stickerId: String, bytes: ByteArray): File
    suspend fun saveTrayIcon(packIdentifier: String, bytes: ByteArray): File
    suspend fun deletePackDirectory(packIdentifier: String): Boolean
    suspend fun deleteStickerFile(packIdentifier: String, stickerId: String): Boolean
    fun getAbsoluteFile(relativePath: String): File
}
```

- **Escritura Atómica:**
  Se escriben los bytes en un archivo temporal (`${name}.${UUID}.tmp`) dentro del directorio del pack y se renombran atómicamente con `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.

---

## 6. Motor de División Inteligente (`PackSplittingEngine`)

### 6.1 Reglas y Algoritmo de Partición Homogénea

WhatsApp requiere:
$$\text{Mínimo: } 3 \le N \le 30 \text{ :Máximo}$$

Si un usuario importa un conjunto con $N$ emotes del mismo formato:
1. **Validación:** Si $N < 3$, retorna error `StickerError.InsufficientStickers(actual = N, min = 3)`.
2. **Caso $3 \le N \le 30$:** Retorna una sola partición con los $N$ emotes.
3. **Caso $N > 30$:**
   - Calcula el número de particiones necesarias:
     $$K = \left\lceil \frac{N}{30} \right\rceil$$
   - Tamaño base de cada partición:
     $$B = \left\lfloor \frac{N}{K} \right\rfloor$$
   - Remanente:
     $$R = N \pmod K$$
   - Las primeras $R$ particiones reciben $B + 1$ elementos; las restantes reciben $B$ elementos.

**Propiedad matemática:** Como $K = \lceil N / 30 \rceil$, se cumple que $B \le 30$ y para cualquier $N \ge 31$, $B \ge 15 > 3$. Por tanto, **toda partición resultante contiene garantizadamente entre 15 y 30 elementos**, eliminando completamente el riesgo de particiones inválidas como $30 + 1$.

### 6.2 Segregación de Formatos (Animado vs. Estático)
Si el conjunto contiene emotes mixtos:
1. Divide el conjunto en `animatedEmotes` y `staticEmotes`.
2. Si ambos grupos tienen $\ge 3$ elementos, aplica el algoritmo de división a cada grupo por separado.
3. Sufija los nombres resultantes:
   - Para un solo sub-pack: `"$baseName (Animados)"` o `"$baseName (Estáticos)"`.
   - Para múltiples sub-packs: `"$baseName (Animados) Part 1"`, `"$baseName (Animados) Part 2"`, etc.
4. Si un grupo tiene $< 3$ elementos y el otro $\ge 3$, ofrece la opción de fallback estático para unificar o excluye el grupo menor generando un aviso.

---

## 7. Modelos de Dominio y Extensiones de Error

### 7.1 Modelos de Dominio (`core.domain.model`)

```kotlin
package com.seventv.stickers.core.domain.model

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImagePath: String,
    val isAnimated: Boolean,
    val isWhitelisted: Boolean,
    val stickers: List<PackSticker>,
    val createdAt: Long
)

data class PackSticker(
    val id: String,
    val packIdentifier: String,
    val emoteId: String,
    val emoteName: String,
    val filePath: String,
    val emojis: List<String>,
    val fileLength: Long,
    val sortOrder: Int
)
```

### 7.2 Extensiones en `StickerError`
```kotlin
data class InsufficientStickers(val actual: Int, val min: Int = 3) : StickerError
data class PackNotFound(val identifier: String) : StickerError
```

---

## 8. Repositorio de Paquetes (`StickerPackRepository`)

```kotlin
package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlinx.coroutines.flow.Flow

interface StickerPackRepository {
    fun observeAllPacks(): Flow<List<StickerPack>>
    suspend fun getPack(identifier: String): StickerResult<StickerPack>
    suspend fun createPacksFromEmotes(
        basePackName: String,
        publisher: String,
        emotes: List<SevenTvEmote>
    ): StickerResult<List<StickerPack>>
    suspend fun deletePack(identifier: String): StickerResult<Unit>
    suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit>
    suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit>
}
```

---

## 9. Estrategia de Pruebas y Validación

1. **Pruebas de Partición (`PackSplittingEngineTest`):**
   - Validación de casos límite: $N = 1, 2$ (falla por insuficiente), $N = 3$ (mínimo válido), $N = 30$ (máximo en 1 pack).
   - Casos críticos de partición: $N = 31 \to [16, 15]$, $N = 65 \to [22, 22, 21]$, $N = 90 \to [30, 30, 30]$, $N = 91 \to [23, 23, 23, 22]$.
   - Segregación mixta (animados vs. estáticos).
2. **Pruebas de TypeConverter (`EmojiListConverterTest`):**
   - Serialización de listas vacías, de 1, 2 y 3 emojis, y corte de exceso a máximo 3.
3. **Pruebas de Almacenamiento (`StickerStorageManagerTest`):**
   - Creación de archivos de sticker, icono de bandeja, commit atómico y eliminación recursiva de directorios.
4. **Pruebas de Base de Datos Room (`StickerPackDaoTest`):**
   - Inserción y consulta reactiva con `Room.inMemoryDatabaseBuilder`, borrado en cascada de stickers al eliminar el pack.
5. **Pruebas de Repositorio (`DefaultStickerPackRepositoryTest`):**
   - Creación completa de pack a partir de emotes mockeados, integración con `EmoteStickerBridge` y persistencia en Room.

# Documentación Técnica: API de 7TV (v3 & GraphQL)

Guía completa y especificación técnica de la API de **7TV (versión 3)** para desarrolladores. Este documento detalla endpoints REST, el servicio GraphQL, la resolución de archivos en el CDN, eventos en tiempo real vía WebSockets y ejemplos de código listos para producción en múltiples lenguajes.

---

## 1. Información General y Arquitectura

* **URL Base de la API**: `https://7tv.io/`
* **Endpoint GraphQL**: `https://7tv.io/v3/gql`
* **CDN de Medios**: `https://cdn.7tv.app/`
* **EventAPI (WebSockets)**: `wss://events.7tv.io/v3`
* **Autenticación**: La mayoría de las operaciones de lectura (búsqueda de emotes, canales, sets) son públicas y **no requieren autenticación**. Para modificaciones (gestionar sets de un usuario, subir emotes) se requiere token Bearer en el header `Authorization`.
* **Headers Recomendados**:
  * `User-Agent`: Se recomienda especificar un User-Agent descriptivo (ej: `MiApp/1.0 (contacto@midominio.com)`).
  * `Content-Type`: `application/json`

> [!IMPORTANT]
> En la API v3 de 7TV, el endpoint REST clásico `GET /v3/emotes` para búsquedas generales fue discontinuado (devuelve error `405 Method Not Allowed`). **Todas las búsquedas de texto y filtros globales deben realizarse obligatoriamente a través del endpoint GraphQL (`POST /v3/gql`)**.

---

## 2. GraphQL (`POST /v3/gql`)

El servicio GraphQL es el método oficial y más eficiente para buscar emotes y perfiles de usuarios/streamers.

### 2.1 Búsqueda de Emotes (`SearchEmotes`)

Permite buscar emotes por nombre o palabras clave con paginación y límite configurable.

#### Query
```graphql
query SearchEmotes($query: String!, $page: Int, $limit: Int) {
  emotes(query: $query, page: $page, limit: $limit) {
    count
    items {
      id
      name
      animated
      flags
      host {
        url
        files {
          name
          format
          width
          height
          size
        }
      }
      owner {
        id
        username
        display_name
      }
    }
  }
}
```

#### Variables
```json
{
  "query": "catjam",
  "page": 1,
  "limit": 30
}
```

---

### 2.2 Búsqueda de Usuarios / Canales (`SearchUsers`)

Permite localizar a un creador de contenido o streamer para obtener sus sets de emotes activos (`emote_sets`).

#### Query
```graphql
query SearchUsers($query: String!, $limit: Int) {
  users(query: $query, limit: $limit) {
    id
    username
    display_name
    emote_sets {
      id
      name
      capacity
    }
    connections {
      id
      platform
      username
      display_name
    }
  }
}
```

#### Variables
```json
{
  "query": "ibai",
  "limit": 5
}
```

---

## 3. Endpoints REST (v3)

### 3.1 Obtener Emote por ID
* **Método**: `GET`
* **Ruta**: `/v3/emotes/{id}`
* **Descripción**: Devuelve metadatos detallados de un emote individual.

#### Ejemplo de Petición
```http
GET https://7tv.io/v3/emotes/60ae3e542597b4202869a844 HTTP/1.1
Host: 7tv.io
User-Agent: 7TV-Client/1.0
```

#### Respuesta de Ejemplo (JSON)
```json
{
  "id": "60ae3e542597b4202869a844",
  "name": "catJAM",
  "flags": 0,
  "lifecycle": 3,
  "state": ["LISTED"],
  "listed": true,
  "animated": true,
  "owner": {
    "id": "603b57352345e6565158145c",
    "username": "kostawhite",
    "display_name": "KostaWhite"
  },
  "host": {
    "url": "//cdn.7tv.app/emote/60ae3e542597b4202869a844",
    "files": [
      { "name": "1x.avif", "static_name": "1x_static.avif", "width": 32, "height": 32, "frame_count": 52, "size": 35464, "format": "AVIF" },
      { "name": "1x.webp", "static_name": "1x_static.webp", "width": 32, "height": 32, "frame_count": 52, "size": 65128, "format": "WEBP" },
      { "name": "2x.webp", "static_name": "2x_static.webp", "width": 64, "height": 64, "frame_count": 52, "size": 178224, "format": "WEBP" },
      { "name": "3x.webp", "static_name": "3x_static.webp", "width": 96, "height": 96, "frame_count": 52, "size": 331442, "format": "WEBP" },
      { "name": "4x.webp", "static_name": "4x_static.webp", "width": 128, "height": 128, "frame_count": 52, "size": 521408, "format": "WEBP" }
    ]
  }
}
```

---

### 3.2 Obtener Emote Set por ID
* **Método**: `GET`
* **Ruta**: `/v3/emote-sets/{id}`
* **Descripción**: Obtiene la colección de emotes asignados a un set específico (habitualmente el pack de un canal de Twitch/YouTube/Kick).

#### Ejemplo de Petición
```http
GET https://7tv.io/v3/emote-sets/62c5b967d7195c6f376cfd02 HTTP/1.1
Host: 7tv.io
```

#### Respuesta de Ejemplo (JSON)
```json
{
  "id": "62c5b967d7195c6f376cfd02",
  "name": "Global Emotes",
  "flags": 0,
  "capacity": 1000,
  "owner_name": "7TV",
  "emotes": [
    {
      "id": "60ae3e542597b4202869a844",
      "name": "catJAM",
      "flags": 0,
      "data": {
        "id": "60ae3e542597b4202869a844",
        "name": "catJAM",
        "animated": true,
        "host": {
          "url": "//cdn.7tv.app/emote/60ae3e542597b4202869a844",
          "files": [...]
        }
      }
    }
  ]
}
```

---

### 3.3 Obtener Usuario por Plataforma Conectada
7TV permite consultar directamente el usuario y su set activo utilizando su identificador en Twitch o YouTube:

* **Twitch**: `GET https://7tv.io/v3/users/twitch/{twitch_user_id}`
* **YouTube**: `GET https://7tv.io/v3/users/youtube/{youtube_channel_id}`
* **Kick**: `GET https://7tv.io/v3/users/kick/{kick_user_id}`

Devuelve el objeto de usuario junto a `emote_set` que contiene los emotes habilitados en el chat de ese streamer.

---

## 4. Resolución de URLs y CDN de Medios

7TV no almacena URLs absolutas en cada archivo; en su lugar, proporciona un objeto `host` con una URL base protocol-relative (`//cdn.7tv.app/...`) y un array de archivos (`files`).

### 4.1 Algoritmo de Normalización de URL

1. Tomar `host.url`.
2. Si comienza con `//`, anteponer `https:`.
3. Eliminar barras finales (`/`).
4. Añadir el nombre del archivo deseado (`1x.webp`, `2x.webp`, `4x.webp`, etc.).

```
URL Final = "https:" + host.url.trimEnd('/') + "/" + file.name
```

**Ejemplo real**:
* `host.url`: `"//cdn.7tv.app/emote/60ae3e542597b4202869a844"`
* Archivo seleccionado: `"2x.webp"`
* Resultado: `https://cdn.7tv.app/emote/60ae3e542597b4202869a844/2x.webp`

### 4.2 Escalas y Formatos Disponibles

| Escala | Resolución Máxima | Uso Recomendado |
| :--- | :--- | :--- |
| `1x.webp` | 32 x 32 px | Chats densos, iconos pequeños |
| `2x.webp` | 64 x 64 px | Miniaturas de UI, listas, previews de stickers |
| `3x.webp` | 96 x 96 px | Vistas intermedias, bandejas de stickers |
| `4x.webp` | 128 x 128 px | Alta resolución, generación de stickers / exportación |

> [!TIP]
> **Optimización de memoria**: Para mostrar galerías o listas con scroll, carga siempre `2x.webp` o `1x.webp`. Los archivos `4x.webp` animados pueden contener más de 60 fotogramas y pesar más de 1 MB cada uno, lo que puede causar saturación de memoria si se precargan decenas simultáneamente.

---

## 5. Flags y Modificadores de Emotes

El campo `flags` es una máscara de bits (bitmask):

```
+--------------------+------------+---------------------------------------------------+
| Nombre del Flag    | Bit / Valor| Descripción                                       |
+--------------------+------------+---------------------------------------------------+
| ZERO_WIDTH         | 1 (1 << 0) | Emote de ancho cero. Se superpone sobre el emote  |
|                    |            | adyacente anterior en el chat de streaming.       |
+--------------------+------------+---------------------------------------------------+
| NSFW               | 65536      | Marcado como contenido adulto / explícito.        |
|                    | (1 << 16)  |                                                   |
+--------------------+------------+---------------------------------------------------+
```

### Comprobación en código:
```python
is_zero_width = bool(flags & 1)
is_nsfw = bool(flags & (1 << 16))
```

---

## 6. EventAPI (WebSockets en Tiempo Real)

Para aplicaciones que necesitan escuchar cambios en vivo (añadir, eliminar o renombrar emotes en un canal):

* **URL WebSocket**: `wss://events.7tv.io/v3`

### Suscripción a un Emote Set
Al conectarse, enviar un mensaje JSON solicitando la suscripción al set deseado:

```json
{
  "op": 35,
  "d": {
    "type": "emote_set.update",
    "condition": {
      "object_id": "<EMOTE_SET_ID>"
    }
  }
}
```

### Evento Recibido
```json
{
  "op": 36,
  "d": {
    "type": "emote_set.update",
    "body": {
      "id": "<EMOTE_SET_ID>",
      "pushed": [
        {
          "key": "emotes",
          "value": {
            "id": "<EMOTE_ID>",
            "name": "monkaS",
            "data": { ... }
          }
        }
      ],
      "pulled": [
        {
          "key": "emotes",
          "old_value": {
            "id": "<REMOVED_EMOTE_ID>"
          }
        }
      ]
    }
  }
}
```

---

## 7. Ejemplos de Implementación Práctica

### 7.1 TypeScript / JavaScript (Node.js & Browser)

```typescript
interface SevenTvFile {
  name: string;
  format: string;
  width: number;
  height: number;
}

interface SevenTvEmote {
  id: string;
  name: string;
  animated: boolean;
  flags: number;
  host: {
    url: string;
    files: SevenTvFile[];
  };
}

// Búsqueda de emotes vía GraphQL
async function search7TvEmotes(query: string, page = 1, limit = 20): Promise<SevenTvEmote[]> {
  const gqlQuery = `
    query SearchEmotes($query: String!, $page: Int, $limit: Int) {
      emotes(query: $query, page: $page, limit: $limit) {
        items {
          id
          name
          animated
          flags
          host {
            url
            files {
              name
              format
              width
              height
            }
          }
        }
      }
    }
  `;

  const response = await fetch("https://7tv.io/v3/gql", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      query: gqlQuery,
      variables: { query, page, limit }
    })
  });

  const json = await response.json();
  return json.data?.emotes?.items ?? [];
}

// Resolver URL de imagen
function getEmoteImageUrl(emote: SevenTvEmote, preferredScale: "1x" | "2x" | "3x" | "4x" = "2x"): string {
  const targetFile = `${preferredScale}.webp`;
  const fileExists = emote.host.files.some(f => f.name === targetFile);
  const selectedName = fileExists ? targetFile : (emote.host.files[0]?.name ?? "1x.webp");
  
  const baseUrl = emote.host.url.startsWith("//") ? `https:${emote.host.url}` : emote.host.url;
  return `${baseUrl.replace(/\/+$/, "")}/${selectedName}`;
}
```

---

### 7.2 Python (`requests` o `aiohttp`)

```python
import requests

def search_7tv_emotes(query: str, page: int = 1, limit: int = 20) -> list[dict]:
    gql_query = """
    query SearchEmotes($query: String!, $page: Int, $limit: Int) {
      emotes(query: $query, page: $page, limit: $limit) {
        items {
          id
          name
          animated
          flags
          host {
            url
            files {
              name
              format
              width
              height
            }
          }
        }
      }
    }
    """
    payload = {
        "query": gql_query,
        "variables": {
            "query": query,
            "page": page,
            "limit": limit
        }
    }
    res = requests.post("https://7tv.io/v3/gql", json=payload, timeout=10)
    res.raise_for_status()
    data = res.json()
    return data.get("data", {}).get("emotes", {}).get("items", [])

def get_emote_cdn_url(emote: dict, scale: str = "2x.webp") -> str:
    host_url = emote["host"]["url"]
    base = f"https:{host_url}" if host_url.startswith("//") else host_url
    return f"{base.rstrip('/')}/{scale}"

# Uso:
emotes = search_7tv_emotes("pepe", limit=5)
for e in emotes:
    print(f"[{e['name']}] -> {get_emote_cdn_url(e, '2x.webp')}")
```

---

### 7.3 Kotlin / Android (Retrofit + Kotlinx Serialization)

```kotlin
@Serializable
data class GqlRequest(val query: String, val variables: Map<String, kotlinx.serialization.json.JsonElement>? = null)

@Serializable
data class GqlResponse<T>(val data: T? = null)

interface SevenTvService {
    @POST("v3/gql")
    suspend fun query(@Body request: GqlRequest): Response<GqlResponse<EmotesData>>

    @GET("v3/emote-sets/{id}")
    suspend fun getEmoteSet(@Path("id") id: String): Response<EmoteSetDto>
}
```

---

## 8. Límites de Tasa (Rate Limiting) y Buenas Prácticas

1. **Código HTTP 429**: Si se excede la tasa de peticiones, 7TV devolverá `429 Too Many Requests`. Implementa reintentos con retroceso exponencial (*exponential backoff*).
2. **Caché en Disco**: Los emotes de 7TV son inmutables por ID (si un emote cambia, genera un nuevo ID). Puedes almacenar en caché local los WebP de forma permanente asociándolos a su ID de archivo.
3. **Paginación Inteligente**: Al consultar sets de canales grandes (algunos streamers tienen más de 500 emotes), utiliza una cola de descarga paralela con semáforo (ej: 4–6 descargas simultáneas) para evitar saturar el ancho de banda del usuario.

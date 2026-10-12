package com.seventv.stickers.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import java.io.File

object AddToExistingPackDialogDefaults {
    const val MAX_STICKERS_PER_PACK = 30

    fun isPackCompatible(pack: StickerPack, emotesToAdd: List<SevenTvEmote>): Boolean {
        if (emotesToAdd.isEmpty()) return false
        val hasAnimatedEmotes = emotesToAdd.any { it.isAnimated }
        val hasStaticEmotes = emotesToAdd.any { !it.isAnimated }
        if (hasAnimatedEmotes && hasStaticEmotes) return false
        val formatMatches = if (pack.isAnimated) !hasStaticEmotes else !hasAnimatedEmotes
        val fitsCapacity = pack.stickers.size + emotesToAdd.size <= MAX_STICKERS_PER_PACK
        return formatMatches && fitsCapacity
    }

    fun incompatibilityReason(pack: StickerPack, emotesToAdd: List<SevenTvEmote>): String? {
        if (emotesToAdd.isEmpty()) return null
        val hasAnimatedEmotes = emotesToAdd.any { it.isAnimated }
        val hasStaticEmotes = emotesToAdd.any { !it.isAnimated }
        if (hasAnimatedEmotes && hasStaticEmotes) {
            return "Selección mixta: contiene emotes animados y estáticos"
        }
        val formatMatches = if (pack.isAnimated) !hasStaticEmotes else !hasAnimatedEmotes
        if (!formatMatches) {
            return if (pack.isAnimated) {
                "Incompatible (el pack es animado)"
            } else {
                "Incompatible (el pack es estático)"
            }
        }
        val remaining = (MAX_STICKERS_PER_PACK - pack.stickers.size).coerceAtLeast(0)
        if (pack.stickers.size + emotesToAdd.size > MAX_STICKERS_PER_PACK) {
            return "Supera el límite de 30 (quedan $remaining espacios)"
        }
        return null
    }
}

/**
 * Dialog displaying available existing sticker packs to add selected emotes to.
 * Enforces WhatsApp compatibility:
 * - 100% homogeneous pack formats (no animated in static packs, no static in animated packs).
 * - Maximum 30 stickers capacity per pack.
 */
@Composable
fun AddToExistingPackDialog(
    packs: List<StickerPack>,
    emotesToAdd: List<SevenTvEmote>,
    onSelectPack: (StickerPack) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Añadir a un pack",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${emotesToAdd.size} sticker${if (emotesToAdd.size > 1) "s" else ""} seleccionado${if (emotesToAdd.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (packs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No tienes paquetes creados todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crea un paquete primero para poder añadirle stickers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(packs, key = { it.identifier }) { pack ->
                        val isCompatible = AddToExistingPackDialogDefaults.isPackCompatible(pack, emotesToAdd)
                        val reason = AddToExistingPackDialogDefaults.incompatibilityReason(pack, emotesToAdd)

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .then(
                                    if (isCompatible) {
                                        Modifier.clickable { onSelectPack(pack) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    val trayFile = File(pack.trayImagePath)
                                    if (trayFile.exists()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(trayFile)
                                                .size(96, 96)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = pack.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.padding(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = pack.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCompatible) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${pack.stickers.size}/30 stickers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = if (pack.isAnimated) "ANIM" else "STATIC",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (pack.isAnimated) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    if (reason != null) {
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    )
}

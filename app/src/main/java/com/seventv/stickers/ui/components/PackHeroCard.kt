package com.seventv.stickers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.ui.theme.WhatsAppGreen
import java.io.File

enum class PackMenuAction {
    RENAME,
    DUPLICATE,
    DELETE
}

enum class PackSyncStatus {
    SYNCED,
    PENDING,
    NOT_EXPORTED
}

object PackHeroCardDefaults {
    const val MAX_STICKERS = 30

    val WarningYellow = Color(0xFFFFB74D)

    fun syncStatus(isWhitelisted: Boolean, hasPendingChanges: Boolean = false): PackSyncStatus = when {
        isWhitelisted && !hasPendingChanges -> PackSyncStatus.SYNCED
        hasPendingChanges -> PackSyncStatus.PENDING
        else -> PackSyncStatus.NOT_EXPORTED
    }

    fun syncStatus(pack: StickerPack, hasPendingChanges: Boolean = false): PackSyncStatus =
        syncStatus(pack.isWhitelisted, hasPendingChanges)

    fun statusLabel(status: PackSyncStatus): String = when (status) {
        PackSyncStatus.SYNCED -> "Sincronizado"
        PackSyncStatus.PENDING -> "Pendiente"
        PackSyncStatus.NOT_EXPORTED -> "No exportado"
    }

    fun statusEmoji(status: PackSyncStatus): String = when (status) {
        PackSyncStatus.SYNCED -> "🟢"
        PackSyncStatus.PENDING -> "🟡"
        PackSyncStatus.NOT_EXPORTED -> "⚪"
    }

    fun statusChipText(status: PackSyncStatus): String =
        "${statusEmoji(status)} ${statusLabel(status)}"

    fun statusColor(
        status: PackSyncStatus,
        colorScheme: ColorScheme? = null
    ): Color = when (status) {
        PackSyncStatus.SYNCED -> colorScheme?.tertiary ?: WhatsAppGreen
        PackSyncStatus.PENDING -> WarningYellow
        PackSyncStatus.NOT_EXPORTED -> colorScheme?.onSurfaceVariant ?: Color(0xFF9AA0A6)
    }

    fun counterText(count: Int, maxCount: Int = MAX_STICKERS): String =
        "$count / $maxCount"
}

/**
 * Material 3 Hero Card for pack management in "Mis Packs".
 * Features 24dp rounded corners, surfaceContainer tonal elevation,
 * metadata header with live WhatsApp status chip, horizontal preview strip of stickers,
 * and WhatsApp export button with 3-dot overflow menu.
 */
@Composable
fun PackHeroCard(
    pack: StickerPack,
    stickers: List<PackSticker>,
    onExportToWhatsApp: () -> Unit,
    onPackClick: () -> Unit,
    onMenuAction: (PackMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    onStickerClick: ((PackSticker) -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val displayStickers = if (stickers.isNotEmpty()) stickers else pack.stickers
    val stickerCount = displayStickers.size
    val syncStatus = PackHeroCardDefaults.syncStatus(pack)
    val statusColor = PackHeroCardDefaults.statusColor(syncStatus, MaterialTheme.colorScheme)

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.seventv.stickers.ui.theme.GlassSurfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(
                    com.seventv.stickers.ui.theme.ElectricCyan.copy(alpha = 0.4f),
                    com.seventv.stickers.ui.theme.NeonFuchsia.copy(alpha = 0.4f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPackClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Titles, Meta, Counter & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Por ${pack.publisher} • ${if (pack.isAnimated) "Animado" else "Estático"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right header chips: Count & WhatsApp Status
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Counter Pill
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = Color(0x3501CDFE),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            com.seventv.stickers.ui.theme.ElectricCyan.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = PackHeroCardDefaults.counterText(stickerCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = com.seventv.stickers.ui.theme.ElectricCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Status Chip
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = Color(0x351F1435),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            statusColor.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = PackHeroCardDefaults.statusLabel(syncStatus),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sticker Preview Strip (Vitrina de Cristal)
            if (displayStickers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displayStickers, key = { it.id }) { sticker ->
                        val stickerFile = remember(sticker.filePath) {
                            val f = File(sticker.filePath)
                            if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
                        }

                        val boxModifier = if (onStickerClick != null) {
                            Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(Color(0x351F1435))
                                .clickable { onStickerClick(sticker) }
                        } else {
                            Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(Color(0x351F1435))
                        }

                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x35FFFFFF))
                        ) {
                            Box(
                                modifier = boxModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(if (stickerFile.exists()) stickerFile else sticker.filePath)
                                        .size(128, 128)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = sticker.emoteName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin stickers aún",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Footer: WhatsApp Add CTA + 3-Dots Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WhatsAppAddButton(
                    isWhitelisted = pack.isWhitelisted,
                    onClick = onExportToWhatsApp,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones de ${pack.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Renombrar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(PackMenuAction.RENAME)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Duplicar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(PackMenuAction.DUPLICATE)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Eliminar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onMenuAction(PackMenuAction.DELETE)
                            }
                        )
                    }
                }
            }
        }
    }
}

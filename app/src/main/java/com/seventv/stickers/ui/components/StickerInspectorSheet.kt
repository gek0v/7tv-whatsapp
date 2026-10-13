package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.media.model.StickerSpecs
import java.io.File
import java.util.Locale

object StickerInspectorSheetDefaults {
    const val MAX_STATIC_BYTES: Long = StickerSpecs.STATIC_MAX_BYTES     // 100 * 1024L
    const val MAX_ANIMATED_BYTES: Long = StickerSpecs.ANIMATED_MAX_BYTES // 500 * 1024L
    const val MAX_EMOJIS: Int = 3
    const val MAX_DURATION_SECONDS: Float = 3.0f
    const val MAX_FPS: Int = StickerSpecs.ANIMATED_MAX_FPS               // 30

    val quickEmojis: List<String> = listOf("💬", "😂", "🔥", "❤️", "👍", "😮", "🎉", "💀")

    fun maxFileSizeBytes(isAnimated: Boolean): Long =
        if (isAnimated) MAX_ANIMATED_BYTES else MAX_STATIC_BYTES

    fun isFileSizeExceeded(bytes: Long, isAnimated: Boolean): Boolean =
        bytes > maxFileSizeBytes(isAnimated)

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return String.format(Locale.US, "%.1f KB", kb)
    }

    fun canAddEmoji(currentEmojis: List<String>): Boolean =
        currentEmojis.size < MAX_EMOJIS

    fun formatDuration(durationSeconds: Float): String =
        String.format(Locale.US, "%.1fs", durationSeconds)

    fun formatDuration(durationMs: Long): String =
        String.format(Locale.US, "%.1fs", durationMs / 1000f)

    fun formatFps(fps: Int): String = "$fps FPS"
}

/**
 * M3 ModalBottomSheet for deep inspection of individual stickers in a pack.
 * Includes interactive WhatsApp chat simulator preview with background switcher,
 * WhatsApp keyboard emoji mappings (up to 3), live technical metrics (KB limit, duration, FPS),
 * and primary actions (Set as Tray Icon, Delete Sticker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerInspectorSheet(
    sticker: PackSticker,
    onDismiss: () -> Unit,
    onUpdateEmojis: (List<String>) -> Unit = {},
    onDeleteSticker: () -> Unit = {},
    onSetAsTrayIcon: () -> Unit = {},
    isAnimated: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var simMode by remember { mutableStateOf(ChatSimulatorMode.WHATSAPP_DARK) }
    var isEmojiPickerVisible by remember { mutableStateOf(false) }

    val stickerFile = remember(sticker.filePath) {
        val f = File(sticker.filePath)
        if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
    }

    val isSizeExceeded = StickerInspectorSheetDefaults.isFileSizeExceeded(sticker.fileLength, isAnimated)
    val sizeColor = if (isSizeExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val canAddMore = StickerInspectorSheetDefaults.canAddEmoji(sticker.emojis)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Emote Name & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sticker.emoteName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isAnimated) "Sticker animado de WhatsApp" else "Sticker estático de WhatsApp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onFullscreenClick != null) {
                        TextButton(onClick = onFullscreenClick) {
                            Text(
                                text = "⛶ Expandir",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulator Background Mode Selector (Segmented/Filter Chips)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fondo:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = simMode == ChatSimulatorMode.WHATSAPP_DARK,
                    onClick = { simMode = ChatSimulatorMode.WHATSAPP_DARK },
                    label = { Text("WA Oscuro") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = simMode == ChatSimulatorMode.WHATSAPP_LIGHT,
                    onClick = { simMode = ChatSimulatorMode.WHATSAPP_LIGHT },
                    label = { Text("WA Claro") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = simMode == ChatSimulatorMode.CHECKERBOARD,
                    onClick = { simMode = ChatSimulatorMode.CHECKERBOARD },
                    label = { Text("Malla") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Preview inside ChatSimulatorBackground
            ChatSimulatorBackground(
                mode = simMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(enabled = onFullscreenClick != null) { onFullscreenClick?.invoke() }
            ) {
                val bubbleColor = ChatSimulatorDefaults.bubbleColor(simMode)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bubbleColor != Color.Transparent) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = bubbleColor,
                            modifier = Modifier.size(176.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(if (stickerFile.exists()) stickerFile else sticker.filePath)
                                        .size(512, 512)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = sticker.emoteName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(160.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (stickerFile.exists()) stickerFile else sticker.filePath)
                                .size(512, 512)
                                .crossfade(true)
                                .build(),
                            contentDescription = sticker.emoteName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(160.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Emoji Assignment Section
            Text(
                text = "Emojis de WhatsApp (${sticker.emojis.size} / ${StickerInspectorSheetDefaults.MAX_EMOJIS})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sticker.emojis.forEach { emoji ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val updated = sticker.emojis.toMutableList().also { it.remove(emoji) }
                                    onUpdateEmojis(updated)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar $emoji",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                if (canAddMore) {
                    OutlinedButton(
                        onClick = { isEmojiPickerVisible = !isEmojiPickerVisible },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Añadir emoji",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Quick emoji picker drawer
            AnimatedVisibility(
                visible = isEmojiPickerVisible && canAddMore,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Sugerencias rápidas:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(StickerInspectorSheetDefaults.quickEmojis) { candidate ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        if (canAddMore && candidate !in sticker.emojis) {
                                            val updated = sticker.emojis + candidate
                                            onUpdateEmojis(updated)
                                            if (updated.size >= StickerInspectorSheetDefaults.MAX_EMOJIS) {
                                                isEmojiPickerVisible = false
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = candidate, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Technical Metrics Row
            Text(
                text = "Especificaciones Técnicas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: File Size
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSizeExceeded) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tamaño",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = StickerInspectorSheetDefaults.formatFileSize(sticker.fileLength),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = sizeColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isAnimated) "≤ 500 KB" else "≤ 100 KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric 2: Duration
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Duración",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAnimated) StickerInspectorSheetDefaults.formatDuration(3.0f) else "Estático",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isAnimated) "≤ 3.0s" else "1 frame",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric 3: Framerate
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAnimated) StickerInspectorSheetDefaults.formatFps(30) else "N/A",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isAnimated) "≤ 30 FPS" else "Estático",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions: Tray Icon & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSetAsTrayIcon()
                        onDismiss()
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Icono del pack",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        onDeleteSticker()
                        onDismiss()
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Eliminar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

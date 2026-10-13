package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.ui.theme.WhatsAppGreen
import java.util.Locale

object EmoteFullscreenViewerDefaults {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 4.0f
    const val DOUBLE_TAP_ZOOM = 2.5f

    fun clampZoom(scale: Float): Float = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun badgeText(isAnimated: Boolean): String = if (isAnimated) "ANIM" else "STATIC"

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return String.format(Locale.US, "%.1f KB", kb)
    }
}

/**
 * Fullscreen modal dialog displaying a 7TV emote or pack sticker in high resolution,
 * featuring interactive background switching (WhatsApp Dark, WhatsApp Light, Checkerboard),
 * pinch-to-zoom and pan gestures, double-tap zoom toggle, and optional quick actions.
 */
@Composable
fun EmoteFullscreenViewer(
    name: String,
    isAnimated: Boolean,
    model: Any,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isInDraft: Boolean? = null,
    onToggleDraft: (() -> Unit)? = null,
    fileSizeText: String? = null,
    emojis: List<String>? = null,
    onShareSticker: (() -> Unit)? = null,
    isSharingSticker: Boolean = false
) {
    var simMode by remember { mutableStateOf(ChatSimulatorMode.WHATSAPP_DARK) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ChatSimulatorBackground(
            mode = simMode,
            modifier = modifier.fillMaxSize()
        ) {
            // Main Zoomable Image Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = EmoteFullscreenViewerDefaults.DOUBLE_TAP_ZOOM
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = EmoteFullscreenViewerDefaults.clampZoom(scale * zoom)
                            scale = newScale
                            if (scale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .size(512, 512)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            // Top Bar Overlay
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isAnimated) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = EmoteFullscreenViewerDefaults.badgeText(isAnimated),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAnimated) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = scale > 1.05f,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1fx", scale)} ↺",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (scale <= 1.05f) {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
            }

            // Bottom Controls Overlay
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Background Mode Selector Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fondo:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        FilterChip(
                            selected = simMode == ChatSimulatorMode.WHATSAPP_DARK,
                            onClick = { simMode = ChatSimulatorMode.WHATSAPP_DARK },
                            label = { Text("WA Oscuro") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = simMode == ChatSimulatorMode.WHATSAPP_LIGHT,
                            onClick = { simMode = ChatSimulatorMode.WHATSAPP_LIGHT },
                            label = { Text("WA Claro") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = simMode == ChatSimulatorMode.CHECKERBOARD,
                            onClick = { simMode = ChatSimulatorMode.CHECKERBOARD },
                            label = { Text("Malla") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                    }

                    // Metadata row (File size / Emojis)
                    if (fileSizeText != null || !emojis.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (fileSizeText != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = fileSizeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (!emojis.isNullOrEmpty()) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = emojis.joinToString(" "),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Direct Share to WhatsApp Action Button
                    if (onShareSticker != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onShareSticker,
                            enabled = !isSharingSticker,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White,
                                disabledContainerColor = WhatsAppGreen.copy(alpha = 0.6f),
                                disabledContentColor = Color.White.copy(alpha = 0.8f)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSharingSticker) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Preparando sticker...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enviar sticker a WhatsApp",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Draft Action Button (for Explore & Channel)
                    if (isInDraft != null && onToggleDraft != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onToggleDraft,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isInDraft) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (isInDraft) {
                                    MaterialTheme.colorScheme.onTertiary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isInDraft) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isInDraft) "En el borrador (Quitar)" else "Añadir al borrador",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

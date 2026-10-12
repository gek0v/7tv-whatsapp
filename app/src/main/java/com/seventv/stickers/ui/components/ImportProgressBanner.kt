package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seventv.stickers.core.domain.manager.PackImportManager
import com.seventv.stickers.core.domain.manager.PackImportProgress
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan
import com.seventv.stickers.ui.theme.WhatsAppGreen
import kotlinx.coroutines.delay

@Composable
fun ImportProgressBanner(
    importManager: PackImportManager,
    modifier: Modifier = Modifier
) {
    val progressState by importManager.progress.collectAsState()

    // Auto dismiss after 4 seconds when completed
    LaunchedEffect(progressState) {
        if (progressState is PackImportProgress.Completed) {
            delay(4000)
            importManager.dismissProgress()
        }
    }

    AnimatedVisibility(
        visible = progressState !is PackImportProgress.Idle,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        when (val state = progressState) {
            is PackImportProgress.Running -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(1.dp, SevenTvCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    shadowElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = WhatsAppGreen,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Creando packs de ${state.streamerName} (${state.currentPack}/${state.totalPacks})",
                                    color = OnSurfaceText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val percent = if (state.totalStickers > 0) {
                                    (state.currentSticker * 100) / state.totalStickers
                                } else 0
                                Text(
                                    text = "Procesando sticker ${state.currentSticker}/${state.totalStickers} ($percent%)",
                                    color = OnSurfaceSubtext,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (state.totalStickers > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.currentSticker.toFloat() / state.totalStickers.coerceAtLeast(1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = WhatsAppGreen,
                                trackColor = DarkSurface
                            )
                        }
                    }
                }
            }
            is PackImportProgress.Completed -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(1.dp, WhatsAppGreen.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "¡${state.packsCount} pack(s) de ${state.streamerName} listos en Mis Packs!",
                                color = OnSurfaceText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = { importManager.dismissProgress() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = OnSurfaceSubtext,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            is PackImportProgress.Failed -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(1.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = state.errorMessage,
                                color = OnSurfaceText,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { importManager.dismissProgress() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = OnSurfaceSubtext,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            PackImportProgress.Idle -> Unit
        }
    }
}

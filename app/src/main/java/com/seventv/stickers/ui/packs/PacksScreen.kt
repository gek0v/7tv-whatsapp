package com.seventv.stickers.ui.packs

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.ui.components.WhatsAppAddButton
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper
import java.io.File

@Composable
fun PacksScreen(
    viewModel: PacksViewModel,
    onNavigateToPackDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle WhatsApp Export Events
    LaunchedEffect(state.exportEvent) {
        val event = state.exportEvent ?: return@LaunchedEffect
        when (event) {
            is WhatsAppExportEvent.LaunchIntent -> {
                val intent = WhatsAppIntentHelper.createAddPackIntent(
                    packIdentifier = event.pack.identifier,
                    packName = event.pack.name
                )
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error al abrir WhatsApp: ${e.message}")
                }
                viewModel.onClearExportEvent()
            }
            is WhatsAppExportEvent.Error -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.onClearExportEvent()
            }
            WhatsAppExportEvent.WhatsAppNotInstalled -> {
                // Handled via Dialog below
            }
        }
    }

    // Sync whitelist for all visible packs
    LaunchedEffect(state.loadState) {
        val success = state.loadState as? PacksLoadState.Success ?: return@LaunchedEffect
        success.packs.forEach { pack ->
            viewModel.syncWhitelist(context, pack.identifier)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mis Packs de Stickers",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceText
            )
            Text(
                text = "Gestiona y exporta tus paquetes guardados a WhatsApp",
                fontSize = 13.sp,
                color = OnSurfaceSubtext
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val load = state.loadState) {
                is PacksLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SevenTvCyan)
                    }
                }
                is PacksLoadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(load.message, color = ErrorRed, textAlign = TextAlign.Center)
                    }
                }
                is PacksLoadState.Success -> {
                    if (load.packs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "No tienes paquetes creados todavía",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Explora emotes de 7TV o importa un canal completo para crear tus primeros stickers de WhatsApp.",
                                    fontSize = 13.sp,
                                    color = OnSurfaceSubtext,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(load.packs, key = { it.identifier }) { pack ->
                                PackCard(
                                    pack = pack,
                                    onClick = { onNavigateToPackDetail(pack.identifier) },
                                    onAddWhatsAppClick = { viewModel.onAddPackToWhatsApp(context, pack) },
                                    onDeleteClick = { viewModel.onOpenDeleteDialog(pack) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Delete Confirmation Dialog
    if (state.isDeleteConfirmDialogOpen && state.packToDelete != null) {
        val pack = state.packToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteDialog() },
            title = { Text("Eliminar Paquete", color = OnSurfaceText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar '${pack.name}'? Se borrarán sus ${pack.stickers.size} stickers del almacenamiento interno.",
                    color = OnSurfaceSubtext
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmDeletePack() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteDialog() }) {
                    Text("Cancelar", color = OnSurfaceSubtext)
                }
            },
            containerColor = DarkSurface
        )
    }

    // WhatsApp Not Installed Dialog
    if (state.exportEvent is WhatsAppExportEvent.WhatsAppNotInstalled) {
        AlertDialog(
            onDismissRequest = { viewModel.onClearExportEvent() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SevenTvCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WhatsApp no encontrado", color = OnSurfaceText)
                }
            },
            text = {
                Text(
                    "WhatsApp Estándar (com.whatsapp) no parece estar instalado en tu dispositivo. Instálalo desde Google Play Store para poder añadir tus stickers.",
                    color = OnSurfaceSubtext
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onClearExportEvent() },
                    colors = ButtonDefaults.buttonColors(containerColor = SevenTvCyan, contentColor = Color.Black)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun PackCard(
    pack: StickerPack,
    onClick: () -> Unit,
    onAddWhatsAppClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tray Icon or First sticker
                val trayFile = File(pack.trayImagePath)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (trayFile.exists()) {
                        AsyncImage(
                            model = trayFile,
                            contentDescription = "Icono de ${pack.name}",
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        Text(
                            text = pack.name.take(1).uppercase(),
                            color = SevenTvCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.name,
                        color = OnSurfaceText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pack.stickers.size} stickers • ${if (pack.isAnimated) "Animados" else "Estáticos"}",
                        color = OnSurfaceSubtext,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Por ${pack.publisher}",
                        color = OnSurfaceSubtext,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar pack",
                        tint = OnSurfaceSubtext
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            WhatsAppAddButton(
                isWhitelisted = pack.isWhitelisted,
                onClick = onAddWhatsAppClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

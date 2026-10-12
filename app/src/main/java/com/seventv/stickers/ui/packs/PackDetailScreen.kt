package com.seventv.stickers.ui.packs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.ui.components.ChatSimulatorBackground
import com.seventv.stickers.ui.components.ChatSimulatorMode
import com.seventv.stickers.ui.components.WhatsAppAddButton
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan
import com.seventv.stickers.whatsapp.StickerContentProvider
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper
import java.io.File

@Composable
fun PackDetailScreen(
    packId: String,
    viewModel: PacksViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val addPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val validationError = result.data?.getStringExtra("validation_error")
        if (!validationError.isNullOrBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error de WhatsApp: $validationError")
            }
        }
        viewModel.syncWhitelist(context, packId)
    }

    LaunchedEffect(packId) {
        viewModel.onSelectPackDetail(packId)
        viewModel.syncWhitelist(context, packId)
    }

    // Handle WhatsApp Export Events
    LaunchedEffect(state.exportEvent) {
        val event = state.exportEvent ?: return@LaunchedEffect
        when (event) {
            is WhatsAppExportEvent.LaunchIntent -> {
                val intent = WhatsAppIntentHelper.createAddPackIntent(
                    authority = StickerContentProvider.AUTHORITY,
                    packIdentifier = event.pack.identifier,
                    packName = event.pack.name,
                    context = context
                )
                try {
                    addPackLauncher.launch(intent)
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

    val pack = state.selectedPackDetail

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (pack == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SevenTvCyan)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Top bar with back navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = OnSurfaceText
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pack.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pack.stickers.size} stickers • Por ${pack.publisher}",
                            fontSize = 12.sp,
                            color = OnSurfaceSubtext
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chat Simulator Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fondo:",
                        color = OnSurfaceSubtext,
                        fontSize = 12.sp
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.WHATSAPP_DARK,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.WHATSAPP_DARK) },
                        label = { Text("WA Oscuro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            labelColor = OnSurfaceText
                        )
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.WHATSAPP_LIGHT,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.WHATSAPP_LIGHT) },
                        label = { Text("WA Claro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            labelColor = OnSurfaceText
                        )
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.CHECKERBOARD,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.CHECKERBOARD) },
                        label = { Text("Malla") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            labelColor = OnSurfaceText
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sticker Grid wrapped in Chat Simulator Background
                ChatSimulatorBackground(
                    mode = state.chatSimulatorMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pack.stickers, key = { it.id }) { sticker ->
                            PackStickerCell(sticker = sticker)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Export Button
                WhatsAppAddButton(
                    isWhitelisted = pack.isWhitelisted,
                    onClick = { viewModel.onAddPackToWhatsApp(context, pack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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
                    "WhatsApp o WhatsApp Business no parece estar instalado en tu dispositivo. Instálalo desde Google Play Store para poder añadir tus stickers.",
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
private fun PackStickerCell(sticker: PackSticker) {
    val context = LocalContext.current
    val file = remember(sticker.filePath) {
        val f = File(sticker.filePath)
        if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = file,
                contentDescription = sticker.emoteName,
                modifier = Modifier.size(76.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sticker.emoteName,
                color = OnSurfaceText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sticker.emojis.isNotEmpty()) {
                Text(
                    text = sticker.emojis.joinToString(" "),
                    fontSize = 11.sp
                )
            }
        }
    }
}

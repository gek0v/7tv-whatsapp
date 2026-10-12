package com.seventv.stickers.ui.packs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    var pendingExportPackId by remember { mutableStateOf<String?>(null) }

    val addPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val validationError = result.data?.getStringExtra("validation_error")
        if (!validationError.isNullOrBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error de WhatsApp: $validationError")
            }
        }
        pendingExportPackId?.let { packId ->
            viewModel.syncWhitelist(context, packId)
            pendingExportPackId = null
            viewModel.onAdvanceExportQueue(context)
        }
    }

    // Handle WhatsApp Export Events
    LaunchedEffect(state.exportEvent) {
        val event = state.exportEvent ?: return@LaunchedEffect
        when (event) {
            is WhatsAppExportEvent.LaunchIntent -> {
                pendingExportPackId = event.pack.identifier
                val intent = WhatsAppIntentHelper.createAddPackIntent(
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

    // Sync whitelist for all visible packs
    LaunchedEffect(state.loadState) {
        val success = state.loadState as? PacksLoadState.Success ?: return@LaunchedEffect
        success.packs.forEach { pack ->
            viewModel.syncWhitelist(context, pack.identifier)
        }
    }

    // Handle user feedback messages
    LaunchedEffect(state.userFeedbackMessage) {
        val msg = state.userFeedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onClearFeedbackMessage()
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

            // Top Header Bar
            val currentPacks = (state.loadState as? PacksLoadState.Success)?.packs.orEmpty()
            if (state.isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.selectedPackIds.size} seleccionados",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (state.selectedPackIds.size == currentPacks.size) {
                                    viewModel.onClearSelection()
                                } else {
                                    viewModel.onSelectAllPacks()
                                }
                            }
                        ) {
                            Text(
                                text = if (state.selectedPackIds.size == currentPacks.size) "Deseleccionar" else "Todos",
                                color = SevenTvCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { viewModel.onToggleSelectionMode() }) {
                            Text("Cancelar", color = OnSurfaceSubtext)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    }
                    if (currentPacks.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onToggleSelectionMode() }) {
                            Text("Seleccionar", color = SevenTvCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

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
                            contentPadding = PaddingValues(bottom = if (state.isSelectionMode) 90.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(load.packs, key = { it.identifier }) { pack ->
                                PackCard(
                                    pack = pack,
                                    isSelectionMode = state.isSelectionMode,
                                    isSelected = pack.identifier in state.selectedPackIds,
                                    onToggleSelect = { viewModel.onTogglePackSelection(pack.identifier) },
                                    onClick = {
                                        if (state.isSelectionMode) {
                                            viewModel.onTogglePackSelection(pack.identifier)
                                        } else {
                                            onNavigateToPackDetail(pack.identifier)
                                        }
                                    },
                                    onRenameClick = { viewModel.onOpenRenameDialog(pack) },
                                    onAddWhatsAppClick = { viewModel.onAddPackToWhatsApp(context, pack) },
                                    onDeleteClick = { viewModel.onOpenDeleteDialog(pack) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Bar for Batch Actions in Selection Mode
        if (state.isSelectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceVariant,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.onOpenBulkDeleteDialog() },
                        enabled = state.selectedPackIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = Color.White,
                            disabledContainerColor = DarkSurface
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Eliminar (${state.selectedPackIds.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (state.selectedPackIds.size >= 2) {
                        Button(
                            onClick = { viewModel.onOpenMergeDialog() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SevenTvCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Combinar (${state.selectedPackIds.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.onStartBatchWhatsAppExport(context) },
                        enabled = state.selectedPackIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White,
                            disabledContainerColor = DarkSurface
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Añadir a WA (${state.selectedPackIds.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Merge Confirmation Dialog
    if (state.isMergeDialogOpen && state.selectedPackIds.size >= 2) {
        val currentPacksList = (state.loadState as? PacksLoadState.Success)?.packs.orEmpty()
        val selectedPacks = currentPacksList.filter { it.identifier in state.selectedPackIds }
        val totalStickers = selectedPacks.sumOf { it.stickers.size }
        val expectedPacksCount = (totalStickers + 29) / 30
        var baseName by remember(state.selectedPackIds) {
            mutableStateOf(selectedPacks.firstOrNull()?.name ?: "Pack Combinado")
        }

        AlertDialog(
            onDismissRequest = {
                if (!state.isMergingPacks) viewModel.onDismissMergeDialog()
            },
            title = {
                Text(
                    "Combinar Paquetes",
                    color = OnSurfaceText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Se combinarán los ${selectedPacks.size} paquetes seleccionados ($totalStickers stickers en total).\n\n" +
                                if (expectedPacksCount == 1) {
                                    "Caben todos en 1 único paquete consolidado."
                                } else {
                                    "Se auto-reempaquetarán en $expectedPacksCount paquetes (hasta 30 stickers por paquete)."
                                },
                        color = OnSurfaceSubtext,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nombre del nuevo paquete:",
                        color = OnSurfaceText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = baseName,
                        onValueChange = { baseName = it },
                        singleLine = true,
                        enabled = !state.isMergingPacks,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceText,
                            unfocusedTextColor = OnSurfaceText,
                            focusedBorderColor = SevenTvCyan,
                            unfocusedBorderColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.isMergingPacks) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = SevenTvCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Combinando y reempaquetando...", color = SevenTvCyan, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (baseName.isNotBlank() && !state.isMergingPacks) {
                            viewModel.onConfirmMergePacks(baseName)
                        }
                    },
                    enabled = baseName.isNotBlank() && !state.isMergingPacks,
                    colors = ButtonDefaults.buttonColors(containerColor = SevenTvCyan, contentColor = Color.Black)
                ) {
                    Text("Combinar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!state.isMergingPacks) {
                    TextButton(onClick = { viewModel.onDismissMergeDialog() }) {
                        Text("Cancelar", color = OnSurfaceSubtext)
                    }
                }
            },
            containerColor = DarkSurface
        )
    }

    // Single Delete Confirmation Dialog
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

    // Bulk Delete Confirmation Dialog
    if (state.isBulkDeleteConfirmDialogOpen && state.selectedPackIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissBulkDeleteDialog() },
            title = { Text("Eliminar Paquetes", color = OnSurfaceText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar los ${state.selectedPackIds.size} paquetes seleccionados? Se borrarán todos sus stickers del almacenamiento.",
                    color = OnSurfaceSubtext
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmBulkDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Eliminar Todos", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissBulkDeleteDialog() }) {
                    Text("Cancelar", color = OnSurfaceSubtext)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Rename Pack Dialog
    if (state.isRenameDialogOpen && state.packToRename != null) {
        val pack = state.packToRename!!
        var newName by remember(pack.identifier) { mutableStateOf(pack.name) }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissRenameDialog() },
            title = { Text("Editar Nombre del Pack", color = OnSurfaceText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Introduce el nuevo nombre para este paquete de stickers:",
                        color = OnSurfaceSubtext,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceText,
                            unfocusedTextColor = OnSurfaceText,
                            focusedBorderColor = SevenTvCyan,
                            unfocusedBorderColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.onConfirmRename(newName)
                        }
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SevenTvCyan, contentColor = Color.Black)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissRenameDialog() }) {
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
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onAddWhatsAppClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode) {
                    onToggleSelect()
                } else {
                    onClick()
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SevenTvCyan,
                            checkmarkColor = Color.Black,
                            uncheckedColor = OnSurfaceSubtext
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Tray Icon or First sticker
                val context = LocalContext.current
                val trayFile = remember(pack.trayImagePath) {
                    val f = File(pack.trayImagePath)
                    if (f.isAbsolute && f.exists()) f else File(context.filesDir, pack.trayImagePath)
                }
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

                if (!isSelectionMode) {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar nombre",
                            tint = OnSurfaceSubtext
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
            }

            if (!isSelectionMode) {
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
}

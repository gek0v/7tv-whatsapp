package com.seventv.stickers.ui.packs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import com.seventv.stickers.core.domain.validator.StickerIssue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.ui.components.ChatSimulatorBackground
import com.seventv.stickers.ui.components.ChatSimulatorMode
import com.seventv.stickers.ui.components.DraftBottomSheetDefaults
import com.seventv.stickers.ui.components.StickerInspectorSheet
import com.seventv.stickers.ui.components.WhatsAppAddButton
import com.seventv.stickers.whatsapp.StickerContentProvider
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
    var inspectedSticker by remember { mutableStateOf<PackSticker?>(null) }

    val addPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val validationError = result.data?.getStringExtra("validation_error")
        if (!validationError.isNullOrBlank()) {
            if (validationError.contains("handleStickerPackPreviewResult", ignoreCase = true)) {
                viewModel.onWhatsAppPreviewValidationFailed(packId)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("WhatsApp rechazó la vista previa del paquete. Revisa los stickers marcados.")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Error de WhatsApp: $validationError")
                }
            }
        }
        viewModel.syncWhitelist(context, packId)
    }

    LaunchedEffect(packId) {
        viewModel.onSelectPackDetail(packId)
        viewModel.syncWhitelist(context, packId)
    }

    // Handle user feedback message
    LaunchedEffect(state.userFeedbackMessage) {
        val msg = state.userFeedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onClearFeedbackMessage()
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    if (pack != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pack.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = { viewModel.onOpenRenameDialog(pack) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Renombrar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Detalle del Pack",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (pack != null) {
                        IconButton(onClick = { viewModel.onOpenAddStickersDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir stickers",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.onAddPackToWhatsApp(context, pack) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (pack.isWhitelisted) "Sincronizado" else "Añadir a WA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (pack == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pack Health Indicator
                val stickerCount = pack.stickers.size
                val progress = (stickerCount / DraftBottomSheetDefaults.MAX_STICKERS.toFloat()).coerceIn(0f, 1f)
                val isBelowMinimum = stickerCount < DraftBottomSheetDefaults.MIN_STICKERS

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Capacidad del Pack",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$stickerCount / ${DraftBottomSheetDefaults.MAX_STICKERS}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isBelowMinimum) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = when {
                                isBelowMinimum -> MaterialTheme.colorScheme.error
                                stickerCount >= DraftBottomSheetDefaults.MAX_STICKERS -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        if (isBelowMinimum) {
                            val missing = DraftBottomSheetDefaults.MIN_STICKERS - stickerCount
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Faltan $missing para el mínimo de ${DraftBottomSheetDefaults.MIN_STICKERS} stickers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Chat Simulator Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fondo:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.WHATSAPP_DARK,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.WHATSAPP_DARK) },
                        label = { Text("WA Oscuro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.WHATSAPP_LIGHT,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.WHATSAPP_LIGHT) },
                        label = { Text("WA Claro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = state.chatSimulatorMode == ChatSimulatorMode.CHECKERBOARD,
                        onClick = { viewModel.onSetChatSimulatorMode(ChatSimulatorMode.CHECKERBOARD) },
                        label = { Text("Malla") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Diagnostic Warning Banner
                if (state.stickerIssues.isNotEmpty() || (state.packDiagnosticReport != null && !state.packDiagnosticReport!!.isValid)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { viewModel.onOpenDiagnosticDialog() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.stickerIssues.isNotEmpty())
                                        "⚠️ ${state.stickerIssues.size} sticker(s) con problemas para WhatsApp"
                                    else
                                        "⚠️ Incompatibilidad con WhatsApp",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toca para ver los detalles y corregirlos.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.onOpenDiagnosticDialog() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Revisar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sticker Grid wrapped in Chat Simulator Background
                ChatSimulatorBackground(
                    mode = state.chatSimulatorMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(pack.stickers, key = { _, sticker -> sticker.id }) { index, sticker ->
                            PackStickerCell(
                                sticker = sticker,
                                isTrayIcon = index == 0,
                                issue = state.stickerIssues[sticker.id],
                                onClick = { inspectedSticker = sticker },
                                onDeleteClick = { viewModel.onOpenDeleteStickerDialog(sticker) },
                                onIssueClick = { viewModel.onOpenDiagnosticDialog() }
                            )
                        }
                    }
                }

                // Bottom Export Button
                WhatsAppAddButton(
                    isWhitelisted = pack.isWhitelisted,
                    onClick = { viewModel.onAddPackToWhatsApp(context, pack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }

    // Sticker Inspector Sheet Integration
    if (inspectedSticker != null && pack != null) {
        StickerInspectorSheet(
            sticker = inspectedSticker!!,
            onDismiss = { inspectedSticker = null },
            onUpdateEmojis = { emojis ->
                viewModel.onUpdateStickerEmojis(pack.identifier, inspectedSticker!!.id, emojis)
            },
            onDeleteSticker = {
                val toDelete = inspectedSticker!!
                inspectedSticker = null
                viewModel.onOpenDeleteStickerDialog(toDelete)
            },
            onSetAsTrayIcon = {
                viewModel.onSetStickerAsTrayIcon(pack.identifier, inspectedSticker!!.id)
                inspectedSticker = null
            },
            isAnimated = pack.isAnimated
        )
    }

    // Delete Sticker Confirmation Dialog
    if (state.isDeleteStickerConfirmDialogOpen && state.stickerToDelete != null && pack != null) {
        val sticker = state.stickerToDelete!!
        val willBeBelowMinimum = pack.stickers.size <= DraftBottomSheetDefaults.MIN_STICKERS
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteStickerDialog() },
            title = {
                Text(
                    text = if (willBeBelowMinimum) "⚠️ Advertencia de WhatsApp" else "Eliminar Sticker",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (willBeBelowMinimum) {
                    Text(
                        text = "Al eliminar '${sticker.emoteName}', el pack quedará con ${pack.stickers.size - 1} stickers.\n\nWhatsApp exige un mínimo de ${DraftBottomSheetDefaults.MIN_STICKERS} stickers por paquete. No podrás añadir o actualizar este paquete en WhatsApp hasta que contenga al menos ${DraftBottomSheetDefaults.MIN_STICKERS} stickers.\n\n¿Deseas eliminarlo de todas formas?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "¿Estás seguro de que deseas eliminar el sticker '${sticker.emoteName}' de este paquete?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmDeleteSticker(pack.identifier) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteStickerDialog() }) {
                    Text("Cancelar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Rename Pack Dialog
    if (state.isRenameDialogOpen && state.packToRename != null) {
        val p = state.packToRename!!
        var newName by remember(p.identifier) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissRenameDialog() },
            title = {
                Text(
                    text = "Editar Nombre del Pack",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce el nuevo nombre para este paquete de stickers:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                    enabled = newName.isNotBlank()
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissRenameDialog() }) {
                    Text("Cancelar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Add Stickers Modal
    if (state.isAddStickersDialogOpen && pack != null) {
        AddStickersModal(
            pack = pack,
            viewModel = viewModel,
            onDismiss = { viewModel.onDismissAddStickersDialog() }
        )
    }

    // WhatsApp Not Installed Dialog
    if (state.exportEvent is WhatsAppExportEvent.WhatsAppNotInstalled) {
        AlertDialog(
            onDismissRequest = { viewModel.onClearExportEvent() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WhatsApp no encontrado",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = "WhatsApp o WhatsApp Business no parece estar instalado en tu dispositivo. Instálalo desde Google Play Store para poder añadir tus stickers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onClearExportEvent() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // WhatsApp Diagnostic Dialog
    if (state.isDiagnosticDialogOpen && pack != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDiagnosticDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnóstico de WhatsApp",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.packDiagnosticReport?.packIssues?.isNotEmpty() == true) {
                        for (packIssue in state.packDiagnosticReport!!.packIssues) {
                            Text(
                                text = "• $packIssue",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (state.stickerIssues.isNotEmpty()) {
                        Text(
                            text = "Los siguientes stickers impiden exportar el paquete a WhatsApp:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.stickerIssues.values.toList(), key = { it.stickerId }) { issue ->
                                val sticker = pack.stickers.find { it.id == issue.stickerId }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (sticker != null) {
                                            val file = remember(sticker.filePath) {
                                                val f = File(sticker.filePath)
                                                if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
                                            }
                                            AsyncImage(
                                                model = file,
                                                contentDescription = issue.emoteName,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = issue.emoteName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = issue.reason,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                        if (sticker != null) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.onOpenDeleteStickerDialog(sticker)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (state.packDiagnosticReport?.packIssues?.isEmpty() == true) {
                        Text(
                            text = "No se detectaron problemas técnicos en los stickers de este paquete.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (state.stickerIssues.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.onDeleteInvalidStickers(pack.identifier) },
                        enabled = !state.isDeletingInvalidStickers,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (state.isDeletingInvalidStickers) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Eliminar stickers con error", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDiagnosticDialog() }) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun PackStickerCell(
    sticker: PackSticker,
    isTrayIcon: Boolean = false,
    issue: StickerIssue? = null,
    onClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onIssueClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val file = remember(sticker.filePath) {
        val f = File(sticker.filePath)
        if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (issue != null) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            }
        ),
        border = if (issue != null) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (issue != null) onIssueClick() else onClick()
            }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(if (file.exists()) file else sticker.filePath)
                        .size(160, 160)
                        .crossfade(true)
                        .build(),
                    contentDescription = sticker.emoteName,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sticker.emoteName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (issue != null) {
                    Text(
                        text = issue.reason,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                } else if (sticker.emojis.isNotEmpty()) {
                    Text(
                        text = sticker.emojis.joinToString(" "),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (issue != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "⚠️ ERROR",
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            } else if (isTrayIcon) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "★ Tray Icon",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar sticker",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

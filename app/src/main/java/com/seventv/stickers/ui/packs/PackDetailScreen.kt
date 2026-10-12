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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.seventv.stickers.ui.theme.ErrorRed
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

                // Top bar with back navigation, rename and add sticker actions
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pack.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText,
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
                                    tint = SevenTvCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${pack.stickers.size}/30 stickers • Por ${pack.publisher}",
                            fontSize = 12.sp,
                            color = OnSurfaceSubtext
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.onOpenAddStickersDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = SevenTvCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                // Diagnostic Warning Banner
                if (state.stickerIssues.isNotEmpty() || (state.packDiagnosticReport != null && !state.packDiagnosticReport!!.isValid)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { viewModel.onOpenDiagnosticDialog() },
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ErrorRed)
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
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.stickerIssues.isNotEmpty())
                                        "⚠️ ${state.stickerIssues.size} sticker(s) con problemas para WhatsApp"
                                    else
                                        "⚠️ Incompatibilidad con WhatsApp",
                                    color = OnSurfaceText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toca para ver los detalles y corregirlos.",
                                    color = OnSurfaceSubtext,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.onOpenDiagnosticDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
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
                            PackStickerCell(
                                sticker = sticker,
                                issue = state.stickerIssues[sticker.id],
                                onDeleteClick = { viewModel.onOpenDeleteStickerDialog(sticker) },
                                onIssueClick = { viewModel.onOpenDiagnosticDialog() }
                            )
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

    // Delete Sticker Confirmation Dialog
    if (state.isDeleteStickerConfirmDialogOpen && state.stickerToDelete != null && pack != null) {
        val sticker = state.stickerToDelete!!
        val willBeBelowMinimum = pack.stickers.size <= 3
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteStickerDialog() },
            title = {
                Text(
                    if (willBeBelowMinimum) "⚠️ Advertencia de WhatsApp" else "Eliminar Sticker",
                    color = OnSurfaceText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (willBeBelowMinimum) {
                    Text(
                        "Al eliminar '${sticker.emoteName}', el pack quedará con ${pack.stickers.size - 1} stickers.\n\nWhatsApp exige un mínimo de 3 stickers por paquete. No podrás añadir o actualizar este paquete en WhatsApp hasta que contenga al menos 3 stickers.\n\n¿Deseas eliminarlo de todas formas?",
                        color = OnSurfaceSubtext
                    )
                } else {
                    Text(
                        "¿Estás seguro de que deseas eliminar el sticker '${sticker.emoteName}' de este paquete?",
                        color = OnSurfaceSubtext
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmDeleteSticker(pack.identifier) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteStickerDialog() }) {
                    Text("Cancelar", color = OnSurfaceSubtext)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Rename Pack Dialog
    if (state.isRenameDialogOpen && state.packToRename != null) {
        val p = state.packToRename!!
        var newName by remember(p.identifier) { mutableStateOf(p.name) }
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
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
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

    // WhatsApp Diagnostic Dialog
    if (state.isDiagnosticDialogOpen && pack != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDiagnosticDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnóstico de WhatsApp",
                        color = OnSurfaceText,
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
                                color = ErrorRed,
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
                            color = OnSurfaceSubtext,
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
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
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
                                                color = OnSurfaceText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = issue.reason,
                                                color = ErrorRed,
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
                                                    tint = ErrorRed,
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
                            color = OnSurfaceText,
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
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                    ) {
                        if (state.isDeletingInvalidStickers) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Eliminar stickers con error", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDiagnosticDialog() }) {
                    Text("Cerrar", color = OnSurfaceSubtext)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun PackStickerCell(
    sticker: PackSticker,
    issue: StickerIssue? = null,
    onDeleteClick: () -> Unit,
    onIssueClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val file = remember(sticker.filePath) {
        val f = File(sticker.filePath)
        if (f.isAbsolute && f.exists()) f else File(context.filesDir, sticker.filePath)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (issue != null) Modifier.clickable { onIssueClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (issue != null) ErrorRed.copy(alpha = 0.12f) else Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (issue != null) BorderStroke(1.5.dp, ErrorRed) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                if (issue != null) {
                    Text(
                        text = issue.reason,
                        color = ErrorRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 10.sp
                    )
                } else if (sticker.emojis.isNotEmpty()) {
                    Text(
                        text = sticker.emojis.joinToString(" "),
                        fontSize = 12.sp
                    )
                }
            }

            if (issue != null) {
                Surface(
                    color = ErrorRed,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "⚠️ ERROR",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar sticker",
                    tint = ErrorRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

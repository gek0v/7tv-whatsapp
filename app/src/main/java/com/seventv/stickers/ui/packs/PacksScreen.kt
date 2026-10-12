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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seventv.stickers.ui.components.PackHeroCard
import com.seventv.stickers.ui.components.PackMenuAction
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksScreen(
    viewModel: PacksViewModel,
    onNavigateToPackDetail: (String) -> Unit = {},
    onCreatePackClick: () -> Unit = {},
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

    val currentPacks = (state.loadState as? PacksLoadState.Success)?.packs.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.isSelectionMode) "${state.selectedPackIds.size} seleccionados" else "Mis Packs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!state.isSelectionMode) {
                            val count = currentPacks.size
                            Text(
                                text = "$count ${if (count == 1) "paquete" else "paquetes"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
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
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { viewModel.onToggleSelectionMode() }) {
                            Text(
                                text = "Cancelar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (currentPacks.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onToggleSelectionMode() }) {
                            Text(
                                text = "Seleccionar",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
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
        floatingActionButton = {
            if (!state.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onCreatePackClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Crear Pack", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp
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
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Eliminar (${state.selectedPackIds.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (state.selectedPackIds.size >= 2) {
                            Button(
                                onClick = { viewModel.onOpenMergeDialog() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    "Combinar (${state.selectedPackIds.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.onStartBatchWhatsAppExport(context) },
                            enabled = state.selectedPackIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "Añadir a WA (${state.selectedPackIds.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val load = state.loadState) {
                is PacksLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is PacksLoadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = load.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is PacksLoadState.Success -> {
                    if (load.packs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No tienes paquetes creados todavía",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Explora emotes de 7TV o importa un canal completo para crear tus primeros stickers de WhatsApp.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = onCreatePackClick,
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Explorar emotes", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = if (state.isSelectionMode) 100.dp else 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(load.packs, key = { it.identifier }) { pack ->
                                if (state.isSelectionMode) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.onTogglePackSelection(pack.identifier) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = pack.identifier in state.selectedPackIds,
                                            onCheckedChange = { viewModel.onTogglePackSelection(pack.identifier) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        PackHeroCard(
                                            pack = pack,
                                            stickers = pack.stickers,
                                            onExportToWhatsApp = { viewModel.onAddPackToWhatsApp(context, pack) },
                                            onPackClick = { viewModel.onTogglePackSelection(pack.identifier) },
                                            onMenuAction = { action ->
                                                when (action) {
                                                    PackMenuAction.RENAME -> viewModel.onOpenRenameDialog(pack)
                                                    PackMenuAction.DUPLICATE -> viewModel.onOpenDuplicateDialog(pack)
                                                    PackMenuAction.DELETE -> viewModel.onOpenDeleteDialog(pack)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    PackHeroCard(
                                        pack = pack,
                                        stickers = pack.stickers,
                                        onExportToWhatsApp = { viewModel.onAddPackToWhatsApp(context, pack) },
                                        onPackClick = { onNavigateToPackDetail(pack.identifier) },
                                        onMenuAction = { action ->
                                            when (action) {
                                                PackMenuAction.RENAME -> viewModel.onOpenRenameDialog(pack)
                                                PackMenuAction.DUPLICATE -> viewModel.onOpenDuplicateDialog(pack)
                                                PackMenuAction.DELETE -> viewModel.onOpenDeleteDialog(pack)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
                    text = "Combinar Paquetes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nombre del nuevo paquete:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = baseName,
                        onValueChange = { baseName = it },
                        singleLine = true,
                        enabled = !state.isMergingPacks,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Combinando y reempaquetando...",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Combinar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!state.isMergingPacks) {
                    TextButton(onClick = { viewModel.onDismissMergeDialog() }) {
                        Text("Cancelar")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Single Delete Confirmation Dialog
    if (state.isDeleteConfirmDialogOpen && state.packToDelete != null) {
        val pack = state.packToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteDialog() },
            title = {
                Text(
                    text = "Eliminar Paquete",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar '${pack.name}'? Se borrarán sus ${pack.stickers.size} stickers del almacenamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmDeletePack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteDialog() }) {
                    Text("Cancelar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Bulk Delete Confirmation Dialog
    if (state.isBulkDeleteConfirmDialogOpen && state.selectedPackIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissBulkDeleteDialog() },
            title = {
                Text(
                    text = "Eliminar Paquetes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar los ${state.selectedPackIds.size} paquetes seleccionados? Se borrarán todos sus stickers del almacenamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmBulkDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Eliminar Todos", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissBulkDeleteDialog() }) {
                    Text("Cancelar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Rename Pack Dialog
    if (state.isRenameDialogOpen && state.packToRename != null) {
        val pack = state.packToRename!!
        var newName by remember(pack.identifier) { mutableStateOf(pack.name) }
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

    // Duplicate Pack Dialog
    if (state.isDuplicateDialogOpen && state.packToDuplicate != null) {
        val pack = state.packToDuplicate!!
        var duplicateName by remember(pack.identifier) { mutableStateOf("${pack.name} (Copia)") }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDuplicateDialog() },
            title = {
                Text(
                    text = "Duplicar Paquete",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce el nombre para el paquete duplicado:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = duplicateName,
                        onValueChange = { duplicateName = it },
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
                        if (duplicateName.isNotBlank()) {
                            viewModel.onConfirmDuplicatePack(duplicateName)
                        }
                    },
                    enabled = duplicateName.isNotBlank()
                ) {
                    Text("Duplicar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDuplicateDialog() }) {
                    Text("Cancelar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    text = "WhatsApp Estándar (com.whatsapp) no parece estar instalado en tu dispositivo. Instálalo desde Google Play Store para poder añadir tus stickers.",
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
}

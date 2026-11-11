package com.seventv.stickers.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.seventv.stickers.SevenTvApplication
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.components.AddToExistingPackDialog
import com.seventv.stickers.ui.components.DockedDraftPill
import com.seventv.stickers.ui.components.DraftBottomSheet
import com.seventv.stickers.ui.components.EmoteCard
import com.seventv.stickers.ui.components.EmoteFullscreenViewer
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var draftPackName by remember { mutableStateOf("7TV Canal Pack") }
    var showDraftSheet by remember { mutableStateOf(false) }
    var previewEmote by remember { mutableStateOf<SevenTvEmote?>(null) }
    var isSharingSticker by remember { mutableStateOf(false) }

    LaunchedEffect(state.importState) {
        val import = state.importState
        if (import is ChannelImportState.Success) {
            snackbarHostState.showSnackbar("¡${import.createdPacks.size} paquete(s) importados con éxito!")
        } else if (import is ChannelImportState.Error) {
            snackbarHostState.showSnackbar(import.message)
        }
    }

    LaunchedEffect(state.addToPackSuccessMessage, state.addToPackErrorMessage) {
        state.addToPackSuccessMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Ver packs",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToPacks()
            }
            viewModel.onClearAddToPackMessages()
        }
        state.addToPackErrorMessage?.let { err ->
            snackbarHostState.showSnackbar(
                message = err,
                duration = SnackbarDuration.Short
            )
            viewModel.onClearAddToPackMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Top Channel Search using glassmorphism and cyan border
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = com.seventv.stickers.ui.theme.GlassSurfaceDark,
                    border = BorderStroke(1.dp, com.seventv.stickers.ui.theme.GlassBorderCyan),
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = {
                            Text(
                                text = "Buscar streamer o emote set (ej: ibai)...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar canal",
                                tint = com.seventv.stickers.ui.theme.ElectricCyan
                            )
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = com.seventv.stickers.ui.theme.NeonFuchsia
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.onSearchStreamer() }),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = com.seventv.stickers.ui.theme.ElectricCyan
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.onSearchStreamer() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.seventv.stickers.ui.theme.ElectricCyan,
                        contentColor = Color(0xFF0F081D)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cargar Emotes de Canal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dynamic Load State Content
                when (val load = state.loadState) {
                    is ChannelLoadState.Idle -> {
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
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Busca un streamer o emote set de 7TV\npara ver e importar sus emotes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is ChannelLoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Cargando canal '${load.query}'...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is ChannelLoadState.Error -> {
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
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = load.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.onSearchStreamer() },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    is ChannelLoadState.Success -> {
                        ChannelContent(
                            streamerName = load.streamerName,
                            avatarUrl = load.avatarUrl,
                            availableSets = load.availableSets,
                            currentSetId = load.currentSetId,
                            currentSetName = load.emoteSet.name,
                            isLoadingSet = load.isLoadingSet,
                            totalEmotes = load.allEmotes.size,
                            filteredEmotes = viewModel.getFilteredEmotes(),
                            selectedEmoteIds = state.selectedEmoteIds,
                            activeFilter = state.activeFilter,
                            activeProviderFilter = state.activeProviderFilter,
                            emotesByProvider = load.emotesByProvider,
                            draftEmotes = state.draftEmotes,
                            onFilterSelected = viewModel::onFilterSelected,
                            onProviderFilterSelected = viewModel::onProviderFilterSelected,
                            onSelectEmoteSet = viewModel::onSelectEmoteSet,
                            onToggleSelectEmote = viewModel::onToggleSelectEmote,
                            onToggleDraftEmote = viewModel::onToggleDraftEmote,
                            onSelectAll = viewModel::onSelectAllEmotes,
                            onDeselectAll = viewModel::onDeselectAllEmotes,
                            onImportFullChannel = { viewModel.onStartChannelImport(useOnlySelected = false) },
                            onImportSelected = { viewModel.onStartChannelImport(useOnlySelected = true) },
                            onPreviewEmote = { previewEmote = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Continuous Docked Draft Pill
            DockedDraftPill(
                draftEmotes = state.draftEmotes,
                onClickReview = { showDraftSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

            // Draft Bottom Sheet for channel emotes
            if (showDraftSheet) {
                DraftBottomSheet(
                    draftEmotes = state.draftEmotes,
                    packName = draftPackName,
                    onPackNameChanged = { draftPackName = it },
                    onRemoveEmote = { viewModel.onToggleDraftEmote(it) },
                    onCreatePack = {
                        viewModel.onCreatePackFromDraft(draftPackName)
                        showDraftSheet = false
                    },
                    onAddToExistingPack = {
                        showDraftSheet = false
                        viewModel.onOpenAddToExistingPackDialog(state.draftEmotes)
                    },
                    onDismiss = { showDraftSheet = false }
                )
            }

            // Add to Existing Pack Dialog
            if (state.isAddToExistingPackDialogOpen) {
                AddToExistingPackDialog(
                    packs = state.existingPacks,
                    emotesToAdd = state.emotesToAddToPack,
                    onSelectPack = { pack ->
                        viewModel.onAddEmotesToPack(pack.identifier)
                    },
                    onDismiss = viewModel::onDismissAddToExistingPackDialog
                )
            }

            // Adding to Pack Progress Overlay
            if (state.isAddingToPack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Añadiendo stickers al paquete...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Convirtiendo y optimizando WebP para WhatsApp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Fullscreen Emote Viewer
            if (previewEmote != null) {
                val emote = previewEmote!!
                val isInDraft = state.draftEmotes.any { it.id == emote.id }
                EmoteFullscreenViewer(
                    name = emote.name,
                    isAnimated = emote.isAnimated,
                    model = emote.bestAssetUrl,
                    onDismiss = { previewEmote = null },
                    isInDraft = isInDraft,
                    onToggleDraft = { viewModel.onToggleDraftEmote(emote) },
                    onShareSticker = {
                        coroutineScope.launch {
                            isSharingSticker = true
                            try {
                                val shareManager = (context.applicationContext as SevenTvApplication).container.stickerShareManager
                                when (val result = shareManager.prepareEmoteForShare(emote)) {
                                    is StickerResult.Success -> {
                                        shareManager.launchShare(context, result.data)
                                    }
                                    is StickerResult.Failure -> {
                                        snackbarHostState.showSnackbar("Error al preparar sticker para compartir")
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Error al compartir: ${e.message}")
                            } finally {
                                isSharingSticker = false
                            }
                        }
                    },
                    isSharingSticker = isSharingSticker
                )
            }
        }
    }

    // Split Confirmation Modal with Vaporwave & Glassmorphism styling
    val importState = state.importState
    if (importState is ChannelImportState.Confirming) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissImportConfirmation,
            containerColor = Color(0xF2160D2E),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            modifier = Modifier.border(
                1.5.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        com.seventv.stickers.ui.theme.ElectricCyan,
                        com.seventv.stickers.ui.theme.NeonFuchsia
                    )
                ),
                androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✦ División Inteligente",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = com.seventv.stickers.ui.theme.ElectricCyan
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Se crearán ${importState.splitPlans.size} paquete(s) de stickers divididos inteligentemente (3 a 30 por pack):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(importState.splitPlans) { plan ->
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                color = Color(0x351F1435),
                                border = BorderStroke(1.dp, Color(0x4001CDFE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${plan.packName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        color = if (plan.isAnimated) Color(0x30FF71CE) else Color(0x3001CDFE),
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (plan.isAnimated) com.seventv.stickers.ui.theme.NeonFuchsia.copy(alpha = 0.6f) else com.seventv.stickers.ui.theme.ElectricCyan.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Text(
                                            text = "${plan.emotes.size} (${if (plan.isAnimated) "Animado" else "Estático"})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = if (plan.isAnimated) com.seventv.stickers.ui.theme.NeonFuchsia else com.seventv.stickers.ui.theme.ElectricCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmImport(importState.splitPlans) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.seventv.stickers.ui.theme.WhatsAppGlow,
                        contentColor = Color(0xFF0F081D)
                    )
                ) {
                    Text(
                        text = "Crear Paquetes",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissImportConfirmation) {
                    Text(
                        text = "Cancelar",
                        color = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelContent(
    streamerName: String,
    avatarUrl: String?,
    availableSets: List<ChannelEmoteSetSummary>,
    currentSetId: String,
    currentSetName: String,
    isLoadingSet: Boolean,
    totalEmotes: Int,
    filteredEmotes: List<SevenTvEmote>,
    selectedEmoteIds: Set<String>,
    activeFilter: ChannelFilter,
    activeProviderFilter: ChannelProviderFilter,
    emotesByProvider: Map<com.seventv.stickers.core.network.model.EmoteProvider, List<SevenTvEmote>>,
    draftEmotes: List<SevenTvEmote>,
    onFilterSelected: (ChannelFilter) -> Unit,
    onProviderFilterSelected: (ChannelProviderFilter) -> Unit,
    onSelectEmoteSet: (String) -> Unit,
    onToggleSelectEmote: (String) -> Unit,
    onToggleDraftEmote: (SevenTvEmote) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImportFullChannel: () -> Unit,
    onImportSelected: () -> Unit,
    onPreviewEmote: (SevenTvEmote) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Channel Hero Header Card with retro halo and glassmorphic finish
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = com.seventv.stickers.ui.theme.GlassSurfaceCard
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(
                1.5.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        com.seventv.stickers.ui.theme.ElectricCyan,
                        com.seventv.stickers.ui.theme.RetroViolet,
                        com.seventv.stickers.ui.theme.NeonFuchsia
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = Color(0x3501CDFE),
                    border = BorderStroke(
                        2.5.dp,
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(
                                com.seventv.stickers.ui.theme.ElectricCyan,
                                com.seventv.stickers.ui.theme.NeonFuchsia,
                                com.seventv.stickers.ui.theme.SunsetYellow,
                                com.seventv.stickers.ui.theme.ElectricCyan
                            )
                        )
                    )
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = streamerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = streamerName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = com.seventv.stickers.ui.theme.ElectricCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = streamerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val providerSummary = remember(emotesByProvider) {
                        emotesByProvider.filterValues { it.isNotEmpty() }
                            .map { (p, list) -> "${list.size} ${p.displayName}" }
                            .joinToString(" • ")
                    }
                    if (providerSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = providerSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = Color(0x3501CDFE),
                        border = BorderStroke(
                            1.dp,
                            com.seventv.stickers.ui.theme.ElectricCyan.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = if (availableSets.size > 1) {
                                "${availableSets.size} paquetes 7TV • $totalEmotes emotes totales"
                            } else {
                                "$totalEmotes emotes disponibles"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = com.seventv.stickers.ui.theme.ElectricCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Available Emote Sets (Packs) Selector
        if (availableSets.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Paquetes del canal (${availableSets.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(availableSets, key = { it.id }) { packSummary ->
                    val isSelected = packSummary.id == currentSetId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectEmoteSet(packSummary.id) },
                        label = {
                            Text(
                                text = "${packSummary.name} (${packSummary.emoteCount})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (packSummary.isActive) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Activo en canal",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        shape = MaterialTheme.shapes.medium,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Provider Filter Chips (Todos, 7TV, BetterTTV, FrankerFaceZ, Twitch)
        if (emotesByProvider.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(ChannelProviderFilter.entries) { filter ->
                    val isSelected = activeProviderFilter == filter
                    val count = if (filter == ChannelProviderFilter.ALL) {
                        totalEmotes
                    } else {
                        emotesByProvider[filter.provider]?.size ?: 0
                    }
                    val badgeColor = filter.provider?.let { com.seventv.stickers.ui.components.EmoteCardDefaults.providerColor(it) }
                        ?: com.seventv.stickers.ui.theme.ElectricCyan

                    FilterChip(
                        selected = isSelected,
                        onClick = { onProviderFilterSelected(filter) },
                        label = {
                            Text(
                                text = "${filter.displayName} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (isSelected) badgeColor else Color(0x35FFFFFF)),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = badgeColor.copy(alpha = 0.25f),
                            selectedLabelColor = badgeColor,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        // Primary Button "Importar Canal Completo" / "Importar Pack"
        val importButtonLabel = if (availableSets.size > 1) {
            "Importar pack '$currentSetName' a WhatsApp ($totalEmotes)"
        } else {
            "Importar Canal Completo a WhatsApp ($totalEmotes)"
        }

        Button(
            onClick = onImportFullChannel,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = importButtonLabel,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Tabs using M3 PrimaryTabRow
        val tabs = listOf(
            ChannelFilter.ALL to "Todos",
            ChannelFilter.ANIMATED to "Animados",
            ChannelFilter.STATIC to "Estáticos"
        )
        val selectedTabIndex = tabs.indexOfFirst { it.first == activeFilter }.coerceAtLeast(0)

        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, (filter, label) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onFilterSelected(filter) },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTabIndex == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }
        }

        if (isLoadingSet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cargando paquete '$currentSetName'...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Selection Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedEmoteIds.isEmpty()) "Mostrando ${filteredEmotes.size} emotes"
                    else "${selectedEmoteIds.size} seleccionados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = onSelectAll) {
                        Text(
                            text = "Todos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (selectedEmoteIds.isNotEmpty()) {
                        TextButton(onClick = onDeselectAll) {
                            Text(
                                text = "Limpiar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onImportSelected) {
                            Text(
                                text = "Importar (${selectedEmoteIds.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Emotes Grid using EmoteCard
            if (filteredEmotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay emotes que coincidan con el filtro seleccionado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = if (draftEmotes.isNotEmpty()) 100.dp else 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredEmotes, key = { "${it.provider.name}:${it.id}" }) { emote ->
                        val isSelected = selectedEmoteIds.contains(emote.id)
                        val isInDraft = draftEmotes.any { it.id == emote.id }
                        EmoteCard(
                            emote = emote,
                            isInDraft = isInDraft,
                            onToggleDraft = { onToggleDraftEmote(emote) },
                            modifier = if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                            },
                            onClick = { onToggleSelectEmote(emote.id) },
                            onPreviewClick = { onPreviewEmote(emote) }
                        )
                    }
                }
            }
        }
    }
}

package com.seventv.stickers.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.SevenTvApplication
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.components.AddToExistingPackDialog
import com.seventv.stickers.ui.components.DockedDraftPill
import com.seventv.stickers.ui.components.DraftBottomSheet
import com.seventv.stickers.ui.components.EmoteCard
import com.seventv.stickers.ui.components.EmoteFullscreenViewer
import com.seventv.stickers.ui.components.SevenTvSearchBar
import com.seventv.stickers.ui.components.SevenTvSearchBarDefaults
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.statusBars

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var packName by remember { mutableStateOf("7TV Pack") }
    var showDraftSheet by remember { mutableStateOf(false) }
    var previewEmote by remember { mutableStateOf<SevenTvEmote?>(null) }
    var isSharingSticker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState.searchResults is SearchResultsState.Idle) {
            viewModel.loadCategoryEmotes()
        }
    }

    LaunchedEffect(uiState.packCreationState) {
        when (val state = uiState.packCreationState) {
            is PackCreationState.Success -> {
                val createdName = state.createdPacks.firstOrNull()?.name ?: "Pack"
                val result = snackbarHostState.showSnackbar(
                    message = "¡Paquete \"$createdName\" creado exitosamente!",
                    actionLabel = "Ver packs",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToPacks()
                }
                viewModel.onDismissPackCreationState()
            }
            is PackCreationState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                viewModel.onDismissPackCreationState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(uiState.addToPackSuccessMessage, uiState.addToPackErrorMessage) {
        uiState.addToPackSuccessMessage?.let { msg ->
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
        uiState.addToPackErrorMessage?.let { err ->
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

                // M3 Expressive SearchBar with filter chips
                val selectedFilterText = when (uiState.activeFilter) {
                    EmoteFilter.ALL -> "Todos"
                    EmoteFilter.ANIMATED -> "Animados"
                    EmoteFilter.STATIC -> "Estáticos"
                }

                SevenTvSearchBar(
                    query = uiState.query,
                    onQueryChanged = viewModel::onQueryChanged,
                    selectedFilter = selectedFilterText,
                    onFilterSelected = { filter ->
                        when (filter) {
                            "Todos" -> viewModel.onFilterSelected(EmoteFilter.ALL)
                            "Animados" -> viewModel.onFilterSelected(EmoteFilter.ANIMATED)
                            "Estáticos" -> viewModel.onFilterSelected(EmoteFilter.STATIC)
                            else -> viewModel.onFilterSelected(EmoteFilter.ALL)
                        }
                    },
                    onPasteUrl = {
                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                        if (clip.isNotEmpty()) {
                            val directId = SevenTvSearchBarDefaults.extractEmoteIdFromUrl(clip)
                            viewModel.onQueryChanged(directId ?: clip)
                        }
                    },
                    filters = listOf("Todos", "Animados", "Estáticos")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category selector chips (Trending & Top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedCategory == ExploreCategory.TRENDING,
                        onClick = { viewModel.onCategorySelected(ExploreCategory.TRENDING) },
                        label = { Text("🔥 Tendencias") }
                    )
                    FilterChip(
                        selected = uiState.selectedCategory == ExploreCategory.TOP,
                        onClick = { viewModel.onCategorySelected(ExploreCategory.TOP) },
                        label = { Text("⭐ Populares") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Results / Content View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (val results = uiState.searchResults) {
                        is SearchResultsState.Idle -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Escribe para buscar emotes globales en 7TV",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is SearchResultsState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        is SearchResultsState.Empty -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No se encontraron emotes para \"${results.query}\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is SearchResultsState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = results.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is SearchResultsState.Success -> {
                            val emotes = uiState.emotes
                            if (emotes.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No hay emotes que coincidan con el filtro seleccionado",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 100.dp),
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = if (uiState.draftEmotes.isNotEmpty()) 100.dp else 16.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = emotes,
                                        key = { it.id },
                                        contentType = { "emote" }
                                    ) { emote ->
                                        val isInDraft = uiState.draftEmotes.any { it.id == emote.id }
                                        EmoteCard(
                                            emote = emote,
                                            isInDraft = isInDraft,
                                            onToggleDraft = { viewModel.onToggleDraftEmote(emote) },
                                            onClick = {
                                                viewModel.onOpenAddToExistingPackDialog(listOf(emote))
                                            },
                                            onPreviewClick = {
                                                previewEmote = emote
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Continuous docked draft pill
            DockedDraftPill(
                draftEmotes = uiState.draftEmotes,
                onClickReview = { showDraftSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

            // Pack Review Bottom Sheet
            if (showDraftSheet) {
                DraftBottomSheet(
                    draftEmotes = uiState.draftEmotes,
                    packName = packName,
                    onPackNameChanged = { packName = it },
                    onRemoveEmote = { viewModel.onToggleDraftEmote(it) },
                    onCreatePack = {
                        viewModel.onCreatePackFromDraft(packName)
                        showDraftSheet = false
                    },
                    onAddToExistingPack = {
                        showDraftSheet = false
                        viewModel.onOpenAddToExistingPackDialog(uiState.draftEmotes)
                    },
                    onDismiss = { showDraftSheet = false }
                )
            }

            // Add to Existing Pack Dialog
            if (uiState.isAddToExistingPackDialogOpen) {
                AddToExistingPackDialog(
                    packs = uiState.existingPacks,
                    emotesToAdd = uiState.emotesToAddToPack,
                    onSelectPack = { pack ->
                        viewModel.onAddEmotesToPack(pack.identifier)
                    },
                    onDismiss = viewModel::onDismissAddToExistingPackDialog
                )
            }

            // Creating Progress Overlay
            if (uiState.packCreationState is PackCreationState.Creating) {
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
                                    text = "Creando paquete de stickers...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Convirtiendo emotes a WebP para WhatsApp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Adding to Pack Progress Overlay
            if (uiState.isAddingToPack) {
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
                val isInDraft = uiState.draftEmotes.any { it.id == emote.id }
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
                                    is StickerResult.Success<android.net.Uri> -> {
                                        shareManager.launchShare(context, result.data)
                                    }
                                    is StickerResult.Failure -> {
                                        snackbarHostState.showSnackbar("Error al preparar sticker para WhatsApp")
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
}

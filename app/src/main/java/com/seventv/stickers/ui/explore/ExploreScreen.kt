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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.platform.LocalHapticFeedback
import com.seventv.stickers.ui.components.CoilSetup
import com.seventv.stickers.ui.components.EmotePeekAction
import com.seventv.stickers.ui.components.EmotePeekHost
import com.seventv.stickers.ui.components.EmotePeekPayload
import com.seventv.stickers.ui.components.LocalEmotePeekController
import com.seventv.stickers.ui.components.rememberEmotePeekState
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.NeonFuchsia
import com.seventv.stickers.ui.theme.WhatsAppGlow
import com.seventv.stickers.ui.components.AddToExistingPackDialog
import com.seventv.stickers.ui.components.DockedDraftPill
import com.seventv.stickers.ui.components.DraftBottomSheet
import com.seventv.stickers.ui.components.EmoteCard
import com.seventv.stickers.ui.components.EmoteFullscreenViewer
import com.seventv.stickers.ui.components.SevenTvSearchBar
import com.seventv.stickers.core.updater.AppUpdateManager
import com.seventv.stickers.ui.components.SevenTvSearchBarDefaults
import com.seventv.stickers.ui.components.UpdateNotificationBanner
import kotlinx.coroutines.launch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.seventv.stickers.ui.channel.ChannelProviderFilter
import com.seventv.stickers.ui.components.EmoteCardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.statusBars

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigateToPacks: () -> Unit = {},
    updateManager: AppUpdateManager? = null,
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
    val externalPeekController = LocalEmotePeekController.current
    val localPeekState = rememberEmotePeekState()
    val peekState = externalPeekController ?: localPeekState
    val haptic = LocalHapticFeedback.current

    val shareEmote: (SevenTvEmote) -> Unit = { emote ->
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
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalCount = gridState.layoutInfo.totalItemsCount
            totalCount > 0 && lastVisibleIndex >= totalCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMorePages && !uiState.isLoadingMore) {
            viewModel.loadNextPage()
        }
    }

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

    val screenBody: @Composable () -> Unit = {
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

                if (updateManager != null) {
                    UpdateNotificationBanner(
                        updateManager = updateManager,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

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

                // Category selector chips (Trending & Top) - shown when browsing without text query
                if (uiState.query.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isTrending = uiState.selectedCategory == ExploreCategory.TRENDING
                        FilterChip(
                            selected = isTrending,
                            onClick = { viewModel.onCategorySelected(ExploreCategory.TRENDING) },
                            label = { Text("🔥 Tendencias") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isTrending) com.seventv.stickers.ui.theme.NeonFuchsia else androidx.compose.ui.graphics.Color(0x35FFFFFF)
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = androidx.compose.ui.graphics.Color(0x35FF71CE),
                                selectedLabelColor = com.seventv.stickers.ui.theme.NeonFuchsia,
                                containerColor = com.seventv.stickers.ui.theme.GlassSurfaceDark,
                                labelColor = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                            )
                        )
                        val isTop = uiState.selectedCategory == ExploreCategory.TOP
                        FilterChip(
                            selected = isTop,
                            onClick = { viewModel.onCategorySelected(ExploreCategory.TOP) },
                            label = { Text("⭐ Populares") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isTop) com.seventv.stickers.ui.theme.SunsetYellow else androidx.compose.ui.graphics.Color(0x35FFFFFF)
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = androidx.compose.ui.graphics.Color(0x35FFFB96),
                                selectedLabelColor = com.seventv.stickers.ui.theme.SunsetYellow,
                                containerColor = com.seventv.stickers.ui.theme.GlassSurfaceDark,
                                labelColor = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Provider filter chips (Todos, 7TV, BetterTTV, FrankerFaceZ, Twitch)
                val successResults = uiState.searchResults as? SearchResultsState.Success
                if (successResults != null && (successResults.emotesByProvider.size > 1 || uiState.activeProviderFilter != ChannelProviderFilter.ALL)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(ChannelProviderFilter.entries) { filter ->
                            val isSelected = uiState.activeProviderFilter == filter
                            val badgeColor = filter.provider?.let { EmoteCardDefaults.providerColor(it) }
                                ?: com.seventv.stickers.ui.theme.ElectricCyan
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onProviderFilterSelected(filter) },
                                label = { Text(filter.displayName) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) badgeColor else Color(0x35FFFFFF)
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = badgeColor.copy(alpha = 0.25f),
                                    selectedLabelColor = badgeColor,
                                    containerColor = com.seventv.stickers.ui.theme.GlassSurfaceDark,
                                    labelColor = com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

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
                                    state = gridState,
                                    userScrollEnabled = !peekState.isActive,
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
                                        key = { "${it.provider.name}:${it.id}" },
                                        contentType = { "emote" }
                                    ) { emote ->
                                        val isInDraft = emote.id in uiState.draftEmoteIds
                                        EmoteCard(
                                            emote = emote,
                                            isInDraft = isInDraft,
                                            onToggleDraft = { viewModel.onToggleDraftEmote(emote) },
                                            onClick = {
                                                viewModel.onOpenAddToExistingPackDialog(listOf(emote))
                                            },
                                            onPreviewClick = {
                                                previewEmote = emote
                                            },
                                            onPeekStart = { bounds ->
                                                peekState.show(
                                                    EmotePeekPayload(
                                                        id = emote.id,
                                                        name = emote.name,
                                                        isAnimated = emote.isAnimated,
                                                        imageModel = emote.bestAssetUrl.ifBlank { emote.previewUrl },
                                                        placeholderCacheKey = CoilSetup.emotePreviewCacheKey(emote.id),
                                                        sourceLabel = "7TV • Explorar",
                                                        sourceBoundsInRoot = bounds,
                                                        actions = listOf(
                                                            EmotePeekAction(
                                                                id = "draft",
                                                                label = if (isInDraft) "En borrador" else "Borrador",
                                                                icon = if (isInDraft) Icons.Default.Check else Icons.Default.Add,
                                                                accentColor = WhatsAppGlow,
                                                                isActive = isInDraft,
                                                                onClick = { viewModel.onToggleDraftEmote(emote) }
                                                            ),
                                                            EmotePeekAction(
                                                                id = "inspect",
                                                                label = "Inspeccionar",
                                                                icon = Icons.Default.ZoomIn,
                                                                accentColor = ElectricCyan,
                                                                onClick = { previewEmote = emote }
                                                            ),
                                                            EmotePeekAction(
                                                                id = "share",
                                                                label = "Compartir",
                                                                icon = Icons.Default.Share,
                                                                accentColor = NeonFuchsia,
                                                                onClick = { shareEmote(emote) }
                                                            )
                                                        )
                                                    )
                                                )
                                            },
                                            onPeekMove = { pos -> peekState.updatePointer(pos) },
                                            onPeekRelease = { peekState.onRelease(haptic) }
                                        )
                                    }

                                    if (uiState.isLoadingMore) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(32.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 3.dp
                                                )
                                            }
                                        }
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
                val isInDraft = emote.id in uiState.draftEmoteIds
                EmoteFullscreenViewer(
                    name = emote.name,
                    isAnimated = emote.isAnimated,
                    model = emote.bestAssetUrl,
                    onDismiss = { previewEmote = null },
                    isInDraft = isInDraft,
                    onToggleDraft = { viewModel.onToggleDraftEmote(emote) },
                    onShareSticker = { shareEmote(emote) },
                    isSharingSticker = isSharingSticker
                )
            }
        }
    }
    }

    if (externalPeekController == null) {
        EmotePeekHost(state = localPeekState) {
            screenBody()
        }
    } else {
        screenBody()
    }
}
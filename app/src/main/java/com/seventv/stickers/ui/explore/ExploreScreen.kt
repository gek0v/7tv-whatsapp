package com.seventv.stickers.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.ui.components.DockedDraftPill
import com.seventv.stickers.ui.components.DraftBottomSheet
import com.seventv.stickers.ui.components.EmoteCard
import com.seventv.stickers.ui.components.SevenTvSearchBar
import com.seventv.stickers.ui.components.SevenTvSearchBarDefaults

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var packName by remember { mutableStateOf("7TV Pack") }
    var showDraftSheet by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                                    items(emotes, key = { it.id }) { emote ->
                                        val isInDraft = uiState.draftEmotes.any { it.id == emote.id }
                                        EmoteCard(
                                            emote = emote,
                                            isInDraft = isInDraft,
                                            onToggleDraft = { viewModel.onToggleDraftEmote(emote) }
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
                    onDismiss = { showDraftSheet = false }
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
        }
    }
}

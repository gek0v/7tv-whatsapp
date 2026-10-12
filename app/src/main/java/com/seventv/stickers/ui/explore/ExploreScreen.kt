package com.seventv.stickers.ui.explore

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.ui.components.ActiveDraftBanner
import com.seventv.stickers.ui.components.EmoteCard
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.packCreationState) {
        when (val state = uiState.packCreationState) {
            is PackCreationState.Success -> {
                val packName = state.createdPacks.firstOrNull()?.name ?: "Pack"
                val result = snackbarHostState.showSnackbar(
                    message = "¡Paquete \"$packName\" creado exitosamente!",
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
        containerColor = DarkBackground,
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
                // Search Bar
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = {
                        Text(
                            text = "Buscar emotes en 7TV...",
                            color = OnSurfaceSubtext
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = SevenTvCyan
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar búsqueda",
                                    tint = OnSurfaceSubtext
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = SevenTvCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = OnSurfaceText,
                        unfocusedTextColor = OnSurfaceText,
                        cursorColor = SevenTvCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.activeFilter == EmoteFilter.ALL,
                        onClick = { viewModel.onFilterSelected(EmoteFilter.ALL) },
                        label = { Text("Todos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = OnSurfaceSubtext
                        )
                    )
                    FilterChip(
                        selected = uiState.activeFilter == EmoteFilter.ANIMATED,
                        onClick = { viewModel.onFilterSelected(EmoteFilter.ANIMATED) },
                        label = { Text("Animados") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = OnSurfaceSubtext
                        )
                    )
                    FilterChip(
                        selected = uiState.activeFilter == EmoteFilter.STATIC,
                        onClick = { viewModel.onFilterSelected(EmoteFilter.STATIC) },
                        label = { Text("Estáticos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SevenTvCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = OnSurfaceSubtext
                        )
                    )
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
                                    tint = OnSurfaceSubtext,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Escribe para buscar emotes globales en 7TV",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurfaceSubtext,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is SearchResultsState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = SevenTvCyan)
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
                                    tint = OnSurfaceSubtext,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No se encontraron emotes para \"${results.query}\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurfaceSubtext,
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
                                    tint = ErrorRed,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = results.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ErrorRed,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is SearchResultsState.Success -> {
                            val filtered = uiState.filteredEmotes
                            if (filtered.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay emotes que coincidan con el filtro seleccionado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurfaceSubtext
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = if (uiState.draftEmotes.isNotEmpty()) 100.dp else 16.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(filtered, key = { index, emote -> "${emote.id}_$index" }) { _, emote ->
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

            // Floating Active Draft Banner
            if (uiState.draftEmotes.isNotEmpty()) {
                ActiveDraftBanner(
                    draftEmotes = uiState.draftEmotes,
                    onSavePackClick = viewModel::onOpenSavePackDialog,
                    onClearClick = viewModel::onClearDraft,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            // Save Pack Dialog
            if (uiState.isSavePackDialogOpen) {
                AlertDialog(
                    onDismissRequest = viewModel::onDismissSavePackDialog,
                    containerColor = DarkSurface,
                    title = {
                        Text(
                            text = "Guardar Pack de Stickers",
                            color = OnSurfaceText,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Asigna un nombre para tu nuevo pack de stickers (${uiState.draftEmotes.size}/30 stickers):",
                                color = OnSurfaceSubtext,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.draftPackName,
                                onValueChange = viewModel::onDraftPackNameChanged,
                                singleLine = true,
                                label = { Text("Nombre del paquete") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SevenTvCyan,
                                    unfocusedBorderColor = DarkSurfaceVariant,
                                    focusedTextColor = OnSurfaceText,
                                    unfocusedTextColor = OnSurfaceText,
                                    cursorColor = SevenTvCyan
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmSavePack() },
                            enabled = uiState.draftEmotes.size >= ExploreViewModel.MIN_DRAFT_STICKERS,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SevenTvCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Crear")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::onDismissSavePackDialog,
                            colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceSubtext)
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Creating Progress Modal
            if (uiState.packCreationState is PackCreationState.Creating) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = SevenTvCyan)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Creando paquete de stickers...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurfaceText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Convirtiendo emotes a WebP para WhatsApp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceSubtext
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

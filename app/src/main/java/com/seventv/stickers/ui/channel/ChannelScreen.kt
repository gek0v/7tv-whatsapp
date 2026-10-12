package com.seventv.stickers.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.components.DockedDraftPill
import com.seventv.stickers.ui.components.DraftBottomSheet
import com.seventv.stickers.ui.components.EmoteCard

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var draftPackName by remember { mutableStateOf("7TV Canal Pack") }
    var showDraftSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.importState) {
        val import = state.importState
        if (import is ChannelImportState.Success) {
            snackbarHostState.showSnackbar("¡${import.createdPacks.size} paquete(s) importados con éxito!")
        } else if (import is ChannelImportState.Error) {
            snackbarHostState.showSnackbar(import.message)
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

                // Top Channel Search using surfaceContainerLow and rounded pill shape
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = {
                            Text(
                                text = "Buscar streamer o emote set (ej: ibai)...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar canal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.onSearchStreamer() }),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.onSearchStreamer() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                        style = MaterialTheme.typography.labelLarge
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
                            totalEmotes = load.allEmotes.size,
                            filteredEmotes = viewModel.getFilteredEmotes(),
                            selectedEmoteIds = state.selectedEmoteIds,
                            activeFilter = state.activeFilter,
                            draftEmotes = state.draftEmotes,
                            onFilterSelected = viewModel::onFilterSelected,
                            onToggleSelectEmote = viewModel::onToggleSelectEmote,
                            onToggleDraftEmote = viewModel::onToggleDraftEmote,
                            onSelectAll = viewModel::onSelectAllEmotes,
                            onDeselectAll = viewModel::onDeselectAllEmotes,
                            onImportFullChannel = { viewModel.onStartChannelImport(useOnlySelected = false) },
                            onImportSelected = { viewModel.onStartChannelImport(useOnlySelected = true) },
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
                    onDismiss = { showDraftSheet = false }
                )
            }
        }
    }

    // Split Confirmation Modal with M3 surfaceContainerHigh
    val importState = state.importState
    if (importState is ChannelImportState.Confirming) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissImportConfirmation,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Importar a WhatsApp",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Se crearán ${importState.splitPlans.size} paquete(s) de stickers divididos inteligentemente (3 a 30 por pack):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(importState.splitPlans) { plan ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${plan.packName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${plan.emotes.size} stickers (${if (plan.isAnimated) "Animado" else "Estático"})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmImport(importState.splitPlans) },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text("Crear Paquetes")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissImportConfirmation) {
                    Text(
                        text = "Cancelar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    totalEmotes: Int,
    filteredEmotes: List<SevenTvEmote>,
    selectedEmoteIds: Set<String>,
    activeFilter: ChannelFilter,
    draftEmotes: List<SevenTvEmote>,
    onFilterSelected: (ChannelFilter) -> Unit,
    onToggleSelectEmote: (String) -> Unit,
    onToggleDraftEmote: (SevenTvEmote) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImportFullChannel: () -> Unit,
    onImportSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Channel Hero Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = streamerName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = streamerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "$totalEmotes emotes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Primary Button "Importar Canal Completo" (using WhatsApp green tertiary)
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
                text = "Importar Canal Completo a WhatsApp ($totalEmotes)",
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
                items(filteredEmotes, key = { it.id }) { emote ->
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
                        onClick = { onToggleSelectEmote(emote.id) }
                    )
                }
            }
        }
    }
}

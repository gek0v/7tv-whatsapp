package com.seventv.stickers.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan
import com.seventv.stickers.ui.theme.WhatsAppGreen

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onNavigateToPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.importState) {
        val import = state.importState
        if (import is ChannelImportState.Success) {
            snackbarHostState.showSnackbar("¡${import.createdPacks.size} paquete(s) importados con éxito!")
        } else if (import is ChannelImportState.Error) {
            snackbarHostState.showSnackbar(import.message)
        }
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

            // Search Streamer / Channel
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar streamer o emote set (ej: ibai)...", color = OnSurfaceSubtext) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = SevenTvCyan) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = OnSurfaceSubtext)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SevenTvCyan,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = OnSurfaceText,
                    unfocusedTextColor = OnSurfaceText
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.onSearchStreamer() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SevenTvCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cargar Emotes de Canal", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Content
            when (val load = state.loadState) {
                is ChannelLoadState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Busca un streamer o emote set de 7TV\npara ver e importar sus emotes",
                            color = OnSurfaceSubtext,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
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
                            CircularProgressIndicator(color = SevenTvCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargando canal '${load.query}'...", color = OnSurfaceSubtext)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(load.message, color = ErrorRed, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.onSearchStreamer() },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Text("Reintentar", color = OnSurfaceText)
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
                        onFilterSelected = { viewModel.onFilterSelected(it) },
                        onToggleSelectEmote = { viewModel.onToggleSelectEmote(it) },
                        onSelectAll = { viewModel.onSelectAllEmotes() },
                        onDeselectAll = { viewModel.onDeselectAllEmotes() },
                        onImportFullChannel = { viewModel.onStartChannelImport(useOnlySelected = false) },
                        onImportSelected = { viewModel.onStartChannelImport(useOnlySelected = true) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Split confirmation dialog
    val importState = state.importState
    if (importState is ChannelImportState.Confirming) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissImportConfirmation() },
            title = { Text("Importar a WhatsApp", color = OnSurfaceText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Se crearán ${importState.splitPlans.size} paquete(s) de stickers divididos inteligentemente (3 a 30 por pack):",
                        color = OnSurfaceSubtext,
                        fontSize = 13.sp
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
                                Text("• ${plan.packName}", color = OnSurfaceText, fontSize = 13.sp)
                                Text(
                                    "${plan.emotes.size} stickers (${if (plan.isAnimated) "Animado" else "Estático"})",
                                    color = SevenTvCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmImport(importState.splitPlans) },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.Black)
                ) {
                    Text("Crear Paquetes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissImportConfirmation() }) {
                    Text("Cancelar", color = OnSurfaceSubtext)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun ChannelContent(
    streamerName: String,
    totalEmotes: Int,
    filteredEmotes: List<SevenTvEmote>,
    selectedEmoteIds: Set<String>,
    activeFilter: ChannelFilter,
    onFilterSelected: (ChannelFilter) -> Unit,
    onToggleSelectEmote: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImportFullChannel: () -> Unit,
    onImportSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Channel header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SevenTvCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = streamerName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(streamerName, color = OnSurfaceText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$totalEmotes emotes disponibles", color = OnSurfaceSubtext, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Import Button
        Button(
            onClick = onImportFullChannel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Canal Completo a WhatsApp ($totalEmotes)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeFilter == ChannelFilter.ALL,
                onClick = { onFilterSelected(ChannelFilter.ALL) },
                label = { Text("Todos") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SevenTvCyan,
                    selectedLabelColor = Color.Black,
                    labelColor = OnSurfaceText
                )
            )
            FilterChip(
                selected = activeFilter == ChannelFilter.ANIMATED,
                onClick = { onFilterSelected(ChannelFilter.ANIMATED) },
                label = { Text("Animados") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SevenTvCyan,
                    selectedLabelColor = Color.Black,
                    labelColor = OnSurfaceText
                )
            )
            FilterChip(
                selected = activeFilter == ChannelFilter.STATIC,
                onClick = { onFilterSelected(ChannelFilter.STATIC) },
                label = { Text("Estáticos") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SevenTvCyan,
                    selectedLabelColor = Color.Black,
                    labelColor = OnSurfaceText
                )
            )
        }

        // Selection actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedEmoteIds.isEmpty()) "Mostrando ${filteredEmotes.size} emotes"
                else "${selectedEmoteIds.size} seleccionados",
                color = OnSurfaceSubtext,
                fontSize = 12.sp
            )
            Row {
                TextButton(onClick = onSelectAll) { Text("Todos", color = SevenTvCyan, fontSize = 12.sp) }
                if (selectedEmoteIds.isNotEmpty()) {
                    TextButton(onClick = onDeselectAll) { Text("Limpiar", color = OnSurfaceSubtext, fontSize = 12.sp) }
                    TextButton(onClick = onImportSelected) { Text("Importar (${selectedEmoteIds.size})", color = WhatsAppGreen, fontSize = 12.sp) }
                }
            }
        }

        // Emotes Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredEmotes, key = { it.id }) { emote ->
                val isSelected = selectedEmoteIds.contains(emote.id)
                ChannelEmoteCell(
                    emote = emote,
                    isSelected = isSelected,
                    onToggleSelect = { onToggleSelectEmote(emote.id) }
                )
            }
        }
    }
}

@Composable
private fun ChannelEmoteCell(
    emote: SevenTvEmote,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .then(
                if (isSelected) Modifier.border(2.dp, WhatsAppGreen, RoundedCornerShape(10.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = emote.previewUrl,
                    contentDescription = emote.name,
                    modifier = Modifier.size(68.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = emote.name,
                    color = OnSurfaceText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = WhatsAppGreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

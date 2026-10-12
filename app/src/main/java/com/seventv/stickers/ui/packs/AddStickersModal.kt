package com.seventv.stickers.ui.packs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.DarkBackground
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan

@Composable
fun AddStickersModal(
    pack: StickerPack,
    viewModel: PacksViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isPackFull = pack.stickers.size >= 30

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Añadir a '${pack.name}'",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pack.stickers.size}/30 stickers • ${if (pack.isAnimated) "Animados" else "Estáticos"}",
                            fontSize = 12.sp,
                            color = if (isPackFull) ErrorRed else SevenTvCyan
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = OnSurfaceSubtext)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Buscar emotes en 7TV (ej: kekw, pepe)...", color = OnSurfaceSubtext, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SevenTvCyan) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = OnSurfaceSubtext)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurfaceText,
                        unfocusedTextColor = OnSurfaceText,
                        focusedBorderColor = SevenTvCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = SevenTvCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isAddingStickers) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = SevenTvCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Procesando y añadiendo sticker...", color = OnSurfaceText, fontSize = 14.sp)
                        }
                    }
                } else if (state.isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SevenTvCyan)
                    }
                } else if (state.searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Escribe el nombre de un emote de 7TV arriba para buscar y añadir stickers.",
                            color = OnSurfaceSubtext,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else if (state.searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron emotes en 7TV para '${state.searchQuery}'.",
                            color = OnSurfaceSubtext,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.searchResults, key = { it.id }) { emote ->
                            val alreadyInPack = pack.stickers.any { it.emoteId == emote.id }
                            val isCompatible = !pack.isAnimated || emote.isAnimated // static can't go in animated pack without conversion
                            EmoteSearchResultCard(
                                emote = emote,
                                alreadyInPack = alreadyInPack,
                                isCompatible = isCompatible,
                                isPackFull = isPackFull,
                                onAdd = { viewModel.onAddEmoteToPack(pack.identifier, emote) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmoteSearchResultCard(
    emote: SevenTvEmote,
    alreadyInPack: Boolean,
    isCompatible: Boolean,
    isPackFull: Boolean,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = emote.bestAssetUrl,
                    contentDescription = emote.name,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = emote.name,
                color = OnSurfaceText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (emote.isAnimated) "Animado" else "Estático",
                color = OnSurfaceSubtext,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val canAdd = !alreadyInPack && !isPackFull && isCompatible

            Button(
                onClick = onAdd,
                enabled = canAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SevenTvCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = DarkBackground,
                    disabledContentColor = OnSurfaceSubtext
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                if (alreadyInPack) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadido", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else if (!isCompatible) {
                    Text("Incompatible", fontSize = 11.sp)
                } else if (isPackFull) {
                    Text("Lleno", fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

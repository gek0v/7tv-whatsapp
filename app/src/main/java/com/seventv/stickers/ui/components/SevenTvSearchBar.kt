package com.seventv.stickers.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.GlassBorderCyan
import com.seventv.stickers.ui.theme.GlassSurfaceDark
import com.seventv.stickers.ui.theme.NeonFuchsia

object SevenTvSearchBarDefaults {
    val filters: List<String> = listOf("Todos", "Animados", "Estáticos", "Populares hoy")

    fun extractEmoteIdFromUrl(query: String): String? {
        val trimmed = query.trim()
        val sevenTvRegex = Regex("""(?:https?://)?(?:www\.)?7tv\.app/emotes/([a-fA-F0-9]{24}|[a-zA-Z0-9_-]+)""")
        val bttvRegex = Regex("""(?:https?://)?(?:www\.)?betterttv\.com/emotes/([a-fA-F0-9]{24})""")
        val ffzRegex = Regex("""(?:https?://)?(?:www\.)?frankerfacez\.com/emoticon/(\d+)""")

        return sevenTvRegex.find(trimmed)?.groupValues?.get(1)
            ?: bttvRegex.find(trimmed)?.groupValues?.get(1)
            ?: ffzRegex.find(trimmed)?.groupValues?.get(1)
    }

    fun isDirectEmoteUrl(query: String): Boolean {
        return extractEmoteIdFromUrl(query) != null
    }

    fun shouldShowClear(query: String): Boolean = query.isNotEmpty()
}

/**
 * Vaporwave + Glassmorphism search bar component with translucent glass container,
 * subtle electric cyan glowing border, and neon translucent pill filter chips.
 */
@Composable
fun SevenTvSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onPasteUrl: () -> Unit,
    modifier: Modifier = Modifier,
    filters: List<String> = SevenTvSearchBarDefaults.filters
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GlassSurfaceDark,
            border = BorderStroke(1.dp, GlassBorderCyan),
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = {
                    Text(
                        text = "Buscar emotes en 7TV...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = ElectricCyan
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (SevenTvSearchBarDefaults.shouldShowClear(query)) {
                            IconButton(
                                onClick = { onQueryChanged("") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar búsqueda",
                                    tint = NeonFuchsia
                                )
                            }
                        }
                        IconButton(
                            onClick = onPasteUrl,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Pegar enlace directo",
                                tint = ElectricCyan
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = ElectricCyan
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else Color(0x35FFFFFF)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0x3501CDFE),
                        selectedLabelColor = ElectricCyan,
                        containerColor = GlassSurfaceDark,
                        labelColor = DarkOnSurfaceVariant
                    )
                )
            }
        }
    }
}

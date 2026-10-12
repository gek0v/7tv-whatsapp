package com.seventv.stickers.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.DarkSurface
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan
import com.seventv.stickers.ui.theme.WhatsAppGreen

object EmoteCardDefaults {
    fun badgeText(isAnimated: Boolean): String = if (isAnimated) "ANIM" else "STATIC"
}

/**
 * Grid card component displaying an individual 7TV emote thumbnail, type badge, and draft toggle action.
 */
@Composable
fun EmoteCard(
    emote: SevenTvEmote,
    isInDraft: Boolean,
    onToggleDraft: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(emote.previewUrl)
                        .size(128, 128)
                        .crossfade(true)
                        .build(),
                    contentDescription = emote.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )

                // Type badge in top-end corner: "ANIM" or "STATIC"
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = EmoteCardDefaults.badgeText(emote.isAnimated),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emote.isAnimated) SevenTvCyan else OnSurfaceSubtext,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = emote.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onToggleDraft,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isInDraft) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Quitar del borrador",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir al borrador",
                            tint = SevenTvCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

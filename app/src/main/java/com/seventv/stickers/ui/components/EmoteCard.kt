package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.network.model.SevenTvEmote

object EmoteCardDefaults {
    const val ANIMATED_BADGE = "ANIM"
    const val STATIC_BADGE = "STATIC"

    fun badgeText(isAnimated: Boolean): String = if (isAnimated) ANIMATED_BADGE else STATIC_BADGE
}

/**
 * Grid card component displaying an individual 7TV emote thumbnail with M3 ElevatedCard styling,
 * format badge (ANIM / STATIC), and animated draft toggle action.
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

    ElevatedCard(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium, // 16dp
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
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

                // Format badge in top-end corner: "ANIM" or "STATIC"
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = MaterialTheme.shapes.extraSmall, // 8dp
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = EmoteCardDefaults.badgeText(emote.isAnimated),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emote.isAnimated) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = emote.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val tintColor by animateColorAsState(
                    targetValue = if (isInDraft) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    animationSpec = tween(durationMillis = 200),
                    label = "DraftTintAnimation"
                )

                IconButton(
                    onClick = onToggleDraft,
                    modifier = Modifier.size(32.dp)
                ) {
                    AnimatedContent(
                        targetState = isInDraft,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(180)))
                                .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.7f, animationSpec = tween(140)))
                        },
                        label = "DraftToggleIcon"
                    ) { inDraft ->
                        Icon(
                            imageVector = if (inDraft) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (inDraft) "Quitar del borrador" else "Añadir al borrador",
                            tint = tintColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

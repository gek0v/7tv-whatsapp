package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.GlassBorder
import com.seventv.stickers.ui.theme.GlassSurfaceCard
import com.seventv.stickers.ui.theme.WhatsAppGlow

object EmoteCardDefaults {
    const val ANIMATED_BADGE = "ANIM"
    const val STATIC_BADGE = "STATIC"

    fun badgeText(isAnimated: Boolean): String = if (isAnimated) ANIMATED_BADGE else STATIC_BADGE
}

/**
 * Vaporwave + Glassmorphism grid card displaying an individual 7TV emote thumbnail
 * with translucent glass surface, subtle glowing border, frosted badge, neon draft toggle,
 * and optional peek & pop gesture preview.
 */
@Composable
fun EmoteCard(
    emote: SevenTvEmote,
    isInDraft: Boolean,
    onToggleDraft: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onPreviewClick: (() -> Unit)? = null,
    onPeekStart: ((Rect) -> Unit)? = null,
    onPeekMove: ((Offset) -> Unit)? = null,
    onPeekRelease: (() -> Unit)? = null
) {
    val peekModifier = if (onPeekStart != null && onPeekMove != null && onPeekRelease != null) {
        Modifier.emotePeekGesture(
            key = emote.id,
            onPeekStart = onPeekStart,
            onPeekMove = onPeekMove,
            onPeekRelease = onPeekRelease
        )
    } else {
        Modifier
    }

    val cardModifier = modifier
        .then(peekModifier)
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurfaceCard
        ),
        border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            val imageBoxModifier = if (onPreviewClick != null) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onPreviewClick)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
            }

            Box(
                modifier = imageBoxModifier
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(emote.previewUrl)
                        .size(128, 128)
                        .memoryCacheKey(CoilSetup.emotePreviewCacheKey(emote.id))
                        .crossfade(true)
                        .build(),
                    contentDescription = emote.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )

                // Frosted neon format badge in top-end corner
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x800F081D),
                    border = BorderStroke(
                        0.5.dp,
                        if (emote.isAnimated) ElectricCyan.copy(alpha = 0.6f) else Color(0x35FFFFFF)
                    )
                ) {
                    Text(
                        text = EmoteCardDefaults.badgeText(emote.isAnimated),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emote.isAnimated) ElectricCyan else DarkOnSurfaceVariant,
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
                    targetValue = if (isInDraft) WhatsAppGlow else ElectricCyan,
                    animationSpec = tween(durationMillis = 200),
                    label = "DraftTintAnimation"
                )

                Surface(
                    shape = CircleShape,
                    color = if (isInDraft) Color(0x3005FFA1) else Color(0x2001CDFE),
                    border = BorderStroke(
                        1.dp,
                        if (isInDraft) WhatsAppGlow.copy(alpha = 0.8f) else ElectricCyan.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = onToggleDraft,
                        modifier = Modifier.fillMaxSize()
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
                                contentDescription = if (inDraft) "Quitar del borrador" else "AÃ±adir al borrador",
                                tint = tintColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

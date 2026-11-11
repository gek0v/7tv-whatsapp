package com.seventv.stickers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.ErrorRed
import com.seventv.stickers.ui.theme.WhatsAppGreen

enum class DraftStatus {
    INSUFFICIENT,
    READY,
    EXCEEDED
}

object DockedDraftPillDefaults {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30

    val WarningOrange = Color(0xFFFF9800)

    fun draftStatus(count: Int, minCount: Int = MIN_STICKERS, maxCount: Int = MAX_STICKERS): DraftStatus =
        when {
            count < minCount -> DraftStatus.INSUFFICIENT
            count <= maxCount -> DraftStatus.READY
            else -> DraftStatus.EXCEEDED
        }

    fun statusMessage(status: DraftStatus): String = when (status) {
        DraftStatus.INSUFFICIENT -> "Mínimo 3 requeridos"
        DraftStatus.READY -> "Listo para WhatsApp"
        DraftStatus.EXCEEDED -> "Límite excedido"
    }

    fun statusMessage(count: Int, minCount: Int = MIN_STICKERS, maxCount: Int = MAX_STICKERS): String =
        statusMessage(draftStatus(count, minCount, maxCount))

    fun statusColor(
        status: DraftStatus,
        errorColor: Color = ErrorRed,
        readyColor: Color = WhatsAppGreen,
        warningColor: Color = WarningOrange
    ): Color = when (status) {
        DraftStatus.INSUFFICIENT -> errorColor
        DraftStatus.READY -> readyColor
        DraftStatus.EXCEEDED -> warningColor
    }

    fun statusColor(count: Int): Color =
        statusColor(draftStatus(count), ErrorRed, WhatsAppGreen, WarningOrange)

    fun statusColor(
        count: Int,
        errorColor: Color,
        readyColor: Color,
        warningColor: Color = WarningOrange
    ): Color = statusColor(draftStatus(count), errorColor, readyColor, warningColor)

    fun statusColor(status: DraftStatus, colorScheme: ColorScheme): Color =
        statusColor(
            status = status,
            errorColor = colorScheme.error,
            readyColor = colorScheme.tertiary,
            warningColor = WarningOrange
        )

    fun statusColor(count: Int, colorScheme: ColorScheme): Color =
        statusColor(draftStatus(count), colorScheme)

    fun counterText(count: Int, maxCount: Int = MAX_STICKERS): String =
        "$count / $maxCount stickers"

    fun isValid(count: Int, minCount: Int = MIN_STICKERS, maxCount: Int = MAX_STICKERS): Boolean =
        count in minCount..maxCount
}

/**
 * Interactive floating pill displaying live active draft emote count, mini previews,
 * linear health status progress indicator, and a CTA button to inspect/review the pack draft.
 */
@Composable
fun DockedDraftPill(
    draftEmotes: List<SevenTvEmote>,
    onClickReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = draftEmotes.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val count = draftEmotes.size
        val status = DockedDraftPillDefaults.draftStatus(count)
        val statusColor = DockedDraftPillDefaults.statusColor(status, MaterialTheme.colorScheme)

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = com.seventv.stickers.ui.theme.GlassSurfaceBar,
            border = BorderStroke(
                1.5.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        com.seventv.stickers.ui.theme.NeonFuchsia,
                        com.seventv.stickers.ui.theme.SunsetYellow,
                        com.seventv.stickers.ui.theme.ElectricCyan
                    )
                )
            ),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.clickable(onClick = onClickReview)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mini thumbnails row with glowing retro frames
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-6).dp)
                    ) {
                        draftEmotes.take(3).forEach { emote ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    1.5.dp,
                                    com.seventv.stickers.ui.theme.ElectricCyan.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(emote.previewUrl)
                                        .size(64, 64)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = emote.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Counter and health message
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = DockedDraftPillDefaults.counterText(count),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = DockedDraftPillDefaults.statusMessage(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action button with Sunset neon gradient
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(
                                        com.seventv.stickers.ui.theme.NeonFuchsia,
                                        com.seventv.stickers.ui.theme.ElectricCyan
                                    )
                                )
                            )
                            .clickable(onClick = onClickReview)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Revisar Pack ▶",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Linear tonal progress bar with vaporwave tint
                LinearProgressIndicator(
                    progress = { (count / DockedDraftPillDefaults.MAX_STICKERS.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = statusColor,
                    trackColor = Color(0x30FFFFFF)
                )
            }
        }
    }
}

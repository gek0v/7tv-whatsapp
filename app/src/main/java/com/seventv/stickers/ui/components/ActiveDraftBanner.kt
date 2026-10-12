package com.seventv.stickers.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.theme.DarkSurfaceVariant
import com.seventv.stickers.ui.theme.OnSurfaceSubtext
import com.seventv.stickers.ui.theme.OnSurfaceText
import com.seventv.stickers.ui.theme.SevenTvCyan

object ActiveDraftBannerDefaults {
    const val MIN_STICKERS = 3
    const val DEFAULT_MAX_COUNT = 30

    fun canSave(count: Int, maxCount: Int = DEFAULT_MAX_COUNT): Boolean =
        count in MIN_STICKERS..maxCount

    fun counterText(count: Int, maxCount: Int = DEFAULT_MAX_COUNT): String =
        "$count/$maxCount stickers"

    fun subtitleText(count: Int): String =
        if (count >= MIN_STICKERS) "Listo para crear pack" else "(Mínimo 3 stickers)"
}

/**
 * Floating banner displaying active draft count, threshold status, and save/discard actions.
 */
@Composable
fun ActiveDraftBanner(
    draftEmotes: List<SevenTvEmote>,
    maxCount: Int = 30,
    onSavePackClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = draftEmotes.size
    val canSave = ActiveDraftBannerDefaults.canSave(count, maxCount)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Descartar borrador",
                    tint = OnSurfaceSubtext
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = ActiveDraftBannerDefaults.counterText(count, maxCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceText
                )
                Text(
                    text = ActiveDraftBannerDefaults.subtitleText(count),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (count >= ActiveDraftBannerDefaults.MIN_STICKERS) SevenTvCyan else OnSurfaceSubtext
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSavePackClick,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SevenTvCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = OnSurfaceSubtext
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Guardar Pack",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

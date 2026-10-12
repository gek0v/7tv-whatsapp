package com.seventv.stickers.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seventv.stickers.core.network.model.SevenTvEmote

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
 * Banner displaying active draft count, delegating to the expressive M3 DockedDraftPill
 * for backwards compatibility during screen refactoring.
 */
@Composable
fun ActiveDraftBanner(
    draftEmotes: List<SevenTvEmote>,
    maxCount: Int = 30,
    onSavePackClick: () -> Unit,
    onClearClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    DockedDraftPill(
        draftEmotes = draftEmotes,
        onClickReview = onSavePackClick,
        modifier = modifier
    )
}

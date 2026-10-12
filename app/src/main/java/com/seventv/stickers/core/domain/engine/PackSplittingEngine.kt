package com.seventv.stickers.core.domain.engine

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlin.math.ceil

/**
 * Represents a partition plan for a sticker pack adhering strictly
 * to WhatsApp constraints (3 to 30 stickers per pack, homogeneous formats).
 */
data class SplitPlan(
    val packName: String,
    val isAnimated: Boolean,
    val emotes: List<SevenTvEmote>
)

/**
 * Pure mathematical partitioning and format segregation engine.
 * Guarantees that every resulting pack contains between 3 and 30 stickers,
 * formats are strictly segregated (animated vs. static), and large catalogs
 * are split into balanced, proportional parts.
 */
object PackSplittingEngine {

    private const val MIN_STICKERS = 3
    private const val MAX_STICKERS = 30

    fun splitEmotes(
        basePackName: String,
        emotes: List<SevenTvEmote>
    ): StickerResult<List<SplitPlan>> {
        if (emotes.size < MIN_STICKERS) {
            return StickerResult.Failure(
                StickerError.InsufficientStickers(actual = emotes.size, min = MIN_STICKERS)
            )
        }

        val (animated, static) = emotes.partition { it.isAnimated }

        val hasValidStatic = static.size >= MIN_STICKERS
        val hasValidAnimated = animated.size >= MIN_STICKERS

        return when {
            hasValidStatic && hasValidAnimated -> {
                val staticPlans = partitionGroup(
                    groupBaseName = "$basePackName (Estáticos)",
                    isAnimated = false,
                    groupEmotes = static
                )
                val animatedPlans = partitionGroup(
                    groupBaseName = "$basePackName (Animados)",
                    isAnimated = true,
                    groupEmotes = animated
                )
                StickerResult.Success(staticPlans + animatedPlans)
            }
            hasValidStatic -> {
                val groupBaseName = if (animated.isNotEmpty()) {
                    "$basePackName (Estáticos)"
                } else {
                    basePackName
                }
                val staticPlans = partitionGroup(
                    groupBaseName = groupBaseName,
                    isAnimated = false,
                    groupEmotes = static
                )
                StickerResult.Success(staticPlans)
            }
            hasValidAnimated -> {
                val groupBaseName = if (static.isNotEmpty()) {
                    "$basePackName (Animados)"
                } else {
                    basePackName
                }
                val animatedPlans = partitionGroup(
                    groupBaseName = groupBaseName,
                    isAnimated = true,
                    groupEmotes = animated
                )
                StickerResult.Success(animatedPlans)
            }
            else -> {
                // Neither group individually satisfies MIN_STICKERS
                StickerResult.Failure(
                    StickerError.InsufficientStickers(actual = emotes.size, min = MIN_STICKERS)
                )
            }
        }
    }

    private fun partitionGroup(
        groupBaseName: String,
        isAnimated: Boolean,
        groupEmotes: List<SevenTvEmote>
    ): List<SplitPlan> {
        val n = groupEmotes.size
        if (n <= MAX_STICKERS) {
            return listOf(
                SplitPlan(
                    packName = groupBaseName,
                    isAnimated = isAnimated,
                    emotes = groupEmotes
                )
            )
        }

        val k = ceil(n.toDouble() / MAX_STICKERS.toDouble()).toInt()
        val baseSize = n / k
        val remainder = n % k

        val plans = ArrayList<SplitPlan>(k)
        var startIndex = 0

        for (i in 1..k) {
            val partSize = if (i <= remainder) baseSize + 1 else baseSize
            val subList = groupEmotes.subList(startIndex, startIndex + partSize)
            plans.add(
                SplitPlan(
                    packName = "$groupBaseName Part $i",
                    isAnimated = isAnimated,
                    emotes = subList
                )
            )
            startIndex += partSize
        }

        return plans
    }
}

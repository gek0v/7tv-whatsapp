package com.seventv.stickers.core.domain.engine

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.SevenTvEmote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackSplittingEngineTest {

    private fun createEmote(
        id: String = "emote",
        name: String = "testEmote",
        isAnimated: Boolean = false
    ): SevenTvEmote = SevenTvEmote(
        id = id,
        name = name,
        isAnimated = isAnimated,
        isZeroWidth = false,
        isNsfw = false,
        bestAssetUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
        availableScales = listOf("1x", "2x", "3x", "4x")
    )

    private fun createEmotes(
        count: Int,
        isAnimated: Boolean = false,
        prefix: String = "e"
    ): List<SevenTvEmote> = (1..count).map { i ->
        createEmote(
            id = "$prefix-$i",
            name = "$prefix$i",
            isAnimated = isAnimated
        )
    }

    @Test
    fun rejectsLessThan3Emotes() {
        // 0 emotes
        val result0 = PackSplittingEngine.splitEmotes("Base", emptyList())
        assertTrue(result0 is StickerResult.Failure)
        assertEquals(
            StickerError.InsufficientStickers(actual = 0, min = 3),
            (result0 as StickerResult.Failure).error
        )

        // 1 emote
        val result1 = PackSplittingEngine.splitEmotes("Base", createEmotes(1))
        assertTrue(result1 is StickerResult.Failure)
        assertEquals(
            StickerError.InsufficientStickers(actual = 1, min = 3),
            (result1 as StickerResult.Failure).error
        )

        // 2 emotes
        val result2 = PackSplittingEngine.splitEmotes("Base", createEmotes(2))
        assertTrue(result2 is StickerResult.Failure)
        assertEquals(
            StickerError.InsufficientStickers(actual = 2, min = 3),
            (result2 as StickerResult.Failure).error
        )

        // 2 static and 2 animated (total 4, but neither group has >= 3)
        val mixed4 = createEmotes(2, isAnimated = false, prefix = "s") +
            createEmotes(2, isAnimated = true, prefix = "a")
        val resultMixed4 = PackSplittingEngine.splitEmotes("Base", mixed4)
        assertTrue(resultMixed4 is StickerResult.Failure)
        assertEquals(
            StickerError.InsufficientStickers(actual = 4, min = 3),
            (resultMixed4 as StickerResult.Failure).error
        )

        // 2 static and 1 animated (total 3, but neither group has >= 3)
        val mixed3 = createEmotes(2, isAnimated = false, prefix = "s") +
            createEmotes(1, isAnimated = true, prefix = "a")
        val resultMixed3 = PackSplittingEngine.splitEmotes("Base", mixed3)
        assertTrue(resultMixed3 is StickerResult.Failure)
        assertEquals(
            StickerError.InsufficientStickers(actual = 3, min = 3),
            (resultMixed3 as StickerResult.Failure).error
        )
    }

    @Test
    fun keeps3To30EmotesInSinglePack() {
        // 3 emotes
        val result3 = PackSplittingEngine.splitEmotes("Community Pack", createEmotes(3, isAnimated = false))
        assertTrue(result3 is StickerResult.Success)
        val plans3 = (result3 as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(1, plans3.size)
        assertEquals("Community Pack", plans3[0].packName)
        assertFalse(plans3[0].isAnimated)
        assertEquals(3, plans3[0].emotes.size)

        // 30 emotes
        val result30 = PackSplittingEngine.splitEmotes("Community Pack", createEmotes(30, isAnimated = false))
        assertTrue(result30 is StickerResult.Success)
        val plans30 = (result30 as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(1, plans30.size)
        assertEquals("Community Pack", plans30[0].packName)
        assertFalse(plans30[0].isAnimated)
        assertEquals(30, plans30[0].emotes.size)

        // Animated group single pack
        val resultAnimated = PackSplittingEngine.splitEmotes("Animated Only", createEmotes(15, isAnimated = true))
        assertTrue(resultAnimated is StickerResult.Success)
        val plansAnim = (resultAnimated as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(1, plansAnim.size)
        assertEquals("Animated Only", plansAnim[0].packName)
        assertTrue(plansAnim[0].isAnimated)
        assertEquals(15, plansAnim[0].emotes.size)
    }

    @Test
    fun splits31EmotesIntoBalanced16And15() {
        val emotes = createEmotes(31, isAnimated = false)
        val result = PackSplittingEngine.splitEmotes("Streamer", emotes)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(2, plans.size)

        assertEquals("Streamer Part 1", plans[0].packName)
        assertFalse(plans[0].isAnimated)
        assertEquals(16, plans[0].emotes.size)

        assertEquals("Streamer Part 2", plans[1].packName)
        assertFalse(plans[1].isAnimated)
        assertEquals(15, plans[1].emotes.size)

        // Ensure all emotes preserved in order
        assertEquals(emotes, plans.flatMap { it.emotes })
    }

    @Test
    fun splits65EmotesIntoBalanced22_22_21() {
        val emotes = createEmotes(65, isAnimated = false)
        val result = PackSplittingEngine.splitEmotes("Streamer", emotes)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(3, plans.size)

        assertEquals("Streamer Part 1", plans[0].packName)
        assertEquals(22, plans[0].emotes.size)

        assertEquals("Streamer Part 2", plans[1].packName)
        assertEquals(22, plans[1].emotes.size)

        assertEquals("Streamer Part 3", plans[2].packName)
        assertEquals(21, plans[2].emotes.size)

        assertEquals(emotes, plans.flatMap { it.emotes })
    }

    @Test
    fun splits90EmotesInto30_30_30() {
        val emotes = createEmotes(90, isAnimated = false)
        val result = PackSplittingEngine.splitEmotes("Huge Pack", emotes)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(3, plans.size)

        assertEquals("Huge Pack Part 1", plans[0].packName)
        assertEquals(30, plans[0].emotes.size)

        assertEquals("Huge Pack Part 2", plans[1].packName)
        assertEquals(30, plans[1].emotes.size)

        assertEquals("Huge Pack Part 3", plans[2].packName)
        assertEquals(30, plans[2].emotes.size)

        assertEquals(emotes, plans.flatMap { it.emotes })
    }

    @Test
    fun splits91EmotesIntoBalanced23_23_23_22() {
        val emotes = createEmotes(91, isAnimated = false)
        val result = PackSplittingEngine.splitEmotes("Massive Pack", emotes)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(4, plans.size)

        assertEquals("Massive Pack Part 1", plans[0].packName)
        assertEquals(23, plans[0].emotes.size)

        assertEquals("Massive Pack Part 2", plans[1].packName)
        assertEquals(23, plans[1].emotes.size)

        assertEquals("Massive Pack Part 3", plans[2].packName)
        assertEquals(23, plans[2].emotes.size)

        assertEquals("Massive Pack Part 4", plans[3].packName)
        assertEquals(22, plans[3].emotes.size)

        assertEquals(emotes, plans.flatMap { it.emotes })
    }

    @Test
    fun segregatesMixedAnimatedAndStaticEmotes() {
        val staticEmotes = createEmotes(10, isAnimated = false, prefix = "s")
        val animatedEmotes = createEmotes(10, isAnimated = true, prefix = "a")
        val mixed = staticEmotes + animatedEmotes

        val result = PackSplittingEngine.splitEmotes("MixPack", mixed)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(2, plans.size)

        assertEquals("MixPack (Estáticos)", plans[0].packName)
        assertFalse(plans[0].isAnimated)
        assertEquals(10, plans[0].emotes.size)
        assertEquals(staticEmotes, plans[0].emotes)

        assertEquals("MixPack (Animados)", plans[1].packName)
        assertTrue(plans[1].isAnimated)
        assertEquals(10, plans[1].emotes.size)
        assertEquals(animatedEmotes, plans[1].emotes)
    }

    @Test
    fun segregatesMixedWithMultiPartSplitting() {
        val staticEmotes = createEmotes(35, isAnimated = false, prefix = "s")
        val animatedEmotes = createEmotes(10, isAnimated = true, prefix = "a")
        val mixed = staticEmotes + animatedEmotes

        val result = PackSplittingEngine.splitEmotes("Channel", mixed)
        assertTrue(result is StickerResult.Success)
        val plans = (result as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(3, plans.size)

        // Static splits into 18 and 17
        assertEquals("Channel (Estáticos) Part 1", plans[0].packName)
        assertFalse(plans[0].isAnimated)
        assertEquals(18, plans[0].emotes.size)

        assertEquals("Channel (Estáticos) Part 2", plans[1].packName)
        assertFalse(plans[1].isAnimated)
        assertEquals(17, plans[1].emotes.size)

        // Animated stays in 1 pack
        assertEquals("Channel (Animados)", plans[2].packName)
        assertTrue(plans[2].isAnimated)
        assertEquals(10, plans[2].emotes.size)

        assertEquals(staticEmotes, plans[0].emotes + plans[1].emotes)
        assertEquals(animatedEmotes, plans[2].emotes)
    }

    @Test
    fun oneGroupHasSufficientAndOtherHasInsufficientStickers() {
        // 5 static + 2 animated -> static is partitioned with format suffix "(Estáticos)", animated discarded
        val static5 = createEmotes(5, isAnimated = false, prefix = "s")
        val animated2 = createEmotes(2, isAnimated = true, prefix = "a")
        val mixedStatic = static5 + animated2

        val resultStatic = PackSplittingEngine.splitEmotes("Streamer", mixedStatic)
        assertTrue(resultStatic is StickerResult.Success)
        val plansStatic = (resultStatic as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(1, plansStatic.size)
        assertEquals("Streamer (Estáticos)", plansStatic[0].packName)
        assertFalse(plansStatic[0].isAnimated)
        assertEquals(static5, plansStatic[0].emotes)

        // 1 static + 5 animated -> animated is partitioned with format suffix "(Animados)", static discarded
        val static1 = createEmotes(1, isAnimated = false, prefix = "s")
        val animated5 = createEmotes(5, isAnimated = true, prefix = "a")
        val mixedAnim = static1 + animated5

        val resultAnim = PackSplittingEngine.splitEmotes("Streamer", mixedAnim)
        assertTrue(resultAnim is StickerResult.Success)
        val plansAnim = (resultAnim as StickerResult.Success<List<SplitPlan>>).data
        assertEquals(1, plansAnim.size)
        assertEquals("Streamer (Animados)", plansAnim[0].packName)
        assertTrue(plansAnim[0].isAnimated)
        assertEquals(animated5, plansAnim[0].emotes)
    }

    @Test
    fun allPartitionsSatisfyWhatsAppConstraintsAcrossRanges() {
        for (n in 3..120) {
            val emotes = createEmotes(n, isAnimated = false)
            val result = PackSplittingEngine.splitEmotes("Test", emotes)
            assertTrue("Expected success for N=$n", result is StickerResult.Success)
            val plans = (result as StickerResult.Success<List<SplitPlan>>).data
            var totalEmotes = 0
            for (plan in plans) {
                val size = plan.emotes.size
                assertTrue(
                    "Partition size $size for N=$n must be >= 3 and <= 30",
                    size in 3..30
                )
                totalEmotes += size
            }
            assertEquals("Total emotes should equal N=$n", n, totalEmotes)
        }
    }
}

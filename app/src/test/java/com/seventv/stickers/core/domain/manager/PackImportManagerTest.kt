package com.seventv.stickers.core.domain.manager

import android.content.Context
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PackImportManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepository: FakeTestStickerPackRepository
    private lateinit var manager: PackImportManager

    @Before
    fun setUp() {
        fakeRepository = FakeTestStickerPackRepository()
        manager = PackImportManager(
            stickerPackRepository = fakeRepository,
            scope = testScope
        )
    }

    @Test
    fun initialStateIsIdle() {
        assertEquals(PackImportProgress.Idle, manager.progress.value)
    }

    @Test
    fun startImportUpdatesRunningAndCompletesSuccessfully() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("id_$it", "emote_$it") }
        val plans = listOf(
            SplitPlan(packName = "Ibai Pack 1", isAnimated = false, emotes = emotes.take(3)),
            SplitPlan(packName = "Ibai Pack 2", isAnimated = false, emotes = emotes.drop(3))
        )

        manager.startImport("Ibai", plans)
        testScope.advanceUntilIdle()

        val state = manager.progress.value
        assertTrue("Expected Completed, got $state", state is PackImportProgress.Completed)
        val completed = state as PackImportProgress.Completed
        assertEquals("Ibai", completed.streamerName)
        assertEquals(2, completed.packsCount)
    }

    @Test
    fun startImportReportsFailureOnError() = runTest(testDispatcher) {
        fakeRepository.shouldFail = true
        val emotes = (1..3).map { createSampleEmote("id_$it", "emote_$it") }
        val plans = listOf(SplitPlan(packName = "Failing Pack", isAnimated = false, emotes = emotes))

        manager.startImport("Streamer", plans)
        testScope.advanceUntilIdle()

        val state = manager.progress.value
        assertTrue("Expected Failed, got $state", state is PackImportProgress.Failed)
        val failed = state as PackImportProgress.Failed
        assertEquals("Streamer", failed.streamerName)
        assertTrue(failed.errorMessage.contains("Error creando"))
    }

    @Test
    fun dismissProgressResetsToIdle() = runTest(testDispatcher) {
        val emotes = (1..3).map { createSampleEmote("id_$it", "emote_$it") }
        val plans = listOf(SplitPlan(packName = "Pack", isAnimated = false, emotes = emotes))

        manager.startImport("Streamer", plans)
        testScope.advanceUntilIdle()
        assertTrue(manager.progress.value is PackImportProgress.Completed)

        manager.dismissProgress()
        assertEquals(PackImportProgress.Idle, manager.progress.value)
    }

    private fun createSampleEmote(id: String, name: String) = SevenTvEmote(
        id = id,
        name = name,
        isAnimated = false,
        isZeroWidth = false,
        isNsfw = false,
        bestAssetUrl = "https://cdn.7tv.app/$id",
        availableScales = listOf("4x.webp")
    )

    private class FakeTestStickerPackRepository : StickerPackRepository {
        var shouldFail = false

        override fun observeAllPacks(): Flow<List<StickerPack>> = flowOf(emptyList())
        override suspend fun getPack(identifier: String): StickerResult<StickerPack> =
            StickerResult.Failure(StickerError.PackNotFound(identifier))

        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): StickerResult<List<StickerPack>> {
            if (shouldFail) {
                return StickerResult.Failure(StickerError.CorruptedSourceData)
            }
            onProgress?.invoke(emotes.size, emotes.size)
            val pack = StickerPack(
                identifier = "pack_${basePackName.hashCode()}",
                name = basePackName,
                publisher = publisher,
                trayImagePath = "tray.webp",
                isAnimated = false,
                stickers = emptyList()
            )
            return StickerResult.Success(listOf(pack))
        }

        override suspend fun deletePack(identifier: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun deletePacks(identifiers: List<String>): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun renamePack(identifier: String, newName: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun mergePacks(sourcePackIdentifiers: List<String>, basePackName: String): StickerResult<List<StickerPack>> =
            StickerResult.Success(emptyList())
        override suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> =
            StickerResult.Success(Unit)
        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit> =
            StickerResult.Success(Unit)
        override suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean> =
            StickerResult.Success(false)
    }
}

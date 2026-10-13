package com.seventv.stickers.core.domain.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.repository.EmoteStickerBridge
import com.seventv.stickers.data.repository.SevenTvRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerShareManagerTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeSevenTvRepo
    private lateinit var mediaPipeline: StickerMediaPipeline
    private lateinit var bridge: EmoteStickerBridge
    private lateinit var shareManager: StickerShareManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeRepo = FakeSevenTvRepo()
        mediaPipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        bridge = EmoteStickerBridge(fakeRepo, mediaPipeline)
        shareManager = StickerShareManager(
            context = context,
            emoteStickerBridge = bridge,
            sevenTvRepository = fakeRepo,
            uriResolver = { _, auth, file -> Uri.parse("content://$auth/${file.name}") },
            dispatcher = testDispatcher
        )
    }

    @Test
    fun createShareIntent_configuresActionMimeUriAndFlags() {
        val testUri = Uri.parse("content://com.seventv.stickers.fileprovider/quick_share/test_123.webp")
        val intent = shareManager.createShareIntent(testUri, targetPackage = "com.whatsapp")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/webp", intent.type)
        assertEquals(testUri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals("com.whatsapp", intent.`package`)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun createShareIntent_withoutTargetPackage_leavesPackageNull() {
        val testUri = Uri.parse("content://com.seventv.stickers.fileprovider/quick_share/test_123.webp")
        val intent = shareManager.createShareIntent(testUri, targetPackage = null)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertNull(intent.`package`)
    }

    @Test
    fun createChooserIntent_wrapsBaseIntentWithTitleAndFlags() {
        val testUri = Uri.parse("content://com.seventv.stickers.fileprovider/quick_share/test_123.webp")
        val chooserIntent = shareManager.createChooserIntent(testUri)

        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        assertEquals("Compartir imagen WebP", chooserIntent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue((chooserIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((chooserIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertNull(targetIntent?.`package`)
    }

    @Test
    fun getShareUriForSticker_returnsResourceNotFoundWhenFileMissing() {
        val nonExistentSticker = PackSticker(
            id = "s_1",
            packIdentifier = "pack_1",
            emoteId = "e_1",
            emoteName = "OMEGALUL",
            filePath = "sticker_packs/pack_1/missing.webp",
            fileLength = 0L,
            sortOrder = 0,
            emojis = listOf("😂")
        )

        val result = shareManager.getShareUriForSticker(nonExistentSticker)

        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (result as StickerResult.Failure).error)
    }

    @Test
    fun launchShare_attemptsToStartActivity() {
        val testUri = Uri.parse("content://com.seventv.stickers.fileprovider/quick_share/test_123.webp")
        val launched = shareManager.launchShare(context, testUri)
        assertTrue(launched)
    }

    @Test
    fun createShareIntent_withGifMime_configuresGifType() {
        val testUri = Uri.parse("content://com.seventv.stickers.fileprovider/quick_share/test_anim.gif")
        val intent = shareManager.createShareIntent(testUri, mimeType = "image/gif")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/gif", intent.type)
        assertEquals(testUri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun prepareEmoteForShare_animatedEmote_downloadsAndCachesGif() = runTest(testDispatcher) {
        val gifBytes = byteArrayOf(71, 73, 70, 56, 57, 97) // "GIF89a"
        fakeRepo.gifBytes = gifBytes

        val animatedEmote = SevenTvEmote(
            id = "anim_123",
            name = "catJAM",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/anim_123/4x.webp",
            availableScales = listOf("4x.webp", "4x.gif"),
            gifUrl = "https://cdn.7tv.app/emote/anim_123/4x.gif"
        )

        val result = shareManager.prepareEmoteForShare(animatedEmote)
        assertTrue(result is StickerResult.Success)
        val shareMedia = (result as StickerResult.Success).data
        assertEquals("image/gif", shareMedia.mimeType)
        assertTrue(shareMedia.isAnimated)
        assertTrue(shareMedia.uri.toString().endsWith(".gif"))
    }

    @Test
    fun prepareStickerForShare_animatedSticker_fetchesGif() = runTest(testDispatcher) {
        val gifBytes = byteArrayOf(71, 73, 70, 56, 57, 97)
        fakeRepo.gifBytes = gifBytes

        val sticker = PackSticker(
            id = "s_anim",
            packIdentifier = "pack_anim",
            emoteId = "emote_anim_1",
            emoteName = "catJAM",
            filePath = "sticker_packs/pack_anim/dummy.webp",
            fileLength = 100L
        )

        val result = shareManager.prepareStickerForShare(sticker, isAnimated = true)
        assertTrue(result is StickerResult.Success)
        val shareMedia = (result as StickerResult.Success).data
        assertEquals("image/gif", shareMedia.mimeType)
        assertTrue(shareMedia.isAnimated)
    }

    @Test
    fun prepareStickerForShare_staticSticker_sharesLocalWebp() = runTest(testDispatcher) {
        val localDir = java.io.File(context.filesDir, "sticker_packs/pack_1").apply { mkdirs() }
        java.io.File(localDir, "test.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val sticker = PackSticker(
            id = "s_static",
            packIdentifier = "pack_1",
            emoteId = "e_1",
            emoteName = "pepe",
            filePath = "sticker_packs/pack_1/test.webp",
            fileLength = 3L
        )

        val result = shareManager.prepareStickerForShare(sticker, isAnimated = false)
        assertTrue(result is StickerResult.Success)
        val shareMedia = (result as StickerResult.Success).data
        assertEquals("image/webp", shareMedia.mimeType)
        assertFalse(shareMedia.isAnimated)
    }

    private class FakeSevenTvRepo : SevenTvRepository {
        var gifBytes: ByteArray? = null

        override suspend fun downloadEmoteGif(emote: SevenTvEmote): StickerResult<ByteArray> {
            val bytes = gifBytes ?: return StickerResult.Failure(StickerError.ResourceNotFound)
            return StickerResult.Success(bytes)
        }

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun searchEmotes(query: String, page: Int, limit: Int, category: String?): StickerResult<List<SevenTvEmote>> {
            return StickerResult.Success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }
}

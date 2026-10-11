package com.seventv.stickers.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerResultTest {
    @Test
    fun successReturnsData() {
        val result: StickerResult<String> = StickerResult.Success("ok")
        assertTrue(result is StickerResult.Success && result.data == "ok")
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("ok", result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun failureReturnsError() {
        val result: StickerResult<String> = StickerResult.Failure(StickerError.CorruptedSourceData)
        assertTrue(result is StickerResult.Failure && result.error == StickerError.CorruptedSourceData)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertEquals(StickerError.CorruptedSourceData, result.errorOrNull())
    }

    @Test
    fun allStickerErrorVariantsInstantiateCorrectly() {
        val errors: List<StickerError> = listOf(
            StickerError.CorruptedSourceData,
            StickerError.UnsupportedFormat("image/tiff"),
            StickerError.ExceedsSizeLimit(500_000L, 480_000L, canFallbackToStatic = true),
            StickerError.NativeEncodingFailed("libwebp returned error code 1"),
            StickerError.StorageIoError(RuntimeException("Disk full"))
        )
        assertEquals(5, errors.size)
    }
}

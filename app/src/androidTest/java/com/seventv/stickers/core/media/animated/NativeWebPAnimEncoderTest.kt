package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeWebPAnimEncoderTest {

    @Test
    fun nativeLibraryLoadsSuccessfully() {
        val encoder = NativeWebPAnimEncoder()
        assertNotNull(encoder)
        assertTrue("Native library 'seven_tv_webp' should be loaded", NativeWebPAnimEncoder.isLoaded)
    }

    @Test
    fun encodeGeneratesValidWebPFile() {
        val encoder = NativeWebPAnimEncoder()
        val frames = Array(3) { Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888) }
        val timestamps = intArrayOf(0, 200, 400)
        val tempFile = File.createTempFile("anim_test", ".webp")
        try {
            val success = encoder.encode(frames, timestamps, 75f, tempFile.absolutePath)
            assertTrue("Encoding should succeed", success)
            assertTrue("Generated WebP file should not be empty", tempFile.length() > 0)
        } finally {
            tempFile.delete()
        }
    }
}

package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NativeWebPAnimEncoderUnitTest {

    @Test
    fun classInitializationSucceedsWithoutCrashing() {
        val encoder = NativeWebPAnimEncoder()
        assertNotNull(encoder)
    }

    @Test
    fun implementsAnimatedWebPEncoderInterface() {
        val encoder: AnimatedWebPEncoder = NativeWebPAnimEncoder()
        assertNotNull(encoder)
        assertTrue(encoder is NativeWebPAnimEncoder)
    }

    @Test
    fun encodeBehaviorUnderHostJvm() {
        val encoder = NativeWebPAnimEncoder()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bitmaps = arrayOf(bitmap)
        val timestamps = intArrayOf(0)
        val tempFile = File.createTempFile("jvm_test", ".webp")
        try {
            if (NativeWebPAnimEncoder.isLoaded) {
                val result = encoder.encode(bitmaps, timestamps, 75f, tempFile.absolutePath)
                assertTrue(result)
            } else {
                try {
                    encoder.encode(bitmaps, timestamps, 75f, tempFile.absolutePath)
                    fail("Expected UnsatisfiedLinkError when native library is not present")
                } catch (e: UnsatisfiedLinkError) {
                    assertNotNull(e)
                }
            }
        } finally {
            tempFile.delete()
        }
    }
}

package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import com.seventv.stickers.core.media.model.AnimationFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FrameThrottlerTest {
    private val throttler = FrameThrottler()

    private fun mockBitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun truncateDurationCutsAt3000Ms() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 1000, 1000),
            AnimationFrame(mockBitmap(), 1000, 2000),
            AnimationFrame(mockBitmap(), 1000, 3000),
            AnimationFrame(mockBitmap(), 1000, 4000)
        )
        val truncated = throttler.truncateDuration(frames, 3000)
        assertEquals(3, truncated.size)
        assertTrue(truncated.last().timestampMs <= 3000)
    }

    @Test
    fun decimateFramerateMergesDroppedFrameDurations() {
        val frames = (0 until 30).map { i ->
            AnimationFrame(mockBitmap(), 33, (i + 1) * 33)
        }
        val decimated = throttler.decimateFramerate(frames, targetFps = 15)
        assertEquals(15, decimated.size)
    }

    @Test
    fun truncateDurationClampsPartialFrameAtBoundary() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 2000, 2000),
            AnimationFrame(mockBitmap(), 2000, 4000)
        )
        val truncated = throttler.truncateDuration(frames, 3000)
        assertEquals(2, truncated.size)
        assertEquals(2000, truncated[0].durationMs)
        assertEquals(2000, truncated[0].timestampMs)
        assertEquals(1000, truncated[1].durationMs)
        assertEquals(3000, truncated[1].timestampMs)
    }

    @Test
    fun truncateDurationWhenAlreadyUnderMaxReturnsAllFrames() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 500, 500),
            AnimationFrame(mockBitmap(), 500, 1000)
        )
        val truncated = throttler.truncateDuration(frames, 3000)
        assertEquals(2, truncated.size)
        assertEquals(1000, truncated.last().timestampMs)
    }

    @Test
    fun truncateDurationEmptyListReturnsEmpty() {
        val truncated = throttler.truncateDuration(emptyList(), 3000)
        assertTrue(truncated.isEmpty())
    }

    @Test
    fun truncateDurationZeroOrNegativeMaxReturnsEmptyAndRecycles() {
        val b1 = mockBitmap()
        val frames = listOf(AnimationFrame(b1, 500, 500))
        val truncated = throttler.truncateDuration(frames, 0)
        assertTrue(truncated.isEmpty())
        assertTrue(b1.isRecycled)
    }

    @Test
    fun truncateDurationRecyclesDroppedBitmaps() {
        val b1 = mockBitmap()
        val b2 = mockBitmap()
        val b3 = mockBitmap()
        val frames = listOf(
            AnimationFrame(b1, 1000, 1000),
            AnimationFrame(b2, 1000, 2000),
            AnimationFrame(b3, 1000, 3000)
        )
        val truncated = throttler.truncateDuration(frames, 2000)
        assertEquals(2, truncated.size)
        assertFalse(b1.isRecycled)
        assertFalse(b2.isRecycled)
        assertTrue(b3.isRecycled)
    }

    @Test
    fun truncateDurationHandlesSharedBitmapsSafely() {
        val sharedBitmap = mockBitmap()
        val frames = listOf(
            AnimationFrame(sharedBitmap, 1000, 1000),
            AnimationFrame(sharedBitmap, 1000, 2000),
            AnimationFrame(sharedBitmap, 1000, 3000)
        )
        val truncated = throttler.truncateDuration(frames, 2000)
        assertEquals(2, truncated.size)
        assertFalse(sharedBitmap.isRecycled)
    }

    @Test
    fun decimateFramerateReturnsAsIsWhenSourceFpsUnderTarget() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 100, 100),
            AnimationFrame(mockBitmap(), 100, 200)
        )
        val result = throttler.decimateFramerate(frames, targetFps = 15)
        assertEquals(2, result.size)
    }

    @Test
    fun decimateFramerateHandlesEdgeCases() {
        assertEquals(emptyList<AnimationFrame>(), throttler.decimateFramerate(emptyList(), 15))

        val singleFrame = listOf(AnimationFrame(mockBitmap(), 100, 100))
        assertEquals(singleFrame, throttler.decimateFramerate(singleFrame, 15))

        val frames = listOf(
            AnimationFrame(mockBitmap(), 33, 33),
            AnimationFrame(mockBitmap(), 33, 66)
        )
        assertEquals(frames, throttler.decimateFramerate(frames, targetFps = 0))
        assertEquals(frames, throttler.decimateFramerate(frames, targetFps = -5))
    }

    @Test
    fun decimateFramerateRecyclesDroppedBitmaps() {
        val b1 = mockBitmap()
        val b2 = mockBitmap()
        val frames = listOf(
            AnimationFrame(b1, 16, 16),
            AnimationFrame(b2, 16, 32)
        )
        val decimated = throttler.decimateFramerate(frames, targetFps = 30)
        assertEquals(1, decimated.size)
        assertFalse(b1.isRecycled)
        assertTrue(b2.isRecycled)
    }

    @Test
    fun decimateFrameratePreservesTotalDuration() {
        val frames = (0 until 30).map { i ->
            AnimationFrame(mockBitmap(), 33, (i + 1) * 33)
        }
        val decimated = throttler.decimateFramerate(frames, targetFps = 15)
        assertEquals(15, decimated.size)
        assertEquals(30 * 33, decimated.sumOf { it.durationMs })
        assertEquals(30 * 33, decimated.last().timestampMs)
    }

    @Test
    fun decimateFrameratePreventsOvershootFor68FpsWithTarget30() {
        // 48 * 15ms + 20 * 14ms = 720ms + 280ms = 1000ms total duration for 68 frames -> exactly 68 source FPS
        val frames = (0 until 68).map { i ->
            val duration = if (i < 48) 15 else 14
            AnimationFrame(mockBitmap(), duration, 0)
        }
        val decimated = throttler.decimateFramerate(frames, targetFps = 30)
        val totalDurationMs = decimated.sumOf { it.durationMs }
        val effectiveFps = (decimated.size * 1000.0) / totalDurationMs

        assertEquals(1000, totalDurationMs)
        assertTrue("Effective FPS ($effectiveFps) must not exceed target FPS (30)", effectiveFps <= 30.0)
    }

    @Test
    fun truncateDurationEnforcesMinimum10MsFrameDuration() {
        val frames = listOf(
            AnimationFrame(mockBitmap(), 2995, 2995),
            AnimationFrame(mockBitmap(), 100, 3095)
        )
        // Max is 3000ms. Remaining for second frame is 5ms (< 10ms), so it should be dropped
        val truncated = throttler.truncateDuration(frames, 3000)
        assertEquals(1, truncated.size)
        assertTrue(truncated.all { it.durationMs >= 10 })
    }
}

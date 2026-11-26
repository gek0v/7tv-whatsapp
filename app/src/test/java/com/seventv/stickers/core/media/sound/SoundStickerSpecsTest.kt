package com.seventv.stickers.core.media.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundStickerSpecsTest {

    @Test
    fun maxDurationRespectsAnimatedVsStaticLimit() {
        assertEquals(5_000L, SoundStickerSpecs.maxDurationForVisual(isAnimated = true))
        assertEquals(10_000L, SoundStickerSpecs.maxDurationForVisual(isAnimated = false))
    }

    @Test
    fun clampTrimRangeEnforcesMinimumAndMaximumWindow() {
        // Animated limit (5s) clamping a 12s audio
        val (s1, e1) = SoundStickerSpecs.clampTrimRange(
            startMs = 2_000L,
            endMs = 11_000L,
            totalDurationMs = 12_000L,
            maxClipDurationMs = 5_000L
        )
        assertEquals(2_000L, s1)
        assertEquals(7_000L, e1)

        // Enforce 1s minimum clip when user drags handles too close
        val (s2, e2) = SoundStickerSpecs.clampTrimRange(
            startMs = 3_000L,
            endMs = 3_200L,
            totalDurationMs = 8_000L,
            maxClipDurationMs = 5_000L
        )
        assertTrue(e2 - s2 >= SoundStickerSpecs.MIN_CLIP_DURATION_MS)
        assertEquals(3_000L, s2)
        assertEquals(4_000L, e2)

        // Audio shorter than MIN_CLIP_DURATION_MS stays bounded to totalDurationMs
        val (s3, e3) = SoundStickerSpecs.clampTrimRange(
            startMs = 0L,
            endMs = 600L,
            totalDurationMs = 600L,
            maxClipDurationMs = 5_000L
        )
        assertEquals(0L, s3)
        assertEquals(600L, e3)
    }

    @Test
    fun selectAnimationFrameIndexLoopsCorrectlyAcrossDurations() {
        val durations = listOf(100, 200, 100) // total loop = 400ms
        assertEquals(0, SoundStickerSpecs.selectAnimationFrameIndex(0L, durations))
        assertEquals(0, SoundStickerSpecs.selectAnimationFrameIndex(99L, durations))
        assertEquals(1, SoundStickerSpecs.selectAnimationFrameIndex(100L, durations))
        assertEquals(1, SoundStickerSpecs.selectAnimationFrameIndex(299L, durations))
        assertEquals(2, SoundStickerSpecs.selectAnimationFrameIndex(300L, durations))
        // Second loop iteration at 450ms -> 50ms into loop -> frame 0
        assertEquals(0, SoundStickerSpecs.selectAnimationFrameIndex(450L, durations))
    }

    @Test
    fun selectAnimationFrameIndexHoldLastFrameClampsAfterTotalDuration() {
        val durations = listOf(100, 200, 100) // total = 400ms, lastIndex = 2
        assertEquals(0, SoundStickerSpecs.selectAnimationFrameIndex(0L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        assertEquals(1, SoundStickerSpecs.selectAnimationFrameIndex(150L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        assertEquals(2, SoundStickerSpecs.selectAnimationFrameIndex(399L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        // Once elapsedMs >= totalDuration, HOLD_LAST_FRAME freezes on last frame while LOOP wraps
        assertEquals(2, SoundStickerSpecs.selectAnimationFrameIndex(400L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        assertEquals(2, SoundStickerSpecs.selectAnimationFrameIndex(450L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        assertEquals(2, SoundStickerSpecs.selectAnimationFrameIndex(2_500L, durations, StudioLoopMode.HOLD_LAST_FRAME))
        assertEquals(0, SoundStickerSpecs.selectAnimationFrameIndex(450L, durations, StudioLoopMode.LOOP))
    }

    @Test
    fun argbToPlanarYuv420ProducesExpectedStudioSwingLumaForWhiteAndBlack() {
        val w = 4
        val h = 4
        val y = ByteArray(w * h)
        val u = ByteArray((w / 2) * (h / 2))
        val v = ByteArray((w / 2) * (h / 2))

        val whitePixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        SoundStickerSpecs.argbToPlanarYuv420(whitePixels, w, h, y, u, v)
        assertEquals(235, y[0].toInt() and 0xFF)
        assertEquals(128, u[0].toInt() and 0xFF)
        assertEquals(128, v[0].toInt() and 0xFF)

        val blackPixels = IntArray(w * h) { 0xFF000000.toInt() }
        SoundStickerSpecs.argbToPlanarYuv420(blackPixels, w, h, y, u, v)
        assertEquals(16, y[0].toInt() and 0xFF)
        assertEquals(128, u[0].toInt() and 0xFF)
        assertEquals(128, v[0].toInt() and 0xFF)
    }

    @Test
    fun resamplePcmToStereoDoublesSampleCountAndDuplicatesMonoChannel() {
        // Mono 22050Hz -> Stereo 44100Hz doubles frame count and interleaves L/R
        val monoInput = shortArrayOf(1000, 2000, 3000, 4000)
        val stereoOut = SoundStickerSpecs.resamplePcmToStereo(
            input = monoInput,
            inputCount = monoInput.size,
            srcSampleRate = 22050,
            srcChannels = 1,
            dstSampleRate = 44100
        )
        // 4 mono frames at 22050 -> 8 stereo frames (16 shorts) at 44100
        assertEquals(16, stereoOut.size)
        for (frame in 0 until 8) {
            assertEquals(stereoOut[frame * 2], stereoOut[frame * 2 + 1])
        }
        assertEquals(1000.toShort(), stereoOut[0])
    }
}

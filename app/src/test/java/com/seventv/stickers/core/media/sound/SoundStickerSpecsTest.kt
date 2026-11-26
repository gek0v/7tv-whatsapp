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

    @Test
    fun applyPcmEdgeFadeFadesEdgesAndKeepsMiddleSamplesIntact() {
        assertEquals(8L, SoundStickerSpecs.AUDIO_EDGE_FADE_MS)

        // 100 stereo frames at 1000 Hz with 8 ms fade => 8 fade-in frames, 8 fade-out frames
        val totalFrames = 100L
        val chunk1 = ShortArray(40 * 2) { idx -> if (idx % 2 == 0) 10_000 else -10_000 }
        val chunk2 = ShortArray(60 * 2) { idx -> if (idx % 2 == 0) 10_000 else -10_000 }

        SoundStickerSpecs.applyPcmEdgeFade(
            stereoSamples = chunk1,
            stereoFrameOffset = 0L,
            totalTargetStereoFrames = totalFrames,
            sampleRate = 1000,
            fadeDurationMs = 8L
        )
        SoundStickerSpecs.applyPcmEdgeFade(
            stereoSamples = chunk2,
            stereoFrameOffset = 40L,
            totalTargetStereoFrames = totalFrames,
            sampleRate = 1000,
            fadeDurationMs = 8L
        )

        // Fade-in: frame 0 is 0, midpoint frame 4 is 5000/-5000, monotonically increasing up to frame 7
        assertEquals(0.toShort(), chunk1[0])
        assertEquals(0.toShort(), chunk1[1])
        assertEquals(5_000.toShort(), chunk1[4 * 2])
        assertEquals((-5_000).toShort(), chunk1[4 * 2 + 1])
        for (f in 0 until 7) {
            assertTrue(chunk1[f * 2] < chunk1[(f + 1) * 2])
        }
        assertEquals(8_750.toShort(), chunk1[7 * 2])

        // Middle frames (8..39 in chunk1, 40..91 -> local 0..51 in chunk2) remain 1.0x intact
        for (f in 8 until 40) {
            assertEquals(10_000.toShort(), chunk1[f * 2])
            assertEquals((-10_000).toShort(), chunk1[f * 2 + 1])
        }
        for (f in 0 until 52) {
            assertEquals(10_000.toShort(), chunk2[f * 2])
            assertEquals((-10_000).toShort(), chunk2[f * 2 + 1])
        }

        // Fade-out: starts at global frame 92 (local 52) down to 0 at global frame 99 (local 59)
        assertEquals(8_750.toShort(), chunk2[52 * 2])
        assertEquals(5_000.toShort(), chunk2[55 * 2])
        assertEquals((-5_000).toShort(), chunk2[55 * 2 + 1])
        for (f in 52 until 59) {
            assertTrue(chunk2[f * 2] > chunk2[(f + 1) * 2])
        }
        assertEquals(0.toShort(), chunk2[59 * 2])
        assertEquals(0.toShort(), chunk2[59 * 2 + 1])
    }

    @Test
    fun applyPcmEdgeFadeHandlesClipsShorterThanDoubleFadeWithoutOverlapOrInversion() {
        // 10 ms clip at 1000 Hz (10 frames < 2 * 8 ms = 16 ms) -> effective fade is 5 frames each side
        val evenShortClip = ShortArray(10 * 2) { 10_000 }
        SoundStickerSpecs.applyPcmEdgeFade(
            stereoSamples = evenShortClip,
            stereoFrameOffset = 0L,
            totalTargetStereoFrames = 10L,
            sampleRate = 1000,
            fadeDurationMs = 8L
        )
        val expectedEven = shortArrayOf(0, 2000, 4000, 6000, 8000, 8000, 6000, 4000, 2000, 0)
        for (f in 0 until 10) {
            assertEquals(expectedEven[f], evenShortClip[f * 2])
            assertEquals(expectedEven[f], evenShortClip[f * 2 + 1])
        }

        // 11 ms clip at 1000 Hz (11 frames) -> center frame 5 stays untouched at 10_000
        val oddShortClip = ShortArray(11 * 2) { 10_000 }
        SoundStickerSpecs.applyPcmEdgeFade(
            stereoSamples = oddShortClip,
            stereoFrameOffset = 0L,
            totalTargetStereoFrames = 11L,
            sampleRate = 1000,
            fadeDurationMs = 8L
        )
        val expectedOdd = shortArrayOf(0, 2000, 4000, 6000, 8000, 10000, 8000, 6000, 4000, 2000, 0)
        for (f in 0 until 11) {
            assertEquals(expectedOdd[f], oddShortClip[f * 2])
            assertEquals(expectedOdd[f], oddShortClip[f * 2 + 1])
        }

        // 10 ms clip at default 44100 Hz (441 frames < 2 * 352 = 704)
        val clip44k = ShortArray(441 * 2) { 20_000 }
        SoundStickerSpecs.applyPcmEdgeFade(
            stereoSamples = clip44k,
            stereoFrameOffset = 0L,
            totalTargetStereoFrames = 441L
        )
        assertEquals(0.toShort(), clip44k[0])
        assertEquals(20_000.toShort(), clip44k[220 * 2])
        assertEquals(0.toShort(), clip44k[440 * 2])
        for (s in clip44k) {
            assertTrue("Sample should stay non-negative without inversion: $s", s in 0..20_000)
        }
    }
}

package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.model.StickerSpecs
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/**
 * Throttles animation sequences to comply with WhatsApp technical limits.
 *
 * Provides duration truncation to enforce the strict 3000ms duration ceiling,
 * and framerate decimation to downsample sequences exceeding target FPS limits.
 */
class FrameThrottler {

    /**
     * Truncates animation frames so cumulative duration does not exceed [maxDurationMs].
     *
     * Iterates frames and accumulates duration up to [maxDurationMs].
     * If a frame partially spans across [maxDurationMs], clamps its [AnimationFrame.durationMs]
     * so cumulative duration equals [maxDurationMs], updates [AnimationFrame.timestampMs], and truncates any
     * subsequent frames. Recycles dropped frame bitmaps to prevent memory leaks.
     *
     * @param frames The sequence of animation frames.
     * @param maxDurationMs Maximum allowed animation duration in milliseconds.
     * @return Truncated list of animation frames with monotonically increasing timestamps.
     */
    fun truncateDuration(
        frames: List<AnimationFrame>,
        maxDurationMs: Int = StickerSpecs.ANIMATED_MAX_DURATION_MS
    ): List<AnimationFrame> {
        if (frames.isEmpty()) return emptyList()
        if (maxDurationMs <= 0) {
            recycleBitmaps(frames, emptyList())
            return emptyList()
        }

        val result = mutableListOf<AnimationFrame>()
        val dropped = mutableListOf<AnimationFrame>()
        var currentTimestamp = 0

        for (frame in frames) {
            if (currentTimestamp >= maxDurationMs) {
                dropped.add(frame)
                continue
            }
            val remaining = maxDurationMs - currentTimestamp
            if (frame.durationMs <= remaining) {
                currentTimestamp += frame.durationMs
                result.add(frame.copy(timestampMs = currentTimestamp))
            } else {
                val clampedDuration = remaining
                currentTimestamp += clampedDuration
                result.add(frame.copy(durationMs = clampedDuration, timestampMs = currentTimestamp))
            }
        }

        recycleBitmaps(dropped, result)
        return result
    }

    /**
     * Decimates the framerate of [frames] if source FPS exceeds [targetFps].
     *
     * Calculates source FPS from average frame duration or total duration.
     * If source FPS <= [targetFps], returns frames as-is.
     * If source FPS > [targetFps], calculates downsample stride, keeps every k-th frame,
     * accumulates durations of dropped frames into the retained frame's durationMs,
     * recomputes timestamps, and recycles dropped bitmaps.
     *
     * @param frames The sequence of animation frames.
     * @param targetFps Maximum desired frame rate in frames per second.
     * @return Decimated frame list, or unchanged if already under [targetFps].
     */
    fun decimateFramerate(
        frames: List<AnimationFrame>,
        targetFps: Int
    ): List<AnimationFrame> {
        if (frames.isEmpty()) return emptyList()
        if (frames.size == 1 || targetFps <= 0) return frames

        val totalDurationMs = frames.sumOf { it.durationMs }
        if (totalDurationMs <= 0) return frames

        val sourceFps = ((frames.size * 1000.0) / totalDurationMs).roundToInt()
        if (sourceFps <= targetFps) return frames

        val step = (sourceFps.toDouble() / targetFps.toDouble()).roundToInt().coerceAtLeast(2)
        val chunks = frames.chunked(step)
        val result = mutableListOf<AnimationFrame>()
        val dropped = mutableListOf<AnimationFrame>()
        var currentTimestamp = 0

        for (chunk in chunks) {
            val kept = chunk.first()
            val totalChunkDuration = chunk.sumOf { it.durationMs }
            currentTimestamp += totalChunkDuration
            result.add(kept.copy(durationMs = totalChunkDuration, timestampMs = currentTimestamp))
            for (i in 1 until chunk.size) {
                dropped.add(chunk[i])
            }
        }

        recycleBitmaps(dropped, result)
        return result
    }

    private fun recycleBitmaps(
        droppedFrames: List<AnimationFrame>,
        retainedFrames: List<AnimationFrame>
    ) {
        val retainedBitmaps = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
        for (retained in retainedFrames) {
            retainedBitmaps.add(retained.bitmap)
        }
        for (dropped in droppedFrames) {
            if (!retainedBitmaps.contains(dropped.bitmap) && !dropped.bitmap.isRecycled) {
                dropped.bitmap.recycle()
            }
        }
    }
}

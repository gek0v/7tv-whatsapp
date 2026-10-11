package com.seventv.stickers.core.media.normalizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.seventv.stickers.core.media.model.StickerSpecs
import kotlin.math.roundToInt

/**
 * Geometric normalization engine that uses Android Canvas to render source bitmaps
 * centered onto compliant canvas dimensions while preserving aspect ratio.
 */
class CanvasStickerNormalizer : StickerNormalizer {

    override fun normalizeToStickerCanvas(source: Bitmap): Bitmap {
        return normalize(
            source = source,
            canvasSize = StickerSpecs.STICKER_SIZE,
            usableArea = StickerSpecs.USABLE_AREA
        )
    }

    override fun normalizeToTrayIcon(source: Bitmap): Bitmap {
        return normalize(
            source = source,
            canvasSize = StickerSpecs.TRAY_ICON_SIZE,
            usableArea = StickerSpecs.TRAY_ICON_USABLE_AREA
        )
    }

    private fun normalize(
        source: Bitmap,
        canvasSize: Int,
        usableArea: Int
    ): Bitmap {
        require(source.width > 0 && source.height > 0) { "Source bitmap dimensions must be positive" }

        val targetBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val scale = minOf(
            usableArea.toFloat() / source.width,
            usableArea.toFloat() / source.height
        )
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()
        val left = (canvasSize - scaledWidth) / 2f
        val top = (canvasSize - scaledHeight) / 2f

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(source, null, destRect, paint)

        return targetBitmap
    }
}

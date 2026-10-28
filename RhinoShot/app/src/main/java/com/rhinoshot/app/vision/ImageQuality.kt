package com.rhinoshot.app.vision

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheap image-quality metrics computed on a small grayscale copy of the frame.
 *
 *  - brightness : mean luminance of the face crop (0..1)
 *  - sharpness  : Tenengrad (mean gradient magnitude) divided by the mean
 *                 luminance, which keeps the value comparable between a bright
 *                 and a dark clinic.
 */
object ImageQuality {

    data class Metrics(val brightness: Float, val sharpness: Float)

    fun measure(bitmap: Bitmap, box: RectF?): Metrics {
        val src = crop(bitmap, box) ?: bitmap
        val w = 160
        val h = max(24, (src.height * w / src.width).coerceIn(24, 480))
        val small = Bitmap.createScaledBitmap(src, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        var sum = 0.0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val v = (r * 77 + g * 151 + b * 28) shr 8
            gray[i] = v
            sum += v
        }
        val mean = sum / (w * h)

        var gradient = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = gray[i + 1] - gray[i - 1]
                val gy = gray[i + w] - gray[i - w]
                gradient += sqrt((gx * gx + gy * gy).toDouble())
                n++
            }
        }
        val meanGradient = if (n > 0) gradient / n else 0.0

        return Metrics(
            brightness = (mean / 255.0).toFloat().coerceIn(0f, 1f),
            sharpness = (meanGradient / (mean + 8.0)).toFloat(),
        )
    }

    private fun crop(bitmap: Bitmap, box: RectF?): Bitmap? {
        if (box == null || box.width() <= 0f || box.height() <= 0f) return null
        val x0 = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
        val y0 = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
        val cw = (box.width() * bitmap.width).toInt().coerceIn(4, bitmap.width - x0)
        val ch = (box.height() * bitmap.height).toInt().coerceIn(4, bitmap.height - y0)
        return runCatching { Bitmap.createBitmap(bitmap, x0, y0, cw, ch) }.getOrNull()
    }
}

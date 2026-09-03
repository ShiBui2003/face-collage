package com.iykyk.facecollage.pipeline

import android.graphics.Bitmap
import com.iykyk.facecollage.data.BoxF
import kotlin.math.roundToInt

/** Crops to [box], clamped to the bitmap. Returns null if the box has no area inside the frame. */
fun Bitmap.cropTo(box: BoxF): Bitmap? {
    val left = box.left.roundToInt().coerceIn(0, width - 1)
    val top = box.top.roundToInt().coerceIn(0, height - 1)
    val right = box.right.roundToInt().coerceIn(left + 1, width)
    val bottom = box.bottom.roundToInt().coerceIn(top + 1, height)
    if (right - left < 2 || bottom - top < 2) return null
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

/**
 * Variance of the Laplacian over the face region: the standard cheap focus measure.
 * Higher means crisper. The region is downsampled to a fixed size first so the value does
 * not scale with face size, and so a whip-pan frame is rejected in well under a millisecond.
 *
 * The absolute value still shifts with lighting and contrast, so callers must normalise
 * across a candidate set rather than compare against a global constant.
 */
fun laplacianVariance(bitmap: Bitmap, box: BoxF): Float {
    val region = bitmap.cropTo(box) ?: return 0f
    val small = Bitmap.createScaledBitmap(region, SAMPLE_SIZE, SAMPLE_SIZE, true)
    if (region !== small) region.recycle()

    val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
    small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    small.recycle()

    val gray = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        // Rec. 601 luma
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }

    var sum = 0.0
    var sumSq = 0.0
    var count = 0
    for (y in 1 until SAMPLE_SIZE - 1) {
        for (x in 1 until SAMPLE_SIZE - 1) {
            val i = y * SAMPLE_SIZE + x
            val laplace = 4f * gray[i] -
                gray[i - 1] - gray[i + 1] -
                gray[i - SAMPLE_SIZE] - gray[i + SAMPLE_SIZE]
            sum += laplace
            sumSq += laplace.toDouble() * laplace
            count++
        }
    }
    if (count == 0) return 0f
    val mean = sum / count
    return ((sumSq / count) - mean * mean).toFloat().coerceAtLeast(0f)
}

private const val SAMPLE_SIZE = 48

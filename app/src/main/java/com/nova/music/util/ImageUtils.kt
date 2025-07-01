package com.nova.music.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A smart transformation that removes extended color bars from all four sides
 * and returns the tightest bounding box containing real content,
 * then center-crops to square.
 */
class CenterCropSquareTransformation : Transformation {
    override val cacheKey: String = "center_content_crop_v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height

        // Detect bars for all four sides
        val left = detectBarEdge(input, horizontal = true, fromStart = true)
        val right = detectBarEdge(input, horizontal = true, fromStart = false)
        val top = detectBarEdge(input, horizontal = false, fromStart = true)
        val bottom = detectBarEdge(input, horizontal = false, fromStart = false)

        // Ensure valid box (avoid overcrop)
        val cropLeft = min(left, w / 3)
        val cropRight = min(right, w / 3)
        val cropTop = min(top, h / 3)
        val cropBottom = min(bottom, h / 3)

        val boxLeft = cropLeft
        val boxTop = cropTop
        val boxRight = w - cropRight
        val boxBottom = h - cropBottom

        val cropWidth = boxRight - boxLeft
        val cropHeight = boxBottom - boxTop

        // Sanity check
        if (cropWidth <= 0 || cropHeight <= 0) {
            return input
        }

        // Crop to tight bounding box
        val cropped = Bitmap.createBitmap(input, boxLeft, boxTop, cropWidth, cropHeight)

        // Center-crop to square, if needed
        val finalSize = min(cropWidth, cropHeight)
        val cx = cropWidth / 2
        val cy = cropHeight / 2
        val sx = max(0, cx - finalSize / 2)
        val sy = max(0, cy - finalSize / 2)

        val square = Bitmap.createBitmap(cropped, sx, sy, finalSize, finalSize)

        return square
    }

    /**
     * Detect how many pixels from an edge are an extended color bar
     * horizontal=true: scan left/right edge; fromStart=true is left, false is right
     * horizontal=false: scan top/bottom edge; fromStart=true is top, false is bottom
     */
    private fun detectBarEdge(
        bitmap: Bitmap,
        horizontal: Boolean,
        fromStart: Boolean
    ): Int {
        val w = bitmap.width
        val h = bitmap.height
        val sampleCount = 10
        val threshold = 30.0 // color distance threshold
        val maxBarFraction = 0.33 // Don't crop more than 1/3 in

        val barLimit = if (horizontal) (w * maxBarFraction).toInt() else (h * maxBarFraction).toInt()
        val sampleStep = if (horizontal) h / sampleCount else w / sampleCount

        // Get reference color from the edge
        val refColors = IntArray(sampleCount) { i ->
            val x = if (horizontal) {
                if (fromStart) 0 else w - 1
            } else {
                i * sampleStep
            }
            val y = if (horizontal) {
                i * sampleStep
            } else {
                if (fromStart) 0 else h - 1
            }
            bitmap.getPixel(x, y)
        }

        // Scan inwards pixel by pixel
        for (offset in 0 until barLimit) {
            var diffCount = 0
            for (i in 0 until sampleCount) {
                val x = if (horizontal) {
                    if (fromStart) offset else w - 1 - offset
                } else {
                    i * sampleStep
                }
                val y = if (horizontal) {
                    i * sampleStep
                } else {
                    if (fromStart) offset else h - 1 - offset
                }
                val color = bitmap.getPixel(x, y)
                if (!areColorsSimilar(color, refColors[i], threshold)) {
                    diffCount++
                }
            }
            // If >40% of samples differ, we've reached real content
            if (diffCount > sampleCount * 0.4) {
                return offset
            }
        }
        // No clear change: conservative estimate
        return barLimit / 3
    }

    /**
     * Are two colors similar (Euclidean distance in RGB)
     */
    private fun areColorsSimilar(c1: Int, c2: Int, threshold: Double): Boolean {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        val dist = sqrt((dr * dr + dg * dg + db * db).toDouble())
        return dist < threshold
    }
} 
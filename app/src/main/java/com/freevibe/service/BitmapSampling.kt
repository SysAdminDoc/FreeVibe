package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapSampling {
    fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val targetWidth = reqWidth.coerceAtLeast(1)
        val targetHeight = reqHeight.coerceAtLeast(1)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                rawWidth = bounds.outWidth,
                rawHeight = bounds.outHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight,
            )
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0) return 1

        val targetWidth = reqWidth.coerceAtLeast(1)
        val targetHeight = reqHeight.coerceAtLeast(1)
        var sampleSize = 1

        while (rawWidth / (sampleSize * 2) >= targetWidth &&
            rawHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }

        return sampleSize
    }
}

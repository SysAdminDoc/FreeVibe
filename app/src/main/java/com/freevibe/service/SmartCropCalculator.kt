package com.freevibe.service

import android.graphics.RectF

/**
 * Pure geometry helper for Smart Crop (ROADMAP NX-3).
 *
 * Given a source bitmap size, the detected subject bounding box in source
 * pixel coordinates, and the on-screen viewport size, computes (scale,
 * offsetX, offsetY) such that the subject lands at the viewport centre at a
 * sensible coverage ratio (default ~75 %).
 *
 * Coordinate convention matches [com.freevibe.ui.screens.editor.WallpaperCropViewModel.cropBitmap]:
 *
 * ```
 * imgLeft = (viewportWidth  - bitmapWidth  * scale) / 2 + offsetX
 * imgTop  = (viewportHeight - bitmapHeight * scale) / 2 + offsetY
 * ```
 *
 * Pure — no Android dependencies beyond [RectF] — unit-testable in isolation.
 */
object SmartCropCalculator {

    data class Transform(val scale: Float, val offsetX: Float, val offsetY: Float)

    fun computeTransform(
        bitmapWidth: Int,
        bitmapHeight: Int,
        subject: RectF,
        viewportWidth: Int,
        viewportHeight: Int,
        targetCoverage: Float = 0.75f,
        maxScale: Float = 4f,
    ): Transform {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "bitmap dims must be > 0" }
        require(viewportWidth > 0 && viewportHeight > 0) { "viewport dims must be > 0" }
        require(targetCoverage in 0.1f..1f) { "coverage must be 0.1..1.0" }

        val subjectW = (subject.right - subject.left).coerceAtLeast(1f)
        val subjectH = (subject.bottom - subject.top).coerceAtLeast(1f)

        // Scale that lets the subject fill `targetCoverage` of the viewport on its tighter axis.
        val scaleByW = (viewportWidth * targetCoverage) / subjectW
        val scaleByH = (viewportHeight * targetCoverage) / subjectH
        var scale = minOf(scaleByW, scaleByH)

        // Floor: never leave letterboxing — the wallpaper must at minimum fill the viewport.
        val minScale = maxOf(
            viewportWidth.toFloat() / bitmapWidth,
            viewportHeight.toFloat() / bitmapHeight,
        )
        if (scale < minScale) scale = minScale
        if (scale > maxScale) scale = maxScale

        // Subject centre in source pixels.
        val cx = (subject.left + subject.right) / 2f
        val cy = (subject.top + subject.bottom) / 2f

        // Land the subject centre at the viewport centre. Derivation:
        //   subject_screen_x = imgLeft + cx * scale
        //   viewportWidth / 2 = (viewportWidth - bitmapWidth * scale) / 2 + offsetX + cx * scale
        //   offsetX = scale * (bitmapWidth / 2 - cx)
        val offsetX = scale * (bitmapWidth / 2f - cx)
        val offsetY = scale * (bitmapHeight / 2f - cy)
        return Transform(scale = scale, offsetX = offsetX, offsetY = offsetY)
    }
}

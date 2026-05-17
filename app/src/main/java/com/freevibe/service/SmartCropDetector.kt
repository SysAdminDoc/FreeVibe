package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs ML Kit Subject Segmentation against a bitmap and returns the bounding
 * box of the primary subject (foreground-confidence-mask pixels ≥ threshold)
 * in source bitmap coordinates.
 *
 * Returns null when no subject is detected, the mask is empty, or the model
 * is unavailable (the unbundled segmenter ships via Google Play services and
 * may not yet be installed on a fresh device). Caller falls back to the
 * existing centre-crop in that case.
 *
 * Aura uses Subject Segmentation rather than Face Detection because wallpaper
 * subjects are not always faces (landscapes, objects, pets). See
 * [ParallaxWallpaperService] for the matching segmenter on the live-wallpaper
 * side.
 */
@Singleton
class SmartCropDetector @Inject constructor() {

    suspend fun detectSubject(bitmap: Bitmap, threshold: Float = 0.5f): RectF? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine<Any?> { cont ->
                segmenter.process(image)
                    .addOnSuccessListener { res -> if (cont.isActive) cont.resume(res) }
                    .addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                    .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { /* segmenter.close in finally */ }
            } ?: return null

            // result is com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult.
            // Reflect minimally to keep this file robust against minor ML Kit API drift; the
            // type is held only in the unbundled APK at runtime.
            val maskGetter = result.javaClass.getMethod("getForegroundConfidenceMask")
            @Suppress("UNCHECKED_CAST")
            val mask = maskGetter.invoke(result) as? java.nio.FloatBuffer ?: return null
            mask.rewind()

            val w = bitmap.width
            val h = bitmap.height
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var any = false

            // The mask is sized to the input bitmap (one float per pixel, row-major).
            // Scan row-by-row and collapse to a bbox.
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = if (mask.hasRemaining()) mask.get() else 0f
                    if (v >= threshold) {
                        any = true
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (!any) null
            else RectF(
                minX.toFloat(),
                minY.toFloat(),
                (maxX + 1).toFloat(),
                (maxY + 1).toFloat(),
            )
        } catch (_: Exception) {
            null
        } finally {
            try { segmenter.close() } catch (_: Exception) {}
        }
    }
}

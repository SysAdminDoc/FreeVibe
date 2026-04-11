package com.freevibe.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.freevibe.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    /** Download image from URL and apply as wallpaper */
    suspend fun applyFromUrl(
        url: String,
        target: WallpaperTarget = WallpaperTarget.BOTH,
        cropRect: Rect? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = downloadBitmap(url)
                ?: throw IllegalStateException("Failed to decode wallpaper image")
            try {
                val flag = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }

                if (cropRect != null) {
                    wallpaperManager.setBitmap(bitmap, cropRect, true, flag)
                } else {
                    wallpaperManager.setBitmap(bitmap, null, true, flag)
                }
                Unit
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Apply wallpaper from an already-loaded bitmap */
    suspend fun applyFromBitmap(
        bitmap: Bitmap,
        target: WallpaperTarget = WallpaperTarget.BOTH,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val flag = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wallpaperManager.setBitmap(bitmap, null, true, flag)
            Unit
        }
    }

    /**
     * Download image from URL, save to internal storage, and store path in
     * SharedPreferences for ParallaxWallpaperService to read.
     * Returns the saved file path on success.
     */
    suspend fun prepareParallaxWallpaper(url: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
                val dir = java.io.File(context.filesDir, "parallax")
                dir.mkdirs()
                val file = java.io.File(dir, fileName)
                // Atomic temp-then-rename: if copy is interrupted mid-stream, the
                // ParallaxWallpaperService used to read a truncated file on the next surface
                // creation. Write to a sibling .tmp, rename on success, and clean up the .tmp
                // on any failure so orphaned partial writes never accumulate.
                val tempFile = java.io.File(dir, "$fileName.tmp")
                val body = resp.body ?: throw java.io.IOException("Empty response body")
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!tempFile.renameTo(file)) {
                        // renameTo can fail across filesystems; fall back to copy+delete.
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    try { tempFile.delete() } catch (_: Exception) {}
                    throw e
                }
                // Store path for ParallaxWallpaperService
                context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE)
                    .edit()
                    .putString("image_path", file.absolutePath)
                    .apply()
                file.absolutePath
            }
        }
    }

    /** Check if wallpaper operations are supported */
    fun isSupported(): Boolean {
        return wallpaperManager.isWallpaperSupported && wallpaperManager.isSetWallpaperAllowed
    }

    /** Get screen dimensions for optimal crop suggestions */
    fun getScreenDimensions(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty response body")
            val bytes = body.bytes()
            if (bytes.isEmpty()) throw java.io.IOException("Empty response body")

            // First pass: get image dimensions without allocating pixels
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            // Guard against a corrupt first-pass decode. Without this, a zero-width result
            // skips the sub-sampling math and the second decode either crashes or produces
            // a full-resolution bitmap that can OOM on large wallpapers.
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw java.io.IOException("Invalid image: could not decode bounds")
            }

            // Calculate inSampleSize to keep bitmap within 2x screen width
            val screenWidth = context.resources.displayMetrics.widthPixels
            val targetWidth = screenWidth * 2
            var sampleSize = 1
            if (bounds.outWidth > targetWidth) {
                var width = bounds.outWidth
                while (width / 2 >= targetWidth) {
                    sampleSize *= 2
                    width /= 2
                }
            }

            // Second pass: decode with sub-sampling
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}

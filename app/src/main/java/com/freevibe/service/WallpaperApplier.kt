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

    /**
     * Apply a wallpaper from any locator — http(s) URL, file:// URI, content:// URI,
     * or a bare absolute path. Earlier revisions only spoke HTTP via [applyFromUrl];
     * callers that need to handle locally-stored wallpapers (AI-generated, gallery,
     * parallax cache, user uploads) should use this entrypoint instead.
     */
    suspend fun applyByLocator(
        locator: String,
        target: WallpaperTarget = WallpaperTarget.BOTH,
        cropRect: Rect? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeFromLocator(locator)
                ?: throw IllegalStateException("Failed to decode wallpaper image")
            try {
                val flag = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(bitmap, cropRect, true, flag)
                Unit
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
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
                        tempFile.outputStream().use { output ->
                            copyCapped(input, output, MAX_WALLPAPER_BYTES)
                        }
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

    /**
     * Parallax variant for a user-supplied local URI (gallery / share intent). Same output
     * path + atomic write as prepareParallaxWallpaper(url), so ParallaxWallpaperService
     * reads from the same SharedPreferences key.
     */
    suspend fun prepareParallaxFromUri(uri: android.net.Uri, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.filesDir, "parallax")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            val tempFile = java.io.File(dir, "$fileName.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open photo")
                input.use { inStream ->
                    tempFile.outputStream().use { output ->
                        copyCapped(inStream, output, MAX_WALLPAPER_BYTES)
                    }
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                try { tempFile.delete() } catch (_: Exception) {}
                throw e
            }
            context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE)
                .edit()
                .putString("image_path", file.absolutePath)
                .apply()
            file.absolutePath
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

    /**
     * Dispatch decode by scheme. Returns null on unknown scheme or decode failure.
     * Visible for tests (internal).
     */
    internal suspend fun decodeFromLocator(locator: String): Bitmap? {
        if (locator.isBlank()) return null
        return when {
            locator.startsWith("http://", ignoreCase = true) ||
                locator.startsWith("https://", ignoreCase = true) ->
                downloadBitmap(locator)
            locator.startsWith("content://", ignoreCase = true) ->
                decodeFromContentUri(locator)
            locator.startsWith("file:", ignoreCase = true) -> {
                // Both file:/path and file:///path produce a parseable Uri; we want the
                // raw path for BitmapFactory.decodeFile.
                val path = android.net.Uri.parse(locator).path
                if (path.isNullOrBlank()) null else decodeLocalPath(path)
            }
            locator.startsWith("/") -> decodeLocalPath(locator)
            else -> null
        }
    }

    private suspend fun decodeFromContentUri(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull() ?: return@withContext null
        // Two-pass bounded decode mirrors downloadBitmap to avoid OOM on huge gallery picks.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sampleSize = computeSampleSize(bounds.outWidth)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        runCatching {
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private suspend fun decodeLocalPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead()) return@withContext null
        // Cap local files at MAX_WALLPAPER_BYTES — even if the file is on user storage we
        // don't want a runaway 200 MB PNG to wedge the WallpaperManager IPC.
        if (file.length() > MAX_WALLPAPER_BYTES) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sampleSize = computeSampleSize(bounds.outWidth)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(path, options)
    }

    private fun computeSampleSize(srcWidth: Int): Int {
        val targetWidth = (context.resources.displayMetrics.widthPixels * 2).coerceAtLeast(1)
        var sampleSize = 1
        var width = srcWidth
        while (width / 2 >= targetWidth) {
            sampleSize *= 2
            width /= 2
        }
        return sampleSize
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty response body")
            // Reject oversized payloads up front so a hostile or misbehaving CDN can't make us
            // allocate a huge byte[] just to OOM during decode. 64 MB is larger than any real
            // 8K JPG/PNG/WEBP wallpaper. We still stream-cap below in case Content-Length
            // is missing (chunked transfer) or lies.
            val advertised = body.contentLength()
            if (advertised in 1..Long.MAX_VALUE && advertised > MAX_WALLPAPER_BYTES) {
                throw java.io.IOException("Wallpaper too large: $advertised > $MAX_WALLPAPER_BYTES bytes")
            }
            // Stream into a bounded buffer rather than calling body.bytes(), which has no
            // upper bound and will happily allocate gigabytes when Content-Length is unknown
            // or wrong. Abort the read the moment we exceed the cap.
            val bytes = readCapped(body.byteStream(), MAX_WALLPAPER_BYTES)
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

            // Calculate inSampleSize to keep bitmap within 2x screen width.
            val sampleSize = computeSampleSize(bounds.outWidth)

            // Second pass: decode with sub-sampling
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    /**
     * Copy bytes from [input] to [output], aborting (and throwing) if more than [cap]
     * bytes have been written. The output stream's bytes-so-far are intentionally
     * left in place so callers can rely on their own try/finally cleanup.
     */
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, cap: Long) {
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > cap) {
                throw java.io.IOException("Source exceeds cap of $cap bytes")
            }
            output.write(chunk, 0, read)
        }
    }

    /**
     * Read at most [cap] bytes from [input] into a ByteArray, throwing IOException if
     * the source produces more. Used to defend against unbounded responses where
     * Content-Length is absent or lies.
     */
    private fun readCapped(input: java.io.InputStream, cap: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream(64 * 1024)
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > cap) {
                throw java.io.IOException("Wallpaper too large: exceeds $cap bytes")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        /** Hard cap on single-wallpaper downloads — mirrors DownloadManager's ceiling. */
        private const val MAX_WALLPAPER_BYTES = 64L * 1024 * 1024
    }
}

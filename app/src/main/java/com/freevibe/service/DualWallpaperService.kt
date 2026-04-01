package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperPair
import com.freevibe.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DualWallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperApplier: WallpaperApplier,
    private val okHttpClient: OkHttpClient,
) {
    /** Apply a wallpaper pair: home wallpaper + lock wallpaper simultaneously */
    suspend fun applyPair(pair: WallpaperPair): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Download both concurrently
            val homeBitmap = async { downloadBitmap(pair.home.fullUrl) }
            val lockBitmap = async { downloadBitmap(pair.lock.fullUrl) }

            val home = try {
                homeBitmap.await()
            } catch (e: Exception) {
                lockBitmap.cancel()
                throw e
            }
            val lock = try {
                lockBitmap.await()
            } catch (e: Exception) {
                home.recycle()
                throw e
            }
            try {
                wallpaperApplier.applyFromBitmap(home, WallpaperTarget.HOME).getOrThrow()
                wallpaperApplier.applyFromBitmap(lock, WallpaperTarget.LOCK).getOrThrow()
            } finally {
                home.recycle()
                lock.recycle()
            }
        }
    }

    /** Apply same wallpaper with different crops to home and lock */
    suspend fun applySplitCrop(
        wallpaper: Wallpaper,
        homeCropTop: Float = 0f,       // 0.0 - 0.5 range
        lockCropTop: Float = 0.3f,     // Offset for lock screen
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = downloadBitmap(wallpaper.fullUrl)
            var homeCrop: Bitmap? = null
            var lockCrop: Bitmap? = null
            try {
                val height = bitmap.height
                val width = bitmap.width
                val cropHeight = (height * 0.7f).toInt().coerceIn(1, height)

                val homeY = (height * homeCropTop).toInt().coerceIn(0, (height - cropHeight).coerceAtLeast(0))
                val lockY = (height * lockCropTop).toInt().coerceIn(0, (height - cropHeight).coerceAtLeast(0))

                homeCrop = Bitmap.createBitmap(bitmap, 0, homeY, width, cropHeight)
                lockCrop = Bitmap.createBitmap(bitmap, 0, lockY, width, cropHeight)

                wallpaperApplier.applyFromBitmap(homeCrop, WallpaperTarget.HOME).getOrThrow()
                wallpaperApplier.applyFromBitmap(lockCrop, WallpaperTarget.LOCK).getOrThrow()
            } finally {
                if (homeCrop !== bitmap) homeCrop?.recycle()
                if (lockCrop !== bitmap) lockCrop?.recycle()
                bitmap.recycle()
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
            resp.body?.byteStream()?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Empty body")
        } ?: throw IllegalStateException("Failed to decode image")
    }
}

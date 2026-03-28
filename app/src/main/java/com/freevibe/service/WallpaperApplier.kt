package com.freevibe.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
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

    /** Check if wallpaper operations are supported */
    fun isSupported(): Boolean {
        return wallpaperManager.isWallpaperSupported &&
            (Build.VERSION.SDK_INT < 24 || wallpaperManager.isSetWallpaperAllowed)
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
            resp.body?.byteStream()?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }
}

package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts Material You-style color palette from wallpaper images.
 * Uses Android Palette API to extract dominant, vibrant, and muted colors.
 * Shows users what their system theme colors would look like before applying.
 */
@Singleton
class ColorExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    data class WallpaperPalette(
        val dominantColor: Int = 0,
        val vibrantColor: Int = 0,
        val vibrantDark: Int = 0,
        val vibrantLight: Int = 0,
        val mutedColor: Int = 0,
        val mutedDark: Int = 0,
        val mutedLight: Int = 0,
        val dominantSwatch: Palette.Swatch? = null,
        /**
         * Best accent for Material You preview.
         *
         * Falls back through the ladder when [dominantColor] would render as
         * dim gray on cartoon / solid-color images: dominant (if HSL passes
         * the quality gate) → darkVibrant → vibrant → lightVibrant →
         * mutedDark → muted → mutedLight → dominant. See
         * [ColorAccentSelector.selectAccent] for the gate logic.
         *
         * Always non-zero when ANY swatch is non-zero.
         */
        val bestAccentColor: Int = 0,
    )

    /** Extract color palette from a wallpaper URL */
    suspend fun extractFromUrl(url: String): WallpaperPalette? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@withContext null
                // Palette only needs a ~200x200 downsample; cap the buffered payload at 32 MB
                // so a hostile redirect to a giant image can't explode heap just for tinting.
                val advertised = body.contentLength()
                if (advertised in 1..Long.MAX_VALUE && advertised > MAX_COLOR_EXTRACT_BYTES) {
                    return@withContext null
                }
                val bytes = body.bytes()
                if (bytes.size > MAX_COLOR_EXTRACT_BYTES) return@withContext null
                // Decode at reduced size for faster palette extraction
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
                options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 200, 200)
                options.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return@withContext null
                extractFromBitmap(bitmap).also { bitmap.recycle() }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** Extract color palette from a bitmap */
    fun extractFromBitmap(bitmap: Bitmap): WallpaperPalette {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val dominant = palette.getDominantColor(0)
        val vibrant = palette.getVibrantColor(0)
        val vibrantDark = palette.getDarkVibrantColor(0)
        val vibrantLight = palette.getLightVibrantColor(0)
        val muted = palette.getMutedColor(0)
        val mutedDark = palette.getDarkMutedColor(0)
        val mutedLight = palette.getLightMutedColor(0)
        return WallpaperPalette(
            dominantColor = dominant,
            vibrantColor = vibrant,
            vibrantDark = vibrantDark,
            vibrantLight = vibrantLight,
            mutedColor = muted,
            mutedDark = mutedDark,
            mutedLight = mutedLight,
            dominantSwatch = palette.dominantSwatch,
            bestAccentColor = ColorAccentSelector.selectAccent(
                dominant = dominant,
                vibrantDark = vibrantDark,
                vibrant = vibrant,
                vibrantLight = vibrantLight,
                mutedDark = mutedDark,
                muted = muted,
                mutedLight = mutedLight,
                hslOf = { color ->
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(color, hsl)
                    hsl
                },
            ),
        )
    }

    private fun calculateSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        if (rawH > reqH || rawW > reqW) {
            val halfH = rawH / 2
            val halfW = rawW / 2
            // Guard the multiply-by-2: a pathological image with dimensions near Integer.MAX_VALUE
            // would otherwise overflow `sample` into negatives and break the sub-sampling math.
            while (halfH / sample >= reqH && halfW / sample >= reqW && sample < (1 shl 28)) {
                sample *= 2
            }
        }
        return sample
    }

    private companion object {
        /** Palette extraction only downsamples to 200x200; 32 MB is plenty for 4K originals. */
        private const val MAX_COLOR_EXTRACT_BYTES = 32L * 1024 * 1024
    }
}

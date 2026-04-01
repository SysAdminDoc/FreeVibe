package com.freevibe.ui.screens.editor

import android.graphics.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.WallpaperApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorState(
    val originalBitmap: Bitmap? = null,
    val editedBitmap: Bitmap? = null,
    val brightness: Float = 0f,       // -100 to 100
    val contrast: Float = 1f,         // 0.5 to 2.0
    val saturation: Float = 1f,       // 0 to 2.0
    val blurRadius: Float = 0f,       // 0 to 25
    val vignette: Float = 0f,         // 0 to 1.0
    val grain: Float = 0f,            // 0 to 1.0
    val amoledCrush: Float = 0f,      // 0 to 1.0 — pushes dark pixels to pure black
    val warmth: Float = 0f,           // -50 to 50 — color temperature shift
    val isProcessing: Boolean = false,
    val isApplying: Boolean = false,
    val isLoadingImage: Boolean = false,
    val success: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WallpaperEditorViewModel @Inject constructor(
    private val wallpaperApplier: WallpaperApplier,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()
    private var filterJob: kotlinx.coroutines.Job? = null
    private var loadedWallpaperId: String? = null

    fun loadWallpaper(wallpaper: Wallpaper): Boolean {
        val currentState = _state.value
        if (loadedWallpaperId == wallpaper.id && (currentState.originalBitmap != null || currentState.isLoadingImage)) {
            return true
        }
        loadedWallpaperId = wallpaper.id
        loadFromUrl(wallpaper.fullUrl)
        return true
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun setSourceBitmap(bitmap: Bitmap) {
        _state.update { it.copy(originalBitmap = bitmap, editedBitmap = bitmap) }
    }

    fun updateBrightness(value: Float) {
        _state.update { it.copy(brightness = value) }
        applyFilters()
    }

    fun updateContrast(value: Float) {
        _state.update { it.copy(contrast = value) }
        applyFilters()
    }

    fun updateSaturation(value: Float) {
        _state.update { it.copy(saturation = value) }
        applyFilters()
    }

    fun updateBlur(value: Float) {
        _state.update { it.copy(blurRadius = value) }
        applyFilters()
    }

    fun updateVignette(value: Float) { _state.update { it.copy(vignette = value) }; applyFilters() }
    fun updateGrain(value: Float) { _state.update { it.copy(grain = value) }; applyFilters() }
    fun updateAmoledCrush(value: Float) { _state.update { it.copy(amoledCrush = value) }; applyFilters() }
    fun updateWarmth(value: Float) { _state.update { it.copy(warmth = value) }; applyFilters() }

    fun applyPreset(brightness: Float, contrast: Float, saturation: Float, blur: Float,
                    vignette: Float = 0f, grain: Float = 0f, amoledCrush: Float = 0f, warmth: Float = 0f) {
        _state.update {
            it.copy(brightness = brightness, contrast = contrast, saturation = saturation,
                blurRadius = blur, vignette = vignette, grain = grain, amoledCrush = amoledCrush, warmth = warmth)
        }
        applyFilters()
    }

    fun resetAll() {
        filterJob?.cancel()
        _state.update {
            it.copy(
                editedBitmap = it.originalBitmap,
                brightness = 0f, contrast = 1f, saturation = 1f, blurRadius = 0f,
                vignette = 0f, grain = 0f, amoledCrush = 0f, warmth = 0f,
            )
        }
    }

    fun apply(target: WallpaperTarget) {
        val bitmap = _state.value.editedBitmap ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            wallpaperApplier.applyFromBitmap(bitmap, target)
                .onSuccess { _state.update { it.copy(isApplying = false, success = "Applied") } }
                .onFailure { e -> _state.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun clearSuccess() = _state.update { it.copy(success = null) }

    private fun loadFromUrl(url: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    originalBitmap = null,
                    editedBitmap = null,
                    brightness = 0f,
                    contrast = 1f,
                    saturation = 1f,
                    blurRadius = 0f,
                    vignette = 0f,
                    grain = 0f,
                    amoledCrush = 0f,
                    warmth = 0f,
                    isLoadingImage = true,
                    success = null,
                    error = null,
                )
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(url).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val bytes = response.body?.bytes() ?: throw Exception("Empty response body")
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw Exception("Failed to decode image")
                    }
                }
                setSourceBitmap(bitmap)
                _state.update { it.copy(isLoadingImage = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingImage = false, error = e.message ?: "Failed to load image") }
            }
        }
    }

    private fun applyFilters() {
        val original = _state.value.originalBitmap ?: return
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            val s = _state.value
            val result = withContext(Dispatchers.Default) {
                var bmp = applyColorMatrix(original, s.brightness, s.contrast, s.saturation, s.warmth)
                if (s.blurRadius > 0.5f) {
                    val prev = bmp
                    bmp = stackBlur(bmp, s.blurRadius.toInt().coerceIn(1, 25))
                    if (prev !== original && prev !== bmp) prev.recycle()
                }
                if (s.amoledCrush > 0.01f) {
                    val prev = bmp
                    bmp = applyAmoledCrush(bmp, s.amoledCrush)
                    if (prev !== original && prev !== bmp) prev.recycle()
                }
                if (s.vignette > 0.01f) {
                    val prev = bmp
                    bmp = applyVignette(bmp, s.vignette)
                    if (prev !== original && prev !== bmp) prev.recycle()
                }
                if (s.grain > 0.01f) {
                    val prev = bmp
                    bmp = applyGrain(bmp, s.grain)
                    if (prev !== original && prev !== bmp) prev.recycle()
                }
                bmp
            }
            _state.update { it.copy(editedBitmap = result, isProcessing = false) }
        }
    }

    private fun applyColorMatrix(
        src: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float = 0f,
    ): Bitmap {
        val result = try {
            Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            val scale = 0.5f
            Bitmap.createBitmap((src.width * scale).toInt(), (src.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(result)
        val paint = Paint()

        val brightnessMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f,
            ))
        }

        val t = (1f - contrast) / 2f * 255f
        val contrastMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ))
        }

        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }

        val warmthMatrix = ColorMatrix().apply {
            if (warmth != 0f) {
                val r = warmth.coerceIn(-50f, 50f)
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, r,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -r,
                    0f, 0f, 0f, 1f, 0f,
                ))
            }
        }

        val combined = ColorMatrix()
        combined.postConcat(brightnessMatrix)
        combined.postConcat(contrastMatrix)
        combined.postConcat(saturationMatrix)
        if (warmth != 0f) combined.postConcat(warmthMatrix)

        paint.colorFilter = ColorMatrixColorFilter(combined)
        if (result.width != src.width || result.height != src.height) {
            val srcRect = android.graphics.Rect(0, 0, src.width, src.height)
            val dstRect = android.graphics.RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
            canvas.drawBitmap(src, srcRect, dstRect, paint)
        } else {
            canvas.drawBitmap(src, 0f, 0f, paint)
        }

        return result
    }

    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val scale = 1f / (1 + radius * 0.15f)
        val smallW = (src.width * scale).toInt().coerceAtLeast(1)
        val smallH = (src.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val result = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (small !== result) small.recycle()
        return result
    }

    private fun applyAmoledCrush(src: Bitmap, intensity: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val threshold = (intensity * 80).toInt()
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < threshold) {
                val factor = lum.toFloat() / threshold.coerceAtLeast(1)
                val crush = factor * factor
                pixels[i] = (c and 0xFF000000.toInt()) or
                    (((r * crush).toInt().coerceIn(0, 255)) shl 16) or
                    (((g * crush).toInt().coerceIn(0, 255)) shl 8) or
                    ((b * crush).toInt().coerceIn(0, 255))
            }
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyVignette(src: Bitmap, intensity: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cx = src.width / 2f
        val cy = src.height / 2f
        val radius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
        val colors = intArrayOf(0x00000000, 0x00000000, android.graphics.Color.argb((intensity * 220).toInt(), 0, 0, 0))
        val stops = floatArrayOf(0f, 0.4f, 1f)
        val gradient = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return result
    }

    private fun applyGrain(src: Bitmap, intensity: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val strength = (intensity * 40).toInt()
        val random = java.util.Random(42)
        for (i in pixels.indices) {
            val noise = random.nextInt(strength * 2 + 1) - strength
            val c = pixels[i]
            val r = ((c shr 16 and 0xFF) + noise).coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) + noise).coerceIn(0, 255)
            val b = ((c and 0xFF) + noise).coerceIn(0, 255)
            pixels[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }
}

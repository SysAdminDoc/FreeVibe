package com.freevibe.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.stableKey
import com.freevibe.service.WallpaperApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class CropState(
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isApplying: Boolean = false,
    val success: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WallpaperCropViewModel @Inject constructor(
    private val wallpaperApplier: WallpaperApplier,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CropState())
    val state = _state.asStateFlow()
    private var loadedWallpaperKey: String? = null

    fun loadWallpaper(wallpaper: Wallpaper): Boolean {
        val currentState = _state.value
        val wallpaperKey = wallpaper.stableKey()
        if (loadedWallpaperKey == wallpaperKey && (currentState.bitmap != null || currentState.isLoading)) {
            return true
        }
        loadedWallpaperKey = wallpaperKey
        loadFromUrl(wallpaper.fullUrl)
        return true
    }

    fun loadFromUrl(url: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    bitmap = null,
                    isLoading = true,
                    scale = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    success = null,
                    error = null,
                )
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body ?: throw Exception("Empty body")
                        val advertised = body.contentLength()
                        if (advertised in 1..Long.MAX_VALUE && advertised > MAX_CROP_BYTES) {
                            throw Exception("Image too large to crop")
                        }
                        val bytes = body.bytes()
                        if (bytes.size.toLong() > MAX_CROP_BYTES) {
                            throw Exception("Image too large to crop")
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw Exception("Failed to decode image")
                    }
                }
                _state.update { it.copy(bitmap = bitmap, isLoading = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFromBitmap(bitmap: Bitmap) {
        _state.update { it.copy(bitmap = bitmap) }
    }

    fun updateTransform(scale: Float, offsetX: Float, offsetY: Float) {
        _state.update { it.copy(scale = scale, offsetX = offsetX, offsetY = offsetY) }
    }

    fun resetTransform() {
        _state.update { it.copy(scale = 1f, offsetX = 0f, offsetY = 0f) }
    }

    fun applyCropped(target: WallpaperTarget, viewportWidth: Int, viewportHeight: Int) {
        val bmp = _state.value.bitmap ?: return
        val s = _state.value

        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            var cropped: Bitmap? = null
            try {
                cropped = withContext(Dispatchers.Default) {
                    cropBitmap(bmp, s.scale, s.offsetX, s.offsetY, viewportWidth, viewportHeight)
                }
                wallpaperApplier.applyFromBitmap(cropped, target)
                    .onSuccess {
                        cropped.recycle()
                        _state.update { it.copy(isApplying = false, success = "Applied") }
                    }
                    .onFailure { e ->
                        cropped.recycle()
                        _state.update { it.copy(isApplying = false, error = e.message) }
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                cropped?.recycle()
                _state.update { it.copy(isApplying = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    private fun cropBitmap(
        source: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        viewWidth: Int,
        viewHeight: Int,
    ): Bitmap {
        val scaledW = source.width * scale
        val scaledH = source.height * scale

        val imgLeft = (viewWidth - scaledW) / 2f + offsetX
        val imgTop = (viewHeight - scaledH) / 2f + offsetY

        val visLeft = (0f - imgLeft).coerceAtLeast(0f)
        val visTop = (0f - imgTop).coerceAtLeast(0f)
        val visRight = (viewWidth - imgLeft).coerceAtMost(scaledW)
        val visBottom = (viewHeight - imgTop).coerceAtMost(scaledH)

        val srcLeft = (visLeft / scale).toInt().coerceIn(0, source.width - 1)
        val srcTop = (visTop / scale).toInt().coerceIn(0, source.height - 1)
        val srcRight = (visRight / scale).toInt().coerceIn(srcLeft + 1, source.width)
        val srcBottom = (visBottom / scale).toInt().coerceIn(srcTop + 1, source.height)

        return Bitmap.createBitmap(
            source,
            srcLeft,
            srcTop,
            srcRight - srcLeft,
            srcBottom - srcTop,
        )
    }

    private companion object {
        /** Max bytes accepted when downloading a wallpaper for cropping. */
        private const val MAX_CROP_BYTES = 64L * 1024 * 1024
    }
}

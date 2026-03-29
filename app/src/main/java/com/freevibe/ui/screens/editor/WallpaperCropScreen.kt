package com.freevibe.ui.screens.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.SelectedContentHolder
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
    selectedContent: SelectedContentHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(CropState())
    val state = _state.asStateFlow()

    init {
        selectedContent.selectedWallpaper.value?.let { wp ->
            loadFromUrl(wp.fullUrl)
        }
    }

    fun loadFromUrl(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val bytes = response.body?.bytes() ?: throw Exception("Empty body")
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
                _state.update { it.copy(bitmap = bitmap, isLoading = false) }
            } catch (e: Exception) {
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
                cropped?.recycle()
                _state.update { it.copy(isApplying = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    /**
     * Extract the visible viewport region from the bitmap given the current
     * pan/zoom transform.
     */
    private fun cropBitmap(
        source: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        viewWidth: Int,
        viewHeight: Int,
    ): Bitmap {
        // The image is rendered at (scale * naturalSize) with offset
        // Viewport shows a window into this scaled image
        // We need to map viewport coords back to source bitmap coords

        val scaledW = source.width * scale
        val scaledH = source.height * scale

        // Image is centered by default, then offset
        val imgLeft = (viewWidth - scaledW) / 2f + offsetX
        val imgTop = (viewHeight - scaledH) / 2f + offsetY

        // Visible region in scaled image space
        val visLeft = (0f - imgLeft).coerceAtLeast(0f)
        val visTop = (0f - imgTop).coerceAtLeast(0f)
        val visRight = (viewWidth - imgLeft).coerceAtMost(scaledW)
        val visBottom = (viewHeight - imgTop).coerceAtMost(scaledH)

        // Convert to source bitmap coordinates
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperCropScreen(
    onBack: () -> Unit,
    viewModel: WallpaperCropViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Gesture state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Crop & Position") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                        viewModel.resetTransform()
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Crop viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                            viewModel.updateTransform(scale, offsetX, offsetY)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                state.bitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Wallpaper to crop",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                }

                // Screen overlay guides
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )

                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Zoom info + aspect ratio presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pinch to zoom, drag to position",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "%.0f%%".format(scale * 100),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Aspect ratio quick presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf("Free" to null, "9:16" to (9f / 16f), "16:9" to (16f / 9f), "1:1" to 1f)
                presets.forEach { (label, ratio) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            if (ratio != null && state.bitmap != null && viewportSize != IntSize.Zero) {
                                val bmp = state.bitmap!!
                                val vpW = viewportSize.width.toFloat()
                                val vpH = viewportSize.height.toFloat()
                                val vpRatio = vpW / vpH
                                // Scale so the target aspect ratio fills the viewport
                                val fitScale = if (bmp.width.toFloat() / bmp.height > vpRatio) {
                                    vpH / bmp.height // image wider than viewport → fit height
                                } else {
                                    vpW / bmp.width // image taller → fit width
                                }
                                val targetScale = if (ratio < vpRatio) {
                                    // Target is taller than viewport: need to show less width → zoom in
                                    (vpW / ratio) / (bmp.height * fitScale)
                                } else {
                                    // Target is wider: need to show less height → zoom in
                                    (vpH * ratio) / (bmp.width * fitScale)
                                }
                                scale = targetScale.coerceIn(0.5f, 5f)
                                offsetX = 0f
                                offsetY = 0f
                                viewModel.updateTransform(scale, offsetX, offsetY)
                            } else {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                                viewModel.resetTransform()
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp),
                    )
                }
            }

            // Apply buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.applyCropped(WallpaperTarget.HOME, viewportSize.width, viewportSize.height)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying && state.bitmap != null,
                ) { Text("Home") }
                OutlinedButton(
                    onClick = {
                        viewModel.applyCropped(WallpaperTarget.LOCK, viewportSize.width, viewportSize.height)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying && state.bitmap != null,
                ) { Text("Lock") }
                Button(
                    onClick = {
                        viewModel.applyCropped(WallpaperTarget.BOTH, viewportSize.width, viewportSize.height)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying && state.bitmap != null,
                ) {
                    if (state.isApplying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Both")
                }
            }
        }
    }
}

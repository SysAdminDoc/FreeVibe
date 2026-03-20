package com.freevibe.ui.screens.editor

import android.graphics.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.SelectedContentHolder
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
    selectedContent: SelectedContentHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    init {
        selectedContent.selectedWallpaper.value?.let { wp ->
            viewModelScope.launch {
                _state.update { it.copy(isLoadingImage = true) }
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val request = okhttp3.Request.Builder().url(wp.fullUrl).build()
                        val response = okHttpClient.newCall(request).execute()
                        val bytes = response.body?.bytes() ?: throw Exception("Empty response body")
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw Exception("Failed to decode image")
                    }
                    setSourceBitmap(bitmap)
                    _state.update { it.copy(isLoadingImage = false) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoadingImage = false, error = e.message ?: "Failed to load image") }
                }
            }
        } ?: run {
            _state.update { it.copy(error = "No wallpaper selected") }
        }
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

    fun resetAll() {
        _state.update {
            it.copy(
                editedBitmap = it.originalBitmap,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                blurRadius = 0f,
            )
        }
    }

    fun apply(target: WallpaperTarget) {
        val bitmap = _state.value.editedBitmap ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            wallpaperApplier.applyFromBitmap(bitmap, target)
                .onSuccess { _state.update { it.copy(isApplying = false, success = "Applied") } }
                .onFailure { _state.update { it.copy(isApplying = false) } }
        }
    }

    fun clearSuccess() = _state.update { it.copy(success = null) }

    private fun applyFilters() {
        val original = _state.value.originalBitmap ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            val s = _state.value
            val result = withContext(Dispatchers.Default) {
                var bmp = applyColorMatrix(original, s.brightness, s.contrast, s.saturation)
                if (s.blurRadius > 0.5f) {
                    bmp = stackBlur(bmp, s.blurRadius.toInt().coerceIn(1, 25))
                }
                bmp
            }
            _state.update { it.copy(editedBitmap = result, isProcessing = false) }
        }
    }

    /** Apply brightness, contrast, and saturation via ColorMatrix */
    private fun applyColorMatrix(
        src: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
    ): Bitmap {
        val result = try {
            Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            // Fallback: scale down for large images
            val scale = 0.5f
            Bitmap.createBitmap((src.width * scale).toInt(), (src.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(result)
        val paint = Paint()

        // Brightness
        val brightnessMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f,
            ))
        }

        // Contrast
        val t = (1f - contrast) / 2f * 255f
        val contrastMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ))
        }

        // Saturation
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }

        // Combine all matrices
        val combined = ColorMatrix()
        combined.postConcat(brightnessMatrix)
        combined.postConcat(contrastMatrix)
        combined.postConcat(saturationMatrix)

        paint.colorFilter = ColorMatrixColorFilter(combined)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return result
    }

    /** Fast box blur approximation — downscale + upscale for performance */
    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val scale = 1f / (1 + radius * 0.15f)
        val smallW = (src.width * scale).toInt().coerceAtLeast(1)
        val smallH = (src.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val result = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (small !== result) small.recycle()
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditorScreen(
    onBack: () -> Unit,
    viewModel: WallpaperEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedFilter by remember { mutableStateOf("Brightness") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearError() }
    }

    val filters = listOf(
        FilterControl("Brightness", Icons.Default.BrightnessHigh, state.brightness, -100f..100f) {
            viewModel.updateBrightness(it)
        },
        FilterControl("Contrast", Icons.Default.Contrast, state.contrast, 0.5f..2f) {
            viewModel.updateContrast(it)
        },
        FilterControl("Saturation", Icons.Default.ColorLens, state.saturation, 0f..2f) {
            viewModel.updateSaturation(it)
        },
        FilterControl("Blur", Icons.Default.BlurOn, state.blurRadius, 0f..25f) {
            viewModel.updateBlur(it)
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Wallpaper") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetAll() }) {
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
            // Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoadingImage -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Loading image...", color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    state.editedBitmap != null -> {
                        Image(
                            bitmap = state.editedBitmap!!.asImageBitmap(),
                            contentDescription = "Edited wallpaper",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    state.error != null && state.originalBitmap == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrokenImage, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))
                            Text("Failed to load wallpaper", color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Filter selector
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter.name,
                        onClick = { selectedFilter = filter.name },
                        label = { Text(filter.name) },
                        leadingIcon = {
                            Icon(filter.icon, null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }

            // Active slider
            filters.find { it.name == selectedFilter }?.let { active ->
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(active.name, style = MaterialTheme.typography.labelMedium)
                        Text("%.1f".format(active.value), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        value = active.value,
                        onValueChange = active.onChange,
                        valueRange = active.range,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
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
                    onClick = { viewModel.apply(WallpaperTarget.HOME) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying,
                ) { Text("Home") }
                OutlinedButton(
                    onClick = { viewModel.apply(WallpaperTarget.LOCK) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying,
                ) { Text("Lock") }
                Button(
                    onClick = { viewModel.apply(WallpaperTarget.BOTH) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isApplying,
                ) {
                    if (state.isApplying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Both")
                }
            }
        }
    }
}

private data class FilterControl(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val onChange: (Float) -> Unit,
)

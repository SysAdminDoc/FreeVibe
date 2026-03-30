package com.freevibe.ui.screens.editor

import android.graphics.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    selectedContent: SelectedContentHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()
    private var filterJob: kotlinx.coroutines.Job? = null

    init {
        selectedContent.selectedWallpaper.value?.let { wp ->
            viewModelScope.launch {
                _state.update { it.copy(isLoadingImage = true) }
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val request = okhttp3.Request.Builder().url(wp.fullUrl).build()
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
            // Don't recycle old bitmap here — Compose may still reference it.
            // Let GC handle it after Compose moves to the new state.
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
            // Don't recycle old bitmap — Compose rendering pipeline may still reference it.
            // Let GC handle the old bitmap after Compose moves to the new state.
            _state.update { it.copy(editedBitmap = result, isProcessing = false) }
        }
    }

    /** Apply brightness, contrast, saturation, and warmth via ColorMatrix */
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

        // Warmth (shift red/blue channels)
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

        // Combine all matrices
        val combined = ColorMatrix()
        combined.postConcat(brightnessMatrix)
        combined.postConcat(contrastMatrix)
        combined.postConcat(saturationMatrix)
        if (warmth != 0f) combined.postConcat(warmthMatrix)

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

    /** AMOLED Crush — push dark pixels toward pure black for OLED battery savings */
    private fun applyAmoledCrush(src: Bitmap, intensity: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val threshold = (intensity * 80).toInt() // 0-80 brightness threshold
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < threshold) {
                val factor = lum.toFloat() / threshold.coerceAtLeast(1)
                val crush = factor * factor // quadratic falloff for smooth transition
                pixels[i] = (c and 0xFF000000.toInt()) or
                    (((r * crush).toInt().coerceIn(0, 255)) shl 16) or
                    (((g * crush).toInt().coerceIn(0, 255)) shl 8) or
                    ((b * crush).toInt().coerceIn(0, 255))
            }
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    /** Vignette — darken edges with radial gradient */
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

    /** Film grain — random noise overlay */
    private fun applyGrain(src: Bitmap, intensity: Float): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val strength = (intensity * 40).toInt()
        val random = java.util.Random(42) // Fixed seed for consistent preview
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

    data class EditorPreset(val name: String, val b: Float, val c: Float, val s: Float, val bl: Float,
                             val v: Float = 0f, val g: Float = 0f, val a: Float = 0f, val w: Float = 0f)
    val presets = listOf(
        EditorPreset("AMOLED", -20f, 1.3f, 1.1f, 0f, v = 0.3f, a = 0.7f),
        EditorPreset("Warm", 15f, 1.1f, 1.3f, 0f, w = 25f),
        EditorPreset("Cool", -10f, 1.1f, 0.8f, 0f, w = -20f),
        EditorPreset("Vivid", 5f, 1.3f, 1.6f, 0f),
        EditorPreset("Cinematic", -5f, 1.4f, 0.7f, 0f, v = 0.4f, g = 0.15f, w = 10f),
        EditorPreset("Dreamy", 20f, 0.9f, 1.1f, 8f, v = 0.2f),
        EditorPreset("B&W", 0f, 1.2f, 0f, 0f),
        EditorPreset("Noir", -15f, 1.5f, 0f, 0f, v = 0.5f, g = 0.2f, a = 0.4f),
        EditorPreset("Film", 5f, 1.1f, 0.9f, 0f, g = 0.25f, v = 0.15f, w = 8f),
        EditorPreset("Moody", -10f, 1.2f, 0.6f, 2f, v = 0.35f, w = -10f),
    )

    val filters = listOf(
        FilterControl("Brightness", Icons.Default.BrightnessHigh, state.brightness, -100f..100f) { viewModel.updateBrightness(it) },
        FilterControl("Contrast", Icons.Default.Contrast, state.contrast, 0.5f..2f) { viewModel.updateContrast(it) },
        FilterControl("Saturation", Icons.Default.ColorLens, state.saturation, 0f..2f) { viewModel.updateSaturation(it) },
        FilterControl("Warmth", Icons.Default.Thermostat, state.warmth, -50f..50f) { viewModel.updateWarmth(it) },
        FilterControl("Blur", Icons.Default.BlurOn, state.blurRadius, 0f..25f) { viewModel.updateBlur(it) },
        FilterControl("AMOLED", Icons.Default.DarkMode, state.amoledCrush, 0f..1f) { viewModel.updateAmoledCrush(it) },
        FilterControl("Vignette", Icons.Default.Vignette, state.vignette, 0f..1f) { viewModel.updateVignette(it) },
        FilterControl("Grain", Icons.Default.Grain, state.grain, 0f..1f) { viewModel.updateGrain(it) },
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

            // Preset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            viewModel.applyPreset(preset.b, preset.c, preset.s, preset.bl, preset.v, preset.g, preset.a, preset.w)
                        },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(20.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }

            // Filter selector — two rows so all 8 filters are visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter.name,
                        onClick = { selectedFilter = filter.name },
                        label = { Text(filter.name, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(filter.icon, null, modifier = Modifier.size(14.dp))
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

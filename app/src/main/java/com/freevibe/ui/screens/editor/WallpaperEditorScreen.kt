package com.freevibe.ui.screens.editor

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditorScreen(
    wallpaperId: String,
    fallbackWallpaper: Wallpaper? = null,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    viewModel: WallpaperEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editorIdentityKey = remember(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        listOf(
            wallpaperId,
            fallbackWallpaper?.source?.name.orEmpty(),
            fallbackWallpaper?.fullUrl.orEmpty(),
        ).joinToString("|")
    }
    var selectedFilter by remember(editorIdentityKey) { mutableStateOf("Brightness") }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectionResolved by remember(editorIdentityKey) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(wallpaperId, fallbackWallpaper?.source, fallbackWallpaper?.fullUrl) {
        val wallpaper = fallbackWallpaper?.let {
            recoveryViewModel.resolveWallpaper(
                id = wallpaperId,
                source = it.source,
                fullUrl = it.fullUrl,
            ) ?: it
        } ?: recoveryViewModel.resolveWallpaper(wallpaperId)
        selectionResolved = wallpaper?.let { viewModel.loadWallpaper(it) } ?: false
    }

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
        if (selectionResolved == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (selectionResolved == false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BrokenImage,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Wallpaper unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onBack) { Text("Back") }
                }
            }
            return@Scaffold
        }

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
                        val editedBitmap = state.editedBitmap ?: return@Box
                        Image(
                            bitmap = editedBitmap.asImageBitmap(),
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

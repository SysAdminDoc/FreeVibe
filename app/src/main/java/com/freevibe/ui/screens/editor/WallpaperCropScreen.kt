package com.freevibe.ui.screens.editor

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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.freevibe.data.model.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperCropScreen(
    wallpaperId: String,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.wallpapers.WallpapersViewModel = hiltViewModel(),
    viewModel: WallpaperCropViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var selectionResolved by remember(wallpaperId) { mutableStateOf<Boolean?>(null) }

    // Gesture state — survives configuration changes
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }
    LaunchedEffect(wallpaperId) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        val wallpaper = recoveryViewModel.resolveWallpaper(wallpaperId)
        selectionResolved = wallpaper?.let { viewModel.loadWallpaper(it) } ?: false
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

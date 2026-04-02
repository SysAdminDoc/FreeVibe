package com.freevibe.ui.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import kotlin.math.abs
import kotlin.math.max

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEditorScreen(
    soundId: String? = null,
    fallbackSound: Sound? = null,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.sounds.SoundsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    viewModel: SoundEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentSelectedSound by recoveryViewModel.selectedSound.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorIdentityKey = remember(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl) {
        listOf(
            soundId.orEmpty(),
            fallbackSound?.source?.name.orEmpty(),
            fallbackSound?.previewUrl.orEmpty(),
            fallbackSound?.downloadUrl.orEmpty(),
        ).joinToString("|")
    }
    var selectionResolved by remember(editorIdentityKey) {
        mutableStateOf<Boolean?>(if (soundId == null) true else null)
    }

    // Local file picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromLocalUri(it) }
    }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }
    LaunchedEffect(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl) {
        if (soundId == null) {
            selectionResolved = true
        } else {
            val sound = fallbackSound?.let {
                recoveryViewModel.resolveSound(
                    id = soundId,
                    source = it.source,
                    previewUrl = it.previewUrl.takeIf { url -> url.isNotBlank() },
                    downloadUrl = it.downloadUrl.takeIf { url -> url.isNotBlank() },
                ) ?: it
            } ?: recoveryViewModel.resolveSound(soundId)
            selectionResolved = sound?.let { viewModel.loadSound(it) } ?: false
        }
    }
    LaunchedEffect(soundId, currentSelectedSound?.id) {
        if (soundId == null) {
            currentSelectedSound?.let { viewModel.loadSound(it) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Sound") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (viewModel.canUndo) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                    }
                    IconButton(onClick = { filePicker.launch("audio/*") }) {
                        Icon(Icons.Default.FolderOpen, "Open file")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (soundId != null && selectionResolved == null) {
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

        if (soundId != null && selectionResolved == false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicOff,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Sound unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onBack) { Text("Back") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // File name
            Text(
                state.fileName.ifEmpty { "No audio loaded" },
                style = MaterialTheme.typography.titleLarge,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading waveform...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (state.waveform.isEmpty() && state.localFilePath == null) {
                // No audio loaded — show open file prompt
                Box(
                    Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AudioFile,
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Open an audio file to create a ringtone, notification, or alarm sound",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { filePicker.launch("audio/*") }) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Files")
                        }
                    }
                }
            } else if (state.waveform.isNotEmpty()) {
                // Waveform with trim handles
                WaveformView(
                    waveform = state.waveform,
                    trimStart = state.trimStartFraction,
                    trimEnd = state.trimEndFraction,
                    playbackPosition = state.playbackPosition,
                    isPlaying = state.isPlaying,
                    fadeInFraction = if (state.durationMs > 0) state.fadeInMs.toFloat() / state.durationMs else 0f,
                    fadeOutFraction = if (state.durationMs > 0) state.fadeOutMs.toFloat() / state.durationMs else 0f,
                    onDragStart = { viewModel.saveUndo() },
                    onTrimStartChange = { viewModel.setTrimStart(it) },
                    onTrimEndChange = { viewModel.setTrimEnd(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )

                // Time display
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatMs(state.trimStartMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Duration: ${formatMs(state.trimDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        formatMs(state.trimEndMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Playback controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Fade controls
                Text("Fade Effects", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Fade In
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fade In: ${state.fadeInMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        var fadeInUndoSaved by remember { mutableStateOf(false) }
                        Slider(
                            value = state.fadeInMs.toFloat(),
                            onValueChange = {
                                if (!fadeInUndoSaved) { viewModel.saveUndo(); fadeInUndoSaved = true }
                                viewModel.setFadeIn(it.toLong())
                            },
                            onValueChangeFinished = { fadeInUndoSaved = false },
                            valueRange = 0f..(state.trimDurationMs / 2f).coerceAtLeast(100f),
                            modifier = Modifier.height(32.dp),
                        )
                    }
                    // Fade Out
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fade Out: ${state.fadeOutMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        var fadeOutUndoSaved by remember { mutableStateOf(false) }
                        Slider(
                            value = state.fadeOutMs.toFloat(),
                            onValueChange = {
                                if (!fadeOutUndoSaved) { viewModel.saveUndo(); fadeOutUndoSaved = true }
                                viewModel.setFadeOut(it.toLong())
                            },
                            onValueChangeFinished = { fadeOutUndoSaved = false },
                            valueRange = 0f..(state.trimDurationMs / 2f).coerceAtLeast(100f),
                            modifier = Modifier.height(32.dp),
                        )
                    }
                }

                // Apply buttons
                Text("Set trimmed audio as:", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ApplyBtn("Ringtone", Modifier.weight(1f), state.isApplying) {
                        viewModel.applyTrimmed(ContentType.RINGTONE)
                    }
                    ApplyBtn("Notification", Modifier.weight(1f), state.isApplying) {
                        viewModel.applyTrimmed(ContentType.NOTIFICATION)
                    }
                    ApplyBtn("Alarm", Modifier.weight(1f), state.isApplying) {
                        viewModel.applyTrimmed(ContentType.ALARM)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplyBtn(text: String, modifier: Modifier, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Waveform visualization with draggable trim handles + fade overlays */
@Composable
private fun WaveformView(
    waveform: FloatArray,
    trimStart: Float,
    trimEnd: Float,
    playbackPosition: Float,
    isPlaying: Boolean,
    fadeInFraction: Float = 0f,
    fadeOutFraction: Float = 0f,
    onDragStart: () -> Unit = {},
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val playhead = MaterialTheme.colorScheme.tertiary
    val fadeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onHorizontalDrag = { change, _ ->
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val distToStart = abs(fraction - trimStart)
                            val distToEnd = abs(fraction - trimEnd)
                            if (distToStart < distToEnd) onTrimStartChange(fraction)
                            else onTrimEndChange(fraction)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2
            val barWidth = w / waveform.size
            val maxAmp = h * 0.45f

            // Draw waveform bars
            for (i in waveform.indices) {
                val x = i * barWidth
                val amplitude = waveform[i] * maxAmp
                val fraction = i.toFloat() / waveform.size
                val inTrim = fraction in trimStart..trimEnd
                val color = if (inTrim) primary else dimmed

                drawLine(
                    color = color,
                    start = Offset(x + barWidth / 2, centerY - amplitude),
                    end = Offset(x + barWidth / 2, centerY + amplitude),
                    strokeWidth = max(barWidth - 1f, 1f),
                )
            }

            // Dimmed overlay outside trim region
            drawRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w * trimStart, h),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(w * trimEnd, 0f),
                size = androidx.compose.ui.geometry.Size(w * (1f - trimEnd), h),
            )

            // Fade in overlay (triangle)
            if (fadeInFraction > 0f) {
                val fadeInX = w * (trimStart + fadeInFraction)
                val trimStartX = w * trimStart
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trimStartX, 0f)
                    lineTo(trimStartX, h)
                    lineTo(fadeInX, h)
                    close()
                }
                drawPath(path, fadeColor)
            }

            // Fade out overlay (triangle)
            if (fadeOutFraction > 0f) {
                val fadeOutStartX = w * (trimEnd - fadeOutFraction)
                val trimEndX = w * trimEnd
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trimEndX, 0f)
                    lineTo(trimEndX, h)
                    lineTo(fadeOutStartX, h)
                    close()
                }
                drawPath(path, fadeColor)
            }

            // Trim handles
            drawTrimHandle(w * trimStart, h, primary)
            drawTrimHandle(w * trimEnd, h, primary)

            // Playback position
            if (isPlaying) {
                drawLine(
                    color = playhead,
                    start = Offset(w * playbackPosition, 0f),
                    end = Offset(w * playbackPosition, h),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

private fun DrawScope.drawTrimHandle(x: Float, height: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = 3.dp.toPx(),
    )
    drawCircle(
        color = color,
        radius = 8.dp.toPx(),
        center = Offset(x, 8.dp.toPx()),
    )
    drawCircle(
        color = color,
        radius = 8.dp.toPx(),
        center = Offset(x, height - 8.dp.toPx()),
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val frac = (ms % 1000) / 100
    return "%d:%02d.%d".format(min, sec, frac)
}

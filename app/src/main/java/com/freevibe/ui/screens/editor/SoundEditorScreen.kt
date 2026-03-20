package com.freevibe.ui.screens.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentType
import com.freevibe.service.AudioTrimmer
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SoundEditorState(
    val isLoading: Boolean = false,
    val waveform: FloatArray = floatArrayOf(),
    val durationMs: Long = 0,
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    val playbackPosition: Float = 0f,
    val isPlaying: Boolean = false,
    val isApplying: Boolean = false,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val fileName: String = "",
    val localFilePath: String? = null,
    val isLocalFile: Boolean = false,
    val success: String? = null,
    val error: String? = null,
) {
    val trimStartMs: Long get() = (durationMs * trimStartFraction).toLong()
    val trimEndMs: Long get() = (durationMs * trimEndFraction).toLong()
    val trimDurationMs: Long get() = trimEndMs - trimStartMs

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoundEditorState) return false
        return isLoading == other.isLoading && waveform.contentEquals(other.waveform) &&
            durationMs == other.durationMs && trimStartFraction == other.trimStartFraction &&
            trimEndFraction == other.trimEndFraction && playbackPosition == other.playbackPosition &&
            isPlaying == other.isPlaying && isApplying == other.isApplying &&
            fadeInMs == other.fadeInMs && fadeOutMs == other.fadeOutMs &&
            fileName == other.fileName && localFilePath == other.localFilePath &&
            isLocalFile == other.isLocalFile && success == other.success && error == other.error
    }

    override fun hashCode() = waveform.contentHashCode()
}

@HiltViewModel
class SoundEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val soundApplier: SoundApplier,
    private val audioTrimmer: AudioTrimmer,
    selectedContent: SelectedContentHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundEditorState())
    val state = _state.asStateFlow()

    private var player: android.media.MediaPlayer? = null

    init {
        selectedContent.selectedSound.value?.let { sound ->
            loadFromUrl(sound.downloadUrl, sound.name)
        }
    }

    fun loadFromUrl(url: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, fileName = name, error = null, isLocalFile = false) }
            try {
                val file = withContext(Dispatchers.IO) { downloadToCache(url, name) }
                val waveform = withContext(Dispatchers.Default) { extractWaveform(file.absolutePath) }
                val duration = getAudioDuration(file.absolutePath)
                _state.update {
                    it.copy(
                        isLoading = false,
                        waveform = waveform,
                        durationMs = duration,
                        localFilePath = file.absolutePath,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load: ${e.message}") }
            }
        }
    }

    fun loadFromLocalUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, isLocalFile = true) }
            try {
                val file = withContext(Dispatchers.IO) { copyUriToCache(uri) }
                val name = file.nameWithoutExtension
                val waveform = withContext(Dispatchers.Default) { extractWaveform(file.absolutePath) }
                val duration = getAudioDuration(file.absolutePath)
                _state.update {
                    it.copy(
                        isLoading = false,
                        fileName = name,
                        waveform = waveform,
                        durationMs = duration,
                        localFilePath = file.absolutePath,
                        trimStartFraction = 0f,
                        trimEndFraction = 1f,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load file: ${e.message}") }
            }
        }
    }

    fun setTrimStart(fraction: Float) {
        val clamped = fraction.coerceIn(0f, _state.value.trimEndFraction - 0.02f)
        _state.update { it.copy(trimStartFraction = clamped) }
    }

    fun setTrimEnd(fraction: Float) {
        val clamped = fraction.coerceIn(_state.value.trimStartFraction + 0.02f, 1f)
        _state.update { it.copy(trimEndFraction = clamped) }
    }

    fun setFadeIn(ms: Long) = _state.update { it.copy(fadeInMs = ms.coerceIn(0, it.trimDurationMs / 2)) }
    fun setFadeOut(ms: Long) = _state.update { it.copy(fadeOutMs = ms.coerceIn(0, it.trimDurationMs / 2)) }

    fun togglePlayback() {
        if (_state.value.isPlaying) stopPlayback() else startPlayback()
    }

    fun applyTrimmed(type: ContentType) {
        val s = _state.value
        val path = s.localFilePath ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            try {
                val trimmedPath = audioTrimmer.trim(
                    inputPath = path,
                    startMs = s.trimStartMs,
                    endMs = s.trimEndMs,
                    outputFileName = s.fileName,
                    fadeInMs = s.fadeInMs,
                    fadeOutMs = s.fadeOutMs,
                ).getOrThrow()

                soundApplier.applyFromLocalFile(trimmedPath, s.fileName, type)
                    .onSuccess {
                        val label = when (type) {
                            ContentType.RINGTONE -> "ringtone"
                            ContentType.NOTIFICATION -> "notification"
                            ContentType.ALARM -> "alarm"
                            else -> "sound"
                        }
                        _state.update { it.copy(isApplying = false, success = "Set as $label") }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(isApplying = false, error = e.message) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(isApplying = false, error = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    private fun startPlayback() {
        val path = _state.value.localFilePath ?: return
        val startMs = _state.value.trimStartMs.toInt()
        val endMs = _state.value.trimEndMs.toInt()

        stopPlayback()
        player = android.media.MediaPlayer().apply {
            setDataSource(path)
            prepare()
            seekTo(startMs)
            start()
            _state.update { it.copy(isPlaying = true) }

            viewModelScope.launch {
                while (isPlaying && currentPosition < endMs) {
                    val pos = currentPosition.toFloat() / duration
                    _state.update { it.copy(playbackPosition = pos) }
                    kotlinx.coroutines.delay(50)
                }
                stopPlayback()
            }

            setOnCompletionListener { stopPlayback() }
        }
    }

    private fun stopPlayback() {
        player?.apply {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            release()
        }
        player = null
        _state.update { it.copy(isPlaying = false) }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    private suspend fun downloadToCache(url: String, name: String): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audio_edit")
        cacheDir.mkdirs()
        val ext = when {
            url.contains(".ogg") -> ".ogg"
            url.contains(".wav") -> ".wav"
            url.contains(".flac") -> ".flac"
            else -> ".mp3"
        }
        val file = File(cacheDir, name.replace(Regex("[^a-zA-Z0-9]"), "_") + ext)
        if (!file.exists()) {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.body?.byteStream()?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        file
    }

    private suspend fun copyUriToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audio_edit")
        cacheDir.mkdirs()
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "local_audio"
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(cacheDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot read file")
        file
    }

    private fun extractWaveform(path: String, numSamples: Int = 200): FloatArray {
        val amplitudes = FloatArray(numSamples)
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)

            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack < 0) return amplitudes

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val duration = format.getLong(MediaFormat.KEY_DURATION)
            val sampleInterval = duration / numSamples

            val buffer = ByteBuffer.allocate(65536)
            var sampleIndex = 0

            while (sampleIndex < numSamples) {
                val targetTime = sampleIndex * sampleInterval
                extractor.seekTo(targetTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                buffer.order(ByteOrder.LITTLE_ENDIAN)
                var sumSquares = 0.0
                val samples = min(size / 2, 1024)
                for (i in 0 until samples) {
                    val sample = buffer.getShort(i * 2).toFloat()
                    sumSquares += sample * sample
                }
                val rms = Math.sqrt(sumSquares / max(samples, 1)).toFloat()
                amplitudes[sampleIndex] = rms / 32768f

                buffer.clear()
                sampleIndex++
                extractor.advance()
            }

            extractor.release()
        } catch (_: Exception) {
            for (i in amplitudes.indices) {
                amplitudes[i] = (Math.sin(i * 0.3) * 0.5 + 0.5).toFloat() * 0.7f
            }
        }
        return amplitudes
    }

    private fun getAudioDuration(path: String): Long {
        return try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            val dur = mp.duration.toLong()
            mp.release()
            dur
        } catch (_: Exception) { 0L }
    }
}

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEditorScreen(
    onBack: () -> Unit,
    viewModel: SoundEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Sound") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Open local file button
                    IconButton(onClick = { filePicker.launch("audio/*") }) {
                        Icon(Icons.Default.FolderOpen, "Open file")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
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
                        Text("Open an audio file to create a ringtone", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Slider(
                            value = state.fadeInMs.toFloat(),
                            onValueChange = { viewModel.setFadeIn(it.toLong()) },
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
                        Slider(
                            value = state.fadeOutMs.toFloat(),
                            onValueChange = { viewModel.setFadeOut(it.toLong()) },
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
                    detectHorizontalDragGestures { change, _ ->
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val distToStart = abs(fraction - trimStart)
                        val distToEnd = abs(fraction - trimEnd)
                        if (distToStart < distToEnd) onTrimStartChange(fraction)
                        else onTrimEndChange(fraction)
                    }
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

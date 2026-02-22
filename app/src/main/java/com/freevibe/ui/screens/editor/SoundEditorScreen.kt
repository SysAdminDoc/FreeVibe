package com.freevibe.ui.screens.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val trimStartFraction: Float = 0f,    // 0.0 - 1.0
    val trimEndFraction: Float = 1f,      // 0.0 - 1.0
    val playbackPosition: Float = 0f,     // 0.0 - 1.0
    val isPlaying: Boolean = false,
    val isApplying: Boolean = false,
    val fileName: String = "",
    val localFilePath: String? = null,
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
            fileName == other.fileName && localFilePath == other.localFilePath &&
            success == other.success && error == other.error
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

    /** Load audio from URL, download locally, extract waveform */
    fun loadFromUrl(url: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, fileName = name, error = null) }
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

    fun setTrimStart(fraction: Float) {
        val clamped = fraction.coerceIn(0f, _state.value.trimEndFraction - 0.02f)
        _state.update { it.copy(trimStartFraction = clamped) }
    }

    fun setTrimEnd(fraction: Float) {
        val clamped = fraction.coerceIn(_state.value.trimStartFraction + 0.02f, 1f)
        _state.update { it.copy(trimEndFraction = clamped) }
    }

    fun togglePlayback() {
        if (_state.value.isPlaying) stopPlayback() else startPlayback()
    }

    fun applyTrimmed(type: ContentType) {
        val s = _state.value
        val path = s.localFilePath ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            try {
                // Trim audio losslessly via MediaExtractor + MediaMuxer
                val trimmedPath = audioTrimmer.trim(
                    inputPath = path,
                    startMs = s.trimStartMs,
                    endMs = s.trimEndMs,
                    outputFileName = s.fileName,
                ).getOrThrow()

                // Apply trimmed file from local path
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

            // Update playback position
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

    /** Extract waveform amplitudes from audio file using MediaExtractor */
    private fun extractWaveform(path: String, numSamples: Int = 200): FloatArray {
        val amplitudes = FloatArray(numSamples)
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)

            // Find audio track
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

                // Calculate RMS amplitude from PCM-like data
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                var sumSquares = 0.0
                val samples = min(size / 2, 1024)
                for (i in 0 until samples) {
                    val sample = buffer.getShort(i * 2).toFloat()
                    sumSquares += sample * sample
                }
                val rms = Math.sqrt(sumSquares / max(samples, 1)).toFloat()
                amplitudes[sampleIndex] = rms / 32768f  // Normalize to 0-1

                buffer.clear()
                sampleIndex++
                extractor.advance()
            }

            extractor.release()
        } catch (_: Exception) {
            // Fill with placeholder waveform on error
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            } else if (state.waveform.isNotEmpty()) {
                // Waveform with trim handles
                WaveformView(
                    waveform = state.waveform,
                    trimStart = state.trimStartFraction,
                    trimEnd = state.trimEndFraction,
                    playbackPosition = state.playbackPosition,
                    isPlaying = state.isPlaying,
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
            }

            Spacer(Modifier.weight(1f))

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

/** Waveform visualization with draggable trim handles */
@Composable
private fun WaveformView(
    waveform: FloatArray,
    trimStart: Float,
    trimEnd: Float,
    playbackPosition: Float,
    isPlaying: Boolean,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryDim = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val surfaceVar = MaterialTheme.colorScheme.surfaceContainerHigh
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val playhead = MaterialTheme.colorScheme.tertiary

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
    // Vertical line
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = 3.dp.toPx(),
    )
    // Top handle circle
    drawCircle(
        color = color,
        radius = 8.dp.toPx(),
        center = Offset(x, 8.dp.toPx()),
    )
    // Bottom handle circle
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

package com.freevibe.ui.screens.editor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.service.AudioTrimmer
import com.freevibe.service.SoundUrlResolver
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

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + waveform.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + trimStartFraction.hashCode()
        result = 31 * result + trimEndFraction.hashCode()
        result = 31 * result + playbackPosition.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + isApplying.hashCode()
        result = 31 * result + fadeInMs.hashCode()
        result = 31 * result + fadeOutMs.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (localFilePath?.hashCode() ?: 0)
        result = 31 * result + isLocalFile.hashCode()
        result = 31 * result + (success?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

@HiltViewModel
class SoundEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val soundApplier: SoundApplier,
    private val audioTrimmer: AudioTrimmer,
    private val soundUrlResolver: SoundUrlResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundEditorState())
    val state = _state.asStateFlow()

    private var player: android.media.MediaPlayer? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var undoState: UndoSnapshot? = null
    private var loadedSoundKey: String? = null

    private data class UndoSnapshot(
        val trimStart: Float, val trimEnd: Float,
        val fadeIn: Long, val fadeOut: Long,
    )

    fun loadSound(sound: Sound): Boolean {
        val currentState = _state.value
        val soundKey = sound.stableKey()
        if (loadedSoundKey == soundKey && (currentState.localFilePath != null || currentState.isLoading)) {
            return true
        }
        loadedSoundKey = soundKey
        if (sound.downloadUrl.isBlank() && sound.previewUrl.isBlank() && sound.sourcePageUrl.isBlank()) return false
        loadRemoteSound(sound.name) {
            val resolvedUrl = soundUrlResolver.resolve(sound)
                ?: throw IllegalStateException("No audio URL available")
            downloadToCache(resolvedUrl, sound.name)
        }
        return true
    }

    fun loadFromUrl(url: String, name: String) {
        loadRemoteSound(name) { downloadToCache(url, name) }
    }

    private fun loadRemoteSound(name: String, loader: suspend () -> File) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            stopPlayback()
            _state.update {
                it.copy(
                    isLoading = true,
                    waveform = floatArrayOf(),
                    durationMs = 0,
                    trimStartFraction = 0f,
                    trimEndFraction = 1f,
                    playbackPosition = 0f,
                    isPlaying = false,
                    isApplying = false,
                    fadeInMs = 0,
                    fadeOutMs = 0,
                    fileName = name,
                    localFilePath = null,
                    isLocalFile = false,
                    success = null,
                    error = null,
                )
            }
            try {
                val file = withContext(Dispatchers.IO) { loader() }
                val waveform = withContext(Dispatchers.Default) { extractWaveform(file.absolutePath) }
                val duration = withContext(Dispatchers.IO) { getAudioDuration(file.absolutePath) }
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
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            stopPlayback()
            _state.update {
                it.copy(
                    isLoading = true,
                    waveform = floatArrayOf(),
                    durationMs = 0,
                    trimStartFraction = 0f,
                    trimEndFraction = 1f,
                    playbackPosition = 0f,
                    isPlaying = false,
                    isApplying = false,
                    fadeInMs = 0,
                    fadeOutMs = 0,
                    error = null,
                    isLocalFile = true,
                    success = null,
                )
            }
            try {
                val file = withContext(Dispatchers.IO) { copyUriToCache(uri) }
                val name = file.nameWithoutExtension
                val waveform = withContext(Dispatchers.Default) { extractWaveform(file.absolutePath) }
                val duration = withContext(Dispatchers.IO) { getAudioDuration(file.absolutePath) }
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

    fun saveUndo() {
        val s = _state.value
        undoState = UndoSnapshot(s.trimStartFraction, s.trimEndFraction, s.fadeInMs, s.fadeOutMs)
    }

    fun undo() {
        undoState?.let { snap ->
            _state.update {
                it.copy(
                    trimStartFraction = snap.trimStart, trimEndFraction = snap.trimEnd,
                    fadeInMs = snap.fadeIn, fadeOutMs = snap.fadeOut,
                )
            }
            undoState = null
        }
    }

    val canUndo: Boolean get() = undoState != null

    fun setTrimStart(fraction: Float) {
        val clamped = fraction.coerceIn(0f, _state.value.trimEndFraction - 0.02f)
        _state.update { it.copy(trimStartFraction = clamped) }
    }

    fun setTrimEnd(fraction: Float) {
        val clamped = fraction.coerceIn(_state.value.trimStartFraction + 0.02f, 1f)
        _state.update { it.copy(trimEndFraction = clamped) }
    }

    fun setFadeIn(ms: Long) {
        _state.update { it.copy(fadeInMs = ms.coerceIn(0, (it.trimDurationMs / 2).coerceAtLeast(1))) }
    }

    fun setFadeOut(ms: Long) {
        _state.update { it.copy(fadeOutMs = ms.coerceIn(0, (it.trimDurationMs / 2).coerceAtLeast(1))) }
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
        try {
            player = android.media.MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    mp.seekTo(startMs)
                    mp.start()
                    _state.update { it.copy(isPlaying = true) }

                    playbackJob?.cancel()
                    playbackJob = viewModelScope.launch {
                        try {
                            while (_state.value.isPlaying) {
                                val p = player ?: break
                                val pos = try { p.currentPosition } catch (_: IllegalStateException) { break }
                                if (pos >= endMs) break
                                val dur = try { p.duration } catch (_: IllegalStateException) { break }
                                if (dur > 0) _state.update { it.copy(playbackPosition = pos.toFloat() / dur) }
                                kotlinx.coroutines.delay(50)
                            }
                        } catch (_: Exception) {}
                        stopPlayback()
                    }
                }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            player?.apply {
                try { setOnPreparedListener(null) } catch (_: Exception) {}
                try { setOnCompletionListener(null) } catch (_: Exception) {}
                try { setOnErrorListener(null) } catch (_: Exception) {}
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
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
            val tmpFile = File(cacheDir, file.name + ".tmp")
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")
                    val body = response.body ?: throw Exception("Empty response body")
                    body.byteStream().use { input ->
                        FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
                    }
                }
                if (tmpFile.length() > 0) {
                    tmpFile.renameTo(file)
                } else {
                    tmpFile.delete()
                    throw Exception("Download produced empty file")
                }
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
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
        val extractor = MediaExtractor()
        try {
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
        } catch (_: Exception) {
            for (i in amplitudes.indices) {
                amplitudes[i] = (Math.sin(i * 0.3) * 0.5 + 0.5).toFloat() * 0.7f
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return amplitudes
    }

    private fun getAudioDuration(path: String): Long {
        val mp = android.media.MediaPlayer()
        return try {
            mp.setDataSource(path)
            mp.prepare()
            mp.duration.toLong()
        } catch (_: Exception) { 0L }
        finally { try { mp.release() } catch (_: Exception) {} }
    }
}

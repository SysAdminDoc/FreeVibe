package com.freevibe.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freevibe.data.model.Sound
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile private var controller: MediaController? = null
    @Volatile private var stopped = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentSoundId = MutableStateFlow<String?>(null)
    val currentSoundId: StateFlow<String?> = _currentSoundId.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                _currentSoundId.value = null
                _currentPosition.value = 0L
            }
        }
    }

    private fun ensureConnected(onReady: (MediaController) -> Unit) {
        controller?.let { if (it.isConnected) { onReady(it); return } }

        // Release old controller/future before creating new ones
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(playerListener)

        try {
            val token = SessionToken(
                context,
                ComponentName(context, AudioPlaybackService::class.java),
            )
            controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
                future.addListener({
                    try {
                        if (stopped) return@addListener
                        val mc = future.get()
                        controller = mc
                        mc.addListener(playerListener)
                        onReady(mc)
                    } catch (e: Exception) {
                        _currentSoundId.value = null
                        _isPlaying.value = false
                    }
                }, MoreExecutors.directExecutor())
            }
        } catch (e: Exception) {
            _currentSoundId.value = null
            _isPlaying.value = false
        }
    }

    fun play(sound: Sound, url: String, volume: Float = 1f) {
        stopped = false
        ensureConnected { mc ->
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(sound.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(sound.name)
                        .setArtist(sound.uploaderName.ifBlank { null })
                        .build()
                )
                .build()
            mc.stop()
            mc.clearMediaItems()
            mc.setMediaItem(mediaItem)
            mc.prepare()
            mc.volume = volume
            mc.play()
            _currentSoundId.value = sound.id
            _duration.value = 0L
            _currentPosition.value = 0L
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun stop() {
        stopped = true
        controller?.apply {
            stop()
            clearMediaItems()
        }
        _isPlaying.value = false
        _currentSoundId.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume
    }

    /**
     * Call from a coroutine polling loop to update position/duration state.
     */
    fun pollProgress() {
        val mc = controller ?: return
        if (mc.isConnected && mc.duration > 0) {
            _currentPosition.value = mc.currentPosition
            _duration.value = mc.duration
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }
}

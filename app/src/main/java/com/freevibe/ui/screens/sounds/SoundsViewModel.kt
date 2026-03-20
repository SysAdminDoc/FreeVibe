package com.freevibe.ui.screens.sounds

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SoundRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoundsUiState(
    val sounds: List<Sound> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,       // #4: Pull-to-refresh
    val error: String? = null,
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: SoundTab = SoundTab.RINGTONES,
    val playingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, TRENDING, SEARCH }

@HiltViewModel
class SoundsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundRepo: SoundRepository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundsUiState())
    val state = _state.asStateFlow()

    val selectedSound = selectedContent.selectedSound

    private var exoPlayer: ExoPlayer? = null

    init {
        loadSounds()
    }

    fun selectTab(tab: SoundTab) {
        stopPlayback()
        _state.update { it.copy(selectedTab = tab, sounds = emptyList(), currentPage = 1, hasMore = true) }
        loadSounds()
    }

    fun search(query: String) {
        stopPlayback()
        _state.update {
            it.copy(
                query = query,
                selectedTab = SoundTab.SEARCH,
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
            )
        }
        loadSounds()
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadSounds(loadMore = true)
    }

    // #4: Pull-to-refresh
    fun refresh() {
        stopPlayback()
        _state.update { it.copy(isRefreshing = true, currentPage = 1, sounds = emptyList(), error = null) }
        loadSounds(isRefresh = true)
    }

    fun selectSound(sound: Sound) {
        selectedContent.selectSound(sound)
    }

    fun togglePlayback(sound: Sound) {
        if (_state.value.playingId == sound.id) {
            stopPlayback()
        } else {
            startPlayback(sound)
        }
    }

    fun applySound(sound: Sound, type: ContentType) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            soundApplier.downloadAndApply(sound.downloadUrl, sound.name, type)
                .onSuccess {
                    val label = when (type) {
                        ContentType.RINGTONE -> "ringtone"
                        ContentType.NOTIFICATION -> "notification sound"
                        ContentType.ALARM -> "alarm sound"
                        else -> "sound"
                    }
                    _state.update { it.copy(isApplying = false, applySuccess = "Set as $label") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    // #3: Standalone sound download
    fun downloadSound(sound: Sound) {
        viewModelScope.launch {
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3")
            downloadManager.downloadSound(
                id = sound.id,
                url = sound.downloadUrl,
                fileName = "FreeVibe_${sound.name.take(40)}.$ext",
                type = ContentType.RINGTONE,
            )
            _state.update { it.copy(applySuccess = "Download started") }
        }
    }

    fun canWriteSettings(): Boolean = soundApplier.canWriteSettings()
    fun requestWriteSettings() = soundApplier.requestWriteSettings()

    fun toggleFavorite(sound: Sound) {
        viewModelScope.launch {
            val entity = sound.toFavoriteEntity()
            val isFav = favoritesRepo.isFavorite(sound.id).first()
            favoritesRepo.toggle(entity, isFav)
        }
    }

    fun isFavorite(id: String): Flow<Boolean> = favoritesRepo.isFavorite(id)

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sound.previewUrl))
            prepare()
            play()
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        _state.update { it.copy(playingId = null) }
                    }
                }
            })
        }
        _state.update { it.copy(playingId = sound.id) }
    }

    private fun stopPlayback() {
        exoPlayer?.apply { stop(); release() }
        exoPlayer = null
        _state.update { it.copy(playingId = null) }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    private fun loadSounds(loadMore: Boolean = false, isRefresh: Boolean = false) {
        viewModelScope.launch {
            val s = _state.value
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }
            try {
                val result = when (s.selectedTab) {
                    SoundTab.RINGTONES -> soundRepo.searchRingtones(page = s.currentPage)
                    SoundTab.NOTIFICATIONS -> soundRepo.searchNotifications(page = s.currentPage)
                    SoundTab.ALARMS -> soundRepo.searchAlarms(page = s.currentPage)
                    SoundTab.TRENDING -> soundRepo.getTrending(page = s.currentPage)
                    SoundTab.SEARCH -> soundRepo.search(query = s.query, page = s.currentPage)
                }
                _state.update {
                    it.copy(
                        sounds = if (loadMore) it.sounds + result.items else result.items,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = result.hasMore,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = e.message) }
            }
        }
    }
}

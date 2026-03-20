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
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: SoundTab = SoundTab.TRENDING,
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val selectedCategory: SoundCategory? = null,
    val playingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
)

enum class SoundTab { TRENDING, RINGTONES, NOTIFICATIONS, ALARMS, SEARCH }

enum class DurationFilter(val label: String, val minSec: Int, val maxSec: Int) {
    ALL("All", 0, 300),
    SHORT("< 5s", 0, 5),
    MEDIUM("5-15s", 5, 15),
    LONG("15-60s", 15, 60),
}

enum class SoundCategory(val label: String, val emoji: String, val query: String) {
    NATURE("Nature", "\uD83C\uDF3F", "nature rain water ocean birds wind thunder forest"),
    ELECTRONIC("Electronic", "\uD83C\uDFB9", "electronic synth beep digital glitch circuit"),
    FUNNY("Funny", "\uD83D\uDE02", "funny comedy cartoon humor laugh silly"),
    SCARY("Scary", "\uD83D\uDC7B", "horror scary dark creepy suspense ghost"),
    SCIFI("Sci-Fi", "\uD83D\uDE80", "sci-fi space laser futuristic robot alien"),
    MUSICAL("Musical", "\uD83C\uDFB5", "musical instrument piano guitar drum trumpet"),
    AMBIENT("Ambient", "\uD83C\uDF19", "ambient drone pad atmospheric texture calm"),
    VOICE("Voice", "\uD83D\uDDE3\uFE0F", "voice speech human vocal talk"),
    GAMING("Gaming", "\uD83C\uDFAE", "game 8bit retro arcade pixel chiptune"),
    MECHANICAL("Mechanical", "\u2699\uFE0F", "mechanical click switch button industrial metal"),
}

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
        _state.update {
            it.copy(
                selectedTab = tab,
                selectedCategory = null,
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
            )
        }
        loadSounds()
    }

    fun search(query: String) {
        stopPlayback()
        _state.update {
            it.copy(
                query = query,
                selectedTab = SoundTab.SEARCH,
                selectedCategory = null,
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
            )
        }
        loadSounds()
    }

    fun setDurationFilter(filter: DurationFilter) {
        if (filter == _state.value.durationFilter) return
        stopPlayback()
        _state.update { it.copy(durationFilter = filter, sounds = emptyList(), currentPage = 1, hasMore = true) }
        loadSounds()
    }

    fun selectCategory(category: SoundCategory) {
        stopPlayback()
        val isSame = _state.value.selectedCategory == category
        _state.update {
            it.copy(
                selectedCategory = if (isSame) null else category,
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

    /** Load similar sounds from Freesound for "More Like This" */
    suspend fun loadSimilar(freesoundId: Int): List<Sound> {
        return soundRepo.getSimilar(freesoundId).items
    }

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
                val dur = s.durationFilter
                val cat = s.selectedCategory

                val result = when {
                    // Category overrides tab behavior
                    cat != null -> soundRepo.search(
                        query = cat.query,
                        page = s.currentPage,
                        maxDuration = dur.maxSec,
                        minDuration = dur.minSec,
                    )
                    // Tab-specific
                    s.selectedTab == SoundTab.TRENDING -> soundRepo.getTrending(
                        page = s.currentPage,
                        maxDuration = dur.maxSec,
                        minDuration = dur.minSec,
                    )
                    s.selectedTab == SoundTab.RINGTONES -> soundRepo.searchRingtones(
                        page = s.currentPage,
                        maxDuration = dur.maxSec.coerceAtMost(30),
                        minDuration = dur.minSec.coerceAtLeast(3),
                    )
                    s.selectedTab == SoundTab.NOTIFICATIONS -> soundRepo.searchNotifications(
                        page = s.currentPage,
                        maxDuration = dur.maxSec.coerceAtMost(8),
                        minDuration = dur.minSec,
                    )
                    s.selectedTab == SoundTab.ALARMS -> soundRepo.searchAlarms(
                        page = s.currentPage,
                        maxDuration = dur.maxSec.coerceAtMost(20),
                        minDuration = dur.minSec.coerceAtLeast(2),
                    )
                    s.selectedTab == SoundTab.SEARCH -> soundRepo.search(
                        query = s.query,
                        page = s.currentPage,
                        maxDuration = dur.maxSec,
                        minDuration = dur.minSec,
                    )
                    else -> soundRepo.getTrending(page = s.currentPage)
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

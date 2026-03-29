package com.freevibe.ui.screens.sounds

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.local.PreferencesManager
import android.util.Log
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.SoundRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class SoundsUiState(
    val sounds: List<Sound> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadingProgress: String? = null,
    val error: String? = null,
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: SoundTab = SoundTab.RINGTONES,
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val selectedCategory: SoundCategory? = null,
    val playingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, SEARCH }

enum class DurationFilter(val label: String, val minSec: Int, val maxSec: Int) {
    ALL("All", 0, 60),
    SHORT("< 5s", 0, 5),
    MEDIUM("5-30s", 5, 30),
    LONG("30s-60s", 30, 60),
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
    private val youtubeRepo: YouTubeRepository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
    private val searchHistoryRepo: SearchHistoryRepository,
    prefs: PreferencesManager,
    val voteRepo: VoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundsUiState())
    val state = _state.asStateFlow()

    val selectedSound = selectedContent.selectedSound

    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val previewVolume = prefs.soundPreviewVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)

    private val _cachedYtIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedYtIds = _cachedYtIds.asStateFlow()

    val recentSearches = searchHistoryRepo.getRecentSoundSearches(8)
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        loadSounds()
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "SOUND") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearSoundHistory() }
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
        } else if (sound.id.startsWith("yt_") && sound.previewUrl.isEmpty()) {
            // YouTube: use fast preview URL (worstaudio, cached)
            viewModelScope.launch {
                _state.update { it.copy(playingId = sound.id) }
                val videoId = sound.id.removePrefix("yt_")
                if (com.freevibe.BuildConfig.DEBUG) Log.d("SoundsVM", "Resolving YouTube preview for: $videoId")
                val url = youtubeRepo.getAudioPreviewUrl(videoId)
                if (com.freevibe.BuildConfig.DEBUG) Log.d("SoundsVM", "YouTube preview URL: ${url?.take(80) ?: "NULL"}")
                if (url != null) {
                    val resolved = sound.copy(previewUrl = url)
                    startPlayback(resolved)
                } else {
                    _state.update { it.copy(playingId = null, applySuccess = "Could not load audio") }
                }
            }
        } else {
            startPlayback(sound)
        }
    }

    fun applySound(sound: Sound, type: ContentType) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            val url = if (sound.id.startsWith("yt_") && sound.downloadUrl.isEmpty()) {
                youtubeRepo.getAudioStreamUrl(sound.id.removePrefix("yt_"))
                    ?: run { _state.update { it.copy(isApplying = false, error = "Could not resolve audio") }; return@launch }
            } else sound.downloadUrl
            soundApplier.downloadAndApply(url, sound.name, type)
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
            val dlUrl = if (sound.id.startsWith("yt_") && sound.downloadUrl.isEmpty()) {
                youtubeRepo.getAudioStreamUrl(sound.id.removePrefix("yt_")) ?: return@launch
            } else sound.downloadUrl
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3")
            downloadManager.downloadSound(
                id = sound.id,
                url = dlUrl,
                fileName = "Aura_${sound.name.take(40)}.$ext",
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
            _state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
        }
    }

    fun isFavorite(id: String): Flow<Boolean> = favoritesRepo.isFavorite(id)

    /** Load similar sounds by keyword search */
    suspend fun loadSimilar(soundId: String): List<Sound> {
        val sound = selectedContent.selectedSound.value ?: return emptyList()
        val keywords = sound.name.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
        return soundRepo.searchSimilar(keywords, soundId)
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    // -- Voting --
    fun upvote(id: String) { viewModelScope.launch { voteRepo.upvote(id) } }
    fun downvote(id: String) { viewModelScope.launch { voteRepo.downvote(id) } }

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sound.previewUrl))
            prepare()
            volume = previewVolume.value
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
                _state.update { it.copy(isLoading = true, error = null, loadingProgress = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            val progressCallback: ((Int, Int) -> Unit) = { resolved, total ->
                try {
                    if (total > 0) {
                        _state.update { it.copy(loadingProgress = "Fetching sounds... $resolved/$total") }
                    }
                } catch (_: Exception) {}
            }

            val allResults = java.util.concurrent.CopyOnWriteArrayList<Sound>()
            var lastUiUpdate = 0L

            val streamCallback: ((Sound) -> Unit) = { sound ->
                try {
                    allResults.add(sound)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > 300) {
                        lastUiUpdate = now
                        val snapshot = allResults.toList()
                        _state.update { st ->
                            st.copy(
                                sounds = if (loadMore) st.sounds + snapshot.filter { snd -> st.sounds.none { it.id == snd.id } } else snapshot,
                                isLoading = false,
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            try {
                val dur = s.durationFilter
                val cat = s.selectedCategory

                // Build specific YouTube queries that return actual sound clips, not compilations
                val ytQuery = when {
                    cat != null -> "${cat.label} sound effect free download"
                    s.selectedTab == SoundTab.RINGTONES -> "ringtone sound effect free"
                    s.selectedTab == SoundTab.NOTIFICATIONS -> "notification sound effect short"
                    s.selectedTab == SoundTab.ALARMS -> "alarm sound effect free"
                    s.selectedTab == SoundTab.SEARCH -> "${s.query} sound effect"
                    else -> "ringtone sound effect free"
                }

                // Tab-specific YouTube duration caps
                val ytMaxDur = when (s.selectedTab) {
                    SoundTab.RINGTONES -> dur.maxSec.coerceAtMost(30)
                    SoundTab.NOTIFICATIONS -> dur.maxSec.coerceAtMost(3)
                    SoundTab.ALARMS -> dur.maxSec.coerceAtMost(40)
                    else -> dur.maxSec.coerceAtMost(60)
                }
                val ytMinDur = when (s.selectedTab) {
                    SoundTab.RINGTONES -> dur.minSec.coerceAtLeast(5)
                    SoundTab.ALARMS -> dur.minSec.coerceAtLeast(5)
                    else -> dur.minSec
                }

                supervisorScope {
                    // YouTube results first — fast single API call
                    try {
                        val ytResult = youtubeRepo.searchSounds(
                            query = ytQuery,
                            maxDuration = ytMaxDur,
                            minDuration = ytMinDur,
                        )
                        // Flush YouTube results to UI immediately
                        if (ytResult.items.isNotEmpty()) {
                            ytResult.items.forEach { allResults.add(it) }
                            _state.update { st ->
                                st.copy(sounds = allResults.toList(), isLoading = false)
                            }
                            // Pre-resolve ALL YouTube audio URLs in background
                            val preResolveSemaphore = kotlinx.coroutines.sync.Semaphore(8)
                            ytResult.items.forEach { yt ->
                                launch {
                                    preResolveSemaphore.acquire()
                                    try {
                                        val vid = yt.id.removePrefix("yt_")
                                        youtubeRepo.getAudioPreviewUrl(vid)
                                        _cachedYtIds.value = _cachedYtIds.value + yt.id
                                    } catch (_: Exception) {} finally {
                                        preResolveSemaphore.release()
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // IA results stream progressively after YouTube
                    val iaJob = async {
                        try {
                            when {
                                cat != null -> soundRepo.search(
                                    query = cat.query, page = s.currentPage,
                                    maxDuration = dur.maxSec, minDuration = dur.minSec,
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                                s.selectedTab == SoundTab.RINGTONES -> soundRepo.searchRingtones(
                                    page = s.currentPage, maxDuration = dur.maxSec.coerceAtMost(30),
                                    minDuration = dur.minSec.coerceAtLeast(5),
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                                s.selectedTab == SoundTab.NOTIFICATIONS -> soundRepo.searchNotifications(
                                    page = s.currentPage, maxDuration = dur.maxSec.coerceAtMost(3),
                                    minDuration = dur.minSec,
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                                s.selectedTab == SoundTab.ALARMS -> soundRepo.searchAlarms(
                                    page = s.currentPage, maxDuration = dur.maxSec.coerceAtMost(40),
                                    minDuration = dur.minSec.coerceAtLeast(5),
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                                s.selectedTab == SoundTab.SEARCH -> soundRepo.search(
                                    query = s.query, page = s.currentPage,
                                    maxDuration = dur.maxSec, minDuration = dur.minSec,
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                                else -> soundRepo.searchRingtones(
                                    page = s.currentPage,
                                    onProgress = progressCallback, onSoundResolved = streamCallback,
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    iaJob.await()
                }

                // Final flush with all results
                val combined = allResults.toList()
                _state.update {
                    it.copy(
                        sounds = if (loadMore) it.sounds + combined.filter { snd -> it.sounds.none { e -> e.id == snd.id } } else combined,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = combined.size >= 10,
                        loadingProgress = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = e.message, loadingProgress = null) }
            }
        }
    }
}

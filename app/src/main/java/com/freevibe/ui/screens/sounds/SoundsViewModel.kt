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
    private val freesoundRepo: com.freevibe.data.repository.FreesoundRepository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val audioTrimmer: com.freevibe.service.AudioTrimmer,
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

    /** Trending sounds from Freesound (most downloaded) */
    private val _trendingSounds = MutableStateFlow<List<Sound>>(emptyList())
    val trendingSounds = _trendingSounds.asStateFlow()

    /** Sound of the Day */
    private val _soundOfTheDay = MutableStateFlow<Sound?>(null)
    val soundOfTheDay = _soundOfTheDay.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    init {
        loadSounds()
        fetchTrendingAndSotd()
    }

    private fun fetchTrendingAndSotd() {
        viewModelScope.launch {
            try {
                val trending = freesoundRepo.getTrending(page = 1)
                _trendingSounds.value = trending.items.take(10)
                _soundOfTheDay.value = trending.items.firstOrNull()
            } catch (_: Exception) {}
        }
    }

    /** Normalize audio volume via FFmpeg loudnorm */
    fun normalizeAudio(inputPath: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(audioTrimmer.normalize(inputPath))
        }
    }

    /** Convert audio format via FFmpeg */
    fun convertAudio(inputPath: String, targetFormat: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(audioTrimmer.convert(inputPath, targetFormat))
        }
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

    /** Playback position as 0.0-1.0 fraction for seekable waveform */
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()
    private var progressJob: kotlinx.coroutines.Job? = null

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(sound.previewUrl))
            prepare()
            volume = previewVolume.value
            play()
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        _state.update { it.copy(playingId = null) }
                        _playbackProgress.value = 0f
                        progressJob?.cancel()
                    }
                }
            })
        }
        _state.update { it.copy(playingId = sound.id) }
        // Track playback position
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_state.value.playingId == sound.id) {
                val player = exoPlayer
                if (player != null && player.duration > 0) {
                    _playbackProgress.value = player.currentPosition.toFloat() / player.duration
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun seekTo(fraction: Float) {
        exoPlayer?.let { player ->
            if (player.duration > 0) {
                player.seekTo((fraction * player.duration).toLong())
            }
        }
    }

    private fun stopPlayback() {
        progressJob?.cancel()
        _playbackProgress.value = 0f
        exoPlayer?.apply { stop(); clearMediaItems() }
        _state.update { it.copy(playingId = null) }
    }

    override fun onCleared() {
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
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

                // Build specific YouTube queries — precise terms to avoid music/compilations
                val ytQuery = when {
                    cat != null -> "${cat.label} sound effect short free download"
                    s.selectedTab == SoundTab.RINGTONES -> "phone ringtone tone free download no copyright"
                    s.selectedTab == SoundTab.NOTIFICATIONS -> "notification sound effect beep chime short"
                    s.selectedTab == SoundTab.ALARMS -> "alarm clock sound effect wake up tone free"
                    s.selectedTab == SoundTab.SEARCH -> "${s.query} sound effect free"
                    else -> "phone ringtone tone free download"
                }

                // Tab-specific YouTube duration caps
                val ytMaxDur = when (s.selectedTab) {
                    SoundTab.RINGTONES -> dur.maxSec.coerceAtMost(30)
                    SoundTab.NOTIFICATIONS -> dur.maxSec.coerceAtMost(5)
                    SoundTab.ALARMS -> dur.maxSec.coerceAtMost(40)
                    else -> dur.maxSec.coerceAtMost(60)
                }
                val ytMinDur = when (s.selectedTab) {
                    SoundTab.RINGTONES -> dur.minSec.coerceAtLeast(5)
                    SoundTab.ALARMS -> dur.minSec.coerceAtLeast(3)
                    else -> dur.minSec
                }

                // Stock Android sounds — instant, perfectly categorized, no API call needed
                if (s.currentPage == 1 && cat == null && s.query.isEmpty()) {
                    val stockSounds = getStockSounds(s.selectedTab)
                    if (stockSounds.isNotEmpty()) {
                        stockSounds.forEach { allResults.add(it) }
                        _state.update { st -> st.copy(sounds = allResults.toList(), isLoading = false) }
                    }
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

                    // Freesound results (fast — metadata in response)
                    val fsJob = async {
                        try {
                            val fsResult = when {
                                cat != null -> freesoundRepo.search(cat.query, minDuration = dur.minSec.toDouble(), maxDuration = dur.maxSec.toDouble(), page = s.currentPage)
                                s.selectedTab == SoundTab.RINGTONES -> freesoundRepo.getRingtones(page = s.currentPage)
                                s.selectedTab == SoundTab.NOTIFICATIONS -> freesoundRepo.getNotifications(page = s.currentPage)
                                s.selectedTab == SoundTab.ALARMS -> freesoundRepo.getAlarms(page = s.currentPage)
                                s.selectedTab == SoundTab.SEARCH -> freesoundRepo.search(s.query, minDuration = dur.minSec.toDouble(), maxDuration = dur.maxSec.toDouble(), page = s.currentPage)
                                else -> freesoundRepo.getRingtones(page = s.currentPage)
                            }
                            fsResult.items.forEach { allResults.add(it) }
                            if (fsResult.items.isNotEmpty()) {
                                _state.update { st ->
                                    st.copy(sounds = allResults.toList(), isLoading = false)
                                }
                            }
                        } catch (_: Exception) {}
                    }

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
                                    page = s.currentPage, maxDuration = dur.maxSec.coerceAtMost(5),
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

                    fsJob.await()
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

    /** Stock Android sounds from AOSP — perfectly categorized, instant load */
    private fun getStockSounds(tab: SoundTab): List<Sound> {
        val base = "https://raw.githubusercontent.com/AospExtended/platform_frameworks_base/12.0/data/sounds"
        fun stock(id: String, name: String, path: String, dur: Double) = Sound(
            id = "aosp_$id", source = com.freevibe.data.model.ContentSource.LOCAL,
            name = name, previewUrl = "$base/$path", downloadUrl = "$base/$path",
            duration = dur, fileType = "OGG", license = "Apache 2.0", uploaderName = "Android",
        )
        return when (tab) {
            SoundTab.RINGTONES -> listOf(
                stock("ring_andromeda", "Andromeda", "ringtones/ogg/Andromeda.ogg", 14.0),
                stock("ring_aquila", "Aquila", "ringtones/ogg/Aquila.ogg", 15.0),
                stock("ring_carina", "Carina", "ringtones/ogg/Carina.ogg", 10.0),
                stock("ring_centaurus", "Centaurus", "ringtones/ogg/Centaurus.ogg", 14.0),
                stock("ring_cygnus", "Cygnus", "ringtones/ogg/Cygnus.ogg", 14.0),
                stock("ring_draco", "Draco", "ringtones/ogg/Draco.ogg", 15.0),
                stock("ring_girtab", "Girtab", "ringtones/ogg/Girtab.ogg", 14.0),
                stock("ring_hydra", "Hydra", "ringtones/ogg/Hydra.ogg", 14.0),
                stock("ring_kuma", "Kuma", "ringtones/ogg/Kuma.ogg", 10.0),
                stock("ring_lebes", "Lebes", "ringtones/ogg/Lebes.ogg", 14.0),
                stock("ring_lyra", "Lyra", "ringtones/ogg/Lyra.ogg", 10.0),
                stock("ring_machina", "Machina", "ringtones/ogg/Machina.ogg", 10.0),
                stock("ring_orion", "Orion", "ringtones/ogg/Orion.ogg", 15.0),
                stock("ring_pegasus", "Pegasus", "ringtones/ogg/Pegasus.ogg", 14.0),
                stock("ring_perseus", "Perseus", "ringtones/ogg/Perseus.ogg", 15.0),
                stock("ring_pyxis", "Pyxis", "ringtones/ogg/Pyxis.ogg", 14.0),
                stock("ring_rigel", "Rigel", "ringtones/ogg/Rigel.ogg", 14.0),
                stock("ring_scarabaeus", "Scarabaeus", "ringtones/ogg/Scarabaeus.ogg", 14.0),
                stock("ring_sceptrum", "Sceptrum", "ringtones/ogg/Sceptrum.ogg", 15.0),
                stock("ring_solarium", "Solarium", "ringtones/ogg/Solarium.ogg", 15.0),
                stock("ring_themos", "Themos", "ringtones/ogg/Themos.ogg", 14.0),
                stock("ring_titania", "Titania", "ringtones/ogg/Titania.ogg", 14.0),
                stock("ring_triton", "Triton", "ringtones/ogg/Triton.ogg", 10.0),
            )
            SoundTab.NOTIFICATIONS -> listOf(
                stock("notif_ariel", "Ariel", "notifications/ogg/Ariel.ogg", 1.0),
                stock("notif_carme", "Carme", "notifications/ogg/Carme.ogg", 1.0),
                stock("notif_ceres", "Ceres", "notifications/ogg/Ceres.ogg", 1.0),
                stock("notif_elara", "Elara", "notifications/ogg/Elara.ogg", 1.0),
                stock("notif_europa", "Europa", "notifications/ogg/Europa.ogg", 1.0),
                stock("notif_iapetus", "Iapetus", "notifications/ogg/Iapetus.ogg", 1.0),
                stock("notif_io", "Io", "notifications/ogg/Io.ogg", 1.0),
                stock("notif_lalande", "Lalande", "notifications/ogg/Lalande.ogg", 1.0),
                stock("notif_mira", "Mira", "notifications/ogg/Mira.ogg", 1.0),
                stock("notif_polaris", "Polaris", "notifications/ogg/Polaris.ogg", 1.0),
                stock("notif_procyon", "Procyon", "notifications/ogg/Procyon.ogg", 1.0),
                stock("notif_proxima", "Proxima", "notifications/ogg/Proxima.ogg", 1.0),
                stock("notif_shaula", "Shaula", "notifications/ogg/Shaula.ogg", 1.0),
                stock("notif_spica", "Spica", "notifications/ogg/Spica.ogg", 1.0),
                stock("notif_styx", "Styx", "notifications/ogg/Styx.ogg", 1.0),
                stock("notif_talitha", "Talitha", "notifications/ogg/Talitha.ogg", 1.0),
                stock("notif_tejat", "Tejat", "notifications/ogg/Tejat.ogg", 1.0),
                stock("notif_tethys", "Tethys", "notifications/ogg/Tethys.ogg", 1.0),
            )
            SoundTab.ALARMS -> listOf(
                stock("alarm_argon", "Argon", "alarms/ogg/Argon.ogg", 7.0),
                stock("alarm_carbon", "Carbon", "alarms/ogg/Carbon.ogg", 10.0),
                stock("alarm_helium", "Helium", "alarms/ogg/Helium.ogg", 7.0),
                stock("alarm_krypton", "Krypton", "alarms/ogg/Krypton.ogg", 7.0),
                stock("alarm_neon", "Neon", "alarms/ogg/Neon.ogg", 7.0),
                stock("alarm_noble", "Noble", "alarms/ogg/Noble.ogg", 8.0),
                stock("alarm_osmium", "Osmium", "alarms/ogg/Osmium.ogg", 10.0),
                stock("alarm_platinum", "Platinum", "alarms/ogg/Platinum.ogg", 7.0),
                stock("alarm_promethium", "Promethium", "alarms/ogg/Promethium.ogg", 8.0),
                stock("alarm_scandium", "Scandium", "alarms/ogg/Scandium.ogg", 10.0),
            )
            else -> emptyList()
        }
    }
}

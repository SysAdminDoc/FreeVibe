package com.freevibe.ui.screens.sounds

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.FreesoundV2Repository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
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
    val selectedTab: SoundTab = SoundTab.RINGTONES,
    val playingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val filterKey: Int = 0,
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, YOUTUBE, SEARCH }

@HiltViewModel
class SoundsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val freesoundRepo: com.freevibe.data.repository.FreesoundRepository,
    private val freesoundV2Repo: FreesoundV2Repository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val audioTrimmer: com.freevibe.service.AudioTrimmer,
    private val prefs: PreferencesManager,
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

    private val _topHits = MutableStateFlow<List<Sound>>(emptyList())
    val topHits = _topHits.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                _state.update { it.copy(playingId = null) }
                _playbackProgress.value = 0f
                progressJob?.cancel()
            }
        }
    }

    private val titleBlocklist = Regex("hindi|telugu|pack", RegexOption.IGNORE_CASE)
    private val hasFreesoundV2Key = com.freevibe.BuildConfig.FREESOUND_API_KEY.isNotBlank()

    init {
        loadSounds()
        fetchTopHits()
    }

    // -- Top 5 This Week --

    private fun fetchTopHits() {
        viewModelScope.launch {
            try {
                val blocked = try {
                    prefs.ytSoundBlockedWords.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                } catch (_: Exception) { emptyList() }

                val queries = listOf(
                    "top songs this week 2026 ringtone",
                    "billboard hot 100 this week ringtone 2026",
                    "most popular songs right now ringtone 2026",
                )
                val allHits = mutableListOf<Sound>()
                val seenIds = mutableSetOf<String>()
                for (q in queries) {
                    if (allHits.size >= 5) break
                    try {
                        val result = youtubeRepo.searchSounds(
                            query = q, maxDuration = 40, minDuration = 8,
                            blockedWords = blocked,
                        )
                        result.items
                            .filter { !titleBlocklist.containsMatchIn(it.name) }
                            .forEach { if (seenIds.add(it.id) && allHits.size < 5) allHits.add(it) }
                    } catch (_: Exception) {}
                }
                _topHits.value = allHits.take(5)

                // Pre-resolve preview URLs
                val sem = Semaphore(4)
                supervisorScope {
                    allHits.forEach { hit ->
                        launch {
                            sem.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(hit.id.removePrefix("yt_"))
                                _cachedYtIds.value = _cachedYtIds.value + hit.id
                            } catch (_: Exception) {} finally { sem.release() }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // -- Tab & Search --

    private fun nextFilterKey() = _state.value.filterKey + 1

    fun selectTab(tab: SoundTab) {
        stopPlayback()
        _state.update {
            it.copy(
                selectedTab = tab, query = "", sounds = emptyList(),
                currentPage = 1, hasMore = true, error = null, filterKey = nextFilterKey(),
            )
        }
        if (tab != SoundTab.YOUTUBE) loadSounds()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        stopPlayback()
        _state.update {
            it.copy(
                query = query, selectedTab = SoundTab.SEARCH,
                sounds = emptyList(), currentPage = 1, hasMore = true, filterKey = nextFilterKey(),
            )
        }
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        loadSounds()
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        stopPlayback()
        _state.update {
            it.copy(
                query = query, selectedTab = SoundTab.YOUTUBE,
                sounds = emptyList(), currentPage = 1, hasMore = true,
                filterKey = nextFilterKey(), isLoading = true, error = null,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val blocked = try {
                    prefs.ytSoundBlockedWords.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                } catch (_: Exception) { emptyList() }

                val result = youtubeRepo.searchSounds(
                    query = query, maxDuration = 600, minDuration = 0,
                    blockedWords = blocked,
                )
                _state.update { it.copy(sounds = result.items, isLoading = false, hasMore = result.hasMore) }

                val sem = Semaphore(4)
                supervisorScope {
                    result.items.forEach { yt ->
                        launch {
                            sem.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))
                                _cachedYtIds.value = _cachedYtIds.value + yt.id
                            } catch (_: Exception) {} finally { sem.release() }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun importYouTubeUrl(url: String) {
        val videoId = extractYouTubeId(url)
        if (videoId == null) {
            _state.update { it.copy(error = "Not a valid YouTube URL") }
            return
        }
        stopPlayback()
        _state.update {
            it.copy(
                selectedTab = SoundTab.YOUTUBE, isLoading = true, error = null,
                sounds = emptyList(), filterKey = nextFilterKey(),
            )
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = org.schabi.newpipe.extractor.NewPipe.getService(
                        org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId
                    )
                    val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                    extractor.fetchPage()
                    extractor
                }
                val sound = Sound(
                    id = "yt_$videoId",
                    source = ContentSource.YOUTUBE,
                    name = info.name ?: "YouTube Video",
                    description = "by ${info.uploaderName ?: "Unknown"}",
                    previewUrl = "",
                    downloadUrl = "",
                    duration = info.length.toDouble(),
                    tags = emptyList(),
                    license = "YouTube",
                    uploaderName = info.uploaderName ?: "Unknown",
                    sourcePageUrl = "https://www.youtube.com/watch?v=$videoId",
                )
                _state.update { it.copy(sounds = listOf(sound), isLoading = false) }
                youtubeRepo.getAudioPreviewUrl(videoId)?.let {
                    _cachedYtIds.value = _cachedYtIds.value + "yt_$videoId"
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Could not load video: ${e.message}") }
            }
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
            Regex("""^([a-zA-Z0-9_-]{11})$"""),
        )
        val trimmed = url.trim()
        for (p in patterns) {
            p.find(trimmed)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "SOUND") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearSoundHistory() }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadSounds(loadMore = true)
    }

    fun refresh() {
        stopPlayback()
        _state.update { it.copy(isRefreshing = true, currentPage = 1, sounds = emptyList(), error = null) }
        loadSounds(isRefresh = true)
        fetchTopHits()
    }

    // -- Playback --

    fun selectSound(sound: Sound) { selectedContent.selectSound(sound) }

    fun togglePlayback(sound: Sound) {
        if (_state.value.playingId == sound.id) {
            stopPlayback()
        } else if (sound.id.startsWith("yt_") && sound.previewUrl.isEmpty()) {
            viewModelScope.launch {
                _state.update { it.copy(playingId = sound.id) }
                val videoId = sound.id.removePrefix("yt_")
                val url = youtubeRepo.getAudioPreviewUrl(videoId)
                if (url != null) {
                    startPlayback(sound.copy(previewUrl = url))
                } else {
                    _state.update { it.copy(playingId = null, applySuccess = "Could not load audio") }
                }
            }
        } else {
            startPlayback(sound)
        }
    }

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().also { it.addListener(playerListener) }
        }
        exoPlayer?.apply {
            stop(); clearMediaItems()
            setMediaItem(MediaItem.fromUri(sound.previewUrl))
            prepare(); volume = previewVolume.value; play()
        }
        _state.update { it.copy(playingId = sound.id) }
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_state.value.playingId == sound.id) {
                val player = exoPlayer
                if (player != null && player.duration > 0) {
                    _playbackProgress.value = player.currentPosition.toFloat() / player.duration
                }
                delay(50)
            }
        }
    }

    fun seekTo(fraction: Float) {
        exoPlayer?.let { if (it.duration > 0) it.seekTo((fraction * it.duration).toLong()) }
    }

    private fun stopPlayback() {
        progressJob?.cancel()
        _playbackProgress.value = 0f
        exoPlayer?.apply { stop(); clearMediaItems() }
        _state.update { it.copy(playingId = null) }
    }

    // -- Apply & Download --

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
                .onFailure { e -> _state.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun downloadSound(sound: Sound) {
        viewModelScope.launch {
            val dlUrl = if (sound.id.startsWith("yt_") && sound.downloadUrl.isEmpty()) {
                youtubeRepo.getAudioStreamUrl(sound.id.removePrefix("yt_")) ?: return@launch
            } else sound.downloadUrl
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase()
            downloadManager.downloadSound(
                id = sound.id, url = dlUrl,
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

    suspend fun loadSimilar(soundId: String): List<Sound> {
        val sound = selectedContent.selectedSound.value ?: return emptyList()
        val keywords = sound.name.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 }.take(4).joinToString(" ")
        if (keywords.isBlank()) return emptyList()
        return try {
            if (hasFreesoundV2Key) {
                freesoundV2Repo.search(query = keywords, minDuration = 1.0, maxDuration = 60.0).items
                    .filter { it.id != soundId }.take(10)
            } else {
                freesoundRepo.search(query = keywords, minDuration = 1.0, maxDuration = 60.0).items
                    .filter { it.id != soundId }.take(10)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun normalizeAudio(inputPath: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(audioTrimmer.normalize(inputPath)) }
    }

    fun convertAudio(inputPath: String, targetFormat: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(audioTrimmer.convert(inputPath, targetFormat)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    fun upvote(id: String) { viewModelScope.launch { voteRepo.upvote(id) } }
    fun downvote(id: String) { viewModelScope.launch { voteRepo.downvote(id) } }

    override fun onCleared() {
        loadJob?.cancel(); progressJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release(); exoPlayer = null
        super.onCleared()
    }

    // -- Main Load --

    private fun loadSounds(loadMore: Boolean = false, isRefresh: Boolean = false) {
        if (_state.value.selectedTab == SoundTab.YOUTUBE) return // YouTube tab uses searchYouTube()

        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            val allResults = java.util.concurrent.CopyOnWriteArrayList<Sound>()
            val seenIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            fun addUnique(sound: Sound): Boolean {
                if (titleBlocklist.containsMatchIn(sound.name)) return false
                return if (seenIds.add(sound.id)) { allResults.add(sound); true } else false
            }

            fun flushToUi() {
                _state.update { st ->
                    val snapshot = allResults.toList().sortedByDescending { qualityScore(it) }
                    st.copy(
                        sounds = if (loadMore) st.sounds + snapshot.filter { snd -> st.sounds.none { it.id == snd.id } } else snapshot,
                        isLoading = false,
                    )
                }
            }

            try {
                val queries = buildQueries(s)
                val (cappedMin, cappedMax) = tabDurationRange(s)

                supervisorScope {
                    // YouTube
                    if (!loadMore && queries.ytQueries.isNotEmpty()) {
                        val blocked = try {
                            prefs.ytSoundBlockedWords.first()
                                .split(",").map { it.trim() }.filter { it.isNotBlank() }
                        } catch (_: Exception) { emptyList() }

                        queries.ytQueries.forEach { ytQ ->
                            launch {
                                try {
                                    val result = youtubeRepo.searchSounds(
                                        query = ytQ, maxDuration = cappedMax,
                                        minDuration = cappedMin, blockedWords = blocked,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()

                                    val sem = Semaphore(4)
                                    result.items.forEach { yt ->
                                        launch {
                                            sem.acquire()
                                            try {
                                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))
                                                _cachedYtIds.value = _cachedYtIds.value + yt.id
                                            } catch (_: Exception) {} finally { sem.release() }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    // FreesoundV2
                    if (hasFreesoundV2Key && queries.ovQueries.isNotEmpty()) {
                        queries.ovQueries.forEach { q ->
                            launch {
                                try {
                                    val result = freesoundV2Repo.search(
                                        query = q, minDuration = cappedMin.toDouble(),
                                        maxDuration = cappedMax.toDouble(), page = s.currentPage,
                                        sort = "downloads_desc",
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    // Openverse (zero auth fallback)
                    if (queries.ovQueries.isNotEmpty()) {
                        queries.ovQueries.forEach { q ->
                            launch {
                                try {
                                    val result = freesoundRepo.search(
                                        query = q, minDuration = cappedMin.toDouble(),
                                        maxDuration = cappedMax.toDouble(), page = s.currentPage,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                val combined = allResults.toList().sortedByDescending { qualityScore(it) }
                _state.update {
                    it.copy(
                        sounds = if (loadMore) it.sounds + combined.filter { snd -> it.sounds.none { e -> e.id == snd.id } } else combined,
                        isLoading = false, isLoadingMore = false, isRefreshing = false,
                        hasMore = combined.size >= 3,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    // -- Query Building --

    private data class QuerySet(val ovQueries: List<String>, val ytQueries: List<String>)

    private suspend fun buildQueries(s: SoundsUiState): QuerySet {
        val ytRingQ = prefs.ytSoundQueryRingtones.first()
        val ytNotifQ = prefs.ytSoundQueryNotifications.first()
        val ytAlarmQ = prefs.ytSoundQueryAlarms.first()

        return when (s.selectedTab) {
            SoundTab.RINGTONES -> QuerySet(
                listOf("ringtone melody phone ring", "ringtone tone music tune"),
                listOf(ytRingQ, "best ringtone 2025 2026 phone"),
            )
            SoundTab.NOTIFICATIONS -> QuerySet(
                listOf("notification chime ding alert", "notification beep ping pop"),
                listOf(ytNotifQ, "notification sound effect 2025 short"),
            )
            SoundTab.ALARMS -> QuerySet(
                listOf("alarm clock morning wake", "alarm buzzer bell siren"),
                listOf(ytAlarmQ, "alarm clock tone morning 2025"),
            )
            SoundTab.YOUTUBE -> QuerySet(emptyList(), emptyList()) // handled by searchYouTube()
            SoundTab.SEARCH -> QuerySet(
                listOf(s.query, "${s.query} sound effect"),
                listOf("${s.query} sound", "${s.query} ringtone"),
            )
        }
    }

    private fun tabDurationRange(s: SoundsUiState): Pair<Int, Int> = when (s.selectedTab) {
        SoundTab.RINGTONES -> 5 to 45
        SoundTab.NOTIFICATIONS -> 0 to 10
        SoundTab.ALARMS -> 5 to 60
        SoundTab.YOUTUBE -> 0 to 600
        SoundTab.SEARCH -> 0 to 60
    }

    private fun qualityScore(sound: Sound): Int {
        var score = 50
        score += when (sound.source) {
            ContentSource.FREESOUND -> 15
            ContentSource.YOUTUBE -> 15
            else -> 10
        }
        val name = sound.name
        if (name.contains("_") && !name.contains(" ")) score -= 20
        if (name.length in 6..59) score += 5
        if (name.matches(Regex("^\\d+.*"))) score -= 10
        val idealRange = when (_state.value.selectedTab) {
            SoundTab.RINGTONES -> 10.0..25.0
            SoundTab.NOTIFICATIONS -> 1.0..3.0
            SoundTab.ALARMS -> 10.0..30.0
            SoundTab.YOUTUBE -> 0.0..600.0
            SoundTab.SEARCH -> 3.0..30.0
        }
        if (sound.duration in idealRange) score += 10
        if (sound.tags.size >= 3) score += 5
        if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") score += 3
        return score
    }
}

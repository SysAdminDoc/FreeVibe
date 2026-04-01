package com.freevibe.ui.screens.sounds

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toSound
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.FreesoundV2Repository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.SoundCloudRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.BundledContentProvider
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
import java.time.Year
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
    val resolvingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val filterKey: Int = 0,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, YOUTUBE, COMMUNITY, SEARCH }

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
    private val bundledContent: BundledContentProvider,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val soundCloudRepo: SoundCloudRepository,
    val uploadRepo: UploadRepository,
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

    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var communityJob: Job? = null
    private val ytResolveSemaphore = Semaphore(6)

    private val titleBlocklist = Regex("hindi|telugu|pack|trending|popular|\\bnew\\b|\\btop\\b|\\bbest\\b", RegexOption.IGNORE_CASE)
    private val hasFreesoundV2Key = com.freevibe.BuildConfig.FREESOUND_API_KEY.isNotBlank()
    private val hasSoundCloudKey = com.freevibe.BuildConfig.SOUNDCLOUD_CLIENT_ID.isNotBlank()

    private val _communityUploads = MutableStateFlow<List<Sound>>(emptyList())
    val communityUploads = _communityUploads.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    init {
        loadSounds()
        fetchTopHits()
        // Sync playingId from AudioPlaybackManager
        viewModelScope.launch {
            audioPlaybackManager.currentSoundId.collect { soundId ->
                _state.update { it.copy(playingId = soundId) }
                if (soundId == null) _playbackProgress.value = 0f
            }
        }
    }

    // -- Top 5 This Week --

    private fun fetchTopHits() {
        viewModelScope.launch {
            try {
                val blocked = try {
                    prefs.ytSoundBlockedWords.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                } catch (_: Exception) { emptyList() }

                val queries = PreferencesManager.defaultTopHitQueries(Year.now().value)
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
                supervisorScope {
                    allHits.forEach { hit ->
                        launch {
                            ytResolveSemaphore.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(hit.id.removePrefix("yt_"))
                                _cachedYtIds.update { it + hit.id }
                            } catch (_: Exception) {} finally { ytResolveSemaphore.release() }
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
        if (tab == SoundTab.COMMUNITY) loadCommunityTab()
        else if (tab != SoundTab.YOUTUBE) loadSounds()
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

                supervisorScope {
                    result.items.forEach { yt ->
                        launch {
                            ytResolveSemaphore.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))
                                _cachedYtIds.update { it + yt.id }
                            } catch (_: Exception) {} finally { ytResolveSemaphore.release() }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = categorizeError(e)) }
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
                    _cachedYtIds.update { it + "yt_$videoId" }
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

    suspend fun resolveSound(id: String): Sound? {
        selectedContent.selectedSound.value?.takeIf { it.id == id }?.let { return it }

        _state.value.sounds.firstOrNull { it.id == id }?.let { return it }

        _communityUploads.value.firstOrNull { it.id == id }?.let { return it }

        _topHits.value.firstOrNull { it.id == id }?.let { return it }

        listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { it.id == id }?.let { return it }

        favoritesRepo.getById(id)
            ?.takeIf { it.type == "SOUND" }
            ?.toSound()
            ?.let { return it }

        return null
    }

    suspend fun ensureSelectedSound(id: String): Boolean {
        val resolved = resolveSound(id) ?: return false
        selectedContent.selectSound(resolved)
        return true
    }

    fun togglePlayback(sound: Sound) {
        if (_state.value.playingId == sound.id) {
            stopPlayback()
        } else if (sound.id == _state.value.resolvingId) {
            _state.update { it.copy(resolvingId = null) }
        } else if (sound.id.startsWith("yt_") && sound.previewUrl.isEmpty()) {
            viewModelScope.launch {
                _state.update { it.copy(resolvingId = sound.id) }
                val videoId = sound.id.removePrefix("yt_")
                val url = youtubeRepo.getAudioPreviewUrl(videoId)
                if (_state.value.resolvingId != sound.id) return@launch // user cancelled
                if (url != null) {
                    _state.update { it.copy(resolvingId = null) }
                    startPlayback(sound.copy(previewUrl = url))
                } else {
                    _state.update { it.copy(resolvingId = null, error = "Could not load audio") }
                }
            }
        } else {
            startPlayback(sound)
        }
    }

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        audioPlaybackManager.play(sound, sound.previewUrl, previewVolume.value)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (audioPlaybackManager.currentSoundId.value == sound.id) {
                audioPlaybackManager.pollProgress()
                val dur = audioPlaybackManager.duration.value
                val pos = audioPlaybackManager.currentPosition.value
                _playbackProgress.value = if (dur > 0) pos.toFloat() / dur else 0f
                delay(50)
            }
        }
    }

    fun seekTo(fraction: Float) {
        val dur = audioPlaybackManager.duration.value
        if (dur > 0) audioPlaybackManager.seekTo((fraction * dur).toLong())
    }

    fun stopIfPlaying(soundId: String) {
        if (_state.value.playingId == soundId) stopPlayback()
    }

    private fun stopPlayback() {
        progressJob?.cancel()
        _playbackProgress.value = 0f
        audioPlaybackManager.stop()
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
                youtubeRepo.getAudioStreamUrl(sound.id.removePrefix("yt_")) ?: run {
                    _state.update { it.copy(error = "Could not resolve audio stream URL") }
                    return@launch
                }
            } else sound.downloadUrl
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase()
            downloadManager.downloadSound(
                id = sound.id, url = dlUrl,
                fileName = "Aura_${sound.name.take(40)}.$ext",
                type = currentDownloadType(),
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
        val sound = resolveSound(soundId) ?: return emptyList()
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

    fun upvote(id: String) {
        viewModelScope.launch {
            try { voteRepo.upvote(id) }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Failed to upvote") } }
        }
    }
    fun downvote(id: String) {
        viewModelScope.launch {
            try { voteRepo.downvote(id) }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Failed to downvote") } }
        }
    }

    override fun onCleared() {
        loadJob?.cancel(); progressJob?.cancel(); communityJob?.cancel()
        audioPlaybackManager.stop()
        super.onCleared()
    }

    // -- Main Load --

    private fun loadSounds(loadMore: Boolean = false, isRefresh: Boolean = false) {
        val tab = _state.value.selectedTab
        if (tab == SoundTab.YOUTUBE || tab == SoundTab.COMMUNITY) return

        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            val loadTab = s.selectedTab
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            val allResults = mutableListOf<Sound>()
            val resultLock = Any()
            val seenIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            // Show bundled content immediately while APIs load, and seed allResults so they survive flushToUi()
            if (!loadMore && !isRefresh) {
                val bundled = when (s.selectedTab) {
                    SoundTab.RINGTONES -> bundledContent.getRingtones()
                    SoundTab.NOTIFICATIONS -> bundledContent.getNotifications()
                    SoundTab.ALARMS -> bundledContent.getAlarms()
                    else -> emptyList()
                }
                if (bundled.isNotEmpty()) {
                    bundled.forEach { seenIds.add(it.id) }
                    synchronized(resultLock) { allResults.addAll(bundled) }
                    _state.update { it.copy(sounds = bundled, isLoading = true) }
                }
            }

            fun addUnique(sound: Sound): Boolean {
                if (titleBlocklist.containsMatchIn(sound.name)) return false
                return if (seenIds.add(sound.id)) { synchronized(resultLock) { allResults.add(sound) }; true } else false
            }

            fun flushToUi() {
                _state.update { st ->
                    val snapshot = synchronized(resultLock) { allResults.toList() }.sortedByDescending { qualityScore(it, loadTab) }
                    st.copy(
                        sounds = if (loadMore) st.sounds + snapshot.filter { snd -> st.sounds.none { it.id == snd.id } } else snapshot,
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

                                    result.items.forEach { yt ->
                                        launch {
                                            ytResolveSemaphore.acquire()
                                            try {
                                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))
                                                _cachedYtIds.update { it + yt.id }
                                            } catch (_: Exception) {} finally { ytResolveSemaphore.release() }
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

                    // SoundCloud (CC-licensed)
                    if (hasSoundCloudKey && queries.ovQueries.isNotEmpty()) {
                        queries.ovQueries.take(1).forEach { q ->
                            launch {
                                try {
                                    val result = soundCloudRepo.search(
                                        query = q,
                                        minDurationMs = cappedMin * 1000,
                                        maxDurationMs = cappedMax * 1000,
                                        offset = (s.currentPage - 1) * 20,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                val combined = synchronized(resultLock) { allResults.toList() }.sortedByDescending { qualityScore(it, loadTab) }
                _state.update {
                    it.copy(
                        sounds = if (loadMore) it.sounds + combined.filter { snd -> it.sounds.none { e -> e.id == snd.id } } else combined,
                        isLoading = false, isLoadingMore = false, isRefreshing = false,
                        hasMore = combined.size >= 3,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, error = categorizeError(e)) }
            }
        }
    }

    // -- Query Building --

    private data class QuerySet(val ovQueries: List<String>, val ytQueries: List<String>)

    private suspend fun buildQueries(s: SoundsUiState): QuerySet {
        val ytRingQ = prefs.ytSoundQueryRingtones.first()
        val ytNotifQ = prefs.ytSoundQueryNotifications.first()
        val ytAlarmQ = prefs.ytSoundQueryAlarms.first()
        val currentYear = Year.now().value

        return when (s.selectedTab) {
            SoundTab.RINGTONES -> QuerySet(
                listOf("ringtone melody phone ring", "ringtone tone music tune"),
                listOf(ytRingQ, "best ringtone $currentYear phone"),
            )
            SoundTab.NOTIFICATIONS -> QuerySet(
                listOf("notification chime ding alert", "notification beep ping pop"),
                listOf(ytNotifQ, "notification sound effect $currentYear short"),
            )
            SoundTab.ALARMS -> QuerySet(
                listOf("alarm clock morning wake", "alarm buzzer bell siren"),
                listOf(ytAlarmQ, "alarm clock tone morning $currentYear"),
            )
            SoundTab.YOUTUBE -> QuerySet(emptyList(), emptyList())
            SoundTab.COMMUNITY -> QuerySet(emptyList(), emptyList())
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
        SoundTab.COMMUNITY -> 0 to 600
        SoundTab.SEARCH -> 0 to 60
    }

    private fun currentDownloadType(tab: SoundTab = _state.value.selectedTab): ContentType = when (tab) {
        SoundTab.NOTIFICATIONS -> ContentType.NOTIFICATION
        SoundTab.ALARMS -> ContentType.ALARM
        else -> ContentType.RINGTONE
    }

    private fun qualityScore(sound: Sound, tab: SoundTab = _state.value.selectedTab): Int {
        var score = 50
        score += when (sound.source) {
            ContentSource.BUNDLED -> 25
            ContentSource.FREESOUND -> 15
            ContentSource.YOUTUBE -> 15
            ContentSource.SOUNDCLOUD -> 14
            ContentSource.COMMUNITY -> 12
            else -> 10
        }
        val name = sound.name
        if (name.contains("_") && !name.contains(" ")) score -= 20
        if (name.length in 6..59) score += 5
        if (name.matches(Regex("^\\d+.*"))) score -= 10
        val idealRange = when (tab) {
            SoundTab.RINGTONES -> 10.0..25.0
            SoundTab.NOTIFICATIONS -> 1.0..3.0
            SoundTab.ALARMS -> 10.0..30.0
            SoundTab.YOUTUBE -> 0.0..600.0
            SoundTab.COMMUNITY -> 0.0..600.0
            SoundTab.SEARCH -> 3.0..30.0
        }
        if (sound.duration in idealRange) score += 10
        if (sound.tags.size >= 3) score += 5
        if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") score += 3
        return score
    }

    // -- Community Uploads --

    private fun loadCommunityTab() {
        communityJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        communityJob = viewModelScope.launch {
            val timeoutJob = launch {
                kotlinx.coroutines.delay(10_000L)
                if (_state.value.isLoading) {
                    _state.update { it.copy(isLoading = false, error = "Community uploads timed out") }
                }
            }
            try {
                uploadRepo.getCommunityUploads(limit = 50).collect { sounds ->
                    timeoutJob.cancel()
                    _state.update { it.copy(sounds = sounds, isLoading = false, hasMore = false) }
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun categorizeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Connection timed out — try again"
        is java.net.ConnectException -> "Could not connect to server"
        else -> e.message ?: "Something went wrong"
    }

    fun uploadSound(
        localUri: android.net.Uri,
        name: String,
        category: String,
        tags: List<String> = emptyList(),
    ) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f) }
            uploadRepo.uploadSound(
                localUri = localUri,
                name = name,
                category = category,
                tags = tags,
                onProgress = { progress ->
                    _state.update { it.copy(uploadProgress = progress) }
                },
            ).onSuccess {
                _state.update { it.copy(isUploading = false, uploadProgress = 0f, applySuccess = "Upload complete") }
            }.onFailure { e ->
                _state.update { it.copy(isUploading = false, uploadProgress = 0f, error = "Upload failed: ${e.message}") }
            }
        }
    }
}

package com.freevibe.ui.screens.sounds

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.Sound
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.AudiusRepository
import com.freevibe.data.repository.CcMixterRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.FreesoundV2Repository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.SoundCloudRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toSound
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.DownloadManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    val searchReturnTab: SoundTab = SoundTab.RINGTONES,
    val qualityFilter: SoundQualityFilter = SoundQualityFilter.BEST,
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, YOUTUBE, COMMUNITY, SEARCH }

@HiltViewModel
class SoundsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val freesoundRepo: com.freevibe.data.repository.FreesoundRepository,
    private val freesoundV2Repo: FreesoundV2Repository,
    private val audiusRepo: AudiusRepository,
    private val ccMixterRepo: CcMixterRepository,
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
    private val soundUrlResolver: SoundUrlResolver,
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
                val seenFingerprints = mutableSetOf<String>()
                for (q in queries) {
                    if (allHits.size >= 5) break
                    try {
                        val result = youtubeRepo.searchSounds(
                            query = q, maxDuration = 40, minDuration = 8,
                            blockedWords = blocked,
                        )
                        result.items
                            .filter { !titleBlocklist.containsMatchIn(it.name) }
                            .forEach {
                                if (seenFingerprints.add(soundFingerprint(it)) && allHits.size < 5) {
                                    allHits.add(it)
                                }
                            }
                    } catch (_: Exception) {}
                }
                _topHits.value = rankSounds(allHits, SoundTab.RINGTONES, SoundQualityFilter.BEST).take(5)

                // Pre-resolve preview URLs
                supervisorScope {
                    allHits.forEach { hit ->
                        launch {
                            ytResolveSemaphore.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(hit.id.removePrefix("yt_"))?.let { url ->
                                    cacheResolvedPreview(hit, url)
                                    _cachedYtIds.update { it + hit.id }
                                }
                            } catch (_: Exception) {} finally { ytResolveSemaphore.release() }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // -- Tab & Search --

    private fun nextFilterKey() = _state.value.filterKey + 1

    fun setQualityFilter(filter: SoundQualityFilter) {
        val currentTab = _state.value.selectedTab
        _state.update {
            it.copy(
                qualityFilter = filter,
                sounds = rankSounds(it.sounds, currentTab, filter),
                filterKey = nextFilterKey(),
            )
        }
    }

    fun selectTab(tab: SoundTab) {
        stopPlayback()
        communityJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = tab, query = "", sounds = emptyList(),
                currentPage = 1, hasMore = true, error = null, filterKey = nextFilterKey(),
                isRefreshing = false,
                searchReturnTab = if (tab == SoundTab.SEARCH) it.searchReturnTab else tab,
            )
        }
        if (tab == SoundTab.COMMUNITY) loadCommunityTab()
        else if (tab != SoundTab.YOUTUBE) loadSounds()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        stopPlayback()
        val returnTab = _state.value.selectedTab.takeIf { it != SoundTab.SEARCH } ?: _state.value.searchReturnTab
        _state.update {
            it.copy(
                query = query, selectedTab = SoundTab.SEARCH,
                sounds = emptyList(), currentPage = 1, hasMore = true, filterKey = nextFilterKey(),
                searchReturnTab = returnTab,
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
                isRefreshing = false,
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        executeYouTubeSearch(query)
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
                    cacheResolvedPreview(sound, it)
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

    fun clearSearchMode() {
        stopPlayback()
        val returnTab = _state.value.searchReturnTab
        communityJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = returnTab,
                query = "",
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                filterKey = nextFilterKey(),
            )
        }
        if (returnTab == SoundTab.COMMUNITY) loadCommunityTab()
        else if (returnTab != SoundTab.YOUTUBE) loadSounds()
    }

    fun clearYouTubeSearch() {
        stopPlayback()
        loadJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = SoundTab.YOUTUBE,
                query = "",
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                filterKey = nextFilterKey(),
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadSounds(loadMore = true)
    }

    fun refresh() {
        stopPlayback()
        when (val tab = _state.value.selectedTab) {
            SoundTab.COMMUNITY -> {
                _state.update { it.copy(isRefreshing = true, error = null) }
                loadCommunityTab(isRefresh = true)
            }
            SoundTab.YOUTUBE -> {
                val query = _state.value.query
                if (query.isBlank()) {
                    _state.update { it.copy(isRefreshing = false, error = null) }
                } else {
                    _state.update { it.copy(isRefreshing = true, error = null) }
                    executeYouTubeSearch(query)
                }
            }
            else -> {
                _state.update { it.copy(isRefreshing = true, currentPage = 1, error = null) }
                loadSounds(isRefresh = true)
                if (tab == SoundTab.RINGTONES) fetchTopHits()
            }
        }
    }

    // -- Playback --

    fun selectSound(sound: Sound) { selectedContent.selectSound(sound) }

    suspend fun resolveSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Sound? {
        selectedContent.selectedSound.value
            ?.takeIf { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }
            ?.let { return it }

        _state.value.sounds.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        _communityUploads.value.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        _topHits.value.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        (source?.let {
            favoritesRepo.getByIdentity(
                FavoriteIdentity(
                    id = id,
                    source = it.name,
                    type = "SOUND",
                )
            )
        } ?: favoritesRepo.getLatestByIdAndType(id, "SOUND"))
            ?.takeIf { it.type == "SOUND" }
            ?.toSound()
            ?.takeIf { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }
            ?.let { return it }

        return null
    }

    suspend fun ensureSelectedSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Boolean {
        val resolved = resolveSound(id, source, previewUrl, downloadUrl) ?: return false
        selectedContent.selectSound(resolved)
        return true
    }

    fun togglePlayback(sound: Sound) {
        val soundKey = sound.stableKey()
        if (_state.value.playingId == soundKey) {
            stopPlayback()
        } else if (soundKey == _state.value.resolvingId) {
            _state.update { it.copy(resolvingId = null) }
        } else if (shouldRefreshYouTubePreview(sound)) {
            viewModelScope.launch {
                _state.update { it.copy(resolvingId = soundKey) }
                val videoId = sound.youtubeVideoId()
                    ?: run {
                        _state.update { it.copy(resolvingId = null, error = "Could not load audio") }
                        return@launch
                    }
                val url = youtubeRepo.getAudioPreviewUrl(videoId)
                if (_state.value.resolvingId != soundKey) return@launch // user cancelled
                if (url != null) {
                    val updatedSound = cacheResolvedPreview(sound, url)
                    _state.update { it.copy(resolvingId = null) }
                    startPlayback(updatedSound)
                } else {
                    _state.update { it.copy(resolvingId = null, error = "Could not load audio") }
                }
            }
        } else {
            startPlayback(sound)
        }
    }

    private fun cacheResolvedPreview(sound: Sound, previewUrl: String): Sound {
        if (previewUrl.isBlank()) return sound
        val updatedSound = sound.copy(previewUrl = previewUrl)
        val targetKey = updatedSound.stableKey()

        _state.update { st ->
            val refreshed = st.sounds.map { existing ->
                if (existing.stableKey() == targetKey && existing.previewUrl != previewUrl) {
                    existing.copy(previewUrl = previewUrl)
                } else {
                    existing
                }
            }
            if (refreshed == st.sounds) st else st.copy(sounds = refreshed)
        }
        _topHits.update { hits ->
            hits.map { existing ->
                if (existing.stableKey() == targetKey && existing.previewUrl != previewUrl) {
                    existing.copy(previewUrl = previewUrl)
                } else {
                    existing
                }
            }
        }
        _communityUploads.update { uploads ->
            uploads.map { existing ->
                if (existing.stableKey() == targetKey && existing.previewUrl != previewUrl) {
                    existing.copy(previewUrl = previewUrl)
                } else {
                    existing
                }
            }
        }

        val currentSelected = selectedContent.selectedSound.value
        if (currentSelected?.stableKey() == targetKey && currentSelected.previewUrl != previewUrl) {
            selectedContent.selectSound(currentSelected.copy(previewUrl = previewUrl))
        }

        return selectedContent.selectedSound.value?.takeIf { it.stableKey() == targetKey } ?: updatedSound
    }

    private fun startPlayback(sound: Sound) {
        stopPlayback()
        audioPlaybackManager.play(sound, sound.previewUrl, previewVolume.value)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val soundKey = sound.stableKey()
            while (audioPlaybackManager.currentSoundId.value == soundKey) {
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

    fun stopIfPlaying(sound: Sound) {
        if (_state.value.playingId == sound.stableKey()) stopPlayback()
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
            val url = resolveDownloadUrl(sound)
                ?: run {
                    _state.update { it.copy(isApplying = false, error = "Could not resolve audio") }
                    return@launch
                }
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
            val dlUrl = resolveDownloadUrl(sound) ?: run {
                _state.update { it.copy(error = "Could not resolve audio stream URL") }
                return@launch
            }
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase()
            downloadManager.downloadSound(
                id = sound.stableKey(), url = dlUrl,
                fileName = buildSoundDownloadFileName(sound, ext),
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
            val isFav = favoritesRepo.isFavorite(sound.favoriteIdentity()).first()
            favoritesRepo.toggle(entity, isFav)
            _state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
        }
    }

    private fun buildSoundDownloadFileName(sound: Sound, extension: String): String =
        "Aura_${sound.source.name.lowercase()}_${sound.id}_${sound.name.take(24)}.$extension"

    fun isFavorite(sound: Sound): Flow<Boolean> = favoritesRepo.isFavorite(sound.favoriteIdentity())

    private fun shouldRefreshYouTubePreview(sound: Sound): Boolean {
        val videoId = sound.youtubeVideoId() ?: return false
        return sound.previewUrl.isBlank() || !youtubeRepo.isCached(videoId)
    }

    private suspend fun resolveDownloadUrl(sound: Sound): String? {
        val videoId = sound.youtubeVideoId()
        return if (videoId != null) {
            youtubeRepo.getAudioStreamUrl(videoId)
        } else {
            soundUrlResolver.resolve(sound)
        }
    }

    suspend fun loadSimilar(sound: Sound): List<Sound> {
        val keywords = sound.name.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 }.take(4).joinToString(" ")
        if (keywords.isBlank()) return emptyList()
        return try {
            val richerResults = freesoundV2Repo.search(
                query = keywords,
                minDuration = 1.0,
                maxDuration = 60.0,
            ).items.filter { it.stableKey() != sound.stableKey() }
            val fallbackResults = if (richerResults.isEmpty()) {
                freesoundRepo.search(query = keywords, minDuration = 1.0, maxDuration = 60.0).items
                    .filter { it.stableKey() != sound.stableKey() }
            } else {
                emptyList()
            }
            val audiusResults = audiusRepo.search(
                query = keywords,
                minDuration = 1,
                maxDuration = 60,
                limit = 8,
            ).items.filter { it.stableKey() != sound.stableKey() }
            val ccMixterResults = ccMixterRepo.search(
                query = keywords,
                minDuration = 1.0,
                maxDuration = 60.0,
                limit = 8,
            ).items.filter { it.stableKey() != sound.stableKey() }
            rankSounds(
                sounds = richerResults + fallbackResults + audiusResults + ccMixterResults,
                tab = SoundTab.SEARCH,
                filter = SoundQualityFilter.BEST,
            ).take(10)
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
            if (loadTab == SoundTab.SEARCH && s.query.isBlank()) {
                _state.update {
                    it.copy(
                        sounds = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = null,
                    )
                }
                return@launch
            }
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            val allResults = mutableListOf<Sound>()
            val resultLock = Any()
            val seenKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val seenFingerprints = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val pagedSourcesHaveMore = AtomicBoolean(false)
            val firstFailure = AtomicReference<Exception?>(null)
            val firstPage = s.currentPage <= 1

            if (loadMore) {
                s.sounds.forEach { sound ->
                    seenKeys.add(sound.stableKey())
                    seenFingerprints.add(soundFingerprint(sound))
                }
            }

            // Show bundled content immediately while APIs load, and seed allResults so they survive flushToUi()
            if (!loadMore && !isRefresh) {
                val bundled = when (s.selectedTab) {
                    SoundTab.RINGTONES -> bundledContent.getRingtones()
                    SoundTab.NOTIFICATIONS -> bundledContent.getNotifications()
                    SoundTab.ALARMS -> bundledContent.getAlarms()
                    else -> emptyList()
                }
                if (bundled.isNotEmpty()) {
                    bundled.forEach {
                        seenKeys.add(it.stableKey())
                        seenFingerprints.add(soundFingerprint(it))
                    }
                    synchronized(resultLock) { allResults.addAll(bundled) }
                    _state.update {
                        it.copy(
                            sounds = rankSounds(bundled, loadTab, it.qualityFilter),
                            isLoading = true,
                        )
                    }
                }
            }

            fun addUnique(sound: Sound): Boolean {
                if (titleBlocklist.containsMatchIn(sound.name)) return false
                val fingerprint = soundFingerprint(sound)
                return if (seenKeys.add(sound.stableKey()) && seenFingerprints.add(fingerprint)) {
                    synchronized(resultLock) { allResults.add(sound) }
                    true
                } else {
                    false
                }
            }

            fun flushToUi() {
                _state.update { st ->
                    val snapshot = rankSounds(
                        sounds = synchronized(resultLock) { allResults.toList() },
                        tab = loadTab,
                        filter = st.qualityFilter,
                    )
                    val existingKeys = st.sounds.mapTo(mutableSetOf()) { it.stableKey() }
                    st.copy(
                        sounds = if (loadMore) {
                            st.sounds + snapshot.filter { snd -> existingKeys.add(snd.stableKey()) }
                        } else {
                            snapshot
                        },
                    )
                }
            }

            fun noteHasMore(hasMore: Boolean) {
                if (hasMore) pagedSourcesHaveMore.set(true)
            }

            fun noteFailure(error: Exception) {
                firstFailure.compareAndSet(null, error)
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
                                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))?.let { url ->
                                                    cacheResolvedPreview(yt, url)
                                                    _cachedYtIds.update { it + yt.id }
                                                }
                                            } catch (_: Exception) {} finally { ytResolveSemaphore.release() }
                                        }
                                    }
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }

                    // FreesoundV2
                    if (queries.catalogQueries.isNotEmpty()) {
                        queries.catalogQueries.forEach { q ->
                            launch {
                                try {
                                    val result = freesoundV2Repo.search(
                                        query = q, minDuration = cappedMin.toDouble(),
                                        maxDuration = cappedMax.toDouble(), page = s.currentPage,
                                        sort = "downloads_desc",
                                    )
                                    noteHasMore(result.hasMore)
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }

                    // Openverse (zero auth fallback)
                    if (queries.catalogQueries.isNotEmpty()) {
                        queries.catalogQueries.forEach { q ->
                            launch {
                                try {
                                    val result = freesoundRepo.search(
                                        query = q, minDuration = cappedMin.toDouble(),
                                        maxDuration = cappedMax.toDouble(), page = s.currentPage,
                                    )
                                    noteHasMore(result.hasMore)
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }

                    // SoundCloud (CC-licensed)
                    if (queries.catalogQueries.isNotEmpty()) {
                        queries.catalogQueries.take(1).forEach { q ->
                            launch {
                                try {
                                    val result = soundCloudRepo.search(
                                        query = q,
                                        minDurationMs = cappedMin * 1000,
                                        maxDurationMs = cappedMax * 1000,
                                        offset = (s.currentPage - 1) * 20,
                                    )
                                    noteHasMore(result.hasMore)
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }

                    // Audius is a one-shot catalog here, so avoid re-querying it on later pages.
                    if (firstPage && queries.audiusQueries.isNotEmpty()) {
                        queries.audiusQueries.take(2).forEach { q ->
                            launch {
                                try {
                                    val audiusMax = if (loadTab == SoundTab.RINGTONES) cappedMax.coerceAtLeast(60) else cappedMax
                                    val result = audiusRepo.search(
                                        query = q,
                                        minDuration = cappedMin,
                                        maxDuration = audiusMax,
                                        limit = 20,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }

                    // ccMixter is also a one-shot catalog in the current API integration.
                    if (firstPage && queries.ccMixterQueries.isNotEmpty()) {
                        queries.ccMixterQueries.take(1).forEach { q ->
                            launch {
                                try {
                                    val result = ccMixterRepo.search(
                                        query = q,
                                        minDuration = cappedMin.toDouble(),
                                        maxDuration = cappedMax.toDouble(),
                                        limit = 15,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    noteFailure(e)
                                }
                            }
                        }
                    }
                }

                val combined = rankSounds(
                    sounds = synchronized(resultLock) { allResults.toList() },
                    tab = loadTab,
                    filter = _state.value.qualityFilter,
                )
                val preserveCurrentFeed = !loadMore && combined.isEmpty() && s.sounds.isNotEmpty()
                val surfacedError = firstFailure.get()
                    ?.takeIf { combined.isEmpty() }
                    ?.let(::categorizeError)
                _state.update {
                    it.copy(
                        sounds = when {
                            loadMore -> {
                                val existingKeys = it.sounds.mapTo(mutableSetOf()) { sound -> sound.stableKey() }
                                it.sounds + combined.filter { snd -> existingKeys.add(snd.stableKey()) }
                            }
                            preserveCurrentFeed -> it.sounds
                            else -> combined
                        },
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = pagedSourcesHaveMore.get(),
                        error = when {
                            preserveCurrentFeed && surfacedError != null -> "$surfacedError. Showing your last good results."
                            else -> surfacedError
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = it.hasMore,
                        error = if (it.sounds.isNotEmpty()) {
                            "${categorizeError(e)}. Showing your last good results."
                        } else {
                            categorizeError(e)
                        },
                    )
                }
            }
        }
    }

    // -- Query Building --

    private data class QuerySet(
        val catalogQueries: List<String>,
        val ytQueries: List<String>,
        val audiusQueries: List<String>,
        val ccMixterQueries: List<String>,
    )

    private suspend fun buildQueries(s: SoundsUiState): QuerySet {
        val ytRingQ = prefs.ytSoundQueryRingtones.first()
        val ytNotifQ = prefs.ytSoundQueryNotifications.first()
        val ytAlarmQ = prefs.ytSoundQueryAlarms.first()
        val currentYear = Year.now().value

        return when (s.selectedTab) {
            SoundTab.RINGTONES -> QuerySet(
                catalogQueries = listOf("ringtone melody phone ring", "ringtone tone music tune"),
                ytQueries = listOf(ytRingQ, "clean ringtone $currentYear phone"),
                audiusQueries = listOf("ringtone", "phone ringtone"),
                ccMixterQueries = listOf("ringtone"),
            )
            SoundTab.NOTIFICATIONS -> QuerySet(
                catalogQueries = listOf("notification chime ding alert", "notification beep ping pop"),
                ytQueries = listOf(ytNotifQ, "clean notification sound $currentYear short"),
                audiusQueries = listOf("notification sound", "chime alert"),
                ccMixterQueries = listOf("notification"),
            )
            SoundTab.ALARMS -> QuerySet(
                catalogQueries = listOf("alarm clock morning wake", "alarm buzzer bell siren"),
                ytQueries = listOf(ytAlarmQ, "alarm clock tone morning $currentYear"),
                audiusQueries = listOf("alarm", "wake up tone"),
                ccMixterQueries = listOf("alarm"),
            )
            SoundTab.YOUTUBE -> QuerySet(emptyList(), emptyList(), emptyList(), emptyList())
            SoundTab.COMMUNITY -> QuerySet(emptyList(), emptyList(), emptyList(), emptyList())
            SoundTab.SEARCH -> QuerySet(
                catalogQueries = listOf(s.query, "${s.query} sound effect"),
                ytQueries = listOf("${s.query} sound", "${s.query} ringtone"),
                audiusQueries = listOf(s.query, "${s.query} audio"),
                ccMixterQueries = listOf(s.query),
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

    // -- Community Uploads --

    private fun loadCommunityTab(isRefresh: Boolean = false) {
        communityJob?.cancel()
        _state.update {
            if (isRefresh) it.copy(isRefreshing = true, error = null)
            else it.copy(isLoading = true, error = null)
        }
        communityJob = viewModelScope.launch {
            val timeoutJob = launch {
                kotlinx.coroutines.delay(10_000L)
                val state = _state.value
                if (state.isLoading || state.isRefreshing) {
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = "Community uploads timed out") }
                }
            }
            try {
                uploadRepo.getCommunityUploads(limit = 50).collect { sounds ->
                    timeoutJob.cancel()
                    _state.update {
                        it.copy(
                            sounds = rankSounds(sounds, SoundTab.COMMUNITY, it.qualityFilter),
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = false,
                        )
                    }
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    private fun executeYouTubeSearch(query: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val blocked = try {
                    prefs.ytSoundBlockedWords.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                } catch (_: Exception) { emptyList() }

                val result = youtubeRepo.searchSounds(
                    query = query,
                    maxDuration = 600,
                    minDuration = 0,
                    blockedWords = blocked,
                )
                _state.update {
                    it.copy(
                        sounds = rankSounds(result.items, SoundTab.YOUTUBE, it.qualityFilter),
                        isLoading = false,
                        isRefreshing = false,
                        // We do not support paginating the YouTube tab yet, so avoid advertising
                        // "more" when the generic loadMore() path cannot service it.
                        hasMore = false,
                    )
                }

                supervisorScope {
                    result.items.forEach { yt ->
                        launch {
                            ytResolveSemaphore.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))?.let { url ->
                                    cacheResolvedPreview(yt, url)
                                    _cachedYtIds.update { it + yt.id }
                                }
                            } catch (_: Exception) {
                            } finally {
                                ytResolveSemaphore.release()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = categorizeError(e),
                    )
                }
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

private fun Sound.youtubeVideoId(): String? =
    takeIf { source == ContentSource.YOUTUBE }
        ?.id
        ?.removePrefix("yt_")
        ?.takeIf { it.isNotBlank() && it != id }

internal fun matchesSoundIdentity(
    sound: Sound,
    id: String,
    source: ContentSource? = null,
    previewUrl: String? = null,
    downloadUrl: String? = null,
): Boolean {
    if (sound.id != id) return false
    if (source != null && sound.source != source) return false
    if (!previewUrl.isNullOrBlank() && sound.previewUrl != previewUrl) return false
    if (!downloadUrl.isNullOrBlank() && sound.downloadUrl != downloadUrl) return false
    return true
}

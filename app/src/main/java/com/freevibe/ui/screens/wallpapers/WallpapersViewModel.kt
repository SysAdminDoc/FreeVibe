package com.freevibe.ui.screens.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.ColorExtractor
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.DownloadManager
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class WallpapersUiState(
    val wallpapers: List<Wallpaper> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,       // #4: Pull-to-refresh
    val error: String? = null,
    val errorSource: String? = null,         // #5: Which source failed
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: WallpaperTab = WallpaperTab.DISCOVER,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val selectedColor: String? = null,       // #9: Color filter
    val topRange: String = "1M",             // Wallhaven toplist time range
)

enum class WallpaperTab { DISCOVER, PEXELS, PIXABAY, REDDIT, WALLHAVEN, UNSPLASH, COLOR, SEARCH }

@HiltViewModel
class WallpapersViewModel @Inject constructor(
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
    private val pexelsApi: PexelsApi,
    private val favoritesRepo: FavoritesRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val downloadManager: DownloadManager,
    private val dualWallpaperService: DualWallpaperService,
    private val collectionRepo: CollectionRepository,
    private val selectedContent: SelectedContentHolder,
    private val historyManager: WallpaperHistoryManager,
    private val offlineFavorites: OfflineFavoritesManager,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val prefs: PreferencesManager,
    private val colorExtractor: ColorExtractor,
    private val cacheManager: com.freevibe.data.local.WallpaperCacheManager,
    val voteRepo: VoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WallpapersUiState())
    val state = _state.asStateFlow()

    val selectedWallpaper = selectedContent.selectedWallpaper
    val sharedWallpaperList = selectedContent.wallpaperList

    val activeDownloads = downloadManager.activeDownloads

    // #9: Grid columns preference
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val recentSearches = searchHistoryRepo.getRecentWallpaperSearches(8)
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds = favoritesRepo.allIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Reddit's most upvoted wallpaper today — crowd-sourced quality metric */
    private val _dailyPick = MutableStateFlow<Wallpaper?>(null)
    val dailyPick = _dailyPick.asStateFlow()

    /** Top community-upvoted wallpapers (resolved from cache) */
    private val _topVoted = MutableStateFlow<List<Pair<Wallpaper, Int>>>(emptyList())
    val topVoted = _topVoted.asStateFlow()

    init {
        consumePendingQueries()
        fetchDailyPick()
        fetchTopVoted()
    }

    private fun fetchTopVoted() {
        viewModelScope.launch {
            try {
                val topIds = withTimeoutOrNull(5000L) { voteRepo.getTopVotedIds(50) } ?: return@launch
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Top voted IDs from Firebase: ${topIds.size} entries, first=${topIds.firstOrNull()}")
                if (topIds.isEmpty()) return@launch

                // Firebase stores sanitized keys — try both original and sanitized IDs
                val allIds = topIds.flatMap { (id, _) -> listOf(id, id.replace("_", "."), id.replace("_", "/")) }.distinct()
                val wallpapers = cacheManager.getByIds(allIds)
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Resolved ${wallpapers.size} wallpapers from cache for ${allIds.size} ID variants")

                val voteMap = topIds.toMap()
                val sorted = wallpapers
                    .mapNotNull { wp ->
                        val sanitized = voteRepo.sanitizeKey(wp.id)
                        voteMap[wp.id]?.let { wp to it }
                            ?: voteMap[sanitized]?.let { wp to it }
                    }
                    .distinctBy { it.first.id }
                    .sortedByDescending { it.second }
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Final top voted: ${sorted.size} wallpapers, top=${sorted.firstOrNull()?.let { "${it.first.id}=${it.second}" }}")
                _topVoted.value = sorted
            } catch (e: Exception) {
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.e("WallpapersVM", "fetchTopVoted failed: ${e.message}", e)
            }
        }
    }

    private fun fetchDailyPick() {
        viewModelScope.launch {
            try {
                _dailyPick.value = withTimeoutOrNull(5000L) { redditRepo.getDailyTopWallpaper() }
            } catch (_: Exception) {}
        }
    }

    /** Check for pending queries from detail/category screens. Called on init and on resume. */
    fun consumePendingQueries() {
        val categoryQuery = selectedContent.pendingCategoryQuery
        val colorQuery = selectedContent.pendingColorQuery
        if (categoryQuery != null) {
            selectedContent.pendingCategoryQuery = null
            search(categoryQuery)
        } else if (colorQuery != null) {
            selectedContent.pendingColorQuery = null
            searchByColor(colorQuery)
        } else if (_state.value.wallpapers.isEmpty() && !_state.value.isLoading) {
            loadWallpapers()
        }
    }

    fun selectTab(tab: WallpaperTab) {
        redditRepo.resetPagination() // Always reset to avoid stale afterToken
        _state.update {
            it.copy(
                selectedTab = tab,
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                errorSource = null,
                selectedColor = null,
            )
        }
        loadWallpapers()
    }

    fun setTopRange(range: String) {
        _state.update { it.copy(topRange = range, wallpapers = emptyList(), currentPage = 1, hasMore = true) }
        loadWallpapers()
    }

    fun search(query: String) {
        _state.update {
            it.copy(
                query = query,
                selectedTab = WallpaperTab.SEARCH,
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addWallpaperSearch(query) }
        loadWallpapers()
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "WALLPAPER") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearWallpaperHistory() }
    }

    // #9: Color-based search
    fun searchByColor(color: String) {
        _state.update {
            it.copy(
                selectedTab = WallpaperTab.COLOR,
                selectedColor = color,
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
            )
        }
        loadWallpapers()
    }

    // #4: Pull-to-refresh
    fun refresh() {
        _state.update { it.copy(isRefreshing = true, currentPage = 1, wallpapers = emptyList(), error = null, errorSource = null) }
        if (_state.value.selectedTab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        loadWallpapers(isRefresh = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadWallpapers(loadMore = true)
    }

    fun selectWallpaper(wallpaper: Wallpaper) {
        selectedContent.selectWallpaper(wallpaper, _state.value.wallpapers)
    }

    /** Set pending color search for the wallpapers list screen to pick up on return */
    fun setPendingColorSearch(hex: String) {
        selectedContent.pendingColorQuery = hex
    }

    /** Update selected wallpaper without overwriting the shared list (used by detail pager) */
    fun selectWallpaperOnly(wallpaper: Wallpaper) {
        selectedContent.selectWallpaper(wallpaper)
    }

    fun applyWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            wallpaperApplier.applyFromUrl(wallpaper.fullUrl, target)
                .onSuccess {
                    // #11: Record in history
                    historyManager.record(wallpaper, target)
                    val label = when (target) {
                        WallpaperTarget.HOME -> "home screen"
                        WallpaperTarget.LOCK -> "lock screen"
                        WallpaperTarget.BOTH -> "home & lock screen"
                    }
                    _state.update { it.copy(isApplying = false, applySuccess = "Set as $label wallpaper") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun applySplitCrop(wallpaper: Wallpaper) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            dualWallpaperService.applySplitCrop(wallpaper)
                .onSuccess {
                    historyManager.record(wallpaper, WallpaperTarget.BOTH)
                    _state.update { it.copy(isApplying = false, applySuccess = "Split crop applied to home & lock") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun downloadWallpaper(wallpaper: Wallpaper) {
        viewModelScope.launch {
            val ext = guessImageExtension(wallpaper.fileType, wallpaper.fullUrl)
            downloadManager.downloadWallpaper(
                id = wallpaper.id,
                url = wallpaper.fullUrl,
                fileName = "Aura_${wallpaper.id}.$ext",
            )
        }
    }

    private fun guessImageExtension(fileType: String, url: String): String {
        // Check MIME type first
        if (fileType.isNotBlank()) {
            return when {
                fileType.contains("png", true) -> "png"
                fileType.contains("webp", true) -> "webp"
                fileType.contains("gif", true) -> "gif"
                else -> "jpg"
            }
        }
        // Fallback to URL extension
        val path = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            else -> "jpg"
        }
    }

    fun dismissDownload(id: String) {
        downloadManager.clearCompleted(id)
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch {
            val entity = wallpaper.toFavoriteEntity()
            val isFav = favoritesRepo.isFavorite(wallpaper.id).first()
            favoritesRepo.toggle(entity, isFav)
            // #3: Cache offline when favoriting
            if (!isFav) {
                offlineFavorites.cacheOffline(wallpaper.id, wallpaper.fullUrl, "WALLPAPER")
            } else {
                offlineFavorites.removeOffline(wallpaper.id)
            }
            _state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
        }
    }

    fun isFavorite(id: String): Flow<Boolean> = favoritesRepo.isFavorite(id)

    fun clearError() = _state.update { it.copy(error = null, errorSource = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    // -- Voting --

    val hiddenIds = voteRepo.hiddenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun getVoteCount(contentId: String) = voteRepo.getVoteCount(contentId)

    fun upvote(contentId: String) {
        viewModelScope.launch {
            val success = voteRepo.upvote(contentId)
            if (!success) _state.update { it.copy(applySuccess = "Already voted") }
        }
    }

    fun downvote(contentId: String) {
        viewModelScope.launch {
            voteRepo.downvote(contentId)
            _state.update { it.copy(applySuccess = if (voteRepo.isAdmin) "Moderated (hidden for all)" else "Hidden") }
        }
    }

    // -- Collections --

    val collections = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCollection(name: String, wallpaper: Wallpaper? = null) {
        viewModelScope.launch {
            val id = collectionRepo.create(name)
            wallpaper?.let { collectionRepo.addWallpaper(id, it) }
            _state.update { it.copy(applySuccess = "Created \"$name\"") }
        }
    }

    // -- Color extraction (Material You preview) --

    private val _colorPalette = MutableStateFlow<ColorExtractor.WallpaperPalette?>(null)
    val colorPalette = _colorPalette.asStateFlow()

    fun extractColors(wallpaperUrl: String) {
        viewModelScope.launch {
            _colorPalette.value = null
            _colorPalette.value = colorExtractor.extractFromUrl(wallpaperUrl)
        }
    }

    fun applyRandom() {
        val wallpapers = _state.value.wallpapers
        if (wallpapers.isEmpty()) return
        val wp = wallpapers.random()
        applyWallpaper(wp, WallpaperTarget.BOTH)
    }

    fun addToCollection(collectionId: Long, wallpaper: Wallpaper) {
        viewModelScope.launch {
            collectionRepo.addWallpaper(collectionId, wallpaper)
            _state.update { it.copy(applySuccess = "Added to collection") }
        }
    }

    private fun loadWallpapers(loadMore: Boolean = false, isRefresh: Boolean = false) {
        viewModelScope.launch {
            val s = _state.value
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null, errorSource = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            // Instant cache hit for Discover — show cached results immediately while refreshing
            if (s.selectedTab == WallpaperTab.DISCOVER && !loadMore && !isRefresh) {
                val cached = wallpaperRepo.getCachedDiscover(s.currentPage)
                if (!cached.isNullOrEmpty()) {
                    _state.update { it.copy(wallpapers = cached, isLoading = false, hasMore = true) }
                    // Continue loading fresh results in background (isRefresh-like)
                }
            }

            try {
                val result = when (s.selectedTab) {
                    WallpaperTab.DISCOVER -> wallpaperRepo.getDiscover(
                        page = s.currentPage,
                        redditRepo = redditRepo,
                        pexelsApi = pexelsApi,
                        pexelsKey = prefs.pexelsApiKey.first(),
                    )
                    WallpaperTab.PIXABAY -> wallpaperRepo.getPixabay(s.currentPage)
                    WallpaperTab.PEXELS -> {
                        val key = prefs.pexelsApiKey.first()
                        if (key.isNotBlank()) {
                            val response = pexelsApi.curatedPhotos(apiKey = key, page = s.currentPage)
                            SearchResult(
                                items = response.photos.map { photo ->
                                    Wallpaper(
                                        id = "px_${photo.id}",
                                        source = ContentSource.PEXELS,
                                        thumbnailUrl = photo.src.medium,
                                        fullUrl = photo.src.original,
                                        width = photo.width,
                                        height = photo.height,
                                        sourcePageUrl = photo.url,
                                        uploaderName = photo.photographer,
                                    )
                                },
                                totalCount = response.totalResults,
                                currentPage = response.page,
                                hasMore = response.nextPage != null,
                            )
                        } else {
                            SearchResult(items = emptyList(), totalCount = 0, currentPage = 1, hasMore = false)
                        }
                    }
                    WallpaperTab.REDDIT -> redditRepo.getMultiSubreddit()
                    WallpaperTab.WALLHAVEN -> wallpaperRepo.getWallhaven(page = s.currentPage, topRange = s.topRange)
                    WallpaperTab.UNSPLASH -> wallpaperRepo.getPicsum(s.currentPage)
                    WallpaperTab.SEARCH -> wallpaperRepo.searchAll(s.query, page = s.currentPage)
                    WallpaperTab.COLOR -> wallpaperRepo.searchByColor(s.selectedColor ?: "", s.currentPage)
                }
                _state.update {
                    it.copy(
                        wallpapers = if (loadMore) it.wallpapers + result.items else result.items,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = result.hasMore,
                        error = null,
                        errorSource = null,
                    )
                }
            } catch (e: Exception) {
                // #5: Source-specific error handling
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load wallpapers",
                        errorSource = s.selectedTab.name,
                    )
                }
            }
        }
    }

    /** Find similar wallpapers via Wallhaven like: query + color search */
    fun findSimilar(wallpaper: Wallpaper) {
        val whId = wallpaper.id.removePrefix("wh_")
        viewModelScope.launch {
            _state.update { it.copy(selectedTab = WallpaperTab.SEARCH, query = "Similar", wallpapers = emptyList(), isLoading = true, currentPage = 1) }
            try {
                val results = mutableListOf<Wallpaper>()
                // Tag-similar via Wallhaven like: syntax (only for Wallhaven wallpapers)
                if (wallpaper.source == com.freevibe.data.model.ContentSource.WALLHAVEN) {
                    val similar = wallpaperRepo.findSimilar(whId)
                    results.addAll(similar.items)
                }
                // Color-similar via dominant color
                if (wallpaper.colors.isNotEmpty()) {
                    val colorResult = wallpaperRepo.searchByColor(wallpaper.colors.first().removePrefix("#"))
                    results.addAll(colorResult.items.filter { it.id !in results.map { r -> r.id }.toSet() })
                }
                _state.update { it.copy(wallpapers = results, isLoading = false, hasMore = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Load random wallpapers from Wallhaven */
    fun loadRandom() {
        viewModelScope.launch {
            _state.update { it.copy(selectedTab = WallpaperTab.SEARCH, query = "Random", wallpapers = emptyList(), isLoading = true, currentPage = 1) }
            try {
                val result = wallpaperRepo.getRandomWallhaven()
                _state.update { it.copy(wallpapers = result.items, isLoading = false, hasMore = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Search by Wallhaven tag */
    fun searchByTag(tagName: String) {
        search(tagName)
    }

    /** Match wallpapers to system Material You colors */
    fun matchMyTheme(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val color = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                val hex = if (color) {
                    val accent = context.getColor(android.R.color.system_accent1_500)
                    String.format("%06x", accent and 0xFFFFFF)
                } else {
                    "424153" // Fallback: Catppuccin lavender-ish
                }
                searchByColor(hex)
            } catch (_: Exception) {
                searchByColor("424153")
            }
        }
    }
}

package com.freevibe.ui.screens.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.model.ContentSource
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    val pendingLiveWallpaperLaunch: Boolean = false,  // true when parallax file is ready to launch picker
    val selectedColor: String? = null,       // #9: Color filter
    val topRange: String = "1M",             // Wallhaven toplist time range
)

enum class WallpaperTab { DISCOVER, PEXELS, PIXABAY, REDDIT, WALLHAVEN, UNSPLASH, COLOR, SEARCH }

@HiltViewModel
class WallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
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

    private var loadJob: Job? = null
    private var lastRouteQuery: String? = null
    private var lastRouteColor: String? = null
    private var hasInitiallyLoaded = false

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
        fetchDailyPick()
        fetchTopVoted()
    }

    private fun fetchTopVoted(seedWallpapers: List<Wallpaper> = emptyList()) {
        viewModelScope.launch {
            try {
                val topIds = withTimeoutOrNull(5000L) { voteRepo.getTopVotedIds(50) } ?: return@launch
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Top voted IDs from Firebase: ${topIds.size} entries, first=${topIds.firstOrNull()}")
                if (topIds.isEmpty()) return@launch

                // Firebase stores sanitized keys — try both original and sanitized IDs
                val allIds = topIds.flatMap { (id, _) -> listOf(id, id.replace("_", "."), id.replace("_", "/")) }.distinct()
                val cachedWallpapers = cacheManager.getByIds(allIds)
                val wallpapers = (seedWallpapers + cachedWallpapers).distinctBy { it.id }
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Resolved ${wallpapers.size} wallpapers from seed/cache for ${allIds.size} ID variants")

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

    fun handleRouteFilters(query: String?, color: String?) {
        val normalizedQuery = query?.ifBlank { null }
        val normalizedColor = color?.ifBlank { null }

        // Skip dedup only for non-initial calls with identical filters
        if (hasInitiallyLoaded && normalizedQuery == lastRouteQuery && normalizedColor == lastRouteColor) return

        lastRouteQuery = normalizedQuery
        lastRouteColor = normalizedColor
        hasInitiallyLoaded = true

        when {
            normalizedQuery != null -> {
                if (_state.value.selectedTab != WallpaperTab.SEARCH || _state.value.query != normalizedQuery) {
                    search(normalizedQuery)
                }
            }
            normalizedColor != null -> {
                if (_state.value.selectedTab != WallpaperTab.COLOR || _state.value.selectedColor != normalizedColor) {
                    searchByColor(normalizedColor)
                }
            }
            _state.value.wallpapers.isEmpty() && !_state.value.isLoading -> loadWallpapers()
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
        val tab = _state.value.selectedTab
        _state.update { it.copy(isRefreshing = true, currentPage = 1, wallpapers = emptyList(), error = null, errorSource = null) }
        if (tab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        loadWallpapers(isRefresh = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadWallpapers(loadMore = true)
    }

    fun selectWallpaper(wallpaper: Wallpaper) {
        selectedContent.selectWallpaper(wallpaper, _state.value.wallpapers)
    }

    suspend fun resolveWallpaper(id: String): Wallpaper? =
        resolveWallpaperSelection(id)?.first

    suspend fun ensureSelectedWallpaper(id: String): Boolean {
        val resolved = resolveWallpaperSelection(id) ?: return false
        selectedContent.selectWallpaper(resolved.first, resolved.second.ifEmpty { listOf(resolved.first) })
        return true
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

    fun applyParallax(wallpaper: Wallpaper) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            val ext = guessImageExtension(wallpaper.fileType, wallpaper.fullUrl)
            wallpaperApplier.prepareParallaxWallpaper(wallpaper.fullUrl, "parallax_wp.$ext")
                .onSuccess {
                    _state.update { it.copy(isApplying = false, pendingLiveWallpaperLaunch = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun clearPendingLaunch() = _state.update { it.copy(pendingLiveWallpaperLaunch = false) }

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
        val wp = wallpapers.randomOrNull() ?: return
        applyWallpaper(wp, WallpaperTarget.BOTH)
    }

    fun addToCollection(collectionId: Long, wallpaper: Wallpaper) {
        viewModelScope.launch {
            collectionRepo.addWallpaper(collectionId, wallpaper)
            _state.update { it.copy(applySuccess = "Added to collection") }
        }
    }

    private fun loadWallpapers(loadMore: Boolean = false, isRefresh: Boolean = false) {
        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
                    _state.update { it.copy(wallpapers = cached, hasMore = true) }
                    // Keep isLoading = true — network request still in progress
                }
            }

            val currentTab = _state.value.selectedTab
            val currentPage = _state.value.currentPage
            try {
                val result = when (currentTab) {
                    WallpaperTab.DISCOVER -> wallpaperRepo.getDiscover(
                        page = currentPage,
                        redditRepo = redditRepo,
                    )
                    WallpaperTab.PIXABAY -> wallpaperRepo.getPixabay(currentPage)
                    WallpaperTab.PEXELS -> wallpaperRepo.getPexelsCurated(currentPage)
                    WallpaperTab.REDDIT -> redditRepo.getMultiSubreddit()
                    WallpaperTab.WALLHAVEN -> wallpaperRepo.getWallhaven(page = currentPage, topRange = _state.value.topRange)
                    WallpaperTab.UNSPLASH -> wallpaperRepo.getPicsum(currentPage)
                    WallpaperTab.SEARCH -> wallpaperRepo.searchAll(_state.value.query, page = currentPage)
                    WallpaperTab.COLOR -> wallpaperRepo.searchByColor(_state.value.selectedColor ?: "", currentPage)
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
                if (currentTab == WallpaperTab.DISCOVER && (!loadMore || _topVoted.value.isEmpty())) {
                    fetchTopVoted(result.items)
                }
            } catch (e: Exception) {
                // #5: Source-specific error handling
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        error = categorizeError(e),
                        errorSource = currentTab.name,
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
                    val existingIds = results.map { it.id }.toSet()
                    val colorResult = wallpaperRepo.searchByColor(wallpaper.colors.first().removePrefix("#"))
                    results.addAll(colorResult.items.filter { it.id !in existingIds })
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
                _state.update { it.copy(wallpapers = result.items, isLoading = false, hasMore = false) }
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
    fun matchMyTheme() {
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

    private suspend fun resolveWallpaperSelection(id: String): Pair<Wallpaper, List<Wallpaper>>? {
        selectedContent.selectedWallpaper.value?.takeIf { it.id == id }?.let {
            val shared = selectedContent.wallpaperList.value
            return it to shared.ifEmpty { listOf(it) }
        }

        val shared = selectedContent.wallpaperList.value
        shared.firstOrNull { it.id == id }?.let {
            return it to shared
        }

        val current = _state.value.wallpapers
        current.firstOrNull { it.id == id }?.let {
            return it to current
        }

        val topVotedWallpapers = _topVoted.value.map { pair -> pair.first }
        topVotedWallpapers.firstOrNull { it.id == id }?.let {
            return it to topVotedWallpapers
        }

        _dailyPick.value?.takeIf { it.id == id }?.let {
            return it to listOf(it)
        }

        favoritesRepo.getById(id)
            ?.takeIf { it.type == "WALLPAPER" }
            ?.toWallpaper()
            ?.let {
                return it to listOf(it)
            }

        cacheManager.getByIds(listOf(id)).firstOrNull()?.let {
            return it to listOf(it)
        }

        return null
    }

    private fun categorizeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Connection timed out — try again"
        is java.net.ConnectException -> "Could not connect to server"
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> "API key invalid or expired"
            404 -> "Content not found"
            429 -> "Rate limited — wait a moment and retry"
            in 500..599 -> "Server error — try again later"
            else -> "Service temporarily unavailable"
        }
        else -> e.message ?: "Failed to load wallpapers"
    }
}

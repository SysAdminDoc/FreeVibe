package com.freevibe.ui.screens.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.DownloadManager
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
)

enum class WallpaperTab { DISCOVER, REDDIT, WALLHAVEN, BING, UNSPLASH, COLOR, SEARCH }

@HiltViewModel
class WallpapersViewModel @Inject constructor(
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
    prefs: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(WallpapersUiState())
    val state = _state.asStateFlow()

    val selectedWallpaper = selectedContent.selectedWallpaper

    val activeDownloads = downloadManager.activeDownloads

    // #9: Grid columns preference
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val recentSearches = searchHistoryRepo.getRecentWallpaperSearches(8)
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds = favoritesRepo.allIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        // Check for pending category query from CategoriesScreen
        val categoryQuery = selectedContent.pendingCategoryQuery
        if (categoryQuery != null) {
            selectedContent.pendingCategoryQuery = null
            search(categoryQuery)
        } else {
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
            val ext = wallpaper.fileType.substringAfterLast("/", "jpg").substringAfterLast(".", "jpg")
            downloadManager.downloadWallpaper(
                id = wallpaper.id,
                url = wallpaper.fullUrl,
                fileName = "Aura_${wallpaper.id}.$ext",
            )
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
            try {
                val result = when (s.selectedTab) {
                    WallpaperTab.DISCOVER -> wallpaperRepo.getDiscover(s.currentPage)
                    WallpaperTab.REDDIT -> redditRepo.getMultiSubreddit()
                    WallpaperTab.WALLHAVEN -> wallpaperRepo.getWallhaven(page = s.currentPage)
                    WallpaperTab.BING -> wallpaperRepo.getBingDaily(s.currentPage)
                    WallpaperTab.UNSPLASH -> wallpaperRepo.getPicsum(s.currentPage)
                    WallpaperTab.SEARCH -> wallpaperRepo.searchWallhaven(s.query, page = s.currentPage)
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
}

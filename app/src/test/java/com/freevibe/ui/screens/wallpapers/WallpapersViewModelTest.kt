package com.freevibe.ui.screens.wallpapers

import android.content.Context
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `clearActiveFilter returns to previous wallpaper tab`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.getPexelsCurated(1) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.searchAll("mountain", 1) } returns SearchResult(
            items = listOf(wallpaper("wh_search", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        viewModel.selectTab(WallpaperTab.PEXELS)
        advanceUntilIdle()
        viewModel.search("mountain")
        advanceUntilIdle()

        assertEquals(WallpaperTab.SEARCH, viewModel.state.value.selectedTab)
        assertEquals(WallpaperTab.PEXELS, viewModel.state.value.browseTab)

        viewModel.clearActiveFilter()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WallpaperTab.PEXELS, state.selectedTab)
        assertEquals(WallpaperTab.PEXELS, state.browseTab)
        assertEquals("", state.query)
        assertNull(state.selectedColor)
    }

    @Test
    fun `findSimilarById resolves wallpaper from shared holder`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val selectedContent = SelectedContentHolder()
        val source = wallpaper(id = "wh_123", color = "#112233")
        selectedContent.selectWallpaper(source, listOf(source))

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.findSimilar("123", 1) } returns SearchResult(
            items = listOf(wallpaper(id = "wh_like", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )
        coEvery { wallpaperRepo.searchByColor("112233", 1) } returns SearchResult(
            items = listOf(wallpaper(id = "wh_color", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            selectedContent = selectedContent,
        )

        viewModel.findSimilarById("wh_123")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WallpaperTab.SEARCH, state.selectedTab)
        assertEquals("Similar", state.query)
        assertEquals(setOf("wh_like", "wh_color"), state.wallpapers.map { it.id }.toSet())
    }

    @Test
    fun `refresh preserves discover feed when refreshed results are empty`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val seededWallpaper = wallpaper(id = "wh_seed", color = "#101820")

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.getDiscover(any(), any()) } returnsMany listOf(
            SearchResult(
                items = listOf(seededWallpaper),
                totalCount = 1,
                currentPage = 1,
                hasMore = true,
            ),
            emptyWallpaperResult(),
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        viewModel.handleRouteFilters(query = null, color = null, similarId = null)
        advanceUntilIdle()
        assertEquals(listOf("wh_seed"), viewModel.state.value.wallpapers.map { it.id })

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("wh_seed"), state.wallpapers.map { it.id })
        assertTrue(state.hasMore)
        assertNull(state.error)
    }

    private fun createViewModel(
        wallpaperRepo: WallpaperRepository,
        redditRepo: RedditRepository,
        selectedContent: SelectedContentHolder = SelectedContentHolder(),
    ): WallpapersViewModel {
        val favoritesRepo = mockk<FavoritesRepository>()
        every { favoritesRepo.allIds() } returns flowOf(emptySet())
        every { favoritesRepo.isFavorite(any()) } returns flowOf(false)
        coEvery { favoritesRepo.getById(any()) } returns null

        val searchHistoryRepo = mockk<SearchHistoryRepository>()
        every { searchHistoryRepo.getRecentWallpaperSearches(any()) } returns flowOf(emptyList<SearchHistoryEntity>())
        coEvery { searchHistoryRepo.addWallpaperSearch(any()) } returns Unit
        coEvery { searchHistoryRepo.removeSearch(any(), any()) } returns Unit
        coEvery { searchHistoryRepo.clearWallpaperHistory() } returns Unit

        val prefs = mockk<PreferencesManager>()
        every { prefs.wallpaperGridColumns } returns flowOf(2)
        every { prefs.preferredResolution } returns flowOf("1080x1920")
        every { prefs.userStyles } returns flowOf("minimal,nature")

        val collectionRepo = mockk<CollectionRepository>()
        every { collectionRepo.getAll() } returns flowOf(emptyList<WallpaperCollectionEntity>())

        val downloadManager = mockk<DownloadManager>()
        every { downloadManager.activeDownloads } returns MutableStateFlow(emptyMap())

        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.hiddenIds } returns flowOf(emptySet())
        coEvery { voteRepo.getTopVotedIds(any()) } returns emptyList()

        val cacheManager = mockk<WallpaperCacheManager>()
        coEvery { cacheManager.getByIds(any()) } returns emptyList()

        return WallpapersViewModel(
            context = mockk<Context>(relaxed = true),
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            favoritesRepo = favoritesRepo,
            wallpaperApplier = mockk<WallpaperApplier>(relaxed = true),
            downloadManager = downloadManager,
            dualWallpaperService = mockk<DualWallpaperService>(relaxed = true),
            collectionRepo = collectionRepo,
            selectedContent = selectedContent,
            historyManager = mockk<WallpaperHistoryManager>(relaxed = true),
            offlineFavorites = mockk<OfflineFavoritesManager>(relaxed = true),
            searchHistoryRepo = searchHistoryRepo,
            prefs = prefs,
            colorExtractor = mockk(relaxed = true),
            cacheManager = cacheManager,
            voteRepo = voteRepo,
        )
    }

    private fun stubCommonDependencies(
        wallpaperRepo: WallpaperRepository,
        redditRepo: RedditRepository,
    ) {
        coEvery { wallpaperRepo.searchAll(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.searchByColor(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getPexelsCurated(any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getPixabay(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getWallhaven(any(), any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getDiscover(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getCachedDiscover(any()) } returns emptyList()
        coEvery { wallpaperRepo.findSimilar(any(), any()) } returns emptyWallpaperResult()
        coEvery { redditRepo.getDailyTopWallpaper() } returns null
        every { redditRepo.resetPagination() } just runs
        coEvery { redditRepo.getMultiSubreddit() } returns emptyWallpaperResult()
    }

    private fun emptyWallpaperResult() = SearchResult(
        items = emptyList<Wallpaper>(),
        totalCount = 0,
        currentPage = 1,
        hasMore = false,
    )

    private fun wallpaper(
        id: String,
        color: String,
    ) = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
        tags = listOf("minimal"),
        colors = listOf(color),
    )
}

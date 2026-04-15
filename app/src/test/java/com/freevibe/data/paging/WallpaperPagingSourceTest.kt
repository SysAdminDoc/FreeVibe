package com.freevibe.data.paging

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperPagingSourceTest {

    @Test
    fun `cached page refreshes from network to preserve pagination`() = runTest {
        val cacheManager = mockk<WallpaperCacheManager>()
        val cachedWallpaper = testWallpaper("cached_page_1")
        val freshWallpaper = testWallpaper("fresh_page_1")

        coEvery { cacheManager.getCached("featured_1", ContentSource.WALLHAVEN) } returns listOf(cachedWallpaper)
        coEvery { cacheManager.getCached("featured_2", ContentSource.WALLHAVEN) } returns null
        coEvery { cacheManager.cache("featured_1", listOf(freshWallpaper)) } returns Unit

        val pagingSource = WallpaperPagingSource(
            cacheManager = cacheManager,
            cacheKeyPrefix = "featured",
            source = ContentSource.WALLHAVEN,
            loader = {
                SearchResult(
                    items = listOf(freshWallpaper),
                    totalCount = 1,
                    currentPage = 1,
                    hasMore = true,
                )
            },
        )

        val result = pagingSource.load(LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))

        assertTrue(result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertEquals(listOf(freshWallpaper), page.data)
        assertEquals(2, page.nextKey)
        coVerify(exactly = 1) { cacheManager.cache("featured_1", listOf(freshWallpaper)) }
    }

    @Test
    fun `cached page falls back cleanly when refresh fails`() = runTest {
        val cacheManager = mockk<WallpaperCacheManager>()
        val cachedWallpaper = testWallpaper("cached_page_1")

        coEvery { cacheManager.getCached("featured_1", ContentSource.WALLHAVEN) } returns listOf(cachedWallpaper)
        coEvery { cacheManager.getCached("featured_2", ContentSource.WALLHAVEN) } returns null

        val pagingSource = WallpaperPagingSource(
            cacheManager = cacheManager,
            cacheKeyPrefix = "featured",
            source = ContentSource.WALLHAVEN,
            loader = { throw IOException("offline") },
        )

        val result = pagingSource.load(LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))

        assertTrue(result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertEquals(listOf(cachedWallpaper), page.data)
        assertEquals(null, page.nextKey)
        coVerify(exactly = 0) { cacheManager.cache(any(), any()) }
    }

    @Test
    fun `wallpaper paging source rethrows cancellation`() = runTest {
        val cacheManager = mockk<WallpaperCacheManager>()
        coEvery { cacheManager.getCached("featured_1", ContentSource.WALLHAVEN) } returns null

        val pagingSource = WallpaperPagingSource(
            cacheManager = cacheManager,
            cacheKeyPrefix = "featured",
            source = ContentSource.WALLHAVEN,
            loader = { throw CancellationException("cancelled") },
        )

        try {
            pagingSource.load(LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            throw AssertionError("Expected cancellation")
        } catch (actual: CancellationException) {
            assertEquals("cancelled", actual.message)
        }
    }

    @Test
    fun `sound paging source rethrows cancellation`() = runTest {
        val pagingSource = SoundPagingSource<Wallpaper> {
            throw CancellationException("cancelled")
        }

        try {
            pagingSource.load(LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false))
            throw AssertionError("Expected cancellation")
        } catch (actual: CancellationException) {
            assertEquals("cancelled", actual.message)
        }
    }

    private fun testWallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1080,
        height = 2400,
    )
}

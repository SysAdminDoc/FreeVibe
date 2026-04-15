package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.UnknownHostException

class WallpaperRepositoryTest {

    @Test
    fun `mergeDiscoverResults keeps provider pagination instead of inferring from visible items`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(wallpaper("wallhaven_1"), wallpaper("wallhaven_2")),
                    totalCount = 120,
                    currentPage = 2,
                    hasMore = false,
                ),
                SearchResult(
                    items = listOf(wallpaper("pexels_1", source = ContentSource.PEXELS)),
                    totalCount = 30,
                    currentPage = 2,
                    hasMore = true,
                ),
            ),
            page = 2,
        )

        assertEquals(listOf("wallhaven_1", "pexels_1", "wallhaven_2"), merged.items.map { it.id })
        assertEquals(150, merged.totalCount)
        assertTrue(merged.hasMore)
    }

    @Test
    fun `mergeDiscoverResults ignores unknown totals and falls back to visible item count`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(wallpaper("reddit_1", source = ContentSource.REDDIT)),
                    totalCount = -1,
                    currentPage = 1,
                    hasMore = false,
                ),
                SearchResult(
                    items = listOf(wallpaper("reddit_2", source = ContentSource.REDDIT)),
                    totalCount = -1,
                    currentPage = 1,
                    hasMore = false,
                ),
            ),
            page = 1,
        )

        assertEquals(2, merged.totalCount)
        assertFalse(merged.hasMore)
    }

    @Test
    fun `mergeDiscoverResults limits item count while preserving provider interleave`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(
                        wallpaper("wallhaven_1"),
                        wallpaper("wallhaven_2"),
                        wallpaper("wallhaven_3"),
                    ),
                    totalCount = 100,
                    currentPage = 1,
                    hasMore = true,
                ),
                SearchResult(
                    items = listOf(
                        wallpaper("pexels_1", source = ContentSource.PEXELS),
                        wallpaper("pexels_2", source = ContentSource.PEXELS),
                        wallpaper("pexels_3", source = ContentSource.PEXELS),
                    ),
                    totalCount = 100,
                    currentPage = 1,
                    hasMore = true,
                ),
            ),
            page = 1,
            maxItems = 4,
        )

        assertEquals(
            listOf("wallhaven_1", "pexels_1", "wallhaven_2", "pexels_2"),
            merged.items.map { it.id },
        )
        assertTrue(merged.hasMore)
    }

    @Test
    fun `shouldRetryBingHost only retries transient network failures`() {
        assertTrue(shouldRetryBingHost(UnknownHostException("dns")))
        assertTrue(shouldRetryBingHost(ConnectException("connect")))
        assertFalse(shouldRetryBingHost(IllegalArgumentException("bad request")))
    }

    private fun wallpaper(
        id: String,
        source: ContentSource = ContentSource.WALLHAVEN,
    ) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
    )
}

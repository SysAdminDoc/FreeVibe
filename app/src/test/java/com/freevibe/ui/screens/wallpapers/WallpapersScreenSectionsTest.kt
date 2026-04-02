package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpapersScreenSectionsTest {

    @Test
    fun `visible sections hide featured duplicates from feed`() {
        val dailyPick = wallpaper("hero")
        val topVoted = listOf(
            wallpaper("hero") to 10,
            wallpaper("top") to 9,
        )
        val feed = listOf(
            wallpaper("hero"),
            wallpaper("top"),
            wallpaper("feed"),
        )

        val sections = computeVisibleWallpaperSections(
            wallpapers = feed,
            hiddenIds = emptySet(),
            topVoted = topVoted,
            dailyPick = dailyPick,
            isDiscoverTab = true,
        )

        assertEquals("hero", sections.dailyPick?.id)
        assertEquals(listOf("top"), sections.topVoted.map { it.first.id })
        assertEquals(listOf("feed"), sections.feedWallpapers.map { it.id })
        assertEquals(listOf("hero", "top", "feed"), sections.pagerWallpapers.map { it.id })
        assertTrue(sections.hasRenderableContent)
    }

    @Test
    fun `visible sections respect hidden ids across hero top voted and feed`() {
        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(wallpaper("hero"), wallpaper("top"), wallpaper("feed")),
            hiddenIds = setOf("hero", "top"),
            topVoted = listOf(wallpaper("top") to 6),
            dailyPick = wallpaper("hero"),
            isDiscoverTab = true,
        )

        assertNull(sections.dailyPick)
        assertTrue(sections.topVoted.isEmpty())
        assertEquals(listOf("feed"), sections.feedWallpapers.map { it.id })
        assertTrue(sections.hasRenderableContent)
    }

    @Test
    fun `visible sections report no renderable content when everything is hidden`() {
        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(wallpaper("hidden_feed")),
            hiddenIds = setOf("hidden_feed", "hidden_top", "hidden_hero"),
            topVoted = listOf(wallpaper("hidden_top") to 4),
            dailyPick = wallpaper("hidden_hero"),
            isDiscoverTab = true,
        )

        assertNull(sections.dailyPick)
        assertTrue(sections.topVoted.isEmpty())
        assertTrue(sections.feedWallpapers.isEmpty())
        assertFalse(sections.hasRenderableContent)
    }

    @Test
    fun `pager items preserve feed order and open on tapped wallpaper`() {
        val sharedWallpapers = listOf(
            wallpaper("first"),
            wallpaper("second"),
            wallpaper("third"),
        )

        val pagerItems = computeWallpaperPagerItems(
            currentWallpaper = sharedWallpapers[1],
            sharedWallpapers = sharedWallpapers,
            hiddenIds = emptySet(),
        )

        assertEquals(listOf("first", "second", "third"), pagerItems.wallpapers.map { it.id })
        assertEquals(1, pagerItems.initialPage)
    }

    @Test
    fun `pager items ignore stale shared list when tapped wallpaper is not present`() {
        val pagerItems = computeWallpaperPagerItems(
            currentWallpaper = wallpaper("current"),
            sharedWallpapers = listOf(wallpaper("stale"), wallpaper("other")),
            hiddenIds = emptySet(),
        )

        assertEquals(listOf("current"), pagerItems.wallpapers.map { it.id })
        assertEquals(0, pagerItems.initialPage)
    }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.com/$id.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
    )
}

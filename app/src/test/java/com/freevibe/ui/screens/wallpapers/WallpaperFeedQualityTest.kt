package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperFeedQualityTest {

    @Test
    fun `rankWallpapers keeps stronger duplicate instead of first duplicate`() {
        val weaker = wallpaper(
            id = "px_1",
            source = ContentSource.PEXELS,
            url = "https://example.com/shared.jpg",
            width = 1440,
            height = 2560,
            tags = listOf("photo"),
        )
        val stronger = wallpaper(
            id = "wh_1",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/shared.jpg",
            width = 2160,
            height = 3840,
            tags = listOf("amoled", "minimal"),
            favorites = 900,
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(weaker, stronger),
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(1, ranked.size)
        assertEquals("wh_1", ranked.first().id)
    }

    @Test
    fun `amoled filter keeps dark candidates`() {
        val dark = wallpaper(
            id = "dark",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/dark.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("amoled", "black"),
            colors = listOf("#000000"),
        )
        val bright = wallpaper(
            id = "bright",
            source = ContentSource.PEXELS,
            url = "https://example.com/bright.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("nature"),
            colors = listOf("#f5d142"),
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(bright, dark),
            filter = WallpaperDiscoverFilter.AMOLED,
        )

        assertEquals(listOf("dark"), ranked.map { it.id })
    }

    @Test
    fun `quality hints expose resolution and icon safety`() {
        val wallpaper = wallpaper(
            id = "icon_safe",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/icon.jpg",
            width = 2160,
            height = 3840,
            tags = listOf("minimal", "gradient"),
            colors = listOf("#111111", "#222222"),
        )

        val hints = wallpaper.qualityHints()

        assertEquals("4K+", hints.resolutionLabel)
        assertTrue(hints.isIconSafe)
    }

    @Test
    fun `quality floor drops low signal wallpaper when stronger set exists`() {
        val strongCandidates = listOf(
            wallpaper(
                id = "strong_one",
                source = ContentSource.WALLHAVEN,
                url = "https://example.com/strong-1.jpg",
                width = 2160,
                height = 3840,
                tags = listOf("minimal", "amoled", "gradient"),
                colors = listOf("#000000"),
                favorites = 900,
            ),
            wallpaper(
                id = "strong_two",
                source = ContentSource.BING,
                url = "https://example.com/strong-2.jpg",
                width = 2160,
                height = 3840,
                tags = listOf("clean", "abstract", "dark"),
                colors = listOf("#050505", "#101010"),
                favorites = 500,
            ),
            wallpaper(
                id = "strong_three",
                source = ContentSource.PEXELS,
                url = "https://example.com/strong-3.jpg",
                width = 1440,
                height = 3200,
                tags = listOf("minimal", "soft", "nature"),
                colors = listOf("#101820", "#13293d"),
                favorites = 420,
            ),
            wallpaper(
                id = "strong_four",
                source = ContentSource.REDDIT,
                url = "https://example.com/strong-4.jpg",
                width = 1440,
                height = 3200,
                tags = listOf("amoled", "simple", "space"),
                colors = listOf("#000000", "#0b0f1a"),
                favorites = 300,
            ),
        )
        val weakCandidate = wallpaper(
            id = "weak_logo",
            source = ContentSource.PEXELS,
            url = "https://example.com/weak.jpg",
            width = 720,
            height = 720,
            tags = listOf("logo", "promo"),
            colors = listOf("#f1f1f1"),
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(weakCandidate) + strongCandidates,
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(4, ranked.size)
        assertTrue(ranked.none { it.id == "weak_logo" })
    }

    private fun wallpaper(
        id: String,
        source: ContentSource,
        url: String,
        width: Int,
        height: Int,
        tags: List<String> = emptyList(),
        colors: List<String> = emptyList(),
        favorites: Int = 0,
    ) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = url,
        fullUrl = url,
        width = width,
        height = height,
        tags = tags,
        colors = colors,
        favorites = favorites,
    )
}

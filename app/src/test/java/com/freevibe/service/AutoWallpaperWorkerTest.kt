package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWallpaperWorkerTest {

    @Test
    fun `normalizeWallpaperRotationSource maps legacy unsplash to discover`() {
        assertEquals("discover", "unsplash".normalizeWallpaperRotationSource())
        assertEquals("discover", "".normalizeWallpaperRotationSource())
        assertEquals("wallhaven", "wallhaven".normalizeWallpaperRotationSource())
    }

    @Test
    fun `stable-key comparison keeps different-provider alternatives available`() {
        val primary = wallpaper(id = "shared", source = ContentSource.PEXELS)
        val alternate = wallpaper(id = "shared", source = ContentSource.PIXABAY)

        val rawIdFiltered = listOf(primary, alternate).filter { it.id != primary.id }
        val stableKeyFiltered = listOf(primary, alternate).filter { it.stableKey() != primary.stableKey() }

        assertTrue(rawIdFiltered.isEmpty())
        assertEquals(listOf(alternate), stableKeyFiltered)
    }

    @Test
    fun `pickScheduledWallpaper returns null for empty inputs`() {
        assertNull(pickScheduledWallpaper(emptyList(), shuffle = true))
        assertNull(pickScheduledWallpaper(emptyList(), shuffle = false))
    }

    @Test
    fun `pickScheduledWallpaper returns first item when shuffle disabled`() {
        val first = wallpaper(id = "first", source = ContentSource.WALLHAVEN)
        val second = wallpaper(id = "second", source = ContentSource.REDDIT)

        val picked = pickScheduledWallpaper(listOf(first, second), shuffle = false)

        assertEquals(first, picked)
    }

    @Test
    fun `pickAlternateWallpaper falls back when no distinct alternative exists`() {
        val current = wallpaper(id = "shared", source = ContentSource.WALLHAVEN)
        val duplicate = wallpaper(id = "shared", source = ContentSource.WALLHAVEN)

        val picked = pickAlternateWallpaper(listOf(current, duplicate), current)

        assertSame(current, picked)
    }

    @Test
    fun `pickAlternateWallpaper prefers a different stable key when available`() {
        val current = wallpaper(id = "shared", source = ContentSource.WALLHAVEN)
        val alternate = wallpaper(id = "shared", source = ContentSource.PIXABAY)

        val picked = pickAlternateWallpaper(listOf(current, alternate), current)

        assertEquals(alternate, picked)
    }

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "thumb_$id",
        fullUrl = "full_${source.name.lowercase()}_$id",
        width = 1080,
        height = 2400,
    )
}

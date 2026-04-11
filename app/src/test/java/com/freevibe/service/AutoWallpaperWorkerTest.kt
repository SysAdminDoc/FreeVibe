package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import org.junit.Assert.assertEquals
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

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "thumb_$id",
        fullUrl = "full_${source.name.lowercase()}_$id",
        width = 1080,
        height = 2400,
    )
}

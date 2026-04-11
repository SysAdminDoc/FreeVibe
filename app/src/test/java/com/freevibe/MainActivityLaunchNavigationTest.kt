package com.freevibe

import android.os.Bundle
import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLaunchNavigationTest {
    @Test
    fun `buildLaunchNavigation supports route-only launches`() {
        val navigation = buildLaunchNavigation(route = "favorites")

        assertEquals("favorites", navigation?.route)
        assertNull(navigation?.wallpaper)
    }

    @Test
    fun `buildLaunchWallpaper preserves wallpaper metadata from notification extras`() {
        val wallpaper = buildLaunchWallpaper(
            wallpaperId = "reddit_123",
            fullUrl = "https://example.com/full.jpg",
            thumbnailUrl = "https://example.com/thumb.jpg",
            sourceName = ContentSource.REDDIT.name,
            width = 1440,
            height = 3200,
        )

        assertNotNull(wallpaper)
        assertEquals(ContentSource.REDDIT, wallpaper?.source)
        assertEquals(1440, wallpaper?.width)
        assertEquals(3200, wallpaper?.height)
        assertEquals("https://example.com/thumb.jpg", wallpaper?.thumbnailUrl)
    }

    @Test
    fun `saved state gates initial stale launch replay`() {
        assertFalse(shouldHandleInitialLaunchNavigation(Bundle()))
        assertTrue(shouldHandleInitialLaunchNavigation(null))
    }
}

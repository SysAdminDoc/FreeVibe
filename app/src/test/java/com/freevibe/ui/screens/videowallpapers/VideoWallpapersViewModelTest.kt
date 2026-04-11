package com.freevibe.ui.screens.videowallpapers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoWallpapersViewModelTest {

    @Test
    fun `resolveVideoLoadProgress stops pagination after three empty batches`() {
        val first = resolveVideoLoadProgress(previousEmptyLoadCount = 0, newItemCount = 0)
        val second = resolveVideoLoadProgress(previousEmptyLoadCount = first.emptyLoadCount, newItemCount = 0)
        val third = resolveVideoLoadProgress(previousEmptyLoadCount = second.emptyLoadCount, newItemCount = 0)

        assertTrue(first.hasMore)
        assertTrue(second.hasMore)
        assertFalse(third.hasMore)
        assertEquals(3, third.emptyLoadCount)
    }

    @Test
    fun `resolveVideoLoadProgress resets empty streak after new results`() {
        val progress = resolveVideoLoadProgress(previousEmptyLoadCount = 2, newItemCount = 4)

        assertTrue(progress.hasMore)
        assertEquals(0, progress.emptyLoadCount)
    }

    @Test
    fun `resolvePexelsVideoQuery starts from first fallback query on page one`() {
        val query = resolvePexelsVideoQuery(
            page = 1,
            searchQuery = null,
            fallbackQueries = listOf("mobile wallpaper", "phone wallpaper", "abstract background"),
        )

        assertEquals("mobile wallpaper", query)
    }

    @Test
    fun `resolvePexelsOrientationParam omits orientation for all mode`() {
        assertNull(resolvePexelsOrientationParam(OrientationFilter.ALL))
        assertEquals("portrait", resolvePexelsOrientationParam(OrientationFilter.PORTRAIT))
        assertEquals("landscape", resolvePexelsOrientationParam(OrientationFilter.LANDSCAPE))
    }
}

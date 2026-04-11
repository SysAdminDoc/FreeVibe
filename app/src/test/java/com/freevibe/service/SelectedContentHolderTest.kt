package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedContentHolderTest {

    @Test
    fun selectWallpaper_withoutList_clearsPreviousSharedContext() {
        val holder = SelectedContentHolder()
        val first = wallpaper(id = "shared", source = ContentSource.PEXELS)
        val second = wallpaper(id = "standalone", source = ContentSource.PIXABAY)

        holder.selectWallpaper(first, listOf(first, second))
        holder.selectWallpaper(second)

        assertEquals(second, holder.selectedWallpaper.value)
        assertTrue(holder.wallpaperList.value.isEmpty())
        assertNull(holder.wallpaperListAnchorKey.value)
    }

    @Test
    fun updateSelectedWallpaper_preservesExistingPagerContext() {
        val holder = SelectedContentHolder()
        val first = wallpaper(id = "one", source = ContentSource.PEXELS)
        val second = wallpaper(id = "two", source = ContentSource.PIXABAY)
        val sharedList = listOf(first, second)

        holder.selectWallpaper(first, sharedList)
        holder.updateSelectedWallpaper(second)

        assertEquals(second, holder.selectedWallpaper.value)
        assertEquals(sharedList, holder.wallpaperList.value)
        assertEquals(first.stableKey(), holder.wallpaperListAnchorKey.value)
    }

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "thumb_$id",
        fullUrl = "full_$id",
        width = 1080,
        height = 2400,
    )
}

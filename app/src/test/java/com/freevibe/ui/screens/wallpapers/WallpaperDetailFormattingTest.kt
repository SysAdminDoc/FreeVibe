package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperDetailFormattingTest {

    @Test
    fun `detail title prefers category over tags and source`() {
        val wallpaper = wallpaper(
            category = "neon city",
            tags = listOf("space", "amoled"),
        )

        assertEquals("Neon city", wallpaperDetailTitle(wallpaper))
    }

    @Test
    fun `detail subtitle includes uploader and source when available`() {
        val wallpaper = wallpaper(
            source = ContentSource.PEXELS,
            uploaderName = "Alex",
        )

        assertEquals("By Alex on Pexels", wallpaperDetailSubtitle(wallpaper))
    }

    @Test
    fun `compact count abbreviates large values`() {
        assertEquals("999", formatCompactCount(999))
        assertEquals("1.3k", formatCompactCount(1_250))
        assertEquals("2.5M", formatCompactCount(2_500_000))
    }

    @Test
    fun `file type label normalizes common mime values`() {
        assertEquals("JPG", formatFileTypeLabel("image/jpeg"))
        assertEquals("PNG", formatFileTypeLabel("image/png"))
        assertEquals("WEBP", formatFileTypeLabel("image/webp"))
    }

    @Test
    fun `file size label handles missing and megabyte values`() {
        assertNull(formatFileSizeLabel(0))
        assertEquals("512 KB", formatFileSizeLabel(512L * 1024L))
        assertEquals("3.5 MB", formatFileSizeLabel((3.5f * 1024f * 1024f).toLong()))
    }

    private fun wallpaper(
        source: ContentSource = ContentSource.WALLHAVEN,
        category: String = "",
        tags: List<String> = emptyList(),
        uploaderName: String = "",
    ) = Wallpaper(
        id = "wallpaper",
        source = source,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = "https://example.com/full.jpg",
        width = 1440,
        height = 3200,
        category = category,
        tags = tags,
        uploaderName = uploaderName,
    )
}

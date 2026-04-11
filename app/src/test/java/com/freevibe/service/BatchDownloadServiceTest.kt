package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDownloadServiceTest {

    @Test
    fun `progress counts failures toward completion`() {
        val state = BatchDownloadState(
            totalCount = 4,
            completedCount = 2,
            failedCount = 2,
        )

        assertEquals(1f, state.progress, 0.0001f)
        assertTrue(state.isComplete)
    }

    @Test
    fun `batch download ids are scoped by wallpaper source`() {
        val redditWallpaper = wallpaper(id = "shared_42", source = ContentSource.REDDIT)
        val pexelsWallpaper = wallpaper(id = "shared_42", source = ContentSource.PEXELS)

        assertNotEquals(buildBatchDownloadId(redditWallpaper), buildBatchDownloadId(pexelsWallpaper))
    }

    @Test
    fun `batch file names include source to avoid collisions`() {
        val redditWallpaper = wallpaper(id = "shared_42", source = ContentSource.REDDIT)
        val pexelsWallpaper = wallpaper(id = "shared_42", source = ContentSource.PEXELS)

        assertNotEquals(
            buildBatchFileName(redditWallpaper, "jpg"),
            buildBatchFileName(pexelsWallpaper, "jpg"),
        )
    }

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = "https://example.com/full.jpg",
        width = 1080,
        height = 1920,
        category = "Nature",
        tags = listOf("Forest"),
    )
}

package com.freevibe.data.repository

import com.freevibe.data.local.CollectionDao
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepositoryTest {

    private val dao = mockk<CollectionDao>()
    private val repository = CollectionRepository(dao)

    @Test
    fun `removeWallpaper scopes deletion by source`() = runTest {
        val wallpaper = wallpaper(id = "dup_id", source = ContentSource.PEXELS)
        coEvery { dao.removeItem(any(), any(), any()) } returns Unit

        repository.removeWallpaper(collectionId = 7L, wallpaper = wallpaper)

        coVerify(exactly = 1) { dao.removeItem(7L, "dup_id", "PEXELS") }
    }

    @Test
    fun `isInCollection scopes lookup by source`() = runTest {
        val wallpaper = wallpaper(id = "dup_id", source = ContentSource.WALLHAVEN)
        coEvery { dao.isInCollection(7L, "dup_id", "WALLHAVEN") } returns true

        val result = repository.isInCollection(collectionId = 7L, wallpaper = wallpaper)

        assertTrue(result)
        coVerify(exactly = 1) { dao.isInCollection(7L, "dup_id", "WALLHAVEN") }
    }

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
    )
}

package com.freevibe.ui.screens.downloads

import app.cash.turbine.test
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.DownloadEntity
import com.freevibe.service.DownloadManager
import com.freevibe.service.DownloadProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var downloadDao: DownloadDao
    private lateinit var downloadManager: DownloadManager
    private lateinit var viewModel: DownloadsViewModel

    private val sampleDownloads = listOf(
        DownloadEntity(id = "1", name = "Sunset", type = "WALLPAPER", localPath = "/path/sunset.jpg", downloadedAt = 1000L),
        DownloadEntity(id = "2", name = "Bell", type = "SOUND", localPath = "/path/bell.mp3", downloadedAt = 2000L),
        DownloadEntity(id = "3", name = "Mountains", type = "WALLPAPER", localPath = "/path/mountains.jpg", downloadedAt = 3000L),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        downloadDao = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)

        every { downloadDao.getAll() } returns flowOf(sampleDownloads)
        every { downloadDao.getByType("WALLPAPER") } returns flowOf(sampleDownloads.filter { it.type == "WALLPAPER" })
        every { downloadDao.getByType("SOUND") } returns flowOf(sampleDownloads.filter { it.type == "SOUND" })
        every { downloadManager.activeDownloads } returns MutableStateFlow(emptyMap())

        viewModel = DownloadsViewModel(downloadDao, downloadManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `allDownloads emits all items`() = runTest {
        viewModel.allDownloads.test {
            val items = awaitItem()
            assertEquals(3, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wallpaperDownloads filters by WALLPAPER type`() = runTest {
        viewModel.wallpaperDownloads.test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.all { it.type == "WALLPAPER" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `soundDownloads filters by SOUND type`() = runTest {
        viewModel.soundDownloads.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("SOUND", items[0].type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDownload calls dao deleteById`() = runTest {
        viewModel.deleteDownload("1")
        coVerify { downloadDao.deleteById("1") }
    }

    @Test
    fun `dismissActive calls downloadManager clearCompleted`() {
        viewModel.dismissActive("dl-1")
        verify { downloadManager.clearCompleted("dl-1") }
    }
}

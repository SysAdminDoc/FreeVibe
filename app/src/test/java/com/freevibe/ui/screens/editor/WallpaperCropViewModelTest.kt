package com.freevibe.ui.screens.editor

import app.cash.turbine.test
import com.freevibe.service.WallpaperApplier
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperCropViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: WallpaperCropViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WallpaperCropViewModel(
            wallpaperApplier = mockk(relaxed = true),
            okHttpClient = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no bitmap and is not loading`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.bitmap)
            assertFalse(state.isLoading)
            assertEquals(1f, state.scale)
            assertEquals(0f, state.offsetX)
            assertEquals(0f, state.offsetY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateTransform updates scale and offset`() = runTest {
        viewModel.updateTransform(2.5f, 100f, -50f)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2.5f, state.scale)
            assertEquals(100f, state.offsetX)
            assertEquals(-50f, state.offsetY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetTransform resets to defaults`() = runTest {
        viewModel.updateTransform(3f, 200f, 100f)
        viewModel.resetTransform()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1f, state.scale)
            assertEquals(0f, state.offsetX)
            assertEquals(0f, state.offsetY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearMessages clears success and error`() = runTest {
        viewModel.clearMessages()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.success)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

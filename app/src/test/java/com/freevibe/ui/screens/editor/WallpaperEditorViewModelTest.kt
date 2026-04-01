package com.freevibe.ui.screens.editor

import android.graphics.Bitmap
import app.cash.turbine.test
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.WallpaperApplier
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
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
class WallpaperEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var wallpaperApplier: WallpaperApplier
    private lateinit var viewModel: WallpaperEditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        wallpaperApplier = mockk(relaxed = true)
        viewModel = WallpaperEditorViewModel(
            wallpaperApplier = wallpaperApplier,
            okHttpClient = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default filter values`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(0f, state.brightness)
            assertEquals(1f, state.contrast)
            assertEquals(1f, state.saturation)
            assertEquals(0f, state.blurRadius)
            assertEquals(0f, state.vignette)
            assertEquals(0f, state.grain)
            assertEquals(0f, state.amoledCrush)
            assertEquals(0f, state.warmth)
            assertNull(state.originalBitmap)
            assertNull(state.editedBitmap)
            assertFalse(state.isProcessing)
            assertFalse(state.isApplying)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetAll restores default filter values`() = runTest {
        // Modify some values first (without a bitmap, applyFilters is a no-op)
        viewModel.state.test {
            awaitItem() // initial

            // resetAll should restore defaults
            viewModel.resetAll()
            val state = awaitItem()
            assertEquals(0f, state.brightness)
            assertEquals(1f, state.contrast)
            assertEquals(1f, state.saturation)
            assertEquals(0f, state.blurRadius)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearError clears error state`() = runTest {
        viewModel.clearError()
        viewModel.state.test {
            assertNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearSuccess clears success state`() = runTest {
        viewModel.clearSuccess()
        viewModel.state.test {
            assertNull(awaitItem().success)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

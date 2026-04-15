package com.freevibe.ui.screens.settings

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.WallpaperHistoryManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tempDirs = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `cacheUsage includes offline favorites and wallpaper metadata`() = runTest(dispatcher) {
        val root = createTempDirectory("settings-cache-usage").toFile().also(tempDirs::add)
        val cacheDir = File(root, "cache").apply { mkdirs() }
        File(cacheDir, "discover.json").writeBytes(ByteArray(1024))

        val viewModel = createViewModel(
            cacheDir = cacheDir,
            offlineFavoritesSize = 2048L,
            wallpaperCacheCounts = listOf(3),
        )

        waitForIdle {
            advanceUntilIdle()
            viewModel.cacheUsage.value.fileUsageLabel == "3.0 KB" &&
                viewModel.cacheUsage.value.hasWallpaperMetadataCache
        }

        assertEquals("3.0 KB", viewModel.cacheUsage.value.fileUsageLabel)
        assertTrue(viewModel.cacheUsage.value.hasWallpaperMetadataCache)
    }

    @Test
    fun `clearCache clears temp files and metadata cache but preserves trimmed exports`() = runTest(dispatcher) {
        val root = createTempDirectory("settings-clear-cache").toFile().also(tempDirs::add)
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "temp-preview.jpg").apply { writeText("preview") }
        val trimmedDir = File(cacheDir, "trimmed").apply { mkdirs() }
        val trimmedFile = File(trimmedDir, "clip.mp3").apply { writeText("keep me") }

        val offlineFavorites = mockk<OfflineFavoritesManager>()
        every { offlineFavorites.getCacheSize() } returns 0L
        coEvery { offlineFavorites.clearAll() } returns Unit

        val wallpaperCacheManager = mockk<WallpaperCacheManager>()
        coEvery { wallpaperCacheManager.countEntries() } returnsMany listOf(2, 0)
        coEvery { wallpaperCacheManager.clearAll() } returns Unit

        val viewModel = createViewModel(
            cacheDir = cacheDir,
            offlineFavoritesOverride = offlineFavorites,
            wallpaperCacheManagerOverride = wallpaperCacheManager,
        )

        advanceUntilIdle()
        viewModel.clearCache()
        waitForIdle {
            advanceUntilIdle()
            !tempFile.exists() &&
                viewModel.cacheUsage.value.fileUsageLabel == "0 B" &&
                !viewModel.cacheUsage.value.hasWallpaperMetadataCache
        }

        assertFalse(tempFile.exists())
        assertTrue(trimmedFile.exists())
        assertEquals("0 B", viewModel.cacheUsage.value.fileUsageLabel)
        assertFalse(viewModel.cacheUsage.value.hasWallpaperMetadataCache)
        coVerify(exactly = 1) { offlineFavorites.clearAll() }
        coVerify(exactly = 1) { wallpaperCacheManager.clearAll() }
    }

    @Test
    fun `prepareVideoWallpaperFromUri publishes ready when storage succeeds`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val storage = mockk<VideoWallpaperStorage>()
        coEvery { storage.prepareFromUri(uri) } returns Result.success(File("live_wallpaper.mp4"))
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-video-ready").toFile().also(tempDirs::add),
            videoWallpaperStorageOverride = storage,
        )

        viewModel.prepareVideoWallpaperFromUri(uri)
        advanceUntilIdle()

        assertEquals(VideoWallpaperSelectionResult.Ready, viewModel.videoWallpaperSelectionResult.value)
    }

    @Test
    fun `prepareVideoWallpaperFromUri publishes failure when storage rejects input`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val storage = mockk<VideoWallpaperStorage>()
        coEvery { storage.prepareFromUri(uri) } returns Result.failure(
            IllegalStateException("Animated GIF wallpapers are not supported yet."),
        )
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-video-failure").toFile().also(tempDirs::add),
            videoWallpaperStorageOverride = storage,
        )

        viewModel.prepareVideoWallpaperFromUri(uri)
        advanceUntilIdle()

        val failure = viewModel.videoWallpaperSelectionResult.value as VideoWallpaperSelectionResult.Failure
        assertEquals("Animated GIF wallpapers are not supported yet.", failure.message)
    }

    private fun createViewModel(
        cacheDir: File,
        offlineFavoritesSize: Long = 0L,
        wallpaperCacheCounts: List<Int> = listOf(0),
        offlineFavoritesOverride: OfflineFavoritesManager? = null,
        wallpaperCacheManagerOverride: WallpaperCacheManager? = null,
        videoWallpaperStorageOverride: VideoWallpaperStorage? = null,
    ): SettingsViewModel {
        val context = mockk<Context>(relaxed = true).also {
            every { it.cacheDir } returns cacheDir
            every { it.filesDir } returns cacheDir.parentFile ?: cacheDir
        }
        val prefs = mockPreferences()
        val historyManager = mockk<WallpaperHistoryManager>(relaxed = true).also {
            every { it.getRecent(any()) } returns flowOf(emptyList())
        }
        val offlineFavorites = offlineFavoritesOverride ?: mockk<OfflineFavoritesManager>().also {
            every { it.getCacheSize() } returns offlineFavoritesSize
            coEvery { it.clearAll() } returns Unit
        }
        val wallpaperCacheManager = wallpaperCacheManagerOverride ?: mockk<WallpaperCacheManager>().also {
            coEvery { it.countEntries() } returnsMany wallpaperCacheCounts
            coEvery { it.clearAll() } returns Unit
        }

        val collectionRepo = mockk<com.freevibe.data.repository.CollectionRepository>().also {
            every { it.getAll() } returns flowOf(emptyList())
        }
        val wallpaperApplier = mockk<com.freevibe.service.WallpaperApplier>(relaxed = true)
        val videoWallpaperStorage = videoWallpaperStorageOverride ?: mockk(relaxed = true)
        return SettingsViewModel(
            context = context,
            prefs = prefs,
            historyManager = historyManager,
            offlineFavorites = offlineFavorites,
            wallpaperCacheManager = wallpaperCacheManager,
            collectionRepo = collectionRepo,
            wallpaperApplier = wallpaperApplier,
            videoWallpaperStorage = videoWallpaperStorage,
        )
    }

    private fun mockPreferences(): PreferencesManager =
        mockk<PreferencesManager>().also { prefs ->
            every { prefs.autoWallpaperEnabled } returns flowOf(false)
            every { prefs.autoWallpaperInterval } returns flowOf(12L)
            every { prefs.autoWallpaperSource } returns flowOf("discover")
            every { prefs.schedulerEnabled } returns flowOf(false)
            every { prefs.schedulerIntervalMinutes } returns flowOf(360L)
            every { prefs.schedulerSource } returns flowOf("discover")
            every { prefs.schedulerHomeEnabled } returns flowOf(true)
            every { prefs.schedulerLockEnabled } returns flowOf(true)
            every { prefs.schedulerShuffle } returns flowOf(true)
            every { prefs.weatherEffectsEnabled } returns flowOf(false)
            every { prefs.adaptiveTintEnabled } returns flowOf(false)
            every { prefs.darkModeAutoSwitch } returns flowOf(false)
            every { prefs.autoPreviewSounds } returns flowOf(true)
            every { prefs.wallpaperGridColumns } returns flowOf(2)
            every { prefs.soundPreviewVolume } returns flowOf(0.7f)
            every { prefs.redditSubreddits } returns flowOf("wallpapers")
            every { prefs.preferredResolution } returns flowOf("")
            every { prefs.userStyles } returns flowOf("")
            every { prefs.ytSoundQueryRingtones } returns flowOf("ringtone")
            every { prefs.ytSoundQueryNotifications } returns flowOf("notification")
            every { prefs.ytSoundQueryAlarms } returns flowOf("alarm")
            every { prefs.ytSoundBlockedWords } returns flowOf("mix")
            every { prefs.videoFpsLimit } returns flowOf(30)
            every { prefs.wallhavenApiKey } returns flowOf("")
            every { prefs.pexelsApiKey } returns flowOf("")
            every { prefs.pixabayApiKey } returns flowOf("")
            every { prefs.freesoundApiKey } returns flowOf("")
            every { prefs.schedulerCollectionId } returns flowOf(-1L)
        }

    private fun waitForIdle(
        timeoutMs: Long = 2000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for background work to finish")
    }
}

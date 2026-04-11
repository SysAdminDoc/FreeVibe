package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.AudiusRepository
import com.freevibe.data.repository.CcMixterRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.FreesoundRepository
import com.freevibe.data.repository.FreesoundV2Repository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.SoundCloudRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.DownloadManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SoundsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial mixed load does not advertise more pages from one-shot sources`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { freesoundV2Repo.search(any(), any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { freesoundRepo.search(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { soundCloudRepo.search(any(), any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { audiusRepo.search(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("au_focus", ContentSource.AUDIUS, "Aura Focus Tone")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )
        coEvery { ccMixterRepo.search(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("ccm_focus", ContentSource.CCMIXTER, "Aura Focus Tone CC")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf("au_focus", "ccm_focus"), state.sounds.map { it.id }.toSet())
        assertFalse(state.hasMore)
    }

    @Test
    fun `loadMore skips Audius and ccMixter after first page`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { freesoundV2Repo.search(any(), any(), any(), 1, any()) } returns SearchResult(
            items = listOf(testSound("fs_page1", ContentSource.FREESOUND, "Morning Bell")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )
        coEvery { freesoundV2Repo.search(any(), any(), any(), 2, any()) } returns emptySoundResult(page = 2)
        coEvery { freesoundRepo.search(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { soundCloudRepo.search(any(), any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { audiusRepo.search(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("au_page1", ContentSource.AUDIUS, "Bright Pulse")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )
        coEvery { ccMixterRepo.search(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("ccm_page1", ContentSource.CCMIXTER, "Bright Pulse Mix")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(setOf("fs_page1", "au_page1", "ccm_page1"), viewModel.state.value.sounds.map { it.id }.toSet())
        assertFalse(viewModel.state.value.hasMore)
        coVerify(exactly = 2) { audiusRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 1) { ccMixterRepo.search(any(), any(), any(), any()) }
    }

    @Test
    fun `youtube search does not expose unsupported pagination`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { youtubeRepo.searchSounds("focus", any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_focus", ContentSource.YOUTUBE, "Focus Loop")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        viewModel.searchYouTube("focus")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.YOUTUBE, state.selectedTab)
        assertEquals(listOf("yt_focus"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun `resolving youtube preview updates selected sound`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )
        coEvery { youtubeRepo.getAudioPreviewUrl("focus") } returns "https://example.com/focus.mp3"

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        val unresolved = testSound("yt_focus", ContentSource.YOUTUBE, "Focus Loop").copy(
            previewUrl = "",
            downloadUrl = "",
        )

        viewModel.selectSound(unresolved)
        viewModel.togglePlayback(unresolved)
        advanceUntilIdle()

        assertEquals("https://example.com/focus.mp3", viewModel.selectedSound.value?.previewUrl)
    }

    @Test
    fun `youtube sounds refresh stale preview urls before playback`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )
        every { youtubeRepo.isCached("focus12345") } returns false
        coEvery { youtubeRepo.getAudioPreviewUrl("focus12345") } returns "https://example.com/fresh-focus.mp3"

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        val staleFavorite = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").copy(
            previewUrl = "https://expired.example.com/focus-preview.mp3",
            downloadUrl = "https://expired.example.com/focus-download.mp3",
        )

        viewModel.selectSound(staleFavorite)
        viewModel.togglePlayback(staleFavorite)
        advanceUntilIdle()

        assertEquals("https://example.com/fresh-focus.mp3", viewModel.selectedSound.value?.previewUrl)
    }

    @Test
    fun `downloadSound refreshes youtube stream urls instead of reusing stale favorites`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()
        val downloadManager = mockk<DownloadManager>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )
        coEvery { youtubeRepo.getAudioStreamUrl("focus12345") } returns "https://example.com/fresh-focus-download.mp3"
        coEvery {
            downloadManager.downloadSound(
                id = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").stableKey(),
                url = "https://example.com/fresh-focus-download.mp3",
                fileName = any(),
                type = ContentType.RINGTONE,
            )
        } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
            downloadManagerOverride = downloadManager,
        )

        advanceUntilIdle()
        val staleFavorite = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").copy(
            downloadUrl = "https://expired.example.com/focus-download.mp3",
        )

        viewModel.downloadSound(staleFavorite)
        advanceUntilIdle()

        coVerify(exactly = 1) { youtubeRepo.getAudioStreamUrl("focus12345") }
        coVerify(exactly = 1) {
            downloadManager.downloadSound(
                id = staleFavorite.stableKey(),
                url = "https://example.com/fresh-focus-download.mp3",
                fileName = match { it == "Aura_youtube_yt_focus12345_Focus Loop.mp3" },
                type = ContentType.RINGTONE,
            )
        }
    }

    @Test
    fun `blank search state does not hit providers`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        clearMocks(youtubeRepo, freesoundRepo, freesoundV2Repo, audiusRepo, ccMixterRepo, soundCloudRepo, answers = false, recordedCalls = true)

        viewModel.selectTab(SoundTab.SEARCH)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.SEARCH, state.selectedTab)
        assertTrue(state.sounds.isEmpty())
        assertFalse(state.hasMore)
        assertEquals("", state.query)
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
        coVerify(exactly = 0) { freesoundRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { freesoundV2Repo.search(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { audiusRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { ccMixterRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { soundCloudRepo.search(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `empty search surfaces provider failure`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { youtubeRepo.searchSounds(match { it.contains("focus") }, any(), any(), any()) } throws UnknownHostException()
        coEvery { freesoundRepo.search(match { it.contains("focus") }, any(), any(), any()) } throws UnknownHostException()
        coEvery { freesoundV2Repo.search(match { it.contains("focus") }, any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { audiusRepo.search(match { it.contains("focus") }, any(), any(), any()) } throws UnknownHostException()
        coEvery { ccMixterRepo.search(match { it.contains("focus") }, any(), any(), any()) } throws UnknownHostException()
        coEvery { soundCloudRepo.search(match { it.contains("focus") }, any(), any(), any(), any()) } throws UnknownHostException()

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        viewModel.search("focus")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.SEARCH, state.selectedTab)
        assertTrue(state.sounds.isEmpty())
        assertFalse(state.hasMore)
        assertEquals("No internet connection", state.error)
    }

    @Test
    fun `refresh preserves bundled sounds when providers return empty`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()
        val bundled = listOf(testSound("bundle_keep", ContentSource.BUNDLED, "Aura Bell"))

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
            bundledRingtones = bundled,
        )

        advanceUntilIdle()
        assertEquals(listOf("bundle_keep"), viewModel.state.value.sounds.map { it.id })

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("bundle_keep"), state.sounds.map { it.id })
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh preserves current search results but updates pagination when providers go empty`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { freesoundV2Repo.search(match { it.contains("focus") }, any(), any(), any(), any()) } returnsMany listOf(
            SearchResult(
                items = listOf(testSound("focus_keep", ContentSource.FREESOUND, "Focus Keep")),
                totalCount = 1,
                currentPage = 1,
                hasMore = true,
            ),
            emptySoundResult(),
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        advanceUntilIdle()
        viewModel.search("focus")
        advanceUntilIdle()

        assertEquals(listOf("focus_keep"), viewModel.state.value.sounds.map { it.id })
        assertTrue(viewModel.state.value.hasMore)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("focus_keep"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh preserves bundled sounds and surfaces degraded error`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()
        val bundled = listOf(testSound("bundle_keep", ContentSource.BUNDLED, "Aura Bell"))

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )
        coEvery { freesoundRepo.search(any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { freesoundV2Repo.search(any(), any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { audiusRepo.search(any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { ccMixterRepo.search(any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { soundCloudRepo.search(any(), any(), any(), any(), any()) } throws UnknownHostException()
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } throws UnknownHostException()

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
            bundledRingtones = bundled,
        )

        advanceUntilIdle()
        assertEquals(listOf("bundle_keep"), viewModel.state.value.sounds.map { it.id })

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("bundle_keep"), state.sounds.map { it.id })
        assertEquals("No internet connection. Showing your last good results.", state.error)
    }

    @Test
    fun `stopIfPlaying stops matching stable-key playback`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()
        val currentSoundId = MutableStateFlow<String?>(null)
        val audioPlaybackManager = mockk<AudioPlaybackManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )
        every { audioPlaybackManager.currentSoundId } returns currentSoundId
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(0L)
        every { audioPlaybackManager.duration } returns MutableStateFlow(1_000L)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
            audioPlaybackManagerOverride = audioPlaybackManager,
        )

        advanceUntilIdle()

        val sound = testSound("dup_sound", ContentSource.BUNDLED, "Aura Bell")
        viewModel.selectSound(sound)
        currentSoundId.value = sound.stableKey()
        advanceUntilIdle()

        viewModel.stopIfPlaying(sound)

        verify(exactly = 1) { audioPlaybackManager.stop() }
    }

    @Test
    fun `resolveSound uses type-scoped favorite lookup when raw ids collide across content types`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val freesoundRepo = mockk<FreesoundRepository>()
        val freesoundV2Repo = mockk<FreesoundV2Repository>()
        val audiusRepo = mockk<AudiusRepository>()
        val ccMixterRepo = mockk<CcMixterRepository>()
        val soundCloudRepo = mockk<SoundCloudRepository>()
        val favoritesRepo = mockk<FavoritesRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
        )

        coEvery { favoritesRepo.getByIdentity(any()) } returns null
        coEvery { favoritesRepo.getLatestById(any()) } returns FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.PEXELS.name,
            type = "WALLPAPER",
            thumbnailUrl = "thumb",
            fullUrl = "https://example.com/wallpaper.jpg",
        )
        coEvery { favoritesRepo.getLatestByIdAndType("shared_raw", "SOUND") } returns FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.YOUTUBE.name,
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://example.com/audio.mp3",
            name = "Recovered tone",
            duration = 12.0,
            sourcePageUrl = "https://youtube.com/watch?v=abc123",
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            soundCloudRepo = soundCloudRepo,
            favoritesRepoOverride = favoritesRepo,
        )

        advanceUntilIdle()

        val resolved = viewModel.resolveSound("shared_raw")

        assertEquals(ContentSource.YOUTUBE, resolved?.source)
        assertEquals("Recovered tone", resolved?.name)
        coVerify(exactly = 1) { favoritesRepo.getLatestByIdAndType("shared_raw", "SOUND") }
    }

    private fun createViewModel(
        youtubeRepo: YouTubeRepository,
        freesoundRepo: FreesoundRepository,
        freesoundV2Repo: FreesoundV2Repository,
        audiusRepo: AudiusRepository,
        ccMixterRepo: CcMixterRepository,
        soundCloudRepo: SoundCloudRepository,
        bundledRingtones: List<Sound> = emptyList(),
        bundledNotifications: List<Sound> = emptyList(),
        bundledAlarms: List<Sound> = emptyList(),
        audioPlaybackManagerOverride: AudioPlaybackManager? = null,
        downloadManagerOverride: DownloadManager? = null,
        favoritesRepoOverride: FavoritesRepository? = null,
    ): SoundsViewModel {
        val prefs = mockk<PreferencesManager>()
        every { prefs.autoPreviewSounds } returns flowOf(true)
        every { prefs.soundPreviewVolume } returns flowOf(0.7f)
        every { prefs.ytSoundQueryRingtones } returns flowOf("ringtone search")
        every { prefs.ytSoundQueryNotifications } returns flowOf("notification search")
        every { prefs.ytSoundQueryAlarms } returns flowOf("alarm search")
        every { prefs.ytSoundBlockedWords } returns flowOf("mix,podcast")

        val searchHistoryRepo = mockk<SearchHistoryRepository>()
        every { searchHistoryRepo.getRecentSoundSearches(any()) } returns flowOf(emptyList<SearchHistoryEntity>())
        coEvery { searchHistoryRepo.addSoundSearch(any()) } returns Unit
        coEvery { searchHistoryRepo.removeSearch(any(), any()) } returns Unit
        coEvery { searchHistoryRepo.clearSoundHistory() } returns Unit

        val bundledContent = mockk<BundledContentProvider>()
        every { bundledContent.getRingtones() } returns bundledRingtones
        every { bundledContent.getNotifications() } returns bundledNotifications
        every { bundledContent.getAlarms() } returns bundledAlarms

        val audioPlaybackManager = audioPlaybackManagerOverride ?: mockk<AudioPlaybackManager>(relaxed = true).also {
            every { it.currentSoundId } returns MutableStateFlow(null)
            every { it.currentPosition } returns MutableStateFlow(0L)
            every { it.duration } returns MutableStateFlow(0L)
        }

        val soundUrlResolver = mockk<SoundUrlResolver>()
        coEvery { soundUrlResolver.resolve(any()) } answers { firstArg<Sound>().downloadUrl }

        val favoritesRepo = favoritesRepoOverride ?: mockk<FavoritesRepository>(relaxed = true).also {
            coEvery { it.getByIdentity(any()) } returns null
            coEvery { it.getLatestByIdAndType(any(), any()) } returns null
        }

        return SoundsViewModel(
            context = mockk<Context>(relaxed = true),
            youtubeRepo = youtubeRepo,
            freesoundRepo = freesoundRepo,
            freesoundV2Repo = freesoundV2Repo,
            audiusRepo = audiusRepo,
            ccMixterRepo = ccMixterRepo,
            favoritesRepo = favoritesRepo,
            soundApplier = mockk<SoundApplier>(relaxed = true),
            downloadManager = downloadManagerOverride ?: mockk<DownloadManager>(relaxed = true),
            selectedContent = SelectedContentHolder(),
            searchHistoryRepo = searchHistoryRepo,
            audioTrimmer = mockk<com.freevibe.service.AudioTrimmer>(relaxed = true),
            prefs = prefs,
            voteRepo = mockk<VoteRepository>(relaxed = true),
            bundledContent = bundledContent,
            audioPlaybackManager = audioPlaybackManager,
            soundCloudRepo = soundCloudRepo,
            uploadRepo = mockk<UploadRepository>(relaxed = true),
            soundUrlResolver = soundUrlResolver,
        )
    }

    private fun stubCommonDependencies(
        youtubeRepo: YouTubeRepository,
        freesoundRepo: FreesoundRepository,
        freesoundV2Repo: FreesoundV2Repository,
        audiusRepo: AudiusRepository,
        ccMixterRepo: CcMixterRepository,
        soundCloudRepo: SoundCloudRepository,
    ) {
        every { youtubeRepo.isCached(any()) } returns false
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { freesoundRepo.search(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { freesoundV2Repo.search(any(), any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { audiusRepo.search(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { ccMixterRepo.search(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { soundCloudRepo.search(any(), any(), any(), any(), any()) } returns emptySoundResult()
    }

    private fun emptySoundResult(page: Int = 1) = SearchResult(
        items = emptyList<Sound>(),
        totalCount = 0,
        currentPage = page,
        hasMore = false,
    )

    private fun testSound(id: String, source: ContentSource, name: String) = Sound(
        id = id,
        source = source,
        name = name,
        description = "",
        previewUrl = "https://example.com/$id.mp3",
        downloadUrl = "https://example.com/$id.mp3",
        duration = 12.0,
        tags = listOf("clean", "tone"),
        license = "CC0",
        uploaderName = "Tester",
    )
}

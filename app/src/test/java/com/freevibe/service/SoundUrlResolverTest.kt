package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.repository.YouTubeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundUrlResolverTest {

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
    fun `resolve returns fresh YouTube stream url`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        coEvery { youtubeRepo.getAudioStreamUrl("focus12345") } returns "https://example.com/fresh-focus.mp3"

        val resolver = SoundUrlResolver(
            okHttpClient = mockk<OkHttpClient>(relaxed = true),
            youtubeRepo = youtubeRepo,
        )

        val resolved = resolver.resolve(
            Sound(
                id = "yt_focus12345",
                source = ContentSource.YOUTUBE,
                name = "Focus Loop",
                previewUrl = "",
                downloadUrl = "",
                sourcePageUrl = "https://www.youtube.com/watch?v=focus12345",
            )
        )

        assertEquals("https://example.com/fresh-focus.mp3", resolved)
    }
}

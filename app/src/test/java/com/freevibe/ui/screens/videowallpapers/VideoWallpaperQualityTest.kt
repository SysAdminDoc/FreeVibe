package com.freevibe.ui.screens.videowallpapers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoWallpaperQualityTest {

    @Test
    fun `quality floor drops weak off-fit videos when stronger set exists`() {
        val strongCandidates = listOf(
            video(
                id = "px_one",
                source = "Pexels",
                title = "Abstract loop",
                duration = 12,
                popularity = 12_000,
                videoWidth = 1080,
                videoHeight = 1920,
            ),
            video(
                id = "pb_two",
                source = "Pixabay",
                title = "Ambient particles",
                duration = 10,
                popularity = 8_500,
                videoWidth = 1080,
                videoHeight = 1920,
            ),
            video(
                id = "rd_three",
                source = "Reddit",
                title = "Rain loop",
                duration = 9,
                popularity = 7_000,
                videoWidth = 1080,
                videoHeight = 1920,
            ),
            video(
                id = "yt_four",
                source = "YouTube",
                title = "Galaxy ambient loop",
                duration = 15,
                popularity = 25_000,
                videoWidth = 1080,
                videoHeight = 1920,
            ),
        )
        val weakCandidate = video(
            id = "yt_weak",
            source = "YouTube",
            title = "Phone setup review",
            duration = 75,
            popularity = 40,
            videoWidth = 1920,
            videoHeight = 1080,
        )

        val ranked = rankVideoWallpapers(
            items = listOf(weakCandidate) + strongCandidates,
            filter = VideoFocusFilter.BEST,
            orientation = OrientationFilter.PORTRAIT,
        )

        assertEquals(4, ranked.size)
        assertTrue(ranked.none { it.id == "yt_weak" })
    }

    private fun video(
        id: String,
        source: String,
        title: String,
        duration: Long,
        popularity: Long,
        videoWidth: Int,
        videoHeight: Int,
    ) = VideoWallpaperItem(
        id = id,
        title = title,
        thumbnailUrl = "https://example.com/$id.jpg",
        source = source,
        duration = duration,
        popularity = popularity,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
    )
}

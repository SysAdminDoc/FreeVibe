package com.freevibe.ui.screens.sounds

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundQualityTest {

    @Test
    fun `rankSounds keeps best duplicate instead of first duplicate`() {
        val weak = testSound(
            id = "yt_one",
            source = ContentSource.YOUTUBE,
            name = "Crystal Chime Melody",
            duration = 14.0,
        )
        val stronger = testSound(
            id = "bundle_one",
            source = ContentSource.BUNDLED,
            name = "Crystal Chime Melody",
            duration = 14.0,
            tags = listOf("soft", "clean", "chime"),
            license = "CC0",
        )

        val ranked = rankSounds(
            sounds = listOf(weak, stronger),
            tab = SoundTab.RINGTONES,
            filter = SoundQualityFilter.BEST,
        )

        assertEquals(1, ranked.size)
        assertEquals("bundle_one", ranked.first().id)
    }

    @Test
    fun `clean filter returns only clean sounding entries`() {
        val clean = testSound(
            id = "clean",
            source = ContentSource.FREESOUND,
            name = "Soft Bell Tone",
            duration = 2.0,
            tags = listOf("soft", "chime"),
        )
        val noisy = testSound(
            id = "noisy",
            source = ContentSource.YOUTUBE,
            name = "Podcast Mix Review",
            duration = 2.0,
            tags = listOf("mix"),
        )

        val ranked = rankSounds(
            sounds = listOf(clean, noisy),
            tab = SoundTab.NOTIFICATIONS,
            filter = SoundQualityFilter.CLEAN,
        )

        assertEquals(listOf("clean"), ranked.map { it.id })
    }

    @Test
    fun `sound badges describe intent and mood`() {
        val sound = testSound(
            id = "alarm_one",
            source = ContentSource.CCMIXTER,
            name = "Bright Alarm Bell",
            duration = 9.0,
            tags = listOf("alarm", "bright"),
            license = "CC BY",
        )

        val badges = soundBadges(sound, SoundTab.ALARMS)

        assertTrue(badges.contains("Alarm-ready"))
        assertTrue(badges.contains("Punchy"))
    }

    @Test
    fun `quality floor drops weak long form sound when stronger set exists`() {
        val strongCandidates = listOf(
            testSound(
                id = "bundle_best",
                source = ContentSource.BUNDLED,
                name = "Soft Orbit Chime",
                duration = 14.0,
                tags = listOf("soft", "clean", "chime"),
                license = "CC0",
            ),
            testSound(
                id = "jamendo_best",
                source = ContentSource.JAMENDO,
                name = "Night Pulse Ringtone",
                duration = 16.0,
                tags = listOf("ringtone", "bright", "electro"),
                license = "CC BY",
            ),
            testSound(
                id = "audius_best",
                source = ContentSource.AUDIUS,
                name = "Minimal Echo Tone",
                duration = 12.0,
                tags = listOf("tone", "clean", "soft"),
                license = "CC BY",
            ),
            testSound(
                id = "freesound_best",
                source = ContentSource.FREESOUND,
                name = "Glass Ping Alert",
                duration = 11.0,
                tags = listOf("ping", "alert", "clean"),
                license = "CC0",
            ),
        )
        val weakCandidate = testSound(
            id = "weak_mix",
            source = ContentSource.YOUTUBE,
            name = "Viral Remix Review Edit",
            duration = 245.0,
            tags = listOf("review", "remix", "podcast"),
        )

        val ranked = rankSounds(
            sounds = listOf(weakCandidate) + strongCandidates,
            tab = SoundTab.RINGTONES,
            filter = SoundQualityFilter.BEST,
        )

        assertEquals(4, ranked.size)
        assertTrue(ranked.none { it.id == "weak_mix" })
    }

    private fun testSound(
        id: String,
        source: ContentSource,
        name: String,
        duration: Double,
        tags: List<String> = emptyList(),
        license: String = "",
    ) = Sound(
        id = id,
        source = source,
        name = name,
        description = "",
        previewUrl = "https://example.com/$id.mp3",
        downloadUrl = "https://example.com/$id.mp3",
        duration = duration,
        tags = tags,
        license = license,
        uploaderName = "Tester",
    )
}

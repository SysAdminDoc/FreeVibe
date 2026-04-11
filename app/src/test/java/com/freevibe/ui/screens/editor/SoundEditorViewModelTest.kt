package com.freevibe.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEditorViewModelTest {

    @Test
    fun `remote audio cache file name is scoped by identity`() {
        val first = buildRemoteAudioCacheFileName(
            name = "Focus Loop",
            cacheIdentity = "SOUND::YOUTUBE::yt_focus12345",
            url = "https://example.com/audio.mp3",
        )
        val second = buildRemoteAudioCacheFileName(
            name = "Focus Loop",
            cacheIdentity = "SOUND::YOUTUBE::yt_relax12345",
            url = "https://example.com/audio.mp3",
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `remote audio cache file name preserves detected extension`() {
        val fileName = buildRemoteAudioCacheFileName(
            name = "Ocean Wave",
            cacheIdentity = "SOUND::BUNDLED::ocean_wave",
            url = "https://example.com/ocean.wav?download=1",
        )

        assertTrue(fileName.endsWith(".wav"))
    }

    @Test
    fun `shouldReuseLoadedSound ignores local editor state`() {
        val shouldReuse = shouldReuseLoadedSound(
            loadedSoundKey = "SOUND::YOUTUBE::yt_focus12345",
            requestedSoundKey = "SOUND::YOUTUBE::yt_focus12345",
            state = SoundEditorState(
                localFilePath = "C:/cache/local.mp3",
                isLocalFile = true,
            ),
        )

        assertFalse(shouldReuse)
    }

    @Test
    fun `local audio editor identity is scoped to uri`() {
        val first = buildLocalAudioEditorIdentity("content://audio/1")
        val second = buildLocalAudioEditorIdentity("content://audio/2")

        assertNotEquals(first, second)
    }
}

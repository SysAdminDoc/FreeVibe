package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UploadRepositoryValidationTest {

    @Test
    fun `normalizeUploadCategory trims and lowercases supported categories`() {
        assertEquals("notification", normalizeUploadCategory(" Notification "))
        assertEquals("alarm", normalizeUploadCategory("alarm"))
    }

    @Test
    fun `normalizeUploadCategory rejects unsupported values`() {
        try {
            normalizeUploadCategory("music")
            fail("Expected invalid category to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `sanitizeUploadTags trims dedupes and caps tag count`() {
        val tags = sanitizeUploadTags(
            listOf(
                " Chill ",
                "CHILL",
                "lo-fi",
                "ringtone!!!",
                "a",
                "night drive",
                "focus_mode",
                "ambient",
                "calm",
                "extra-tag",
            )
        )

        assertEquals(
            listOf("chill", "lo-fi", "ringtone", "night drive", "focus_mode", "ambient", "calm", "extra-tag"),
            tags,
        )
    }

    @Test
    fun `isSupportedAudioUploadMime only allows approved audio formats`() {
        assertTrue(isSupportedAudioUploadMime("audio/mpeg"))
        assertTrue(isSupportedAudioUploadMime("audio/x-wav"))
        assertFalse(isSupportedAudioUploadMime("video/mp4"))
        assertFalse(isSupportedAudioUploadMime(""))
    }

    @Test
    fun `sanitizeUploadStorageSegment removes unsafe characters`() {
        assertEquals("user_123", sanitizeUploadStorageSegment("user/123"))
        assertEquals("user", sanitizeUploadStorageSegment("   "))
    }
}

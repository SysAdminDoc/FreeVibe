package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior-locking tests for the Stability AI error mapper. We surface specific
 * user-facing strings for known status codes so the user gets actionable copy
 * instead of "Generation failed (HTTP 402): {raw json blob}".
 */
class AiWallpaperRepositoryFriendlyErrorTest {

    @Test
    fun `401 yields actionable key-invalid message`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(401, null)
        assertTrue("Expected key-related message, got: $msg", msg.contains("key", ignoreCase = true))
    }

    @Test
    fun `402 yields credits message`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(402, null)
        assertTrue("Expected credits message, got: $msg", msg.contains("credits", ignoreCase = true))
    }

    @Test
    fun `403 yields content-policy message`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(403, null)
        assertTrue("Expected content-policy message, got: $msg", msg.contains("content policy", ignoreCase = true))
    }

    @Test
    fun `422 yields prompt-too-complex message`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(422, null)
        assertTrue("Expected prompt message, got: $msg", msg.contains("prompt", ignoreCase = true))
    }

    @Test
    fun `429 yields rate limit message`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(429, null)
        assertTrue("Expected rate-limit message, got: $msg", msg.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun `5xx yields server error message with code`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(503, null)
        assertTrue("Expected server-error message, got: $msg", msg.contains("server", ignoreCase = true))
        assertTrue("Expected 503 in message, got: $msg", msg.contains("503"))
    }

    @Test
    fun `unknown code yields generic message including code`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(418, null)
        assertEquals("Generation failed (HTTP 418).", msg)
    }

    @Test
    fun `error body is appended when present`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(429, "Rate limit exceeded, retry in 30s")
        assertTrue("Expected body suffix, got: $msg", msg.contains("Rate limit exceeded"))
    }

    @Test
    fun `blank error body is dropped`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(401, "   ")
        // Trailing space would be present if the helper naively concatenated. We assert
        // the message ends at a period (the canonical base form).
        assertTrue("Blank error body should not be appended: $msg", msg.endsWith("."))
    }

    @Test
    fun `null error body is dropped`() {
        val msg = AiWallpaperRepository.friendlyErrorMessage(401, null)
        assertTrue("Null error body should not be appended: $msg", msg.endsWith("."))
    }
}

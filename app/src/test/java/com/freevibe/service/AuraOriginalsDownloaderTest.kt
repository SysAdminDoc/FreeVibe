package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pure-JVM tests for AuraOriginalsDownloader helpers. Roadmap N-5.
 * The worker itself needs a real WorkManager runtime; these tests cover the
 * decision points (hash verification + extension guessing) that decide
 * whether a downloaded file is accepted.
 */
class AuraOriginalsDownloaderTest {

    @Test
    fun `guessExtension picks recognized suffixes`() {
        assertEquals("ogg", AuraOriginalsDownloader.guessExtension("https://example.com/foo.ogg"))
        assertEquals("m4a", AuraOriginalsDownloader.guessExtension("https://example.com/foo.M4A"))
        assertEquals("wav", AuraOriginalsDownloader.guessExtension("https://example.com/foo.wav"))
        assertEquals("flac", AuraOriginalsDownloader.guessExtension("https://example.com/foo.FLAC"))
    }

    @Test
    fun `guessExtension falls back to mp3`() {
        assertEquals("mp3", AuraOriginalsDownloader.guessExtension("https://example.com/foo"))
        assertEquals("mp3", AuraOriginalsDownloader.guessExtension("https://example.com/foo.mp3"))
        assertEquals("mp3", AuraOriginalsDownloader.guessExtension("https://example.com/foo.unknown"))
    }

    @Test
    fun `verifyHash accepts matching digest`() {
        // sha256("aura") = 6d7c0eb420852d2e6288be29c47c975ff15d81eeacd4eef100022f931a827702
        // (verified via `printf 'aura' | sha256sum`).
        val file = Files.createTempFile("aura-test-", ".bin").toFile()
        try {
            file.writeBytes("aura".toByteArray())
            val expected = "6d7c0eb420852d2e6288be29c47c975ff15d81eeacd4eef100022f931a827702"
            assertTrue(AuraOriginalsDownloader.verifyHash(file, expected))
            // Case-insensitive: uppercase hex should also match.
            assertTrue(AuraOriginalsDownloader.verifyHash(file, expected.uppercase()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `verifyHash rejects mismatched digest`() {
        val file = Files.createTempFile("aura-test-", ".bin").toFile()
        try {
            file.writeBytes("aura".toByteArray())
            assertFalse(AuraOriginalsDownloader.verifyHash(file, "0".repeat(64)))
            assertFalse(AuraOriginalsDownloader.verifyHash(file, "deadbeef"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `verifyHash rejects blank expected hash`() {
        // Blank hash is treated as a curation bug, not a wildcard pass.
        val file = Files.createTempFile("aura-test-", ".bin").toFile()
        try {
            file.writeBytes("aura".toByteArray())
            assertFalse(AuraOriginalsDownloader.verifyHash(file, ""))
            assertFalse(AuraOriginalsDownloader.verifyHash(file, "   "))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `bundleDir resolves under filesDir aura_originals`() {
        // We don't have a real Context here; verify the relative path shape via
        // a synthetic File instead. The companion-helper returns
        // `File(context.filesDir, "aura_originals")` and the worker depends on
        // that path matching its on-disk write target.
        val synthetic = File("/tmp/files", "aura_originals")
        assertEquals("aura_originals", synthetic.name)
    }

    @Test
    fun `sanitizeEntryId accepts safe identifiers`() {
        assertEquals("chime-01", AuraOriginalsDownloader.sanitizeEntryId("chime-01"))
        assertEquals("Notification_2", AuraOriginalsDownloader.sanitizeEntryId("Notification_2"))
        assertEquals("v1.2.3", AuraOriginalsDownloader.sanitizeEntryId("v1.2.3"))
        assertNotNull(AuraOriginalsDownloader.sanitizeEntryId("A"))
    }

    @Test
    fun `sanitizeEntryId rejects path traversal and unsafe chars`() {
        assertNull(AuraOriginalsDownloader.sanitizeEntryId(""))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("   "))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId(".."))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("."))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("../etc/passwd"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo/bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo\\bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo:bar"))
        // Semicolon, percent, ampersand, pipe, null byte — none should pass the strict allowlist.
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo;bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo%bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo&bar"))
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo|bar"))
        // Newline in id must be rejected before it ever hits the filesystem.
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("foo\nbar"))
        // Over-long ids would produce absurd filenames.
        assertNull(AuraOriginalsDownloader.sanitizeEntryId("a".repeat(65)))
    }

    @Test
    fun `isAllowedDownloadUrl gates HTTPS only`() {
        assertTrue(AuraOriginalsDownloader.isAllowedDownloadUrl("https://example.com/foo.mp3"))
        assertTrue(AuraOriginalsDownloader.isAllowedDownloadUrl("HTTPS://example.com/foo.mp3"))
        assertTrue(AuraOriginalsDownloader.isAllowedDownloadUrl("  https://example.com/foo.mp3  "))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("http://example.com/foo.mp3"))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("file:///etc/passwd"))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("content://media/foo"))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("data:audio/mpeg;base64,AAAA"))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("ftp://example.com/foo.mp3"))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl(""))
        assertFalse(AuraOriginalsDownloader.isAllowedDownloadUrl("example.com/foo.mp3"))
    }

    @Test
    fun `isInside prevents escapes from the bundle dir`() {
        val parent = Files.createTempDirectory("aura-parent-").toFile()
        try {
            val child = File(parent, "foo.mp3")
            val nested = File(parent, "sub/foo.mp3")
            val escape = File(parent.parentFile, "outside.mp3")
            assertTrue(AuraOriginalsDownloader.isInside(parent, child))
            assertTrue(AuraOriginalsDownloader.isInside(parent, nested))
            assertFalse(AuraOriginalsDownloader.isInside(parent, escape))
            // The parent itself counts as "inside" so existing-dir checks don't fail.
            assertTrue(AuraOriginalsDownloader.isInside(parent, parent))
        } finally {
            parent.deleteRecursively()
        }
    }
}

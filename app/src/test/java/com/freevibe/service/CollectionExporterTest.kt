package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionExporterTest {
    @Test
    fun `extractCollectionShareToken accepts Aura deep links and plain tokens`() {
        assertEquals(
            "abc123_DEF",
            extractCollectionShareToken("aura://collection/import/abc123_DEF"),
        )
        assertEquals(
            "token-123",
            extractCollectionShareToken("Share this: https://aura.app/collections/import/token-123"),
        )
        assertEquals("rawToken_42", extractCollectionShareToken("rawToken_42"))
    }

    @Test
    fun `extractCollectionShareToken rejects unsupported or unsafe input`() {
        assertNull(extractCollectionShareToken("https://example.com/import/token-123"))
        assertNull(extractCollectionShareToken("aura://collection/import/no spaces"))
        assertNull(extractCollectionShareToken("short"))
    }

    @Test
    fun `sanitizeImportedCollectionName trims whitespace and caps length`() {
        val longName = "  Travel   Walls  " + "x".repeat(120)

        val sanitized = sanitizeImportedCollectionName(longName)

        assertEquals(80, sanitized.length)
        assertEquals("Travel Walls", sanitized.take("Travel Walls".length))
    }
}

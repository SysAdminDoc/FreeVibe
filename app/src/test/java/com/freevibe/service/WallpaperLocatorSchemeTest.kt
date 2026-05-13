package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Documents the locator-scheme dispatch contract that [WallpaperApplier.decodeFromLocator]
 * follows. We don't instantiate the full applier here (it needs a Context + OkHttp) — we
 * mirror the dispatch logic in a pure helper so the contract has a regression net.
 *
 * The motivating bug: before applyByLocator existed, [SystemThemeListener.applyStoredWallpaper]
 * forwarded `file://` URIs (AI-generated wallpapers) into [WallpaperApplier.applyFromUrl],
 * which builds an OkHttp `Request` and throws IllegalArgumentException for non-HTTP schemes
 * — silently disabling dark/light auto-switch for any user whose last applied wallpaper
 * was AI-generated.
 */
class WallpaperLocatorSchemeTest {

    enum class Kind { HTTP, FILE, CONTENT, PATH, UNKNOWN }

    private fun classify(locator: String): Kind? {
        if (locator.isBlank()) return null
        return when {
            locator.startsWith("http://", ignoreCase = true) ||
                locator.startsWith("https://", ignoreCase = true) -> Kind.HTTP
            locator.startsWith("content://", ignoreCase = true) -> Kind.CONTENT
            locator.startsWith("file:", ignoreCase = true) -> Kind.FILE
            locator.startsWith("/") -> Kind.PATH
            else -> Kind.UNKNOWN
        }
    }

    @Test fun `https url classified as http`() {
        assertEquals(Kind.HTTP, classify("https://w.wallhaven.cc/full/12/wallhaven-12.jpg"))
    }

    @Test fun `http url classified as http`() {
        assertEquals(Kind.HTTP, classify("http://example.com/wp.png"))
    }

    @Test fun `file URI from File toURI classified as file`() {
        // File.toURI().toString() can yield "file:/data/data/com.freevibe/files/ai_wallpapers/abc.png"
        assertEquals(Kind.FILE, classify("file:/data/data/com.freevibe/files/ai_wallpapers/abc.png"))
    }

    @Test fun `file triple-slash classified as file`() {
        assertEquals(Kind.FILE, classify("file:///sdcard/Pictures/wp.png"))
    }

    @Test fun `content URI classified as content`() {
        assertEquals(Kind.CONTENT, classify("content://media/external/images/media/42"))
    }

    @Test fun `absolute path classified as path`() {
        assertEquals(Kind.PATH, classify("/data/user/0/com.freevibe/files/wp.png"))
    }

    @Test fun `case insensitive scheme matching`() {
        assertEquals(Kind.HTTP, classify("HTTPS://example.com"))
        assertEquals(Kind.FILE, classify("FILE:/foo.png"))
        assertEquals(Kind.CONTENT, classify("CONTENT://provider/x"))
    }

    @Test fun `unknown scheme returns unknown not http`() {
        // The regression: `ftp://`, `data:`, etc. must NOT silently route to HTTP — which
        // would then crash on OkHttp Request building.
        assertEquals(Kind.UNKNOWN, classify("ftp://server/file"))
        assertEquals(Kind.UNKNOWN, classify("data:image/png;base64,iVBORw0KG"))
        assertEquals(Kind.UNKNOWN, classify("about:blank"))
    }

    @Test fun `blank locator returns null`() {
        assertNull(classify(""))
        assertNull(classify("   "))
    }

    @Test fun `wallpaper id format split preserves urls containing pipe`() {
        // Wallpaper ID format: "source|id|url" — but `url` may itself contain `|` characters
        // (rare, but observed in some sources' generated thumbnail URLs). Split with limit=3
        // so the URL stays intact.
        val raw = "WALLHAVEN|abc123|https://example.com/path?x=1|2&y=3"
        val parts = raw.split("|", limit = 3)
        assertEquals(3, parts.size)
        assertEquals("WALLHAVEN", parts[0])
        assertEquals("abc123", parts[1])
        assertEquals("https://example.com/path?x=1|2&y=3", parts[2])
    }
}

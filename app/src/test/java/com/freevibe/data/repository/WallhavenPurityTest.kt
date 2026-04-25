package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WallhavenPurityTest {

    @Test
    fun `no api key always coerces to SFW only`() {
        // Wallhaven rejects sketchy/NSFW requests without auth. Coerce so the
        // user still sees wallpapers instead of an empty grid.
        assertEquals("100", computeWallhavenPurity(hasApiKey = false, sketchyOptIn = false, nsfwOptIn = false))
        assertEquals("100", computeWallhavenPurity(hasApiKey = false, sketchyOptIn = true, nsfwOptIn = false))
        assertEquals("100", computeWallhavenPurity(hasApiKey = false, sketchyOptIn = false, nsfwOptIn = true))
        assertEquals("100", computeWallhavenPurity(hasApiKey = false, sketchyOptIn = true, nsfwOptIn = true))
    }

    @Test
    fun `api key + nothing toggled = SFW`() {
        assertEquals("100", computeWallhavenPurity(hasApiKey = true, sketchyOptIn = false, nsfwOptIn = false))
    }

    @Test
    fun `api key + sketchy only = SFW + sketchy`() {
        assertEquals("110", computeWallhavenPurity(hasApiKey = true, sketchyOptIn = true, nsfwOptIn = false))
    }

    @Test
    fun `api key + nsfw without sketchy = everything (nsfw implies sketchy)`() {
        // Reading the rule literally: NSFW wins. Wallhaven's "111" includes sketchy
        // anyway, and a user opting in to NSFW expects the full feed.
        assertEquals("111", computeWallhavenPurity(hasApiKey = true, sketchyOptIn = false, nsfwOptIn = true))
    }

    @Test
    fun `api key + both toggles = everything`() {
        assertEquals("111", computeWallhavenPurity(hasApiKey = true, sketchyOptIn = true, nsfwOptIn = true))
    }
}

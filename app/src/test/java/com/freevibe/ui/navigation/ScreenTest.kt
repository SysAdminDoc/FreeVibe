package com.freevibe.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

    @Test
    fun `bottomNavItems exposes all expected routes`() {
        assertEquals(
            listOf("wallpapers", "video_wallpapers", "sounds", "favorites", "settings"),
            Screen.bottomNavItems.map { it.route },
        )
    }
}

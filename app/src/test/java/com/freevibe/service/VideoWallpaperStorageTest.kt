package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoWallpaperStorageTest {

    @Test
    fun `gif selections are detected by mime type and file name`() {
        assertTrue(isGifVideoWallpaperSelection("image/gif", "wallpaper.bin"))
        assertTrue(isGifVideoWallpaperSelection(null, "wallpaper.GIF"))
        assertFalse(isGifVideoWallpaperSelection("video/mp4", "wallpaper.mp4"))
    }

    @Test
    fun `video wallpaper extension prefers known video formats`() {
        assertEquals("gif", resolveVideoWallpaperExtension("image/gif", "clip.bin"))
        assertEquals("webm", resolveVideoWallpaperExtension("video/webm", "clip.mp4"))
        assertEquals("3gp", resolveVideoWallpaperExtension(null, "clip.3gp"))
        assertEquals("mov", resolveVideoWallpaperExtension("video/mp4", "clip.mov"))
        assertEquals("mkv", resolveVideoWallpaperExtension("video/x-matroska", "clip.mp4"))
        assertEquals("mp4", resolveVideoWallpaperExtension("video/mp4", "clip.bin"))
    }

    @Test
    fun `video wallpaper picker accepts video and gif mime types`() {
        assertEquals(listOf("video/*", "image/gif"), videoWallpaperMimeTypes().toList())
    }
}

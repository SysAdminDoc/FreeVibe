package com.freevibe.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadManagerPathTest {

    @Test
    fun `isAuraManagedRelativePath accepts Aura media directories only`() {
        assertTrue(isAuraManagedRelativePath("Pictures/Aura"))
        assertTrue(isAuraManagedRelativePath("Ringtones/Aura/"))
        assertFalse(isAuraManagedRelativePath("Downloads/Aura"))
        assertFalse(isAuraManagedRelativePath("Pictures/Elsewhere"))
    }

    @Test
    fun `isAuraManagedAbsolutePath requires Aura folder inside allowed media roots`() {
        assertTrue(isAuraManagedAbsolutePath("/storage/emulated/0/Pictures/Aura/wallpaper.jpg"))
        assertTrue(isAuraManagedAbsolutePath("C:/Users/test/Music/Aura/clip.mp3"))
        assertFalse(isAuraManagedAbsolutePath("/storage/emulated/0/Download/Aura/file.jpg"))
        assertFalse(isAuraManagedAbsolutePath("/storage/emulated/0/Pictures/Shared/file.jpg"))
    }
}

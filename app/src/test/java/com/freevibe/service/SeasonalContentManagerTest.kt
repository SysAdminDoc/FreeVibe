package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeasonalContentManagerTest {

    private val manager = SeasonalContentManager()

    // -- Holiday (December) --

    @Test
    fun `december 1 returns holiday theme`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 12, 1))
        assertNotNull(theme)
        assertEquals("Holiday", theme!!.label)
    }

    @Test
    fun `december 25 returns holiday theme`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 12, 25))
        assertNotNull(theme)
        assertEquals("Holiday", theme!!.label)
    }

    @Test
    fun `december 31 returns holiday theme`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 12, 31))
        assertNotNull(theme)
        assertEquals("Holiday", theme!!.label)
    }

    // -- Halloween (Oct 15–31) --

    @Test
    fun `october 15 returns halloween theme`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 10, 15))
        assertNotNull(theme)
        assertEquals("Halloween", theme!!.label)
    }

    @Test
    fun `october 30 returns halloween theme`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 10, 30))
        assertNotNull(theme)
        assertEquals("Halloween", theme!!.label)
    }

    @Test
    fun `october 14 returns null (before halloween window)`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 10, 14))
        assertNull(theme)
    }

    // -- New Year (Jan 1–3) --

    @Test
    fun `january 1 returns new year theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 1, 1))
        assertNotNull(theme)
        assertEquals("New Year", theme!!.label)
    }

    @Test
    fun `january 3 returns new year theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 1, 3))
        assertNotNull(theme)
        assertEquals("New Year", theme!!.label)
    }

    @Test
    fun `january 4 returns null (new year window ended)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 1, 4))
        assertNull(theme)
    }

    // -- Valentine's Day (Feb 10–14) --

    @Test
    fun `february 10 returns valentine theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 2, 10))
        assertNotNull(theme)
        assertEquals("Valentine", theme!!.label)
    }

    @Test
    fun `february 14 returns valentine theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 2, 14))
        assertNotNull(theme)
        assertEquals("Valentine", theme!!.label)
    }

    @Test
    fun `february 9 returns null (before valentine window)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 2, 9))
        assertNull(theme)
    }

    // -- Summer (Jun 21–Sep 1) --

    @Test
    fun `june 21 returns summer theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 6, 21))
        assertNotNull(theme)
        assertEquals("Summer", theme!!.label)
    }

    @Test
    fun `august 15 returns summer theme`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 8, 15))
        assertNotNull(theme)
        assertEquals("Summer", theme!!.label)
    }

    @Test
    fun `september 1 returns summer theme (inclusive end)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 9, 1))
        assertNotNull(theme)
        assertEquals("Summer", theme!!.label)
    }

    @Test
    fun `june 20 returns null (before summer window)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 6, 20))
        assertNull(theme)
    }

    // -- Off-season --

    @Test
    fun `march returns null (off-season)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 3, 15))
        assertNull(theme)
    }

    @Test
    fun `april returns null (off-season)`() {
        val theme = manager.currentTheme(LocalDate.of(2025, 4, 1))
        assertNull(theme)
    }

    @Test
    fun `november before october 15 returns null`() {
        val theme = manager.currentTheme(LocalDate.of(2024, 11, 1))
        assertNull(theme)
    }
}

package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for SolarCalculator. We focus on:
 *   1. Sunrise/sunset are within a sane window for known coordinates.
 *   2. Tint offsets land in the right bucket at each phase boundary.
 *   3. The DST regression — earlier revisions used `TimeZone.getDefault().rawOffset`
 *      which silently lost an hour during summer. We can't easily mock
 *      `TimeZone.getDefault()` in pure JVM, so we pass an explicit `utcOffsetHours`
 *      and assert the math is internally consistent under both DST and standard time.
 */
class SolarCalculatorTest {

    @Test
    fun `sunrise sunset reasonable for equator on equinox`() {
        // March 20 (day-of-year 79) is near vernal equinox: ~12h day everywhere.
        val times = SolarCalculator.sunTimes(
            lat = 0.0,
            lon = 0.0,
            dayOfYear = 79,
            utcOffsetHours = 0.0,
        )
        // Day length should be very close to 12h at the equator on equinox.
        val dayLength = times.sunsetHour - times.sunriseHour
        assertTrue(
            "Equator equinox day length $dayLength should be ~12h",
            dayLength in 11.8..12.2,
        )
    }

    @Test
    fun `sunrise sunset reasonable for high latitude winter`() {
        // Reykjavik (~64°N) on Dec 21 (DOY 355): very short day.
        val times = SolarCalculator.sunTimes(
            lat = 64.0,
            lon = -22.0,
            dayOfYear = 355,
            utcOffsetHours = 0.0,
        )
        val dayLength = times.sunsetHour - times.sunriseHour
        assertTrue(
            "Reykjavik winter day length $dayLength should be <6h",
            dayLength in 0.0..6.0,
        )
    }

    @Test
    fun `dst regression — offset of plus 1 hour shifts sunrise by 1 hour`() {
        // Same location and day, different UTC offset (e.g. PST -8 vs PDT -7).
        // This is the exact bug the SolarCalculator DST fix addresses — pre-fix
        // the default arg used rawOffset which would give the PST answer year-round.
        val winter = SolarCalculator.sunTimes(
            lat = 37.7749,
            lon = -122.4194,
            dayOfYear = 15,    // mid-January (PST in US west coast)
            utcOffsetHours = -8.0,
        )
        val summer = SolarCalculator.sunTimes(
            lat = 37.7749,
            lon = -122.4194,
            dayOfYear = 196,   // mid-July (PDT in US west coast)
            utcOffsetHours = -7.0,
        )
        // Sunrise in summer (DOY 196) at SF should be earlier in local time and
        // sunset later — both because of the season AND the DST offset shift.
        // What we're really checking: a 1h offset bump produces a corresponding
        // 1h ish shift in the local sunrise/sunset clock readings.
        val winterMidday = (winter.sunriseHour + winter.sunsetHour) / 2.0
        val summerMidday = (summer.sunriseHour + summer.sunsetHour) / 2.0
        assertEquals(
            "Local solar-noon should shift ~1h between standard and DST",
            1.0,
            summerMidday - winterMidday,
            0.3, // allow for orbital eccentricity / equation of time variation
        )
    }

    @Test
    fun `tint offsets neutral around solar noon`() {
        val sunTimes = SolarCalculator.SunTimes(sunriseHour = 6.0, sunsetHour = 18.0)
        val offsets = SolarCalculator.tintOffsets(currentHour = 12.0, sunTimes = sunTimes, intensity = 1f)
        assertEquals(0f, offsets[0], 0.001f)
        assertEquals(0f, offsets[1], 0.001f)
        assertEquals(0f, offsets[2], 0.001f)
    }

    @Test
    fun `tint offsets warm around golden hour`() {
        val sunTimes = SolarCalculator.SunTimes(sunriseHour = 6.0, sunsetHour = 18.0)
        // 17.0 falls in the "golden hour: rich amber" branch (set-1.5..<set).
        val offsets = SolarCalculator.tintOffsets(currentHour = 17.0, sunTimes = sunTimes, intensity = 1f)
        assertTrue("Red should be warmer at golden hour: got ${offsets[0]}", offsets[0] > 10f)
        assertTrue("Blue should be cooler at golden hour: got ${offsets[2]}", offsets[2] < -10f)
    }

    @Test
    fun `tint offsets cool at deep night`() {
        val sunTimes = SolarCalculator.SunTimes(sunriseHour = 6.0, sunsetHour = 18.0)
        val offsets = SolarCalculator.tintOffsets(currentHour = 2.0, sunTimes = sunTimes, intensity = 1f)
        assertTrue("Blue should rise at deep night: got ${offsets[2]}", offsets[2] > 10f)
    }

    @Test
    fun `intensity scales tint offsets linearly`() {
        val sunTimes = SolarCalculator.SunTimes(sunriseHour = 6.0, sunsetHour = 18.0)
        val full = SolarCalculator.tintOffsets(17.0, sunTimes, intensity = 1f)
        val half = SolarCalculator.tintOffsets(17.0, sunTimes, intensity = 0.5f)
        assertEquals(full[0] / 2f, half[0], 0.01f)
        assertEquals(full[1] / 2f, half[1], 0.01f)
        assertEquals(full[2] / 2f, half[2], 0.01f)
    }

    @Test
    fun `polar day clamps to 24 hours of daylight`() {
        // North pole on summer solstice (DOY 172) — sun never sets. cosHa goes below -1
        // and is clamped to -1, giving acos(-1) = π → 720 minutes = 12h on each side
        // of solar noon → 24h total day length. The clamp must not crash or NaN.
        val times = SolarCalculator.sunTimes(
            lat = 89.9,
            lon = 0.0,
            dayOfYear = 172,
            utcOffsetHours = 0.0,
        )
        assertEquals(
            "Polar day should produce a 24h sunrise-to-sunset window",
            24.0,
            times.sunsetHour - times.sunriseHour,
            0.5,
        )
        assertTrue("Sunrise must be finite", times.sunriseHour.isFinite())
        assertTrue("Sunset must be finite", times.sunsetHour.isFinite())
    }

    @Test
    fun `polar night clamps to zero daylight`() {
        // North pole on winter solstice (DOY 355) — sun never rises. cosHa goes above 1
        // and is clamped to 1, giving acos(1) = 0 → zero-width sunrise/sunset.
        val times = SolarCalculator.sunTimes(
            lat = 89.9,
            lon = 0.0,
            dayOfYear = 355,
            utcOffsetHours = 0.0,
        )
        assertEquals(
            "Polar night should produce zero-width sunrise/sunset",
            0.0,
            times.sunsetHour - times.sunriseHour,
            0.001,
        )
    }

    @Test
    fun `pre-fix rawOffset bug would not match getOffset for DST regions`() {
        // Documentation test: this asserts the *shape* of the bug. Without DST
        // awareness, the default-arg branch (using rawOffset) would lock to standard
        // time year-round. We can't easily mock TimeZone.getDefault() but we can
        // demonstrate that the function reflects the explicit offset we pass.
        val pst = SolarCalculator.sunTimes(37.77, -122.41, 196, utcOffsetHours = -8.0)
        val pdt = SolarCalculator.sunTimes(37.77, -122.41, 196, utcOffsetHours = -7.0)
        assertNotEquals(
            "DST-aware offsets must produce different local sunrise readings",
            pst.sunriseHour,
            pdt.sunriseHour,
        )
    }
}

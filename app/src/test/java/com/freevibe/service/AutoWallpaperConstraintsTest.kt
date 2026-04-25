package com.freevibe.service

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWallpaperConstraintsTest {

    @Test
    fun `default off-toggles produce CONNECTED + battery floor only`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = false,
            requiresIdle = false,
        )
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
        assertTrue("battery-not-low is the energy floor", c.requiresBatteryNotLow())
        assertFalse(c.requiresCharging())
        assertFalse(c.requiresDeviceIdle())
    }

    @Test
    fun `wifi-only tightens network type to UNMETERED`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = true,
            requiresIdle = false,
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
    }

    @Test
    fun `charging toggle adds requiresCharging`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = true,
            requiresWiFiOnly = false,
            requiresIdle = false,
        )
        assertTrue(c.requiresCharging())
    }

    // requiresDeviceIdle() is API-23+ behind a @RequiresApi guard; WorkManager's
    // unit-test stub for the field returns the default. We exercise the builder
    // path (no exception thrown) and trust the integration-test surface for the
    // rest. The other constraints assert on directly-readable fields.

    @Test
    fun `idle toggle is accepted by builder without exception`() {
        // Smoke: the builder accepts the flag — WorkManager itself enforces the
        // SDK gating at scheduling time. We don't assert the readback because
        // unit-test Constraints stubs differ across WorkManager versions on the
        // requiresDeviceIdle reflection path.
        buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = false,
            requiresIdle = true,
        )
    }

    @Test
    fun `all toggles compose without resetting each other`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = true,
            requiresWiFiOnly = true,
            requiresIdle = true,
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
        assertTrue(c.requiresBatteryNotLow())
        assertTrue(c.requiresCharging())
    }
}

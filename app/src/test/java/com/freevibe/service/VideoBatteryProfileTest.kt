package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBatteryProfileTest {

    @Test
    fun `sanitizeVideoFpsLimit keeps supported steps`() {
        assertEquals(15, sanitizeVideoFpsLimit(0))
        assertEquals(15, sanitizeVideoFpsLimit(15))
        assertEquals(30, sanitizeVideoFpsLimit(24))
        assertEquals(30, sanitizeVideoFpsLimit(30))
        assertEquals(60, sanitizeVideoFpsLimit(61))
    }

    @Test
    fun `low battery saver only activates below fifteen percent while unplugged`() {
        assertTrue(shouldUseVideoBatterySaver(batteryPercent = 14, isCharging = false, autoSaverEnabled = true))
        assertFalse(shouldUseVideoBatterySaver(batteryPercent = 15, isCharging = false, autoSaverEnabled = true))
        assertFalse(shouldUseVideoBatterySaver(batteryPercent = 10, isCharging = true, autoSaverEnabled = true))
        assertFalse(shouldUseVideoBatterySaver(batteryPercent = 10, isCharging = false, autoSaverEnabled = false))
        assertFalse(shouldUseVideoBatterySaver(batteryPercent = null, isCharging = false, autoSaverEnabled = true))
    }

    @Test
    fun `effectiveVideoFpsLimit caps only while saver is active`() {
        assertEquals(60, effectiveVideoFpsLimit(requestedFps = 60, lowBatterySaverActive = false))
        assertEquals(15, effectiveVideoFpsLimit(requestedFps = 60, lowBatterySaverActive = true))
        assertEquals(15, effectiveVideoFpsLimit(requestedFps = 15, lowBatterySaverActive = true))
    }

    @Test
    fun `videoBatteryImpactSummary names active tradeoff`() {
        assertEquals(
            "Balanced - 30 FPS target",
            videoBatteryImpactSummary(
                requestedFps = 30,
                effectiveFps = 30,
                fpsOverlayEnabled = false,
                lowBatterySaverActive = false,
            ),
        )
        assertEquals(
            "Low battery saver - capped at 15 FPS until battery recovers",
            videoBatteryImpactSummary(
                requestedFps = 60,
                effectiveFps = 15,
                fpsOverlayEnabled = false,
                lowBatterySaverActive = true,
            ),
        )
    }
}

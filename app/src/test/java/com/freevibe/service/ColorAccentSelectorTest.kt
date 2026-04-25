package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorAccentSelectorTest {

    /** Pure-JVM HSL stand-in. Encodes (sat * 100, light * 100, sentinel) in
     *  the ARGB channels so each test can construct a synthetic color whose
     *  HSL is whatever the test scenario needs.
     *
     *  Encoding: 0xAARRGGBB where R = saturation%, G = lightness%, B = id.
     *  The id channel lets assertions distinguish two colors with the same
     *  HSL.
     */
    private fun synth(sat: Float, light: Float, id: Int): Int {
        val s = (sat * 100f).toInt().coerceIn(0, 100)
        val l = (light * 100f).toInt().coerceIn(0, 100)
        return (0xFF shl 24) or (s shl 16) or (l shl 8) or (id and 0xFF)
    }

    private val hslOf = ColorAccentSelector.HslProvider { color ->
        if (color == 0) {
            floatArrayOf(0f, 0f, 0f)
        } else {
            val s = ((color shr 16) and 0xFF) / 100f
            val l = ((color shr 8) and 0xFF) / 100f
            floatArrayOf(0f, s, l)
        }
    }

    @Test
    fun `dominant kept when it passes quality gate`() {
        val good = synth(sat = 0.6f, light = 0.5f, id = 1)
        val result = ColorAccentSelector.selectAccent(
            dominant = good,
            vibrantDark = synth(0.9f, 0.3f, 2),
            vibrant = 0, vibrantLight = 0,
            mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(good, result)
    }

    @Test
    fun `low-saturation dominant falls through to vibrantDark`() {
        val grayDominant = synth(sat = 0.05f, light = 0.5f, id = 1)
        val vibrantDark = synth(sat = 0.7f, light = 0.4f, id = 2)
        val result = ColorAccentSelector.selectAccent(
            dominant = grayDominant,
            vibrantDark = vibrantDark,
            vibrant = synth(0.7f, 0.5f, 3),
            vibrantLight = 0, mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(vibrantDark, result)
    }

    @Test
    fun `too-bright dominant falls through`() {
        val tooBright = synth(sat = 0.5f, light = 0.95f, id = 1)
        val vibrant = synth(sat = 0.7f, light = 0.5f, id = 2)
        val result = ColorAccentSelector.selectAccent(
            dominant = tooBright,
            vibrantDark = 0,
            vibrant = vibrant,
            vibrantLight = 0, mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(vibrant, result)
    }

    @Test
    fun `too-dark dominant falls through`() {
        val tooDark = synth(sat = 0.5f, light = 0.10f, id = 1)
        val mutedDark = synth(sat = 0.3f, light = 0.4f, id = 2)
        val result = ColorAccentSelector.selectAccent(
            dominant = tooDark,
            vibrantDark = 0, vibrant = 0, vibrantLight = 0,
            mutedDark = mutedDark, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(mutedDark, result)
    }

    @Test
    fun `ladder order vibrantDark beats vibrant beats muted`() {
        val vibrantDark = synth(0.6f, 0.4f, 1)
        val vibrant = synth(0.6f, 0.5f, 2)
        val muted = synth(0.3f, 0.5f, 3)
        val result = ColorAccentSelector.selectAccent(
            dominant = synth(0.05f, 0.5f, 99), // gray, fails
            vibrantDark = vibrantDark,
            vibrant = vibrant,
            vibrantLight = 0,
            mutedDark = 0,
            muted = muted,
            mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(vibrantDark, result)
    }

    @Test
    fun `relaxes gate when no swatch passes — picks first non-zero from ladder`() {
        // Every non-zero swatch is gray (fails saturation gate).
        val grayDominant = synth(0.05f, 0.5f, 1)
        val grayVibrantLight = synth(0.05f, 0.6f, 2)
        val result = ColorAccentSelector.selectAccent(
            dominant = grayDominant,
            vibrantDark = 0,
            vibrant = 0,
            vibrantLight = grayVibrantLight,
            mutedDark = 0,
            muted = 0,
            mutedLight = 0,
            hslOf = hslOf,
        )
        // None pass quality gate → relaxed pass picks first non-zero in ladder
        // order. vibrantLight is the only non-zero entry and beats falling
        // through to dominant.
        assertEquals(grayVibrantLight, result)
    }

    @Test
    fun `final fallback to dominant when entire ladder is zero`() {
        val grayDominant = synth(0.05f, 0.5f, 1)
        val result = ColorAccentSelector.selectAccent(
            dominant = grayDominant,
            vibrantDark = 0, vibrant = 0, vibrantLight = 0,
            mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(grayDominant, result)
    }

    @Test
    fun `zero dominant + zero ladder still returns 0`() {
        val result = ColorAccentSelector.selectAccent(
            dominant = 0,
            vibrantDark = 0, vibrant = 0, vibrantLight = 0,
            mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(0, result)
    }

    @Test
    fun `zero dominant with one good swatch picks the swatch`() {
        val good = synth(0.7f, 0.5f, 1)
        val result = ColorAccentSelector.selectAccent(
            dominant = 0,
            vibrantDark = 0,
            vibrant = good,
            vibrantLight = 0, mutedDark = 0, muted = 0, mutedLight = 0,
            hslOf = hslOf,
        )
        assertEquals(good, result)
    }
}

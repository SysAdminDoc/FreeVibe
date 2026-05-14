package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchEffectRendererTest {

    @Test
    fun `parseTouchEffectStrength accepts known values case-insensitively`() {
        assertEquals(TouchEffectRenderer.Strength.SUBTLE, parseTouchEffectStrength(" subtle "))
        assertEquals(TouchEffectRenderer.Strength.STRONG, parseTouchEffectStrength("STRONG"))
    }

    @Test
    fun `parseTouchEffectStrength defaults unknown values to off`() {
        assertEquals(TouchEffectRenderer.Strength.OFF, parseTouchEffectStrength(null))
        assertEquals(TouchEffectRenderer.Strength.OFF, parseTouchEffectStrength("heavy"))
    }
}

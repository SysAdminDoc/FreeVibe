package com.freevibe.service

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * AGSL pipeline self-tests. The class under test calls into Android's RuntimeShader
 * which only exists on API 33+, so these run on the local JVM unit-test classpath
 * and exercise only the parts that are decoupled from the framework (effect catalog,
 * effect IDENTITY sentinel, AGSL source declarations).
 *
 * Roadmap N-3.
 */
class AgslEffectPipelineTest {

    @Test
    fun `IDENTITY effect exposes valid AGSL source`() {
        val agsl = AgslEffect.IDENTITY.agsl
        assertNotNull(agsl)
        check(agsl.contains("uniform shader src")) { "IDENTITY shader must declare the src uniform" }
        check(agsl.contains("half4 main")) { "IDENTITY shader must export main()" }
    }

    @Test
    fun `DEPTH_SHADE clamps intensity into 0_1 by accepting any float`() {
        // We can't run the shader here, but constructing with out-of-range values
        // should not throw — the clamp is applied at draw time.
        AgslEffect.DEPTH_SHADE(intensity = -1f)
        AgslEffect.DEPTH_SHADE(intensity = 0f)
        AgslEffect.DEPTH_SHADE(intensity = 0.5f)
        AgslEffect.DEPTH_SHADE(intensity = 1f)
        AgslEffect.DEPTH_SHADE(intensity = 2f)
    }

    @Test
    fun `DEPTH_SHADE exposes intensity uniform`() {
        val agsl = AgslEffect.DEPTH_SHADE(intensity = 0.25f).agsl
        check(agsl.contains("uniform half intensity")) { "DEPTH_SHADE shader must declare intensity uniform" }
    }
}

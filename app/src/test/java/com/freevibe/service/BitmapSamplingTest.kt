package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {

    @Test
    fun `calculateInSampleSize keeps exact fits at full resolution`() {
        assertEquals(1, BitmapSampling.calculateInSampleSize(1080, 1920, 1080, 1920))
    }

    @Test
    fun `calculateInSampleSize uses powers of two for oversized images`() {
        assertEquals(4, BitmapSampling.calculateInSampleSize(4320, 7680, 1080, 1920))
    }

    @Test
    fun `calculateInSampleSize handles smaller square targets`() {
        assertEquals(8, BitmapSampling.calculateInSampleSize(4096, 4096, 512, 512))
    }
}

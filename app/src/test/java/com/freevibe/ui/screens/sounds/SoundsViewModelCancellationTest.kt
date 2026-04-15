package com.freevibe.ui.screens.sounds

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Test

class SoundsViewModelCancellationTest {

    @Test
    fun `rethrowIfCancelled rethrows cancellation exceptions`() {
        val expected = CancellationException("cancelled")

        try {
            expected.rethrowIfCancelled()
            throw AssertionError("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `rethrowIfCancelled ignores ordinary failures`() {
        IllegalStateException("boom").rethrowIfCancelled()
    }
}

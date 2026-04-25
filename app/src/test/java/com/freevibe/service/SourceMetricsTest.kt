package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SourceMetricsTest {

    @Test
    fun `unrecorded source returns null snapshot`() {
        val m = SourceMetrics()
        assertNull(m.snapshot("freesound"))
    }

    @Test
    fun `recordSuccess populates totals + ratio`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", latencyMs = 120L)
        m.recordSuccess("freesound", latencyMs = 240L)
        val s = m.snapshot("freesound")!!
        assertEquals(2L, s.totalRequests)
        assertEquals(2L, s.successCount)
        assertEquals(0L, s.failureCount)
        assertEquals(1.0, s.successRatio, 0.001)
        assertNotNull(s.p50Ms)
        assertNotNull(s.p95Ms)
    }

    @Test
    fun `recordFailure sets last error and bumps failure count`() {
        val m = SourceMetrics()
        m.recordFailure("wallhaven", IOException("timeout"))
        val s = m.snapshot("wallhaven")!!
        assertEquals(1L, s.totalRequests)
        assertEquals(0L, s.successCount)
        assertEquals(1L, s.failureCount)
        assertEquals("IOException", s.lastErrorClass)
        assertEquals("timeout", s.lastErrorMessage)
        assertEquals(0.0, s.successRatio, 0.001)
    }

    @Test
    fun `cancellation is excluded from failure stats`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", 50L)
        m.recordFailure("freesound", kotlinx.coroutines.CancellationException("user backed out"))
        val s = m.snapshot("freesound")!!
        // Cancellation must NOT show up — it's structured-concurrency teardown,
        // not a "source failed" signal.
        assertEquals(1L, s.totalRequests)
        assertEquals(1L, s.successCount)
        assertEquals(0L, s.failureCount)
        assertNull(s.lastErrorClass)
    }

    @Test
    fun `latency ring buffer caps at 50 samples`() {
        val m = SourceMetrics()
        repeat(75) { m.recordSuccess("wallhaven", it.toLong()) }
        val s = m.snapshot("wallhaven")!!
        assertEquals(75L, s.totalRequests)
        // Newest 50 retained — the oldest 25 (latencies 0..24) evicted.
        assertEquals(50, s.recentLatenciesMs.size)
        assertTrue("oldest retained sample is the 26th call", s.recentLatenciesMs.first() >= 25L)
    }

    @Test
    fun `negative latency clamped to zero`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", latencyMs = -50L)
        val s = m.snapshot("freesound")!!
        assertEquals(listOf(0L), s.recentLatenciesMs)
    }

    @Test
    fun `snapshotAll orders by failure count desc then total desc`() {
        val m = SourceMetrics()
        m.recordSuccess("wallhaven", 100L)
        m.recordSuccess("wallhaven", 100L)
        m.recordFailure("freesound", IOException("x"))
        m.recordFailure("freesound", IOException("y"))
        m.recordSuccess("reddit", 100L)

        val all = m.snapshotAll().map { it.source }
        // freesound (2 failures) first; wallhaven (2 reqs, 0 failures) before reddit (1 req).
        assertEquals(listOf("freesound", "wallhaven", "reddit"), all)
    }

    @Test
    fun `reset clears everything`() {
        val m = SourceMetrics()
        m.recordSuccess("wallhaven", 100L)
        m.recordFailure("freesound", IOException("x"))
        m.reset()
        assertNull(m.snapshot("wallhaven"))
        assertNull(m.snapshot("freesound"))
        assertTrue(m.snapshotAll().isEmpty())
    }

    @Test
    fun `blank source name is silently ignored`() {
        val m = SourceMetrics()
        m.recordSuccess("", 100L)
        m.recordFailure("   ", IOException("x"))
        assertTrue(m.snapshotAll().isEmpty())
    }
}

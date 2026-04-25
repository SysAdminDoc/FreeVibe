package com.freevibe.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory per-source health telemetry.
 *
 * Diagnostic surface for "why is X tab loading slowly?" — Settings screen
 * exposes a snapshot of recent request counts, success ratios, last error,
 * and latency p50/p95 per content source. Resets on process death; not
 * persisted (the failure modes we care about are within-session).
 *
 * Thread-safe via ConcurrentHashMap + AtomicLong; hooks are fire-and-forget
 * so they can't block or fail their caller. Designed to be wrapped around
 * existing repository calls without changing return types.
 */
@Singleton
class SourceMetrics @Inject constructor() {

    /** Snapshot of one source's stats, taken at read time. */
    data class SourceStats(
        val source: String,
        val totalRequests: Long,
        val successCount: Long,
        val failureCount: Long,
        val lastErrorClass: String?,
        val lastErrorMessage: String?,
        val lastSuccessAtMs: Long,
        val lastFailureAtMs: Long,
        val recentLatenciesMs: List<Long>,
    ) {
        /** Successes / total. Returns 1.0 when there have been no requests. */
        val successRatio: Double = if (totalRequests == 0L) 1.0
            else successCount.toDouble() / totalRequests.toDouble()

        /** Median latency over the rolling window, or null if empty. */
        val p50Ms: Long? = recentLatenciesMs.takeIf { it.isNotEmpty() }
            ?.sorted()?.let { it[it.size / 2] }

        /** 95th-percentile latency over the rolling window, or null if empty. */
        val p95Ms: Long? = recentLatenciesMs.takeIf { it.isNotEmpty() }
            ?.sorted()?.let { sorted ->
                val idx = ((sorted.size - 1) * 0.95).toInt().coerceAtLeast(0)
                sorted[idx]
            }
    }

    private class MutableEntry {
        val total = AtomicLong(0L)
        val success = AtomicLong(0L)
        val failure = AtomicLong(0L)
        @Volatile var lastErrorClass: String? = null
        @Volatile var lastErrorMessage: String? = null
        @Volatile var lastSuccessAtMs: Long = 0L
        @Volatile var lastFailureAtMs: Long = 0L
        // Bounded ring buffer for latency samples — keeps memory bounded.
        val latencies = java.util.ArrayDeque<Long>()
        val latencyLock = Any()
    }

    private val entries = ConcurrentHashMap<String, MutableEntry>()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun <T> measure(source: String, block: suspend () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            val result = block()
            recordSuccess(source, System.currentTimeMillis() - startedAt)
            result
        } catch (e: Throwable) {
            recordFailure(source, e)
            throw e
        }
    }

    /**
     * Record a successful call. [latencyMs] is the elapsed wall-clock time;
     * negative values are clamped to 0 to keep percentile math sane.
     */
    fun recordSuccess(source: String, latencyMs: Long) {
        if (source.isBlank()) return
        val e = entries.computeIfAbsent(source) { MutableEntry() }
        e.total.incrementAndGet()
        e.success.incrementAndGet()
        e.lastSuccessAtMs = System.currentTimeMillis()
        synchronized(e.latencyLock) {
            e.latencies.addLast(latencyMs.coerceAtLeast(0L))
            while (e.latencies.size > MAX_LATENCY_SAMPLES) e.latencies.pollFirst()
        }
        _version.update { it + 1 }
    }

    fun recordFailure(source: String, error: Throwable) {
        if (source.isBlank()) return
        // Cancellation is structured-concurrency teardown, not a "source failure".
        // Counting it would conflate the user backing out with the API erroring.
        if (error is kotlinx.coroutines.CancellationException) return
        val e = entries.computeIfAbsent(source) { MutableEntry() }
        e.total.incrementAndGet()
        e.failure.incrementAndGet()
        e.lastErrorClass = error.javaClass.simpleName
        e.lastErrorMessage = error.message?.take(200)
        e.lastFailureAtMs = System.currentTimeMillis()
        _version.update { it + 1 }
    }

    /** Atomic-ish snapshot of one source. Returns null if never seen. */
    fun snapshot(source: String): SourceStats? {
        val e = entries[source] ?: return null
        val latencies = synchronized(e.latencyLock) { e.latencies.toList() }
        return SourceStats(
            source = source,
            totalRequests = e.total.get(),
            successCount = e.success.get(),
            failureCount = e.failure.get(),
            lastErrorClass = e.lastErrorClass,
            lastErrorMessage = e.lastErrorMessage,
            lastSuccessAtMs = e.lastSuccessAtMs,
            lastFailureAtMs = e.lastFailureAtMs,
            recentLatenciesMs = latencies,
        )
    }

    /** Snapshot of every recorded source, sorted by most-failed first. */
    fun snapshotAll(): List<SourceStats> = entries.keys
        .mapNotNull { snapshot(it) }
        .sortedWith(
            compareByDescending<SourceStats> { it.failureCount }
                .thenByDescending { it.totalRequests },
        )

    /** Forget all recorded stats (developer-facing reset). */
    fun reset() {
        entries.clear()
        _version.update { it + 1 }
    }

    private companion object {
        const val MAX_LATENCY_SAMPLES = 50
    }
}

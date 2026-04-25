package com.freevibe.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Bounded backoff for HTTP 429 responses, scoped to specific hosts.
 *
 * Freesound v2 enforces a 60 req/min token-bucket per IP and emits Retry-After
 * on 429. Without this interceptor those failures bubble up as generic
 * HttpException and the Sounds tab silently goes blank for ~60s. With this
 * interceptor a transient burst recovers without surfacing an error.
 *
 * Scope is intentionally narrow: only requests whose URL host ends with one of
 * [hostSuffixes] are retried. We don't want to introduce surprise latency on
 * Wallhaven, Reddit, etc.
 *
 * @param hostSuffixes lowercase host suffixes that opt into 429-aware retries
 *   (e.g. "freesound.org"). Match is "host == suffix || host endsWith ".$suffix"".
 * @param maxRetries upper bound on retries for a single request (default 2).
 *   Total wall-clock latency is bounded by maxRetries * retryCeilingMs.
 * @param defaultBackoffMs delay used when the response omits Retry-After.
 * @param retryCeilingMs upper bound on any single retry wait — clamps a
 *   pathological "Retry-After: 99999" so we don't stall the app.
 */
class RateLimitInterceptor(
    private val hostSuffixes: Set<String>,
    private val maxRetries: Int = 2,
    private val defaultBackoffMs: Long = 1_500L,
    private val retryCeilingMs: Long = 30_000L,
    private val sleeper: (Long) -> Unit = { ms -> Thread.sleep(ms) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!hostMatches(request.url.host)) {
            return chain.proceed(request)
        }

        var attempt = 0
        var response: Response = chain.proceed(request)
        while (response.code == 429 && attempt < maxRetries) {
            val waitMs = parseRetryAfterMs(response.header("Retry-After"))
                ?: defaultBackoffMs
            val clamped = min(retryCeilingMs, max(0L, waitMs))
            // Close current response BEFORE we sleep so the connection can be
            // reused / pooled rather than held idle for the entire backoff.
            response.close()
            sleeper(clamped)
            attempt++
            response = chain.proceed(request)
        }
        return response
    }

    private fun hostMatches(host: String): Boolean {
        val h = host.lowercase()
        return hostSuffixes.any { suffix ->
            h == suffix || h.endsWith(".$suffix")
        }
    }

    /**
     * Retry-After is RFC 7231 5.1 — either delta-seconds (int) or HTTP-date.
     * We only honor delta-seconds; HTTP-date support is overkill for the
     * services we target (Freesound returns delta-seconds).
     */
    private fun parseRetryAfterMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val seconds = header.trim().toLongOrNull() ?: return null
        if (seconds < 0) return null
        return TimeUnit.SECONDS.toMillis(seconds)
    }
}

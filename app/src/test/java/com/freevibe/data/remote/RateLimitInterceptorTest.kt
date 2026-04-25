package com.freevibe.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitInterceptorTest {

    private fun req(host: String, path: String = "/apiv2/search/text/"): Request =
        Request.Builder().url("https://$host$path".toHttpUrl()).build()

    private fun resp(req: Request, code: Int, retryAfter: String? = null): Response {
        val builder = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 429) "Too Many Requests" else "OK")
            .body("".toResponseBody("application/json".toMediaType()))
        if (retryAfter != null) builder.header("Retry-After", retryAfter)
        return builder.build()
    }

    @Test
    fun `retries once on 429 with Retry-After then succeeds`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 2,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(
            resp(request, 429, retryAfter = "2"),
            resp(request, 200),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(listOf(2_000L), sleeps)
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `exhausts retries and returns final 429`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 2,
            defaultBackoffMs = 100L,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returns resp(request, 429)

        val result = interceptor.intercept(chain)

        assertEquals(429, result.code)
        // 2 retries scheduled — original call + 2 retries == 3 proceed() calls.
        verify(exactly = 3) { chain.proceed(request) }
        assertEquals(2, sleeps.size)
    }

    @Test
    fun `falls back to default backoff when Retry-After missing`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 1,
            defaultBackoffMs = 1_500L,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(
            resp(request, 429),
            resp(request, 200),
        )

        interceptor.intercept(chain)

        assertEquals(listOf(1_500L), sleeps)
    }

    @Test
    fun `clamps pathological Retry-After at ceiling`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 1,
            retryCeilingMs = 5_000L,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(
            resp(request, 429, retryAfter = "99999"), // 99,999 seconds
            resp(request, 200),
        )

        interceptor.intercept(chain)

        assertEquals(listOf(5_000L), sleeps)
    }

    @Test
    fun `does not retry non-matching host`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 5,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("wallhaven.cc")
        every { chain.request() } returns request
        every { chain.proceed(request) } returns resp(request, 429, retryAfter = "1")

        val result = interceptor.intercept(chain)

        assertEquals(429, result.code)
        assertTrue("non-matching host must not sleep", sleeps.isEmpty())
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `matches host suffix subdomains`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 1,
            defaultBackoffMs = 10L,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("api.freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(
            resp(request, 429),
            resp(request, 200),
        )

        interceptor.intercept(chain)

        // Subdomain should match the suffix and trigger backoff.
        assertEquals(listOf(10L), sleeps)
    }

    @Test
    fun `negative Retry-After treated as missing`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostSuffixes = setOf("freesound.org"),
            maxRetries = 1,
            defaultBackoffMs = 250L,
            sleeper = { sleeps += it },
        )
        val chain = mockk<Interceptor.Chain>()
        val request = req("freesound.org")
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(
            resp(request, 429, retryAfter = "-5"),
            resp(request, 200),
        )

        interceptor.intercept(chain)

        // Negative falls back to defaultBackoffMs rather than waiting forever.
        assertEquals(listOf(250L), sleeps)
    }
}

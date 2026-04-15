package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class CcMixterRepositoryTest {

    @Test
    fun `buildCcMixterFallbackUrl keeps the same query parameters`() {
        val url = buildCcMixterFallbackUrl(query = "ring tone", limit = 15)

        assertEquals("http", url.scheme)
        assertEquals("ccmixter.org", url.host)
        assertEquals(listOf("api", "query"), url.pathSegments.filter { it.isNotEmpty() })
        assertEquals("json", url.queryParameter("f"))
        assertEquals("ring tone", url.queryParameter("search"))
        assertEquals("15", url.queryParameter("limit"))
        assertEquals("rank", url.queryParameter("sort"))
    }

    @Test
    fun `shouldRetryCcMixterOverHttp only retries SSL failures`() {
        assertTrue(shouldRetryCcMixterOverHttp(SSLHandshakeException("broken cert chain")))
        assertFalse(shouldRetryCcMixterOverHttp(IllegalStateException("other failure")))
    }
}

package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.freevibe.service.SourceMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton

internal fun mergeDiscoverResults(
    results: List<SearchResult<Wallpaper>?>,
    page: Int,
    maxItems: Int = Int.MAX_VALUE,
): SearchResult<Wallpaper> {
    val bySource = results.filterNotNull().map { it.items.toMutableList() }
    val interleaved = mutableListOf<Wallpaper>()
    var rounds = 0
    while (bySource.any { it.isNotEmpty() } && interleaved.size < maxItems) {
        for (source in bySource) {
            if (source.isNotEmpty()) {
                interleaved.add(source.removeAt(0))
                if (interleaved.size >= maxItems) break
            }
        }
        rounds++
        if (rounds > 200) break
    }

    val knownTotalCount = results
        .filterNotNull()
        .map { it.totalCount }
        .filter { it >= 0 }
        .sum()

    return SearchResult(
        items = interleaved,
        totalCount = when {
            knownTotalCount > 0 -> maxOf(knownTotalCount, interleaved.size)
            else -> interleaved.size
        },
        currentPage = page,
        hasMore = results.any { it?.hasMore == true },
    )
}

internal fun shouldRetryBingHost(error: Throwable): Boolean = when (error) {
    is UnknownHostException,
    is ConnectException,
    is SocketTimeoutException,
    is SSLException,
    -> true
    else -> false
}

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

private const val DISCOVER_PER_SOURCE_TIMEOUT_MS = 4_500L
private const val DISCOVER_SECONDARY_SOURCE_BUDGET_MS = 1_200L
private const val DISCOVER_PAGE_SIZE = 60
private const val SOURCE_BING = "bing"
private const val SOURCE_DISCOVER = "discover"
private const val SOURCE_PEXELS = "pexels"
private const val SOURCE_PIXABAY = "pixabay"
private const val SOURCE_WALLHAVEN = "wallhaven"

/**
 * Wallhaven purity bitfield: bit0=SFW, bit1=Sketchy, bit2=NSFW (string of three '0'/'1').
 * Wallhaven enforces: any non-SFW request requires an authenticated API key. Without a
 * key the user's sketchy/nsfw opt-ins are coerced to SFW-only — the API would reject
 * "110"/"111" without auth and we'd serve no wallpapers.
 *
 * Visible for unit testing.
 */
internal fun computeWallhavenPurity(
    hasApiKey: Boolean,
    sketchyOptIn: Boolean,
    nsfwOptIn: Boolean,
): String {
    if (!hasApiKey) return "100"
    return when {
        nsfwOptIn -> "111" // user wants everything: SFW + Sketchy + NSFW
        sketchyOptIn -> "110" // sketchy but stay clear of explicit nudity
        else -> "100" // SFW only
    }
}

@Singleton
class WallpaperRepository @Inject constructor(
    private val wallhavenApi: WallhavenApi,
    private val bingApi: BingDailyApi,
    private val pixabayApi: PixabayApi,
    private val pexelsApi: PexelsApi,
    private val cacheManager: WallpaperCacheManager,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
) {
    private suspend fun wallhavenApiKey(): String = prefs.wallhavenApiKey.first()
    private suspend fun pixabayApiKey(): String = prefs.pixabayApiKey.first()
    private suspend fun pexelsApiKey(): String = prefs.pexelsApiKey.first()

    private suspend fun wallhavenPurity(): String =
        computeWallhavenPurity(
            hasApiKey = wallhavenApiKey().isNotBlank(),
            sketchyOptIn = prefs.showSketchyContent.first(),
            nsfwOptIn = prefs.showNsfwContent.first(),
        )

    private suspend fun wallhavenMinRes(): String = prefs.preferredResolution.first()

    // -- Wallhaven (toplist by default) --

    suspend fun getWallhaven(
        query: String = "",
        page: Int = 1,
        topRange: String = "1M",
    ): SearchResult<Wallpaper> {
        val cacheKey = if (query.isBlank()) "wallhaven_toplist_${topRange}_$page" else "wallhaven_search_${query.hashCode()}_$page"
        return withCacheFallback(cacheKey, ContentSource.WALLHAVEN) {
            sourceMetrics.measure(SOURCE_WALLHAVEN) {
                val sorting = if (query.isBlank()) "toplist" else "relevance"
                val apiKey = wallhavenApiKey()
                val response = wallhavenApi.search(
                    query = query,
                    sorting = sorting,
                    topRange = topRange,
                    categories = "111",
                    purity = wallhavenPurity(),
                    minResolution = wallhavenMinRes(),
                    page = page,
                    apiKey = apiKey,
                )
                SearchResult(
                    items = response.data.map { it.toWallpaper() },
                    totalCount = response.meta.total,
                    currentPage = response.meta.currentPage,
                    hasMore = response.meta.currentPage < response.meta.lastPage,
                )
            }
        }
    }

    suspend fun searchWallhaven(query: String, page: Int = 1) =
        getWallhaven(query = query, page = page)

    /** Find wallpapers similar to a Wallhaven wallpaper by its ID (uses like: syntax) */
    suspend fun findSimilar(wallhavenId: String, page: Int = 1): SearchResult<Wallpaper> =
        getWallhaven(query = "like:$wallhavenId", page = page)

    /** Get random wallpapers from Wallhaven */
    suspend fun getRandomWallhaven(): SearchResult<Wallpaper> = sourceMetrics.measure(SOURCE_WALLHAVEN) {
        val apiKey = wallhavenApiKey()
        val response = wallhavenApi.search(
            sorting = "random",
            categories = "111",
            purity = wallhavenPurity(),
            minResolution = wallhavenMinRes(),
            apiKey = apiKey,
        )
        SearchResult(
            items = response.data.map { it.toWallpaper() },
            totalCount = response.meta.total,
            currentPage = 1,
            hasMore = true,
        )
    }

    /** Search across all sources simultaneously */
    suspend fun searchAll(query: String, page: Int = 1): SearchResult<Wallpaper> = supervisorScope {
        val sources = listOf(
            async { loadSourceSafely { getWallhaven(query = query, page = page) } },
            async { loadSourceSafely { getPixabay(query = query, page = page) } },
        )
        val results = sources.awaitAll()
        val bySource = results.filterNotNull().map { it.items.toMutableList() }
        val combined = mutableListOf<Wallpaper>()
        while (bySource.any { it.isNotEmpty() }) {
            bySource.forEach { source ->
                if (source.isNotEmpty()) {
                    combined.add(source.removeAt(0))
                }
            }
        }
        SearchResult(
            items = combined,
            totalCount = results.filterNotNull().sumOf { it.totalCount },
            currentPage = page,
            hasMore = results.any { it?.hasMore == true },
        )
    }

    // -- Wallhaven color search --

    /** Wallhaven only accepts these specific hex colors */
    private val wallhavenColors = listOf(
        0x660000, 0x990000, 0xcc0000, 0xcc3333, 0xea4c88,
        0x993399, 0x663399, 0x333399, 0x0066cc, 0x0099cc,
        0x66cccc, 0x77cc33, 0x669900, 0x336600, 0x666600,
        0x999900, 0xcccc33, 0xffff00, 0xffcc33, 0xff9900,
        0xff6600, 0xcc6633, 0x996633, 0x663300, 0x000000,
        0x999999, 0xcccccc, 0xffffff, 0x424153,
    )

    /** Map an arbitrary hex color to the nearest Wallhaven-supported color */
    private fun nearestWallhavenColor(hex: String): String {
        val rgb = hex.removePrefix("#").lowercase(java.util.Locale.ROOT).let {
            runCatching { it.toInt(16) }.getOrDefault(0)
        }
        val r1 = (rgb shr 16) and 0xFF
        val g1 = (rgb shr 8) and 0xFF
        val b1 = rgb and 0xFF
        val nearest = wallhavenColors.minBy { c ->
            val r2 = (c shr 16) and 0xFF
            val g2 = (c shr 8) and 0xFF
            val b2 = c and 0xFF
            (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)
        }
        return String.format(java.util.Locale.ROOT, "%06x", nearest)
    }

    suspend fun searchByColor(color: String, page: Int = 1): SearchResult<Wallpaper> {
        val mapped = nearestWallhavenColor(color)
        return withCacheFallback("wallhaven_color_${mapped}_$page", ContentSource.WALLHAVEN) {
            sourceMetrics.measure(SOURCE_WALLHAVEN) {
                val response = wallhavenApi.search(
                    query = "",
                    sorting = "relevance",
                    categories = "111",
                    purity = wallhavenPurity(),
                    page = page,
                    apiKey = wallhavenApiKey(),
                    colors = mapped,
                )
                SearchResult(
                    items = response.data.map { it.toWallpaper() },
                    totalCount = response.meta.total,
                    currentPage = response.meta.currentPage,
                    hasMore = response.meta.currentPage < response.meta.lastPage,
                )
            }
        }
    }

    // -- Bing Daily --

    suspend fun getBingDaily(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("bing_$page", ContentSource.BING) {
            sourceMetrics.measure(SOURCE_BING) {
                val marketsCount = BingDailyApi.MARKETS.size
                val idx = (page - 1) / marketsCount
                val marketIndex = (page - 1) % marketsCount
                val market = BingDailyApi.MARKETS[marketIndex]
                val response = fetchBingImages(
                    idx = (idx * 8).coerceAtMost(7),
                    market = market,
                )
                SearchResult(
                    items = response.images.map { it.toWallpaper() },
                    totalCount = marketsCount * 8,
                    currentPage = page,
                    hasMore = page < marketsCount,
                )
            }
        }

    // -- Pixabay --

    suspend fun getPixabay(page: Int = 1, query: String = ""): SearchResult<Wallpaper> {
        val key = pixabayApiKey()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)
        return withCacheFallback("pixabay_${query.hashCode()}_$page", ContentSource.PIXABAY) {
            sourceMetrics.measure(SOURCE_PIXABAY) {
                val response = pixabayApi.searchPhotos(
                    apiKey = key,
                    query = query.ifBlank { "wallpaper" },
                    page = page,
                    editorsChoice = query.isBlank(),
                )
                SearchResult(
                    items = response.hits.map { it.toWallpaper() },
                    totalCount = response.totalHits,
                    currentPage = page,
                    hasMore = page * 30 < response.totalHits,
                )
            }
        }
    }

    suspend fun searchPixabay(query: String, page: Int = 1) =
        getPixabay(page = page, query = query)

    // -- Pexels curated photos --

    suspend fun getPexelsCurated(page: Int = 1): SearchResult<Wallpaper> {
        val key = pexelsApiKey()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)
        return withCacheFallback("pexels_curated_$page", ContentSource.PEXELS) {
            sourceMetrics.measure(SOURCE_PEXELS) {
                val response = pexelsApi.curatedPhotos(apiKey = key, page = page)
                SearchResult(
                    items = response.photos.map { photo ->
                        Wallpaper(
                            id = "px_${photo.id}",
                            source = ContentSource.PEXELS,
                            thumbnailUrl = photo.src.medium,
                            fullUrl = photo.src.original,
                            width = photo.width,
                            height = photo.height,
                            sourcePageUrl = photo.url,
                            uploaderName = photo.photographer,
                        )
                    },
                    totalCount = response.totalResults,
                    currentPage = response.page,
                    hasMore = response.nextPage != null,
                )
            }
        }
    }

    suspend fun getPexels(page: Int = 1, query: String = ""): SearchResult<Wallpaper> {
        val key = pexelsApiKey()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)
        return withCacheFallback("pexels_${query.hashCode()}_$page", ContentSource.PEXELS) {
            sourceMetrics.measure(SOURCE_PEXELS) {
                val response = pexelsApi.searchPhotos(
                    apiKey = key,
                    query = query.ifBlank { "wallpaper" },
                    page = page,
                )
                SearchResult(
                    items = response.photos.map { photo ->
                        Wallpaper(
                            id = "px_${photo.id}",
                            source = ContentSource.PEXELS,
                            thumbnailUrl = photo.src.medium,
                            fullUrl = photo.src.original,
                            width = photo.width,
                            height = photo.height,
                            sourcePageUrl = photo.url,
                            uploaderName = photo.photographer,
                        )
                    },
                    totalCount = response.totalResults,
                    currentPage = response.page,
                    hasMore = response.nextPage != null,
                )
            }
        }
    }

    // -- Discover feed (mixed from ALL sources for diversity) --

    /** Return cached discover results if available (instant, no network) */
    suspend fun getCachedDiscover(page: Int = 1): List<Wallpaper>? {
        return cacheManager.getStaleCached("discover_$page")
    }

    suspend fun getDiscover(page: Int = 1, redditRepo: com.freevibe.data.repository.RedditRepository? = null, userStyles: List<String> = emptyList()): SearchResult<Wallpaper> =
        sourceMetrics.measure(SOURCE_DISCOVER) {
            supervisorScope {
                val primarySources = mutableListOf(
                    async { loadSourceSafely { getWallhaven(page = page) } },
                    async { loadSourceSafely { getPixabay(page = page) } },
                    async { loadSourceSafely { getPexelsCurated(page = page) } },
                )
                if (userStyles.isNotEmpty()) {
                    val styleQuery = styleToWallhavenQuery(userStyles)
                    primarySources.add(async { loadSourceSafely { getWallhaven(query = styleQuery, page = page) } })
                    // Also bias Pexels and Pixabay by style
                    primarySources.add(async { loadSourceSafely { getPixabay(query = styleQuery, page = page) } })
                    primarySources.add(async { loadSourceSafely { getPexels(query = styleQuery, page = page) } })
                }
                if (redditRepo != null) {
                    primarySources.add(async { loadSourceSafely { redditRepo.getMultiSubreddit() } })
                }
                val secondarySources = listOf(
                    async { loadSourceSafely { getBingDaily(page = page) } },
                )

                val primaryResults = primarySources.awaitAll()
                val secondaryResults = withTimeoutOrNull(DISCOVER_SECONDARY_SOURCE_BUDGET_MS) {
                    secondarySources.awaitAll()
                } ?: emptyList()
                val merged = mergeDiscoverResults(
                    results = primaryResults + secondaryResults,
                    page = page,
                    maxItems = DISCOVER_PAGE_SIZE,
                )

                // Cache combined discover result for instant startup next time
                if (merged.items.isNotEmpty()) {
                    cacheManager.cache("discover_$page", merged.items)
                }

                merged
            }
        }

    // -- Error handling with cache fallback --

    /**
     * Converts the user's onboarding style preferences into a Wallhaven search query
     * so the Discover feed is biased toward their aesthetics.
     */
    private fun styleToWallhavenQuery(styles: List<String>): String {
        val queryMap = mapOf(
            "minimal" to "minimal clean",
            "amoled" to "amoled dark",
            "nature" to "nature landscape",
            "space" to "space galaxy",
            "anime" to "anime art",
            "abstract" to "abstract colorful",
            "neon" to "neon cyberpunk",
            "city" to "city urban",
            "gradient" to "gradient colorful",
            "dark" to "dark night",
        )
        return styles.mapNotNull { queryMap[it] }.take(3).joinToString(" ")
    }

    private suspend fun withCacheFallback(
        cacheKey: String,
        source: ContentSource,
        fetch: suspend () -> SearchResult<Wallpaper>,
    ): SearchResult<Wallpaper> {
        return try {
            val result = fetch()
            if (result.items.isNotEmpty()) {
                cacheManager.cache(cacheKey, result.items)
            }
            result
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            val cached = cacheManager.getStaleCached(cacheKey)
            if (cached != null && cached.isNotEmpty()) {
                SearchResult(
                    items = cached,
                    totalCount = cached.size,
                    currentPage = 1,
                    hasMore = false,
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun loadSourceSafely(
        fetch: suspend () -> SearchResult<Wallpaper>,
    ): SearchResult<Wallpaper>? = withTimeoutOrNull(DISCOVER_PER_SOURCE_TIMEOUT_MS) {
        try {
            fetch()
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            null
        }
    }

    private suspend fun fetchBingImages(idx: Int, market: String): com.freevibe.data.remote.bing.BingImageResponse {
        var lastRetryableError: Exception? = null
        for (baseUrl in BingDailyApi.FALLBACK_BASE_URLS) {
            try {
                return bingApi.getImages(
                    url = BingDailyApi.archiveUrl(baseUrl),
                    idx = idx,
                    n = 8,
                    market = market,
                )
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                if (!shouldRetryBingHost(e)) throw e
                lastRetryableError = e
            }
        }
        throw lastRetryableError ?: IllegalStateException("Unable to load Bing Daily wallpapers")
    }
}

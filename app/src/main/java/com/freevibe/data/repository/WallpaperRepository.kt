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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepository @Inject constructor(
    private val wallhavenApi: WallhavenApi,
    private val bingApi: BingDailyApi,
    private val pixabayApi: PixabayApi,
    private val pexelsApi: PexelsApi,
    private val cacheManager: WallpaperCacheManager,
    private val prefs: PreferencesManager,
) {
    private suspend fun wallhavenApiKey(): String = prefs.wallhavenApiKey.first()
    private suspend fun pixabayApiKey(): String = prefs.pixabayApiKey.first()
    private suspend fun pexelsApiKey(): String = prefs.pexelsApiKey.first()

    private suspend fun wallhavenPurity(): String {
        val key = wallhavenApiKey()
        val nsfw = prefs.showNsfwContent.first()
        return if (key.isNotBlank() && nsfw) "111" else "100" // SFW+Sketchy+NSFW with key, SFW only without
    }

    private suspend fun wallhavenMinRes(): String = prefs.preferredResolution.first()

    // -- Wallhaven (toplist by default) --

    suspend fun getWallhaven(
        query: String = "",
        page: Int = 1,
        topRange: String = "1M",
    ): SearchResult<Wallpaper> {
        val cacheKey = if (query.isBlank()) "wallhaven_toplist_${topRange}_$page" else "wallhaven_search_${query.hashCode()}_$page"
        return withCacheFallback(cacheKey, ContentSource.WALLHAVEN) {
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

    suspend fun searchWallhaven(query: String, page: Int = 1) =
        getWallhaven(query = query, page = page)

    /** Find wallpapers similar to a Wallhaven wallpaper by its ID (uses like: syntax) */
    suspend fun findSimilar(wallhavenId: String, page: Int = 1): SearchResult<Wallpaper> =
        getWallhaven(query = "like:$wallhavenId", page = page)

    /** Get random wallpapers from Wallhaven */
    suspend fun getRandomWallhaven(): SearchResult<Wallpaper> {
        val apiKey = wallhavenApiKey()
        val response = wallhavenApi.search(
            sorting = "random",
            categories = "111",
            purity = wallhavenPurity(),
            minResolution = wallhavenMinRes(),
            apiKey = apiKey,
        )
        return SearchResult(
            items = response.data.map { it.toWallpaper() },
            totalCount = response.meta.total,
            currentPage = 1,
            hasMore = true,
        )
    }

    /** Search across all sources simultaneously */
    suspend fun searchAll(query: String, page: Int = 1): SearchResult<Wallpaper> = supervisorScope {
        val sources = listOf(
            async { runCatching { getWallhaven(query = query, page = page) }.getOrNull() },
            async { runCatching { getPixabay(query = query, page = page) }.getOrNull() },
        )
        val results = sources.map { it.await() }
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
        val rgb = hex.removePrefix("#").lowercase().let {
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
        return String.format("%06x", nearest)
    }

    suspend fun searchByColor(color: String, page: Int = 1): SearchResult<Wallpaper> {
        val mapped = nearestWallhavenColor(color)
        return withCacheFallback("wallhaven_color_${mapped}_$page", ContentSource.WALLHAVEN) {
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

    // -- Bing Daily --

    suspend fun getBingDaily(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("bing_$page", ContentSource.BING) {
            val marketsCount = BingDailyApi.MARKETS.size
            val idx = (page - 1) / marketsCount
            val marketIndex = (page - 1) % marketsCount
            val market = BingDailyApi.MARKETS[marketIndex]
            val response = bingApi.getImages(idx = (idx * 8).coerceAtMost(7), n = 8, market = market)
            SearchResult(
                items = response.images.map { it.toWallpaper() },
                totalCount = marketsCount * 8,
                currentPage = page,
                hasMore = page < marketsCount,
            )
        }

    // -- Pixabay --

    suspend fun getPixabay(page: Int = 1, query: String = ""): SearchResult<Wallpaper> {
        val key = pixabayApiKey()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)
        return withCacheFallback("pixabay_${query.hashCode()}_$page", ContentSource.PIXABAY) {
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

    suspend fun searchPixabay(query: String, page: Int = 1) =
        getPixabay(page = page, query = query)

    // -- Pexels curated photos --

    suspend fun getPexelsCurated(page: Int = 1): SearchResult<Wallpaper> {
        val key = pexelsApiKey()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)
        return withCacheFallback("pexels_curated_$page", ContentSource.PEXELS) {
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

    // -- Discover feed (mixed from ALL sources for diversity) --

    /** Return cached discover results if available (instant, no network) */
    suspend fun getCachedDiscover(page: Int = 1): List<Wallpaper>? {
        return cacheManager.getStaleCached("discover_$page")
    }

    suspend fun getDiscover(page: Int = 1, redditRepo: com.freevibe.data.repository.RedditRepository? = null): SearchResult<Wallpaper> = supervisorScope {
        val perSourceTimeout = 6000L // Don't let any single source hold up the feed
        val sources = mutableListOf(
            async { withTimeoutOrNull(perSourceTimeout) { runCatching { getWallhaven(page = page) }.getOrNull() } },
            async { withTimeoutOrNull(perSourceTimeout) { runCatching { getPixabay(page = page) }.getOrNull() } },
            async { withTimeoutOrNull(perSourceTimeout) { runCatching { getBingDaily(page = page) }.getOrNull() } },
            async { withTimeoutOrNull(perSourceTimeout) { runCatching { getPexelsCurated(page = page) }.getOrNull() } },
        )
        // Reddit
        if (redditRepo != null) {
            sources.add(async { withTimeoutOrNull(perSourceTimeout) { runCatching { redditRepo.getMultiSubreddit() }.getOrNull() } })
        }

        val results = sources.map { it.await() }
        // Round-robin interleave sources for even distribution
        val bySource = results.filterNotNull().map { it.items.toMutableList() }
        val interleaved = mutableListOf<Wallpaper>()
        var idx = 0
        while (bySource.any { it.isNotEmpty() }) {
            for (source in bySource) {
                if (source.isNotEmpty()) interleaved.add(source.removeAt(0))
            }
            idx++
            if (idx > 200) break
        }

        // Cache combined discover result for instant startup next time
        if (interleaved.isNotEmpty()) {
            cacheManager.cache("discover_$page", interleaved)
        }

        SearchResult(
            items = interleaved,
            totalCount = interleaved.size * results.count { it != null }.coerceAtLeast(1),
            currentPage = page,
            hasMore = interleaved.isNotEmpty(),
        )
    }

    // -- Error handling with cache fallback --

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
}

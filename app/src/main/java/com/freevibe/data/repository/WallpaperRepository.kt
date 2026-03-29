package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.remote.wallhaven.WallhavenApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepository @Inject constructor(
    private val wallhavenApi: WallhavenApi,
    private val picsumApi: PicsumApi,
    private val bingApi: BingDailyApi,
    private val pixabayApi: PixabayApi,
    private val cacheManager: WallpaperCacheManager,
    private val prefs: PreferencesManager,
) {
    private suspend fun wallhavenApiKey(): String = prefs.wallhavenApiKey.first()

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
    ): SearchResult<Wallpaper> {
        val cacheKey = if (query.isBlank()) "wallhaven_toplist_$page" else "wallhaven_search_${query.hashCode()}_$page"
        return withCacheFallback(cacheKey, ContentSource.WALLHAVEN) {
            val sorting = if (query.isBlank()) "toplist" else "relevance"
            val apiKey = wallhavenApiKey()
            val response = wallhavenApi.search(
                query = query,
                sorting = sorting,
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

    /** Search across all sources simultaneously */
    suspend fun searchAll(query: String, page: Int = 1): SearchResult<Wallpaper> = supervisorScope {
        val sources = listOf(
            async { runCatching { getWallhaven(query = query, page = page) }.getOrNull() },
            async { runCatching { getPixabay(query = query, page = page) }.getOrNull() },
            async { runCatching { getPicsum(page = page) }.getOrNull() }, // Picsum doesn't support search, just adds variety
        )
        val results = sources.map { it.await() }
        val combined = results.filterNotNull().flatMap { it.items }.shuffled()
        SearchResult(
            items = combined,
            totalCount = combined.size * 5,
            currentPage = page,
            hasMore = combined.size >= 5 && results.any { it?.hasMore == true },
        )
    }

    // -- Wallhaven color search --

    suspend fun searchByColor(color: String, page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("wallhaven_color_${color}_$page", ContentSource.WALLHAVEN) {
            val response = wallhavenApi.search(
                query = "",
                sorting = "relevance",
                categories = "111",
                purity = wallhavenPurity(),
                page = page,
                apiKey = wallhavenApiKey(),
                colors = color,
            )
            SearchResult(
                items = response.data.map { it.toWallpaper() },
                totalCount = response.meta.total,
                currentPage = response.meta.currentPage,
                hasMore = response.meta.currentPage < response.meta.lastPage,
            )
        }

    // -- Unsplash via Lorem Picsum --

    suspend fun getPicsum(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("picsum_$page", ContentSource.PICSUM) {
            val photos = picsumApi.list(page = page, limit = 30)
            SearchResult(
                items = photos.map { it.toWallpaper() },
                totalCount = 1000,
                currentPage = page,
                hasMore = photos.size >= 30,
            )
        }

    // -- Bing Daily --

    suspend fun getBingDaily(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("bing_$page", ContentSource.BING) {
            val marketsCount = BingDailyApi.MARKETS.size
            val idx = (page - 1) / marketsCount
            val marketIndex = (page - 1) % marketsCount
            val market = BingDailyApi.MARKETS[marketIndex]
            val response = bingApi.getImages(idx = idx * 8, n = 8, market = market)
            SearchResult(
                items = response.images.map { it.toWallpaper() },
                totalCount = marketsCount * 8 * 2,
                currentPage = page,
                hasMore = idx < 1,
            )
        }

    // -- Pixabay --

    suspend fun getPixabay(page: Int = 1, query: String = ""): SearchResult<Wallpaper> =
        withCacheFallback("pixabay_${query.hashCode()}_$page", ContentSource.PIXABAY) {
            val response = pixabayApi.searchPhotos(
                apiKey = PixabayApi.API_KEY,
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

    suspend fun searchPixabay(query: String, page: Int = 1) =
        getPixabay(page = page, query = query)

    // -- Discover feed (mixed from all sources) --

    suspend fun getDiscover(page: Int = 1): SearchResult<Wallpaper> = supervisorScope {
        val sources = listOf(
            async { runCatching { getWallhaven(page = page) }.getOrNull() },
            async { runCatching { getPicsum(page = page) }.getOrNull() },
            async { runCatching { getPixabay(page = page) }.getOrNull() },
        )
        val results = sources.map { it.await() }
        val combined = results.filterNotNull()
            .flatMap { it.items }
            .shuffled()

        SearchResult(
            items = combined,
            totalCount = combined.size * 10,
            currentPage = page,
            hasMore = combined.size >= 10 && results.any { it?.hasMore == true },
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

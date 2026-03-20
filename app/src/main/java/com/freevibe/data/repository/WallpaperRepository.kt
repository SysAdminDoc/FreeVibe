package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.freevibe.data.remote.wikimedia.WikimediaApi
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
    private val wikimediaApi: WikimediaApi,
    private val cacheManager: WallpaperCacheManager,
    private val prefs: PreferencesManager,
) {
    private suspend fun wallhavenPurity(): String {
        val nsfw = prefs.showNsfwContent.first()
        return if (nsfw && BuildConfig.WALLHAVEN_API_KEY.isNotBlank()) "111" else "100"
    }
    // -- Wallhaven (toplist by default) --

    suspend fun getWallhaven(
        query: String = "",
        page: Int = 1,
    ): SearchResult<Wallpaper> {
        val cacheKey = if (query.isBlank()) "wallhaven_toplist_$page" else "wallhaven_search_${query.hashCode()}_$page"
        return withCacheFallback(cacheKey, ContentSource.WALLHAVEN) {
            val sorting = if (query.isBlank()) "toplist" else "relevance"
            val response = wallhavenApi.search(
                query = query,
                sorting = sorting,
                categories = "111",
                purity = wallhavenPurity(),
                page = page,
                apiKey = BuildConfig.WALLHAVEN_API_KEY,
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

    // -- #9: Wallhaven color search --

    suspend fun searchByColor(color: String, page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("wallhaven_color_${color}_$page", ContentSource.WALLHAVEN) {
            val response = wallhavenApi.search(
                query = "",
                sorting = "relevance",
                categories = "111",
                purity = wallhavenPurity(),
                page = page,
                apiKey = BuildConfig.WALLHAVEN_API_KEY,
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

    // -- #8: Bing Daily (proper pagination via idx + market) --

    suspend fun getBingDaily(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("bing_$page", ContentSource.BING) {
            // Page 1 = idx 0 en-US, Page 2 = idx 0 en-GB, ...
            // After exhausting markets, cycle with higher idx
            val marketsCount = BingDailyApi.MARKETS.size
            val idx = (page - 1) / marketsCount
            val marketIndex = (page - 1) % marketsCount
            val market = BingDailyApi.MARKETS[marketIndex]
            val response = bingApi.getImages(idx = idx * 8, n = 8, market = market)
            SearchResult(
                items = response.images.map { it.toWallpaper() },
                totalCount = marketsCount * 8 * 2,
                currentPage = page,
                hasMore = idx < 1, // Bing supports ~16 days back
            )
        }

    // -- Wikimedia Commons (Featured Pictures) --
    // Per-page continue tokens to avoid race conditions between Discover and tab browsing
    private val wikimediaContinueTokens = mutableMapOf<Int, String?>()

    suspend fun getWikimedia(page: Int = 1): SearchResult<Wallpaper> =
        withCacheFallback("wikimedia_$page", ContentSource.WIKIMEDIA) {
            val continueToken = if (page == 1) null else wikimediaContinueTokens[page]
            val response = wikimediaApi.getCategoryImages(
                continueToken = continueToken,
                limit = 50, // request more since we filter by resolution
            )
            // Store token for the *next* page
            response.continueData?.gcmContinue?.let { token ->
                wikimediaContinueTokens[page + 1] = token
            }

            val wallpapers = response.query?.pages?.values
                ?.mapNotNull { it.toWallpaper() }
                ?: emptyList()

            SearchResult(
                items = wallpapers,
                totalCount = 10000,
                currentPage = page,
                hasMore = response.continueData?.gcmContinue != null,
            )
        }

    // -- #2: Discover feed (mixed from all sources) --

    suspend fun getDiscover(page: Int = 1): SearchResult<Wallpaper> = supervisorScope {
        val sources = listOf(
            async { runCatching { getWallhaven(page = page) }.getOrNull() },
            async { runCatching { getPicsum(page = page) }.getOrNull() },
            async { runCatching { getBingDaily(page = page) }.getOrNull() },
        )
        val results = sources.map { it.await() }
        val combined = results.filterNotNull()
            .flatMap { it.items }
            .shuffled()

        SearchResult(
            items = combined,
            totalCount = combined.size * 10,
            currentPage = page,
            // Only continue if we actually got a reasonable number of results
            hasMore = combined.size >= 10 && results.any { it?.hasMore == true },
        )
    }

    // -- #5: Error handling with cache fallback --

    private suspend fun withCacheFallback(
        cacheKey: String,
        source: ContentSource,
        fetch: suspend () -> SearchResult<Wallpaper>,
    ): SearchResult<Wallpaper> {
        return try {
            val result = fetch()
            // Cache successful results
            if (result.items.isNotEmpty()) {
                cacheManager.cache(cacheKey, result.items)
            }
            result
        } catch (e: Exception) {
            // Try stale cache on network failure
            val cached = cacheManager.getStaleCached(cacheKey)
            if (cached != null && cached.isNotEmpty()) {
                SearchResult(
                    items = cached,
                    totalCount = cached.size,
                    currentPage = 1,
                    hasMore = false,
                )
            } else {
                throw e // No cache available, propagate error
            }
        }
    }
}

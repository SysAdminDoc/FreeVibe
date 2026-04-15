package com.freevibe.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import kotlinx.coroutines.CancellationException

/**
 * Generic PagingSource that wraps any wallpaper API call with caching.
 */
class WallpaperPagingSource(
    private val cacheManager: WallpaperCacheManager,
    private val cacheKeyPrefix: String,
    private val source: ContentSource,
    private val loader: suspend (page: Int) -> SearchResult<Wallpaper>,
) : PagingSource<Int, Wallpaper>() {

    override fun getRefreshKey(state: PagingState<Int, Wallpaper>): Int? {
        return state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(pos)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Wallpaper> {
        val page = params.key ?: 1
        val cacheKey = "${cacheKeyPrefix}_$page"
        val prevKey = if (page == 1) null else page - 1

        return try {
            // Check cache first
            val cached = cacheManager.getCached(cacheKey, source)
            if (cached != null && cached.isNotEmpty()) {
                // Check if the next page is also cached; if not, refresh this page so pagination
                // can keep moving instead of getting stuck on the cached boundary forever.
                val nextCacheKey = "${cacheKeyPrefix}_${page + 1}"
                val nextCached = cacheManager.getCached(nextCacheKey, source)
                if (nextCached != null && nextCached.isNotEmpty()) {
                    return LoadResult.Page(
                        data = cached,
                        prevKey = prevKey,
                        nextKey = page + 1,
                    )
                }

                return try {
                    val refreshed = loader(page)
                    if (refreshed.items.isNotEmpty()) {
                        cacheManager.cache(cacheKey, refreshed.items)
                    }
                    LoadResult.Page(
                        data = refreshed.items.ifEmpty { cached },
                        prevKey = prevKey,
                        nextKey = if (refreshed.hasMore) page + 1 else null,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    LoadResult.Page(
                        data = cached,
                        prevKey = prevKey,
                        nextKey = null,
                    )
                }
            }

            // Fetch from network
            val result = loader(page)

            // Cache results
            if (result.items.isNotEmpty()) {
                cacheManager.cache(cacheKey, result.items)
            }

            LoadResult.Page(
                data = result.items,
                prevKey = prevKey,
                nextKey = if (result.hasMore) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // On network error, try stale cache as fallback
            val staleCache = try {
                cacheManager.getStaleCached(cacheKey)
            } catch (_: Exception) { null }

            if (staleCache != null && staleCache.isNotEmpty()) {
                LoadResult.Page(
                    data = staleCache,
                    prevKey = prevKey,
                    nextKey = null, // Can't verify pagination state when offline
                )
            } else {
                LoadResult.Error(e)
            }
        }
    }
}

/**
 * PagingSource for sounds (no caching since audio files are streamed).
 */
class SoundPagingSource<T : Any>(
    private val loader: suspend (page: Int) -> SearchResult<T>,
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(pos)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val result = loader(page)
            LoadResult.Page(
                data = result.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (result.hasMore) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

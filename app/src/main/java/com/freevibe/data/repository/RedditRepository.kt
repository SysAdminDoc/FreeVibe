package com.freevibe.data.repository

import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.reddit.RedditApi
import com.freevibe.data.remote.toWallpaper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditRepository @Inject constructor(
    private val redditApi: RedditApi,
) {
    // Per-subreddit pagination tokens (thread-safe)
    private val afterTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getSubredditWallpapers(
        subreddit: String = "wallpapers",
        sort: String = "hot",
        timeRange: String = "week",
        after: String? = null,
    ): SearchResult<Wallpaper> {
        val response = redditApi.getSubredditPosts(
            subreddit = subreddit,
            sort = sort,
            timeRange = timeRange,
            after = after ?: afterTokens[subreddit],
        )
        val nextAfter = response.data.after
        if (nextAfter != null) afterTokens[subreddit] = nextAfter else afterTokens.remove(subreddit)

        val wallpapers = response.data.children
            .map { it.data }
            .filter { it.isImage && !it.over18 }
            .map { it.toWallpaper() }

        return SearchResult(
            items = wallpapers,
            totalCount = -1,   // Reddit doesn't provide total count
            currentPage = 0,
            hasMore = response.data.after != null,
        )
    }

    suspend fun searchSubreddit(
        subreddit: String = "wallpapers",
        query: String,
        after: String? = null,
    ): SearchResult<Wallpaper> {
        val response = redditApi.searchSubreddit(
            subreddit = subreddit,
            query = query,
            after = after,
        )

        val wallpapers = response.data.children
            .map { it.data }
            .filter { it.isImage && !it.over18 }
            .map { it.toWallpaper() }

        return SearchResult(
            items = wallpapers,
            totalCount = -1,
            currentPage = 0,
            hasMore = response.data.after != null,
        )
    }

    private val redditWallpaperSubs = listOf(
        "wallpapers", "MobileWallpaper", "wallpaper", "WQHD_Wallpaper",
        "MinimalWallpaper", "phonewallpapers", "iWallpaper",
    )

    /** Get combined wallpapers from multiple subreddits with pagination */
    suspend fun getMultiSubreddit(
        subreddits: List<String> = redditWallpaperSubs,
    ): SearchResult<Wallpaper> = coroutineScope {
        val results = subreddits.map { sub ->
            async {
                try {
                    getSubredditWallpapers(sub, sort = "top", timeRange = "week")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        }.awaitAll().filterNotNull()

        SearchResult(
            items = results.flatMap { it.items }.distinctBy { it.id },
            totalCount = -1,
            currentPage = 0,
            hasMore = results.any { it.hasMore },
        )
    }

    /** Get today's single most-upvoted wallpaper across key subreddits */
    suspend fun getDailyTopWallpaper(): Wallpaper? {
        val subs = listOf("wallpapers", "MobileWallpaper")
        return subs.flatMap { sub ->
            try {
                redditApi.getSubredditPosts(
                    subreddit = sub, sort = "top", timeRange = "day", limit = 5,
                ).data.children.map { it.data }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
            .filter { it.isImage && !it.over18 }
            .maxByOrNull { it.ups }
            ?.let { post ->
                val wp = post.toWallpaper()
                // Enrich with upvote count in the category field for display
                wp.copy(category = "${post.ups} upvotes on r/${post.subreddit}", favorites = post.ups)
            }
    }

    fun resetPagination() {
        afterTokens.clear()
    }
}

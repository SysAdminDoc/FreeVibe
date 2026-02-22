package com.freevibe.data.remote.reddit

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RedditApi {

    /** Fetch posts from a subreddit (public JSON endpoint, no auth needed) */
    @GET("r/{subreddit}/{sort}.json")
    suspend fun getSubredditPosts(
        @Path("subreddit") subreddit: String,
        @Path("sort") sort: String = "hot",
        @Query("limit") limit: Int = 50,
        @Query("after") after: String? = null,
        @Query("t") timeRange: String = "week",
        @Query("raw_json") rawJson: Int = 1,
    ): RedditListingResponse

    /** Search within a subreddit */
    @GET("r/{subreddit}/search.json")
    suspend fun searchSubreddit(
        @Path("subreddit") subreddit: String,
        @Query("q") query: String,
        @Query("restrict_sr") restrictSr: Boolean = true,
        @Query("sort") sort: String = "relevance",
        @Query("limit") limit: Int = 50,
        @Query("after") after: String? = null,
        @Query("raw_json") rawJson: Int = 1,
    ): RedditListingResponse

    companion object {
        const val BASE_URL = "https://www.reddit.com/"

        val WALLPAPER_SUBREDDITS = listOf(
            "wallpapers" to "Wallpapers",
            "wallpaper" to "Wallpaper",
            "Amoledbackgrounds" to "AMOLED",
            "MobileWallpaper" to "Mobile",
            "iWallpaper" to "iPhone/Mobile",
            "MinimalWallpaper" to "Minimal",
            "WidescreenWallpaper" to "Widescreen",
            "EarthPorn" to "Nature",
            "spaceporn" to "Space",
            "CityPorn" to "Cities",
            "Art" to "Art",
        )
    }
}

// ── Reddit JSON response models ───────────────────────────────────

@JsonClass(generateAdapter = true)
data class RedditListingResponse(
    @Json(name = "data") val data: RedditListingData,
)

@JsonClass(generateAdapter = true)
data class RedditListingData(
    @Json(name = "after") val after: String? = null,
    @Json(name = "before") val before: String? = null,
    @Json(name = "children") val children: List<RedditChild> = emptyList(),
    @Json(name = "dist") val dist: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RedditChild(
    @Json(name = "kind") val kind: String = "",
    @Json(name = "data") val data: RedditPost,
)

@JsonClass(generateAdapter = true)
data class RedditPost(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "author") val author: String = "",
    @Json(name = "subreddit") val subreddit: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "permalink") val permalink: String = "",
    @Json(name = "thumbnail") val thumbnail: String = "",
    @Json(name = "score") val score: Int = 0,
    @Json(name = "ups") val ups: Int = 0,
    @Json(name = "num_comments") val numComments: Int = 0,
    @Json(name = "created_utc") val createdUtc: Double = 0.0,
    @Json(name = "is_video") val isVideo: Boolean = false,
    @Json(name = "over_18") val over18: Boolean = false,
    @Json(name = "post_hint") val postHint: String? = null,
    @Json(name = "preview") val preview: RedditPreview? = null,
) {
    /** Check if post is a usable image */
    val isImage: Boolean
        get() = postHint == "image" || url.endsWith(".jpg") || url.endsWith(".jpeg") ||
            url.endsWith(".png") || url.endsWith(".webp")

    /** Get best resolution image URL */
    val imageUrl: String
        get() = when {
            url.contains("i.redd.it") || url.contains("i.imgur.com") -> url
            preview != null -> preview.images.firstOrNull()?.source?.url?.replace("&amp;", "&") ?: url
            else -> url
        }

    /** Get thumbnail for grid display */
    val thumbUrl: String
        get() = preview?.images?.firstOrNull()?.resolutions
            ?.lastOrNull { it.width <= 640 }?.url?.replace("&amp;", "&")
            ?: thumbnail

    /** Extract resolution from title like "[3840x2160]" */
    val parsedResolution: Pair<Int, Int>?
        get() {
            val match = Regex("""\[?(\d{3,5})\s*[xX×]\s*(\d{3,5})]?""").find(title)
            return match?.let {
                it.groupValues[1].toIntOrNull()?.let { w ->
                    it.groupValues[2].toIntOrNull()?.let { h -> w to h }
                }
            }
        }
}

@JsonClass(generateAdapter = true)
data class RedditPreview(
    @Json(name = "images") val images: List<RedditPreviewImage> = emptyList(),
    @Json(name = "enabled") val enabled: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class RedditPreviewImage(
    @Json(name = "source") val source: RedditImageSource? = null,
    @Json(name = "resolutions") val resolutions: List<RedditImageSource> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RedditImageSource(
    @Json(name = "url") val url: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
)

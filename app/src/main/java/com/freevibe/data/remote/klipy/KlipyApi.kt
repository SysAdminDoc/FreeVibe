package com.freevibe.data.remote.klipy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Klipy API — free GIF/sticker/clip platform (replaces dead Tenor).
 * Built by ex-Tenor team. Used by Canva, Figma, Microsoft.
 * Free tier: unlimited after production approval.
 * Formats: GIF, MP4, WebM, WebP — 4 resolution tiers (hd/md/sm/xs).
 * Always prefer MP4 over GIF for wallpaper use (hardware-accelerated, smaller).
 */
interface KlipyApi {

    @GET("api/v1/{appKey}/gifs/search")
    suspend fun searchGifs(
        @Path("appKey") appKey: String,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("content_filter") contentFilter: String = "medium",
    ): KlipyResponse

    @GET("api/v1/{appKey}/gifs/trending")
    suspend fun trendingGifs(
        @Path("appKey") appKey: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): KlipyResponse

    @GET("api/v1/{appKey}/gifs/categories")
    suspend fun categories(
        @Path("appKey") appKey: String,
    ): KlipyCategoriesResponse

    @GET("api/v1/{appKey}/clips/search")
    suspend fun searchClips(
        @Path("appKey") appKey: String,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("content_filter") contentFilter: String = "medium",
    ): KlipyResponse

    @GET("api/v1/{appKey}/clips/trending")
    suspend fun trendingClips(
        @Path("appKey") appKey: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): KlipyResponse

    companion object {
        const val BASE_URL = "https://api.klipy.com/"
        // Register at partner.klipy.com for production key
        const val APP_KEY = "aura_placeholder"
    }
}

@JsonClass(generateAdapter = true)
data class KlipyResponse(
    @Json(name = "results") val results: List<KlipyItem> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page") val page: Int = 1,
    @Json(name = "per_page") val perPage: Int = 30,
)

@JsonClass(generateAdapter = true)
data class KlipyItem(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "content_description") val contentDescription: String = "",
    @Json(name = "created") val created: String = "",
    @Json(name = "media") val media: KlipyMedia = KlipyMedia(),
)

@JsonClass(generateAdapter = true)
data class KlipyMedia(
    @Json(name = "hd") val hd: KlipyMediaVariant? = null,
    @Json(name = "md") val md: KlipyMediaVariant? = null,
    @Json(name = "sm") val sm: KlipyMediaVariant? = null,
    @Json(name = "xs") val xs: KlipyMediaVariant? = null,
)

@JsonClass(generateAdapter = true)
data class KlipyMediaVariant(
    @Json(name = "gif") val gif: KlipyMediaFile? = null,
    @Json(name = "mp4") val mp4: KlipyMediaFile? = null,
    @Json(name = "webm") val webm: KlipyMediaFile? = null,
    @Json(name = "webp") val webp: KlipyMediaFile? = null,
)

@JsonClass(generateAdapter = true)
data class KlipyMediaFile(
    @Json(name = "url") val url: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "size") val size: Long = 0,
)

@JsonClass(generateAdapter = true)
data class KlipyCategoriesResponse(
    @Json(name = "categories") val categories: List<KlipyCategory> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class KlipyCategory(
    @Json(name = "name") val name: String = "",
    @Json(name = "search_term") val searchTerm: String = "",
    @Json(name = "image_url") val imageUrl: String = "",
)

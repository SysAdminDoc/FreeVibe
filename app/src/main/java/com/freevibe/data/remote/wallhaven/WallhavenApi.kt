package com.freevibe.data.remote.wallhaven

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ── API Service ───────────────────────────────────────────────────

interface WallhavenApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String = "",
        @Query("categories") categories: String = "111",   // general/anime/people
        @Query("purity") purity: String = "100",            // SFW only
        @Query("sorting") sorting: String = "relevance",
        @Query("order") order: String = "desc",
        @Query("topRange") topRange: String = "1M",
        @Query("atleast") minResolution: String = "",
        @Query("ratios") ratios: String = "",
        @Query("colors") colors: String = "",
        @Query("page") page: Int = 1,
        @Query("apikey") apiKey: String = "",
    ): WallhavenSearchResponse

    @GET("w/{id}")
    suspend fun getWallpaper(
        @Path("id") id: String,
        @Query("apikey") apiKey: String = "",
    ): WallhavenDetailResponse

    @GET("tag/{id}")
    suspend fun getTag(
        @Path("id") id: Long,
        @Query("apikey") apiKey: String = "",
    ): WallhavenTagResponse
}

// ── Response DTOs ─────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WallhavenSearchResponse(
    @Json(name = "data") val data: List<WallhavenWallpaper> = emptyList(),
    @Json(name = "meta") val meta: WallhavenMeta = WallhavenMeta(),
)

@JsonClass(generateAdapter = true)
data class WallhavenDetailResponse(
    @Json(name = "data") val data: WallhavenWallpaper,
)

@JsonClass(generateAdapter = true)
data class WallhavenTagResponse(
    @Json(name = "data") val data: WallhavenTag,
)

@JsonClass(generateAdapter = true)
data class WallhavenWallpaper(
    @Json(name = "id") val id: String,
    @Json(name = "url") val url: String,
    @Json(name = "short_url") val shortUrl: String = "",
    @Json(name = "views") val views: Int = 0,
    @Json(name = "favorites") val favorites: Int = 0,
    @Json(name = "source") val source: String = "",
    @Json(name = "purity") val purity: String = "",
    @Json(name = "category") val category: String = "",
    @Json(name = "dimension_x") val dimensionX: Int = 0,
    @Json(name = "dimension_y") val dimensionY: Int = 0,
    @Json(name = "resolution") val resolution: String = "",
    @Json(name = "ratio") val ratio: String = "",
    @Json(name = "file_size") val fileSize: Long = 0,
    @Json(name = "file_type") val fileType: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "colors") val colors: List<String> = emptyList(),
    @Json(name = "path") val path: String = "",
    @Json(name = "thumbs") val thumbs: WallhavenThumbs = WallhavenThumbs(),
    @Json(name = "tags") val tags: List<WallhavenTag>? = null,
)

@JsonClass(generateAdapter = true)
data class WallhavenThumbs(
    @Json(name = "large") val large: String = "",
    @Json(name = "original") val original: String = "",
    @Json(name = "small") val small: String = "",
)

@JsonClass(generateAdapter = true)
data class WallhavenTag(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "alias") val alias: String = "",
    @Json(name = "category_id") val categoryId: Long = 0,
    @Json(name = "category") val category: String = "",
    @Json(name = "purity") val purity: String = "",
    @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class WallhavenMeta(
    @Json(name = "current_page") val currentPage: Int = 1,
    @Json(name = "last_page") val lastPage: Int = 1,
    @Json(name = "per_page") val perPage: Int = 24,
    @Json(name = "total") val total: Int = 0,
    @Transient val query: Any? = null,
    @Json(name = "seed") val seed: String? = null,
)

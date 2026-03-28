package com.freevibe.data.remote.pexels

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Pexels API — free HD/4K video and photo library.
 * Requires API key (free, instant approval at pexels.com/api/new).
 * Rate limits: 200 req/hour, 20K req/month.
 * Direct MP4 download URLs — no extraction needed.
 */
interface PexelsApi {

    @GET("videos/search")
    suspend fun searchVideos(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("orientation") orientation: String = "portrait",
        @Query("size") size: String = "medium",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): PexelsVideoResponse

    @GET("videos/popular")
    suspend fun popularVideos(
        @Header("Authorization") apiKey: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): PexelsVideoResponse

    companion object {
        const val BASE_URL = "https://api.pexels.com/"
    }
}

@JsonClass(generateAdapter = true)
data class PexelsVideoResponse(
    @Json(name = "total_results") val totalResults: Int = 0,
    @Json(name = "page") val page: Int = 1,
    @Json(name = "per_page") val perPage: Int = 20,
    @Json(name = "videos") val videos: List<PexelsVideo> = emptyList(),
    @Json(name = "next_page") val nextPage: String? = null,
)

@JsonClass(generateAdapter = true)
data class PexelsVideo(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "duration") val duration: Int = 0,
    @Json(name = "url") val url: String = "",
    @Json(name = "image") val image: String = "", // Thumbnail
    @Json(name = "user") val user: PexelsUser = PexelsUser(),
    @Json(name = "video_files") val videoFiles: List<PexelsVideoFile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PexelsUser(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "url") val url: String = "",
)

@JsonClass(generateAdapter = true)
data class PexelsVideoFile(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "quality") val quality: String = "",
    @Json(name = "file_type") val fileType: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "link") val link: String = "",
)

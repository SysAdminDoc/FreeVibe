package com.freevibe.data.remote.pixabay

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Pixabay API — free HD/4K photos + videos + animated loops.
 * Rate limits: 100 req/60s. Free forever.
 * Key filter: video_type=animation for GIF-like loops.
 * 4M+ photos, 6,200+ animated wallpaper videos, 8,100+ motion loops.
 */
interface PixabayApi {

    @GET("api/")
    suspend fun searchPhotos(
        @Query("key") apiKey: String,
        @Query("q") query: String = "",
        @Query("image_type") imageType: String = "photo",
        @Query("orientation") orientation: String = "vertical",
        @Query("min_width") minWidth: Int = 1080,
        @Query("min_height") minHeight: Int = 1920,
        @Query("editors_choice") editorsChoice: Boolean = false,
        @Query("safesearch") safeSearch: Boolean = true,
        @Query("order") order: String = "popular",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("category") category: String = "",
    ): PixabayPhotoResponse

    @GET("api/videos/")
    suspend fun searchVideos(
        @Query("key") apiKey: String,
        @Query("q") query: String = "",
        @Query("video_type") videoType: String = "all",
        @Query("min_width") minWidth: Int = 0,
        @Query("min_height") minHeight: Int = 0,
        @Query("editors_choice") editorsChoice: Boolean = false,
        @Query("safesearch") safeSearch: Boolean = true,
        @Query("order") order: String = "popular",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("category") category: String = "",
    ): PixabayVideoResponse

    companion object {
        const val BASE_URL = "https://pixabay.com/"
        // Free API key — register at pixabay.com/api/docs/
        const val API_KEY = "48aborc-placeholder"
    }
}

// -- Photo Models --

@JsonClass(generateAdapter = true)
data class PixabayPhotoResponse(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "totalHits") val totalHits: Int = 0,
    @Json(name = "hits") val hits: List<PixabayPhoto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PixabayPhoto(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "pageURL") val pageUrl: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "tags") val tags: String = "",
    @Json(name = "previewURL") val previewUrl: String = "",
    @Json(name = "webformatURL") val webformatUrl: String = "",
    @Json(name = "largeImageURL") val largeImageUrl: String = "",
    @Json(name = "fullHDURL") val fullHdUrl: String? = null,
    @Json(name = "imageURL") val imageUrl: String? = null,
    @Json(name = "imageWidth") val imageWidth: Int = 0,
    @Json(name = "imageHeight") val imageHeight: Int = 0,
    @Json(name = "imageSize") val imageSize: Long = 0,
    @Json(name = "views") val views: Int = 0,
    @Json(name = "downloads") val downloads: Int = 0,
    @Json(name = "likes") val likes: Int = 0,
    @Json(name = "user") val user: String = "",
    @Json(name = "userImageURL") val userImageUrl: String = "",
)

// -- Video Models --

@JsonClass(generateAdapter = true)
data class PixabayVideoResponse(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "totalHits") val totalHits: Int = 0,
    @Json(name = "hits") val hits: List<PixabayVideo> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PixabayVideo(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "pageURL") val pageUrl: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "tags") val tags: String = "",
    @Json(name = "duration") val duration: Int = 0,
    @Json(name = "picture_id") val pictureId: String = "",
    @Json(name = "videos") val videos: PixabayVideoFiles = PixabayVideoFiles(),
    @Json(name = "views") val views: Int = 0,
    @Json(name = "downloads") val downloads: Int = 0,
    @Json(name = "likes") val likes: Int = 0,
    @Json(name = "user") val user: String = "",
    @Json(name = "userImageURL") val userImageUrl: String = "",
) {
    val thumbnailUrl: String get() = "https://i.vimeocdn.com/video/${pictureId}_640x360.jpg"
}

@JsonClass(generateAdapter = true)
data class PixabayVideoFiles(
    @Json(name = "large") val large: PixabayVideoFile? = null,
    @Json(name = "medium") val medium: PixabayVideoFile? = null,
    @Json(name = "small") val small: PixabayVideoFile? = null,
    @Json(name = "tiny") val tiny: PixabayVideoFile? = null,
)

@JsonClass(generateAdapter = true)
data class PixabayVideoFile(
    @Json(name = "url") val url: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "size") val size: Long = 0,
)

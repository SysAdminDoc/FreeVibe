package com.freevibe.data.remote.picsum

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lorem Picsum API - serves high-quality Unsplash photos.
 * No API key required. No auth. Just works.
 * https://picsum.photos/
 */
interface PicsumApi {

    /** Paginated list of all available photos (30 per page by default) */
    @GET("v2/list")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30,
    ): List<PicsumPhoto>

    /** Get info for a specific photo by ID */
    @GET("id/{id}/info")
    suspend fun getInfo(
        @Path("id") id: String,
    ): PicsumPhoto

    companion object {
        const val BASE_URL = "https://picsum.photos/"

        /** Build a sized image URL for a given photo ID */
        fun imageUrl(id: String, width: Int = 1080, height: Int = 1920): String =
            "https://picsum.photos/id/$id/$width/$height"

        /** Build a thumbnail URL */
        fun thumbUrl(id: String, width: Int = 400, height: Int = 600): String =
            "https://picsum.photos/id/$id/$width/$height"
    }
}

@JsonClass(generateAdapter = true)
data class PicsumPhoto(
    @Json(name = "id") val id: String,
    @Json(name = "author") val author: String = "",
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "url") val url: String = "",            // Unsplash page
    @Json(name = "download_url") val downloadUrl: String = "", // Direct download
)

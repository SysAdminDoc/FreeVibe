package com.freevibe.data.remote.nasa

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApodApi {

    /** Get today's Astronomy Picture of the Day */
    @GET("planetary/apod")
    suspend fun getToday(
        @Query("api_key") apiKey: String = "DEMO_KEY",
        @Query("thumbs") thumbs: Boolean = true,
    ): ApodResponse

    /** Get APOD for a specific date */
    @GET("planetary/apod")
    suspend fun getByDate(
        @Query("api_key") apiKey: String = "DEMO_KEY",
        @Query("date") date: String,
        @Query("thumbs") thumbs: Boolean = true,
    ): ApodResponse

    /** Get a range of APODs */
    @GET("planetary/apod")
    suspend fun getRange(
        @Query("api_key") apiKey: String = "DEMO_KEY",
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String? = null,
        @Query("thumbs") thumbs: Boolean = true,
    ): List<ApodResponse>

    /** Get random APODs */
    @GET("planetary/apod")
    suspend fun getRandom(
        @Query("api_key") apiKey: String = "DEMO_KEY",
        @Query("count") count: Int = 20,
        @Query("thumbs") thumbs: Boolean = true,
    ): List<ApodResponse>

    companion object {
        const val BASE_URL = "https://api.nasa.gov/"
    }
}

@JsonClass(generateAdapter = true)
data class ApodResponse(
    @Json(name = "date") val date: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "explanation") val explanation: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "hdurl") val hdUrl: String? = null,
    @Json(name = "media_type") val mediaType: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "copyright") val copyright: String? = null,
    @Json(name = "service_version") val serviceVersion: String = "",
) {
    val isImage: Boolean get() = mediaType == "image"
    val bestUrl: String get() = hdUrl ?: url
    val thumbOrUrl: String get() = thumbnailUrl ?: url
}

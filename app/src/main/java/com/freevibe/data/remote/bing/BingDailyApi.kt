package com.freevibe.data.remote.bing

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Bing Image of the Day API.
 * No API key required. Returns curated daily wallpapers.
 * idx=0 is today, idx=7 is 7 days ago. n=8 max per call.
 * Query multiple markets for variety.
 */
interface BingDailyApi {

    @GET("HPImageArchive.aspx")
    suspend fun getImages(
        @Query("format") format: String = "js",
        @Query("idx") idx: Int = 0,
        @Query("n") n: Int = 8,
        @Query("mkt") market: String = "en-US",
    ): BingImageResponse

    companion object {
        const val BASE_URL = "https://www.bing.com/"

        /** Available markets for more image variety */
        val MARKETS = listOf(
            "en-US", "en-GB", "en-AU", "de-DE", "fr-FR",
            "ja-JP", "zh-CN", "en-CA", "en-IN", "es-ES",
        )

        /** Build full-res URL from urlbase */
        fun fullUrl(urlbase: String): String =
            "https://www.bing.com${urlbase}_UHD.jpg"

        /** Build thumbnail URL from urlbase */
        fun thumbUrl(urlbase: String): String =
            "https://www.bing.com${urlbase}_400x240.jpg"
    }
}

@JsonClass(generateAdapter = true)
data class BingImageResponse(
    @Json(name = "images") val images: List<BingImage> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BingImage(
    @Json(name = "startdate") val startDate: String = "",
    @Json(name = "enddate") val endDate: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "urlbase") val urlbase: String = "",
    @Json(name = "copyright") val copyright: String = "",
    @Json(name = "copyrightlink") val copyrightLink: String = "",
    @Json(name = "title") val title: String = "",
)

package com.freevibe.data.remote.wikimedia

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wikimedia Commons MediaWiki API.
 * No API key required. 64M+ free images.
 * Uses generator=categorymembers to browse curated categories,
 * or generator=search for keyword search.
 */
interface WikimediaApi {

    /** Browse images from a category (e.g. "Featured_pictures_on_Wikimedia_Commons") */
    @GET("w/api.php")
    suspend fun getCategoryImages(
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "categorymembers",
        @Query("gcmtitle") category: String = "Category:Featured_pictures_on_Wikimedia_Commons",
        @Query("gcmtype") type: String = "file",
        @Query("gcmlimit") limit: Int = 30,
        @Query("gcmsort") sort: String = "timestamp",
        @Query("gcmdir") direction: String = "desc",
        @Query("gcmcontinue") continueToken: String? = null,
        @Query("prop") prop: String = "imageinfo",
        @Query("iiprop") iiProp: String = "url|user|extmetadata|size",
        @Query("iiurlwidth") thumbWidth: Int = 400,
        @Query("format") format: String = "json",
    ): WikimediaResponse

    /** Search images by keyword */
    @GET("w/api.php")
    suspend fun searchImages(
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "search",
        @Query("gsrsearch") query: String,
        @Query("gsrnamespace") namespace: Int = 6, // File namespace
        @Query("gsrlimit") limit: Int = 30,
        @Query("gsroffset") offset: Int = 0,
        @Query("prop") prop: String = "imageinfo",
        @Query("iiprop") iiProp: String = "url|user|extmetadata|size",
        @Query("iiurlwidth") thumbWidth: Int = 400,
        @Query("format") format: String = "json",
    ): WikimediaResponse

    companion object {
        const val BASE_URL = "https://commons.wikimedia.org/"

        /** Curated categories with high-quality wallpaper-worthy images */
        val CATEGORIES = listOf(
            "Category:Featured_pictures_on_Wikimedia_Commons",
            "Category:Quality_images",
            "Category:Valued_images_sorted_by_promotion_date",
        )
    }
}

// -- Response models --

@JsonClass(generateAdapter = true)
data class WikimediaResponse(
    @Json(name = "query") val query: WikimediaQuery? = null,
    @Json(name = "continue") val continueData: WikimediaContinue? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaQuery(
    @Json(name = "pages") val pages: Map<String, WikimediaPage>? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaContinue(
    @Json(name = "gcmcontinue") val gcmContinue: String? = null,
    @Json(name = "gsroffset") val gsrOffset: Int? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaPage(
    @Json(name = "pageid") val pageId: Int = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "imageinfo") val imageInfo: List<WikimediaImageInfo>? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaImageInfo(
    @Json(name = "url") val url: String = "",             // Full-res URL
    @Json(name = "descriptionurl") val descriptionUrl: String = "",
    @Json(name = "thumburl") val thumbUrl: String? = null,
    @Json(name = "thumbwidth") val thumbWidth: Int = 0,
    @Json(name = "thumbheight") val thumbHeight: Int = 0,
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "size") val size: Int = 0,
    @Json(name = "user") val user: String = "",
    @Json(name = "extmetadata") val extMetadata: WikimediaExtMetadata? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaExtMetadata(
    @Json(name = "ImageDescription") val description: WikimediaMetaValue? = null,
    @Json(name = "LicenseShortName") val license: WikimediaMetaValue? = null,
    @Json(name = "Categories") val categories: WikimediaMetaValue? = null,
)

@JsonClass(generateAdapter = true)
data class WikimediaMetaValue(
    @Json(name = "value") val value: String = "",
)

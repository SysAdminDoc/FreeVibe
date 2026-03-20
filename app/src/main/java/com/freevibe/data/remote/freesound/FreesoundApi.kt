package com.freevibe.data.remote.freesound

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Freesound.org API v2.
 * Free API key required (token auth). 600K+ well-tagged sounds.
 * Preview URLs (MP3 128kbps) available without OAuth2.
 * Rate limits: 60 req/min, 2000 req/day.
 */
interface FreesoundApi {

    /** Text search with filters */
    @GET("apiv2/search/text/")
    suspend fun search(
        @Query("query") query: String,
        @Query("token") token: String,
        @Query("filter") filter: String = "",
        @Query("sort") sort: String = "score",
        @Query("fields") fields: String = SEARCH_FIELDS,
        @Query("page_size") pageSize: Int = 20,
        @Query("page") page: Int = 1,
    ): FreesoundSearchResponse

    /** Get sound details (includes previews) */
    @GET("apiv2/sounds/{id}/")
    suspend fun getSound(
        @Path("id") id: Int,
        @Query("token") token: String,
        @Query("fields") fields: String = SEARCH_FIELDS,
    ): FreesoundSound

    /** Get sounds similar to a given sound */
    @GET("apiv2/sounds/{id}/similar/")
    suspend fun getSimilar(
        @Path("id") id: Int,
        @Query("token") token: String,
        @Query("fields") fields: String = SEARCH_FIELDS,
        @Query("page_size") pageSize: Int = 15,
    ): FreesoundSearchResponse

    companion object {
        const val BASE_URL = "https://freesound.org/"

        const val SEARCH_FIELDS = "id,name,tags,description,duration,username," +
            "previews,avg_rating,num_downloads,license,filesize,type,samplerate"

        /** Build duration filter for Lucene syntax */
        fun durationFilter(minSec: Int = 0, maxSec: Int = 60): String =
            "duration:[$minSec TO $maxSec]"

        /** Combine multiple filters */
        fun combineFilters(vararg filters: String): String =
            filters.filter { it.isNotBlank() }.joinToString(" ")
    }
}

// -- Response models --

@JsonClass(generateAdapter = true)
data class FreesoundSearchResponse(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "next") val next: String? = null,
    @Json(name = "previous") val previous: String? = null,
    @Json(name = "results") val results: List<FreesoundSound> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FreesoundSound(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "tags") val tags: List<String> = emptyList(),
    @Json(name = "description") val description: String = "",
    @Json(name = "duration") val duration: Double = 0.0,
    @Json(name = "username") val username: String = "",
    @Json(name = "previews") val previews: FreesoundPreviews? = null,
    @Json(name = "avg_rating") val avgRating: Double = 0.0,
    @Json(name = "num_downloads") val numDownloads: Int = 0,
    @Json(name = "license") val license: String = "",
    @Json(name = "filesize") val filesize: Long = 0,
    @Json(name = "type") val type: String = "",
    @Json(name = "samplerate") val samplerate: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class FreesoundPreviews(
    @Json(name = "preview-hq-mp3") val previewHqMp3: String? = null,
    @Json(name = "preview-lq-mp3") val previewLqMp3: String? = null,
    @Json(name = "preview-hq-ogg") val previewHqOgg: String? = null,
    @Json(name = "preview-lq-ogg") val previewLqOgg: String? = null,
)

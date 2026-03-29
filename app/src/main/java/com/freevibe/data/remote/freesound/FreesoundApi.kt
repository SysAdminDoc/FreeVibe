package com.freevibe.data.remote.freesound

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Openverse API — aggregates Freesound.org + Jamendo + Wikimedia Audio.
 * Zero auth required for anonymous access (20 req/min, 200/day).
 * Returns CC-licensed sounds with direct preview URLs.
 */
interface FreesoundApi {

    @GET("v1/audio/")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("mature") mature: Boolean = false,
    ): OpenverseAudioResponse
}

@JsonClass(generateAdapter = true)
data class OpenverseAudioResponse(
    @Json(name = "result_count") val resultCount: Int = 0,
    @Json(name = "page_count") val pageCount: Int = 0,
    @Json(name = "page_size") val pageSize: Int = 20,
    @Json(name = "page") val page: Int = 1,
    @Json(name = "results") val results: List<OpenverseAudio> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OpenverseAudio(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "creator") val creator: String = "",
    @Json(name = "license") val license: String = "",
    @Json(name = "provider") val provider: String = "",
    @Json(name = "source") val source: String = "",
    @Json(name = "duration") val duration: Int? = null, // milliseconds
    @Json(name = "bit_rate") val bitRate: Int? = null,
    @Json(name = "sample_rate") val sampleRate: Int? = null,
    @Json(name = "filesize") val filesize: Long? = null,
    @Json(name = "filetype") val filetype: String? = null,
    @Json(name = "tags") val tags: List<OpenverseTag> = emptyList(),
    @Json(name = "foreign_landing_url") val foreignLandingUrl: String = "",
    @Json(name = "category") val category: String? = null,
)

@JsonClass(generateAdapter = true)
data class OpenverseTag(
    @Json(name = "name") val name: String = "",
)

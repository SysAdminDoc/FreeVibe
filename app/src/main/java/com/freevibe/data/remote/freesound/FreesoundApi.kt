package com.freevibe.data.remote.freesound

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface FreesoundApi {

    @GET("apiv2/search/text/")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("filter") filter: String = "",
        @Query("sort") sort: String = "score",
        @Query("fields") fields: String = "id,name,tags,description,license,duration,samplerate,bitrate,filesize,type,previews,username,created,num_downloads,avg_rating",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
    ): FreesoundSearchResponse

    @GET("apiv2/sounds/{id}/")
    suspend fun getSound(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("fields") fields: String = "id,name,tags,description,license,duration,samplerate,bitrate,filesize,type,previews,username,created,num_downloads,avg_rating",
    ): FreesoundSound
}

@JsonClass(generateAdapter = true)
data class FreesoundSearchResponse(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "next") val next: String? = null,
    @Json(name = "previous") val previous: String? = null,
    @Json(name = "results") val results: List<FreesoundSound> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FreesoundSound(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "tags") val tags: List<String> = emptyList(),
    @Json(name = "description") val description: String = "",
    @Json(name = "license") val license: String = "",
    @Json(name = "duration") val duration: Double = 0.0,
    @Json(name = "samplerate") val samplerate: Int = 0,
    @Json(name = "bitrate") val bitrate: Int = 0,
    @Json(name = "filesize") val filesize: Long = 0,
    @Json(name = "type") val type: String = "",
    @Json(name = "previews") val previews: FreesoundPreviews = FreesoundPreviews(),
    @Json(name = "username") val username: String = "",
    @Json(name = "created") val created: String = "",
    @Json(name = "num_downloads") val numDownloads: Int = 0,
    @Json(name = "avg_rating") val avgRating: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class FreesoundPreviews(
    @Json(name = "preview-hq-mp3") val previewHqMp3: String = "",
    @Json(name = "preview-lq-mp3") val previewLqMp3: String = "",
    @Json(name = "preview-hq-ogg") val previewHqOgg: String = "",
    @Json(name = "preview-lq-ogg") val previewLqOgg: String = "",
)

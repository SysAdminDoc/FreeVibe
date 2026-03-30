package com.freevibe.data.remote.freesound

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface FreesoundV2Api {

    @GET("search/text/")
    suspend fun search(
        @Query("query") query: String,
        @Query("token") token: String,
        @Query("fields") fields: String = "id,name,tags,description,duration,avg_rating,num_downloads,previews,license,username",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("filter") filter: String = "",
        @Query("sort") sort: String = "score"
    ): FreesoundV2Response

    companion object {
        const val BASE_URL = "https://freesound.org/apiv2/"
    }
}

@JsonClass(generateAdapter = true)
data class FreesoundV2Response(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<FreesoundV2Sound> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FreesoundV2Sound(
    val id: Int = 0,
    val name: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val duration: Double = 0.0,
    @Json(name = "avg_rating") val avgRating: Double = 0.0,
    @Json(name = "num_downloads") val numDownloads: Int = 0,
    val previews: FreesoundV2Previews = FreesoundV2Previews(),
    val license: String = "",
    val username: String = ""
)

@JsonClass(generateAdapter = true)
data class FreesoundV2Previews(
    @Json(name = "preview-hq-mp3") val previewHqMp3: String = "",
    @Json(name = "preview-lq-mp3") val previewLqMp3: String = ""
)

package com.freevibe.data.remote.soundcloud

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface SoundCloudApi {

    @GET("tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("filter.license") license: String = "cc-by",
        @Query("filter.duration[from]") minDurationMs: Int = 0,
        @Query("filter.duration[to]") maxDurationMs: Int = 60000,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("client_id") clientId: String,
    ): SoundCloudSearchResponse

    companion object {
        const val BASE_URL = "https://api-v2.soundcloud.com/"
    }
}

@JsonClass(generateAdapter = true)
data class SoundCloudSearchResponse(
    @Json(name = "collection") val collection: List<SoundCloudTrack> = emptyList(),
    @Json(name = "total_results") val totalResults: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SoundCloudTrack(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "title") val title: String = "",
    @Json(name = "duration") val duration: Long = 0, // milliseconds
    @Json(name = "stream_url") val streamUrl: String? = null,
    @Json(name = "playback_count") val playbackCount: Int = 0,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "user") val user: SoundCloudUser? = null,
    @Json(name = "license") val license: String = "",
)

@JsonClass(generateAdapter = true)
data class SoundCloudUser(
    @Json(name = "username") val username: String = "",
)

package com.freevibe.data.remote.audius

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApi {
    companion object {
        const val BASE_URL = "https://api.audius.co/"
    }

    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
    ): AudiusTrackResponse
}

@JsonClass(generateAdapter = true)
data class AudiusTrackResponse(
    @Json(name = "data") val data: List<AudiusTrack> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AudiusTrack(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "duration") val duration: Int = 0,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "mood") val mood: String? = null,
    @Json(name = "tags") val tags: String? = null,
    @Json(name = "license") val license: String? = null,
    @Json(name = "is_streamable") val isStreamable: Boolean = false,
    @Json(name = "is_delete") val isDelete: Boolean = false,
    @Json(name = "access") val access: AudiusTrackAccess = AudiusTrackAccess(),
    @Json(name = "stream") val stream: AudiusTrackStream = AudiusTrackStream(),
    @Json(name = "user") val user: AudiusUser = AudiusUser(),
    @Json(name = "permalink") val permalink: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudiusTrackAccess(
    @Json(name = "stream") val stream: Boolean = false,
    @Json(name = "download") val download: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class AudiusTrackStream(
    @Json(name = "url") val url: String = "",
)

@JsonClass(generateAdapter = true)
data class AudiusUser(
    @Json(name = "name") val name: String = "",
    @Json(name = "is_verified") val isVerified: Boolean = false,
)

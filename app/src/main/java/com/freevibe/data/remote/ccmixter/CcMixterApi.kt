package com.freevibe.data.remote.ccmixter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface CcMixterApi {
    companion object {
        const val BASE_URL = "https://ccmixter.org/"
    }

    @GET("api/query")
    suspend fun searchUploads(
        @Query("f") format: String = "json",
        @Query("search") search: String,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "rank",
    ): List<CcMixterUpload>
}

@JsonClass(generateAdapter = true)
data class CcMixterUpload(
    @Json(name = "upload_id") val uploadId: Long = 0,
    @Json(name = "upload_name") val uploadName: String = "",
    @Json(name = "user_name") val userName: String = "",
    @Json(name = "upload_tags") val uploadTags: String = "",
    @Json(name = "file_page_url") val filePageUrl: String = "",
    @Json(name = "license_name") val licenseName: String = "",
    @Json(name = "upload_description_plain") val uploadDescription: String = "",
    @Json(name = "qrank") val rank: Int = 0,
    @Json(name = "files") val files: List<CcMixterFile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CcMixterFile(
    @Json(name = "download_url") val downloadUrl: String = "",
    @Json(name = "file_rawsize") val rawSize: Long = 0,
    @Json(name = "file_name") val fileName: String = "",
    @Json(name = "file_format_info") val formatInfo: CcMixterFormatInfo = CcMixterFormatInfo(),
    @Json(name = "file_extra") val extra: CcMixterFileExtra = CcMixterFileExtra(),
)

@JsonClass(generateAdapter = true)
data class CcMixterFormatInfo(
    @Json(name = "media-type") val mediaType: String = "",
    @Json(name = "default-ext") val defaultExt: String = "",
    @Json(name = "mime_type") val mimeType: String = "",
    @Json(name = "ps") val playSpan: String = "",
)

@JsonClass(generateAdapter = true)
data class CcMixterFileExtra(
    @Json(name = "type") val type: String = "",
)

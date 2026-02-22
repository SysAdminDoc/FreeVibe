package com.freevibe.data.remote.internetarchive

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Internet Archive Advanced Search + Metadata API.
 * No API key required. No auth. 9M+ audio items.
 * https://archive.org/help/aboutsearch.htm
 */
interface InternetArchiveApi {

    /**
     * Search for audio items.
     * Query uses Lucene syntax, e.g. "rain sounds AND mediatype:audio"
     */
    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String> = listOf(
            "identifier", "title", "description", "creator", "licenseurl",
        ),
        @Query("rows") rows: Int = 30,
        @Query("page") page: Int = 1,
        @Query("output") output: String = "json",
        @Query("sort[]") sort: String = "downloads desc",
    ): IASearchResponse

    /** Get full metadata for an item including its file list */
    @GET("metadata/{identifier}")
    suspend fun getMetadata(
        @Path("identifier") identifier: String,
    ): IAMetadataResponse

    companion object {
        const val BASE_URL = "https://archive.org/"

        /** Build a download URL for a specific file in an item */
        fun downloadUrl(identifier: String, filename: String): String =
            "https://archive.org/download/$identifier/$filename"

        /** Details page URL */
        fun detailsUrl(identifier: String): String =
            "https://archive.org/details/$identifier"
    }
}

// ── Search Response ───────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class IASearchResponse(
    @Json(name = "response") val response: IASearchResult,
)

@JsonClass(generateAdapter = true)
data class IASearchResult(
    @Json(name = "numFound") val numFound: Int = 0,
    @Json(name = "start") val start: Int = 0,
    @Json(name = "docs") val docs: List<IASearchDoc> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class IASearchDoc(
    @Json(name = "identifier") val identifier: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "creator") val creator: String? = null,
    @Json(name = "licenseurl") val licenseUrl: String? = null,
)

// ── Metadata Response ─────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class IAMetadataResponse(
    @Json(name = "metadata") val metadata: IAItemMetadata? = null,
    @Json(name = "files") val files: List<IAFile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class IAItemMetadata(
    @Json(name = "identifier") val identifier: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "creator") val creator: String? = null,
    @Json(name = "licenseurl") val licenseUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class IAFile(
    @Json(name = "name") val name: String = "",
    @Json(name = "format") val format: String = "",
    @Json(name = "size") val size: String? = null,
    @Json(name = "length") val length: String? = null, // duration in seconds
    @Json(name = "source") val source: String = "",    // "original" or "derivative"
)

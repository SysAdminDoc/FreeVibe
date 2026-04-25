package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.ccmixter.CcMixterApi
import com.freevibe.data.remote.ccmixter.CcMixterFile
import com.freevibe.data.remote.ccmixter.CcMixterUpload
import com.freevibe.service.SourceMetrics
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.net.ssl.SSLException
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_CCMIXTER = "ccmixter"

@Singleton
class CcMixterRepository @Inject constructor(
    private val api: CcMixterApi,
    private val okHttpClient: OkHttpClient,
    private val sourceMetrics: SourceMetrics,
    moshi: Moshi,
) {
    private val uploadsAdapter = moshi.adapter<List<CcMixterUpload>>(
        Types.newParameterizedType(List::class.java, CcMixterUpload::class.java),
    )

    suspend fun search(
        query: String,
        minDuration: Double = 0.0,
        maxDuration: Double = 180.0,
        limit: Int = 20,
    ): SearchResult<Sound> {
        val uploads = sourceMetrics.measure(SOURCE_CCMIXTER) {
            fetchUploads(query = query, limit = limit)
        }
        val sounds = uploads.mapNotNull { upload ->
            upload.toDomain()
                ?.takeIf { it.duration in minDuration..maxDuration }
        }
        return SearchResult(
            items = sounds,
            totalCount = sounds.size,
            currentPage = 1,
            hasMore = sounds.size >= limit,
        )
    }

    private suspend fun fetchUploads(
        query: String,
        limit: Int,
    ): List<CcMixterUpload> {
        return try {
            api.searchUploads(search = query, limit = limit)
        } catch (error: Exception) {
            if (!shouldRetryCcMixterOverHttp(error)) throw error
            withContext(Dispatchers.IO) {
                fetchUploadsOverHttp(query = query, limit = limit)
            }
        }
    }

    private fun fetchUploadsOverHttp(
        query: String,
        limit: Int,
    ): List<CcMixterUpload> {
        val request = Request.Builder()
            .url(buildCcMixterFallbackUrl(query = query, limit = limit))
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ccMixter fallback failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            uploadsAdapter.fromJson(body).orEmpty()
        }
    }

    private fun CcMixterUpload.toDomain(): Sound? {
        val bestFile = files
            .filter { it.formatInfo.mediaType.equals("audio", ignoreCase = true) }
            .sortedByDescending { filePriority(it) }
            .firstOrNull()
            ?: return null
        val durationSeconds = parseDuration(bestFile.formatInfo.playSpan)
        return Sound(
            id = "ccm_$uploadId",
            source = ContentSource.CCMIXTER,
            name = uploadName.trim(),
            description = uploadDescription.take(200),
            previewUrl = bestFile.downloadUrl,
            downloadUrl = bestFile.downloadUrl,
            duration = durationSeconds,
            fileType = bestFile.formatInfo.defaultExt.ifBlank { bestFile.formatInfo.mimeType },
            fileSize = bestFile.rawSize,
            tags = uploadTags
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it !in COMMON_CCMIXTER_TAGS }
                .take(10),
            license = normalizeLicense(licenseName),
            uploaderName = userName,
            sourcePageUrl = filePageUrl,
        )
    }

    private fun filePriority(file: CcMixterFile): Int {
        var score = 0
        if (file.extra.type.equals("preview", ignoreCase = true)) score += 20
        if (file.formatInfo.defaultExt.equals("mp3", ignoreCase = true)) score += 12
        if (file.downloadUrl.endsWith(".mp3", ignoreCase = true)) score += 8
        return score
    }

    private fun parseDuration(raw: String): Double {
        val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]).toDouble()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toDouble()
            else -> 0.0
        }
    }

    private fun normalizeLicense(raw: String): String = when {
        raw.contains("noncommercial", ignoreCase = true) -> "CC BY-NC"
        raw.contains("sharealike", ignoreCase = true) -> "CC BY-SA"
        raw.contains("attribution", ignoreCase = true) -> "CC BY"
        else -> raw.ifBlank { "CC" }
    }

    private companion object {
        val COMMON_CCMIXTER_TAGS = setOf("sample", "media", "audio", "preview", "flac", "mp3", "")
    }
}

internal fun shouldRetryCcMixterOverHttp(error: Throwable): Boolean = error is SSLException

internal fun buildCcMixterFallbackUrl(
    query: String,
    limit: Int,
): HttpUrl = HttpUrl.Builder()
    .scheme("http")
    .host("ccmixter.org")
    .addPathSegment("api")
    .addPathSegment("query")
    .addQueryParameter("f", "json")
    .addQueryParameter("search", query)
    .addQueryParameter("limit", limit.toString())
    .addQueryParameter("sort", "rank")
    .build()

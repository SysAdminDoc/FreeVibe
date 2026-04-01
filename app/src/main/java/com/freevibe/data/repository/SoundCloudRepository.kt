package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.soundcloud.SoundCloudApi
import com.freevibe.data.remote.soundcloud.SoundCloudTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudRepository @Inject constructor(
    private val api: SoundCloudApi,
) {
    private val clientId: String = BuildConfig.SOUNDCLOUD_CLIENT_ID

    suspend fun search(
        query: String,
        minDurationMs: Int = 0,
        maxDurationMs: Int = 60000,
        limit: Int = 20,
        offset: Int = 0,
    ): SearchResult<Sound> {
        if (clientId.isBlank()) {
            return SearchResult(items = emptyList(), totalCount = 0, currentPage = 1, hasMore = false)
        }

        val response = api.searchTracks(
            query = query,
            minDurationMs = minDurationMs,
            maxDurationMs = maxDurationMs,
            limit = limit,
            offset = offset,
            clientId = clientId,
        )

        val sounds = response.collection
            .filter { it.duration > 0 }
            .map { it.toDomain() }

        return SearchResult(
            items = sounds,
            totalCount = response.totalResults,
            currentPage = (offset / limit) + 1,
            hasMore = sounds.size >= limit,
        )
    }

    suspend fun getRingtones(offset: Int = 0): SearchResult<Sound> =
        search("ringtone melody tone", minDurationMs = 5000, maxDurationMs = 45000, offset = offset)

    suspend fun getNotifications(offset: Int = 0): SearchResult<Sound> =
        search("notification chime beep alert", minDurationMs = 500, maxDurationMs = 10000, offset = offset)

    suspend fun getAlarms(offset: Int = 0): SearchResult<Sound> =
        search("alarm clock morning wake", minDurationMs = 5000, maxDurationMs = 60000, offset = offset)

    private fun SoundCloudTrack.toDomain(): Sound {
        // v2 API doesn't return stream_url; construct from permalink or track ID
        val streamWithAuth = if (streamUrl != null && clientId.isNotBlank()) {
            if (streamUrl.contains("?")) "$streamUrl&client_id=$clientId"
            else "$streamUrl?client_id=$clientId"
        } else if (permalinkUrl != null && clientId.isNotBlank()) {
            "$permalinkUrl/stream?client_id=$clientId"
        } else if (clientId.isNotBlank()) {
            "https://api-v2.soundcloud.com/tracks/$id/stream?client_id=$clientId"
        } else ""

        return Sound(
            id = "sc_$id",
            source = ContentSource.SOUNDCLOUD,
            name = title,
            description = "by ${user?.username ?: "Unknown"}",
            previewUrl = streamWithAuth,
            downloadUrl = streamWithAuth,
            duration = duration / 1000.0,
            tags = emptyList(),
            license = normalizeLicense(license),
            uploaderName = user?.username ?: "Unknown",
            sourcePageUrl = "https://soundcloud.com/tracks/$id",
        )
    }

    private fun normalizeLicense(license: String): String = when {
        license.contains("cc-by-sa", ignoreCase = true) -> "CC BY-SA"
        license.contains("cc-by-nc", ignoreCase = true) -> "CC BY-NC"
        license.contains("cc-by", ignoreCase = true) -> "CC BY"
        license.contains("cc0", ignoreCase = true) || license.contains("no-rights-reserved", ignoreCase = true) -> "CC0"
        else -> license.take(8).uppercase().ifBlank { "CC" }
    }
}

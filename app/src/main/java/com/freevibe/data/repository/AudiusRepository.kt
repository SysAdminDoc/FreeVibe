package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.audius.AudiusApi
import com.freevibe.data.remote.audius.AudiusTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiusRepository @Inject constructor(
    private val api: AudiusApi,
) {
    suspend fun search(
        query: String,
        minDuration: Int = 0,
        maxDuration: Int = 180,
        limit: Int = 20,
    ): SearchResult<Sound> {
        val response = api.searchTracks(query = query, limit = limit)
        val sounds = response.data
            .filter { !it.isDelete }
            .filter { it.duration in minDuration..maxDuration }
            .filter { it.isStreamable || it.access.stream }
            .filter { it.stream.url.isNotBlank() }
            .map { it.toDomain() }
        return SearchResult(
            items = sounds,
            totalCount = sounds.size,
            currentPage = 1,
            hasMore = sounds.size >= limit,
        )
    }

    private fun AudiusTrack.toDomain(): Sound {
        val metadataTags = buildList {
            genre?.takeIf { it.isNotBlank() }?.let(::add)
            mood?.takeIf { it.isNotBlank() }?.let(::add)
            tags?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let(::addAll)
            if (user.isVerified) add("verified")
        }
        return Sound(
            id = "au_$id",
            source = ContentSource.AUDIUS,
            name = title.trim(),
            description = description.orEmpty().take(200),
            previewUrl = stream.url,
            downloadUrl = stream.url,
            duration = duration.toDouble(),
            tags = metadataTags.take(10),
            license = license?.ifBlank { "Audius" } ?: "Audius",
            uploaderName = user.name.ifBlank { "Audius Artist" },
            sourcePageUrl = permalink?.let { "https://audius.co$it" }.orEmpty(),
        )
    }
}

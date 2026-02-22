package com.freevibe.data.repository

import com.freevibe.data.local.IAAudioCacheDao
import com.freevibe.data.model.IAAudioCacheEntity
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.internetarchive.InternetArchiveApi
import com.freevibe.data.remote.toSound
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepository @Inject constructor(
    private val archiveApi: InternetArchiveApi,
    private val audioCacheDao: IAAudioCacheDao,
) {
    /**
     * Search Internet Archive for audio items, then resolve each to
     * a playable MP3 URL via the metadata endpoint.
     * #7: Uses Room cache to skip re-resolving known identifiers.
     */
    suspend fun search(
        query: String = "",
        sort: String = "downloads desc",
        page: Int = 1,
    ): SearchResult<Sound> = coroutineScope {
        val q = buildString {
            if (query.isNotBlank()) append("$query AND ")
            append("mediatype:audio")
        }

        val response = archiveApi.search(
            query = q,
            rows = 20,
            page = page,
            sort = sort,
        )

        val docs = response.response.docs
        val identifiers = docs.map { it.identifier }

        // #7: Check cache first for known audio URLs
        val cached = audioCacheDao.getByIdentifiers(identifiers)
            .associateBy { it.identifier }

        val sounds = docs.map { doc ->
            async {
                // Use cache if available
                cached[doc.identifier]?.let { entry ->
                    return@async doc.toSound(
                        audioUrl = entry.audioUrl,
                        duration = entry.duration,
                        fileSize = entry.fileSize,
                    )
                }

                // Otherwise resolve via metadata API
                try {
                    val meta = archiveApi.getMetadata(doc.identifier)
                    val mp3 = meta.files.firstOrNull { f ->
                        f.name.endsWith(".mp3", ignoreCase = true) &&
                            f.source == "derivative"
                    } ?: meta.files.firstOrNull { f ->
                        f.name.endsWith(".mp3", ignoreCase = true)
                    } ?: meta.files.firstOrNull { f ->
                        f.name.endsWith(".ogg", ignoreCase = true) ||
                            f.name.endsWith(".wav", ignoreCase = true)
                    }

                    mp3?.let { file ->
                        val url = InternetArchiveApi.downloadUrl(doc.identifier, file.name)
                        val dur = file.length?.toDoubleOrNull() ?: 0.0
                        val size = file.size?.toLongOrNull() ?: 0L

                        // Cache the resolved URL
                        audioCacheDao.insert(
                            IAAudioCacheEntity(
                                identifier = doc.identifier,
                                audioUrl = url,
                                duration = dur,
                                fileSize = size,
                            )
                        )

                        doc.toSound(audioUrl = url, duration = dur, fileSize = size)
                    }
                } catch (_: Exception) { null }
            }
        }.awaitAll().filterNotNull()

        SearchResult(
            items = sounds,
            totalCount = response.response.numFound,
            currentPage = page,
            hasMore = docs.size >= 20,
        )
    }

    suspend fun searchRingtones(query: String = "ringtone", page: Int = 1) =
        search(query = query, page = page)

    suspend fun searchNotifications(query: String = "notification sound", page: Int = 1) =
        search(query = query, page = page)

    suspend fun searchAlarms(query: String = "alarm sound", page: Int = 1) =
        search(query = query, page = page)

    suspend fun getTrending(page: Int = 1) =
        search(query = "sound effects", sort = "downloads desc", page = page)
}

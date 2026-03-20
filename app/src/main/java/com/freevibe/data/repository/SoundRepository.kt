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
    suspend fun search(
        query: String = "",
        sort: String = "score",
        page: Int = 1,
        maxDuration: Int = 60,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        return searchIA(query, page, maxDuration, minDuration = minDuration)
    }

    suspend fun searchRingtones(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 3,
    ): SearchResult<Sound> {
        return searchIA("ringtone OR phone OR melody", page, maxDuration, minDuration = minDuration, sort = "downloads desc")
    }

    suspend fun searchNotifications(
        page: Int = 1,
        maxDuration: Int = 8,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        return searchIA("notification OR alert OR chime OR beep OR ding", page, maxDuration, minDuration = minDuration, sort = "downloads desc")
    }

    suspend fun searchAlarms(
        page: Int = 1,
        maxDuration: Int = 20,
        minDuration: Int = 2,
    ): SearchResult<Sound> {
        return searchIA("alarm OR buzzer OR siren OR wake", page, maxDuration, minDuration = minDuration, sort = "downloads desc")
    }

    suspend fun getTrending(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        return searchIA("sound effect OR ringtone OR notification", page, maxDuration, "downloads desc", minDuration)
    }

    /** Search by keywords for "More Like This" */
    suspend fun searchSimilar(keywords: String, excludeId: String): List<Sound> {
        if (keywords.isBlank()) return emptyList()
        return searchIA(keywords, 1, 60).items.filter { it.id != excludeId }.take(15)
    }

    private suspend fun searchIA(
        query: String = "",
        page: Int = 1,
        maxDuration: Int = 60,
        sort: String = "downloads desc",
        minDuration: Int = 0,
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

        val cached = audioCacheDao.getByIdentifiers(identifiers)
            .associateBy { it.identifier }

        val sounds = docs.map { doc ->
            async {
                cached[doc.identifier]?.let { entry ->
                    if (entry.duration > maxDuration || entry.duration < minDuration || entry.duration <= 0) return@async null
                    return@async doc.toSound(
                        audioUrl = entry.audioUrl,
                        duration = entry.duration,
                        fileSize = entry.fileSize,
                    )
                }

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

                        audioCacheDao.insert(
                            IAAudioCacheEntity(
                                identifier = doc.identifier,
                                audioUrl = url,
                                duration = dur,
                                fileSize = size,
                            )
                        )

                        if (dur > maxDuration || dur < minDuration || dur <= 0) return@async null

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
}

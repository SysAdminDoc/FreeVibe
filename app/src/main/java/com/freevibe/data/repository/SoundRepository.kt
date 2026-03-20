package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.local.IAAudioCacheDao
import com.freevibe.data.model.IAAudioCacheEntity
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.freesound.FreesoundApi
import com.freevibe.data.remote.internetarchive.InternetArchiveApi
import com.freevibe.data.remote.toSound
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepository @Inject constructor(
    private val archiveApi: InternetArchiveApi,
    private val freesoundApi: FreesoundApi,
    private val audioCacheDao: IAAudioCacheDao,
) {
    private val freesoundKey = BuildConfig.FREESOUND_API_KEY

    private val hasFreesound: Boolean get() = freesoundKey.isNotBlank()

    // ── Unified search (both sources merged) ────────────────────────

    suspend fun search(
        query: String = "",
        sort: String = "score",
        page: Int = 1,
        maxDuration: Int = 60,
        minDuration: Int = 0,
    ): SearchResult<Sound> = supervisorScope {
        val fsDeferred = if (hasFreesound) {
            async { runCatching { searchFreesound(query, sort, page, maxDuration, minDuration) }.getOrNull() }
        } else null

        val iaDeferred = async {
            runCatching { searchIA(query, page, maxDuration, minDuration = minDuration) }.getOrNull()
        }

        val fsSounds = fsDeferred?.await()?.items ?: emptyList()
        val iaSounds = iaDeferred.await()?.items ?: emptyList()
        val combined = fsSounds + iaSounds
        val hasMore = (fsSounds.size >= 15) || (iaSounds.size >= 15)

        SearchResult(
            items = combined,
            totalCount = combined.size * 10,
            currentPage = page,
            hasMore = hasMore,
        )
    }

    // ── Tab-specific searches ───────────────────────────────────────

    suspend fun searchRingtones(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 3,
    ): SearchResult<Sound> {
        val fsTags = "ring tone melody music phone"
        val iaTags = "ringtone OR phone OR melody"
        return searchDual(fsTags, iaTags, page, minDuration, maxDuration, sort = "rating_desc")
    }

    suspend fun searchNotifications(
        page: Int = 1,
        maxDuration: Int = 8,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        val fsTags = "notification alert ping ding chime beep"
        val iaTags = "notification OR alert OR chime OR beep OR ding"
        return searchDual(fsTags, iaTags, page, minDuration, maxDuration, sort = "rating_desc")
    }

    suspend fun searchAlarms(
        page: Int = 1,
        maxDuration: Int = 20,
        minDuration: Int = 2,
    ): SearchResult<Sound> {
        val fsTags = "alarm buzzer siren wake alert warning"
        val iaTags = "alarm OR buzzer OR siren OR wake"
        return searchDual(fsTags, iaTags, page, minDuration, maxDuration, sort = "rating_desc")
    }

    suspend fun getTrending(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 0,
    ): SearchResult<Sound> = supervisorScope {
        val fsDeferred = if (hasFreesound) {
            async {
                runCatching {
                    searchFreesound("sound effect", "downloads_desc", page, maxDuration, minDuration)
                }.getOrNull()
            }
        } else null

        val iaDeferred = async {
            runCatching {
                searchIA("sound effect OR ringtone OR notification", page, maxDuration, "downloads desc", minDuration)
            }.getOrNull()
        }

        val fsSounds = fsDeferred?.await()?.items ?: emptyList()
        val iaSounds = iaDeferred.await()?.items ?: emptyList()
        val combined = fsSounds + iaSounds

        SearchResult(
            items = combined,
            totalCount = combined.size * 10,
            currentPage = page,
            hasMore = combined.size >= 15,
        )
    }

    // ── Freesound-specific ──────────────────────────────────────────

    private suspend fun searchFreesound(
        query: String,
        sort: String = "score",
        page: Int = 1,
        maxDuration: Int = 60,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        val filter = FreesoundApi.durationFilter(minDuration, maxDuration)
        val response = freesoundApi.search(
            query = query.ifBlank { "sound" },
            token = freesoundKey,
            filter = filter,
            sort = sort,
            page = page,
            pageSize = 20,
        )
        val sounds = response.results.mapNotNull { it.toSound() }
        return SearchResult(
            items = sounds,
            totalCount = response.count,
            currentPage = page,
            hasMore = response.next != null,
        )
    }

    suspend fun getSimilar(soundId: Int): SearchResult<Sound> {
        if (!hasFreesound) return SearchResult(emptyList(), 0, 1, false)
        val response = freesoundApi.getSimilar(
            id = soundId,
            token = freesoundKey,
            pageSize = 15,
        )
        val sounds = response.results.mapNotNull { it.toSound() }
        return SearchResult(
            items = sounds,
            totalCount = sounds.size,
            currentPage = 1,
            hasMore = false,
        )
    }

    // ── Internet Archive (with duration filtering) ──────────────────

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

    // ── Dual-source helper ──────────────────────────────────────────

    private suspend fun searchDual(
        fsQuery: String,
        iaQuery: String,
        page: Int,
        minDuration: Int = 0,
        maxDuration: Int = 60,
        sort: String = "score",
    ): SearchResult<Sound> = supervisorScope {
        val fsDeferred = if (hasFreesound) {
            async {
                runCatching {
                    searchFreesound(fsQuery, sort, page, maxDuration, minDuration)
                }.getOrNull()
            }
        } else null

        val iaDeferred = async {
            runCatching { searchIA(iaQuery, page, maxDuration, minDuration = minDuration) }.getOrNull()
        }

        val fsSounds = fsDeferred?.await()?.items ?: emptyList()
        val iaSounds = iaDeferred.await()?.items ?: emptyList()
        val combined = fsSounds + iaSounds

        SearchResult(
            items = combined,
            totalCount = combined.size * 10,
            currentPage = page,
            hasMore = combined.size >= 10,
        )
    }
}

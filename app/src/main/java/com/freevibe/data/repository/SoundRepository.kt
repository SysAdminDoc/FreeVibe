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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sound repository using Internet Archive.
 *
 * Targets curated sound effect collections to avoid voicemails, podcasts,
 * and other irrelevant audio. Uses a semaphore to limit concurrent metadata
 * fetches and timeouts to prevent hangs.
 */
@Singleton
class SoundRepository @Inject constructor(
    private val archiveApi: InternetArchiveApi,
    private val audioCacheDao: IAAudioCacheDao,
) {
    // Collections known to contain actual sound effects, ringtones, and short clips
    private val soundCollections = listOf(
        "freesound",
        "opensource_audio",
        "sound_effects",
    ).joinToString(" OR ") { "collection:$it" }

    // Exclude collections that contain podcasts, radio, spoken word
    private val excludeJunk = listOf(
        "podcasts", "radio", "radioprograms", "librivoxaudio",
        "audio_bookspoetry", "community_audio", "etree",
    ).joinToString(" ") { "-collection:$it" }

    suspend fun search(
        query: String = "",
        sort: String = "score",
        page: Int = 1,
        maxDuration: Int = 60,
        minDuration: Int = 0,
        onProgress: ((Int, Int) -> Unit)? = null,
        onSoundResolved: ((Sound) -> Unit)? = null,
    ): SearchResult<Sound> {
        val q = buildSoundQuery(query)
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc", onProgress, onSoundResolved)
    }

    suspend fun searchRingtones(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 5,
        onProgress: ((Int, Int) -> Unit)? = null,
        onSoundResolved: ((Sound) -> Unit)? = null,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("ringtone OR melody OR music OR tone OR jingle")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc", onProgress, onSoundResolved)
    }

    suspend fun searchNotifications(
        page: Int = 1,
        maxDuration: Int = 3,
        minDuration: Int = 0,
        onProgress: ((Int, Int) -> Unit)? = null,
        onSoundResolved: ((Sound) -> Unit)? = null,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("notification OR alert OR chime OR beep OR ding OR ping")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc", onProgress, onSoundResolved)
    }

    suspend fun searchAlarms(
        page: Int = 1,
        maxDuration: Int = 40,
        minDuration: Int = 5,
        onProgress: ((Int, Int) -> Unit)? = null,
        onSoundResolved: ((Sound) -> Unit)? = null,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("alarm OR buzzer OR siren OR wake OR warning")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc", onProgress, onSoundResolved)
    }

    suspend fun searchSimilar(keywords: String, excludeId: String): List<Sound> {
        if (keywords.isBlank()) return emptyList()
        val q = buildSoundQuery(keywords)
        return fetchSounds(q, 1, 60, 0, "downloads desc").items
            .filter { it.id != excludeId }
            .take(10)
    }

    private fun buildSoundQuery(userQuery: String): String = buildString {
        if (userQuery.isNotBlank()) append("($userQuery) AND ")
        append("mediatype:audio AND ($soundCollections) $excludeJunk")
    }

    /**
     * Fetch sounds with concurrency-limited metadata resolution.
     * Results are streamed progressively via onSoundResolved callback
     * so the UI can show sounds as they arrive instead of waiting for all.
     */
    private suspend fun fetchSounds(
        query: String,
        page: Int,
        maxDuration: Int,
        minDuration: Int,
        sort: String,
        onProgress: ((resolved: Int, total: Int) -> Unit)? = null,
        onSoundResolved: ((Sound) -> Unit)? = null,
    ): SearchResult<Sound> = coroutineScope {
        val response = archiveApi.search(
            query = query,
            rows = 50,
            page = page,
            sort = sort,
        )

        val docs = response.response.docs
        val identifiers = docs.map { it.identifier }

        val cached = audioCacheDao.getByIdentifiers(identifiers)
            .associateBy { it.identifier }

        val semaphore = Semaphore(10)
        val total = docs.size
        var resolved = 0
        val mutex = Mutex()
        val results = mutableListOf<Sound>()

        docs.map { doc ->
            async {
                val sound: Sound? = cached[doc.identifier]?.let { entry ->
                    if (entry.duration in minDuration.toDouble()..maxDuration.toDouble() && entry.duration > 0) {
                        doc.toSound(audioUrl = entry.audioUrl, duration = entry.duration, fileSize = entry.fileSize)
                    } else null
                } ?: run {
                    semaphore.acquire()
                    try {
                        withTimeoutOrNull(4000L) {
                            resolveMetadata(doc, minDuration, maxDuration)
                        }
                    } finally {
                        semaphore.release()
                    }
                }

                mutex.withLock {
                    resolved++
                    sound?.let { results.add(it); onSoundResolved?.invoke(it) }
                    onProgress?.invoke(resolved, total)
                }
            }
        }.awaitAll()

        SearchResult(
            items = results.toList(),
            totalCount = response.response.numFound,
            currentPage = page,
            hasMore = docs.size >= 50 && results.isNotEmpty(),
        )
    }

    /** Resolve a single item's audio URL and duration from its metadata */
    private suspend fun resolveMetadata(
        doc: com.freevibe.data.remote.internetarchive.IASearchDoc,
        minDuration: Int,
        maxDuration: Int,
    ): Sound? {
        return try {
            val meta = archiveApi.getMetadata(doc.identifier)

            // Prefer derivative MP3s (smaller, faster), then originals
            val audioFile = meta.files.firstOrNull { f ->
                f.name.endsWith(".mp3", ignoreCase = true) && f.source == "derivative"
            } ?: meta.files.firstOrNull { f ->
                f.name.endsWith(".mp3", ignoreCase = true)
            } ?: meta.files.firstOrNull { f ->
                f.name.endsWith(".ogg", ignoreCase = true) ||
                    f.name.endsWith(".wav", ignoreCase = true)
            } ?: return null

            val url = InternetArchiveApi.downloadUrl(doc.identifier, audioFile.name)
            val dur = audioFile.length?.toDoubleOrNull() ?: 0.0
            val size = audioFile.size?.toLongOrNull() ?: 0L

            // Cache for next time (avoids this metadata fetch entirely)
            audioCacheDao.insert(
                IAAudioCacheEntity(
                    identifier = doc.identifier,
                    audioUrl = url,
                    duration = dur,
                    fileSize = size,
                )
            )

            if (dur <= 0 || dur < minDuration || dur > maxDuration) return null

            doc.toSound(audioUrl = url, duration = dur, fileSize = size)
        } catch (_: Exception) { null }
    }
}

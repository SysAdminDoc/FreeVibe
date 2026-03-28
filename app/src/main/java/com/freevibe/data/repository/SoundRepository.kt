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
    ): SearchResult<Sound> {
        val q = buildSoundQuery(query)
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc")
    }

    suspend fun searchRingtones(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 3,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("ringtone OR melody OR music OR tone OR jingle")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc")
    }

    suspend fun searchNotifications(
        page: Int = 1,
        maxDuration: Int = 8,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("notification OR alert OR chime OR beep OR ding OR ping")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc")
    }

    suspend fun searchAlarms(
        page: Int = 1,
        maxDuration: Int = 20,
        minDuration: Int = 2,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("alarm OR buzzer OR siren OR wake OR warning")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc")
    }

    suspend fun getTrending(
        page: Int = 1,
        maxDuration: Int = 30,
        minDuration: Int = 0,
    ): SearchResult<Sound> {
        val q = buildSoundQuery("sound effect OR sfx OR ringtone OR notification")
        return fetchSounds(q, page, maxDuration, minDuration, "downloads desc")
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
     * - Max 5 concurrent metadata fetches (prevents overwhelming IA servers)
     * - 8 second timeout per metadata fetch (prevents hangs)
     * - Fetches extra items to compensate for duration-filtered rejects
     */
    private suspend fun fetchSounds(
        query: String,
        page: Int,
        maxDuration: Int,
        minDuration: Int,
        sort: String,
    ): SearchResult<Sound> = coroutineScope {
        val response = archiveApi.search(
            query = query,
            rows = 50, // Fetch extra to compensate for duration-filtered rejects
            page = page,
            sort = sort,
        )

        val docs = response.response.docs
        val identifiers = docs.map { it.identifier }

        // Batch-check cache first (single DB query, not N queries)
        val cached = audioCacheDao.getByIdentifiers(identifiers)
            .associateBy { it.identifier }

        val semaphore = Semaphore(5) // Max 5 concurrent metadata fetches

        val sounds = docs.map { doc ->
            async {
                // Use cache if available
                cached[doc.identifier]?.let { entry ->
                    if (entry.duration in minDuration.toDouble()..maxDuration.toDouble() && entry.duration > 0) {
                        return@async doc.toSound(
                            audioUrl = entry.audioUrl,
                            duration = entry.duration,
                            fileSize = entry.fileSize,
                        )
                    }
                    return@async null // Cached but wrong duration
                }

                // Fetch metadata with concurrency limit + timeout
                semaphore.acquire()
                try {
                    withTimeoutOrNull(8000L) {
                        resolveMetadata(doc, minDuration, maxDuration)
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().filterNotNull()

        SearchResult(
            items = sounds,
            totalCount = response.response.numFound,
            currentPage = page,
            hasMore = docs.size >= 50 && sounds.isNotEmpty(),
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

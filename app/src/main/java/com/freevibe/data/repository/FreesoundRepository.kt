package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.freesound.FreesoundApi
import com.freevibe.data.remote.freesound.OpenverseAudio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sound repository backed by Openverse API (aggregates Freesound + Jamendo + Wikimedia Audio).
 * Zero auth required — anonymous access at 20 req/min.
 */
@Singleton
class FreesoundRepository @Inject constructor(
    private val api: FreesoundApi,
) {
    suspend fun search(
        query: String,
        minDuration: Double = 0.0,
        maxDuration: Double = 60.0,
        page: Int = 1,
    ): SearchResult<Sound> {
        // Openverse anonymous limit is page_size=20
        val response = api.search(query = query, page = page, pageSize = 20)
        val filtered = response.results
            .filter { audio ->
                val durSec = (audio.duration ?: 0) / 1000.0
                durSec in minDuration..maxDuration && durSec > 0 && audio.url.isNotBlank()
            }
            .map { it.toSound() }

        // If heavy duration filtering left very few results and there are more pages, fetch page 2
        val items = if (filtered.size < 5 && page == 1 && response.pageCount > 1) {
            try {
                val page2 = api.search(query = query, page = 2, pageSize = 20)
                val extra = page2.results
                    .filter { audio ->
                        val durSec = (audio.duration ?: 0) / 1000.0
                        durSec in minDuration..maxDuration && durSec > 0 && audio.url.isNotBlank()
                    }
                    .map { it.toSound() }
                filtered + extra
            } catch (_: Exception) { filtered }
        } else filtered

        return SearchResult(
            items = items,
            totalCount = response.resultCount,
            currentPage = page,
            hasMore = page < response.pageCount,
        )
    }

    suspend fun getTrending(
        minDuration: Double = 0.0,
        maxDuration: Double = 30.0,
        page: Int = 1,
    ): SearchResult<Sound> = search(
        query = "sound effect",
        minDuration = minDuration,
        maxDuration = maxDuration,
        page = page,
    )

    suspend fun getRingtones(page: Int = 1): SearchResult<Sound> = search(
        query = "ringtone phone ring melody",
        minDuration = 8.0,
        maxDuration = 30.0,
        page = page,
    )

    suspend fun getNotifications(page: Int = 1): SearchResult<Sound> = search(
        query = "notification chime beep ding alert",
        minDuration = 0.5,
        maxDuration = 5.0,
        page = page,
    )

    suspend fun getAlarms(page: Int = 1): SearchResult<Sound> = search(
        query = "alarm clock buzzer bell wake",
        minDuration = 3.0,
        maxDuration = 40.0,
        page = page,
    )

    private fun OpenverseAudio.toSound(): Sound {
        val contentSource = when {
            provider.contains("jamendo", ignoreCase = true) || source.contains("jamendo", ignoreCase = true) -> ContentSource.JAMENDO
            provider.contains("wikimedia", ignoreCase = true) || source.contains("wikimedia", ignoreCase = true) -> ContentSource.WIKIMEDIA
            else -> ContentSource.FREESOUND
        }
        val licenseName = when {
            license.contains("cc0", ignoreCase = true) -> "CC0"
            license == "by" -> "CC BY"
            license == "by-sa" -> "CC BY-SA"
            license == "by-nc" -> "CC BY-NC"
            license == "pdm" -> "Public Domain"
            else -> license.uppercase(java.util.Locale.ROOT).take(8)
        }
        val durationSec = (duration ?: 0) / 1000.0
        val format = filetype?.uppercase(java.util.Locale.ROOT)?.ifBlank { null } ?: "MP3"

        return Sound(
            id = "ov_$id",
            source = contentSource,
            name = title.replace("_", " ").trim(),
            description = "",
            previewUrl = url,
            downloadUrl = url,
            duration = durationSec,
            sampleRate = sampleRate ?: 0,
            fileType = format,
            fileSize = filesize ?: 0,
            tags = tags.map { it.name }.take(10),
            license = licenseName,
            uploaderName = creator,
            sourcePageUrl = foreignLandingUrl,
        )
    }
}

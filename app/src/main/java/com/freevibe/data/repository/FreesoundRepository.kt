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
        val response = api.search(query = query, page = page, pageSize = 20)
        val filtered = response.results
            .filter { audio ->
                val durSec = (audio.duration ?: 0) / 1000.0
                durSec in minDuration..maxDuration && audio.url.isNotBlank()
            }
            .map { it.toSound() }

        return SearchResult(
            items = filtered,
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
        query = "ringtone melody tone phone",
        minDuration = 3.0,
        maxDuration = 30.0,
        page = page,
    )

    suspend fun getNotifications(page: Int = 1): SearchResult<Sound> = search(
        query = "notification alert beep chime ding",
        minDuration = 0.5,
        maxDuration = 5.0,
        page = page,
    )

    suspend fun getAlarms(page: Int = 1): SearchResult<Sound> = search(
        query = "alarm buzzer bell wake up clock",
        minDuration = 3.0,
        maxDuration = 30.0,
        page = page,
    )

    private fun OpenverseAudio.toSound(): Sound {
        val licenseName = when {
            license.contains("cc0", ignoreCase = true) -> "CC0"
            license == "by" -> "CC BY"
            license == "by-sa" -> "CC BY-SA"
            license == "by-nc" -> "CC BY-NC"
            license == "pdm" -> "Public Domain"
            else -> license.uppercase().take(8)
        }
        val durationSec = (duration ?: 0) / 1000.0
        val format = filetype?.removePrefix("mp3")?.let { filetype } ?: "MP3"

        return Sound(
            id = "ov_$id",
            source = ContentSource.FREESOUND,
            name = title.replace("_", " ").trim(),
            description = "",
            previewUrl = url,
            downloadUrl = url,
            duration = durationSec,
            sampleRate = sampleRate ?: 0,
            fileType = format.uppercase().replace("MP32", "MP3"),
            fileSize = filesize ?: 0,
            tags = tags.map { it.name }.take(10),
            license = licenseName,
            uploaderName = creator,
            sourcePageUrl = foreignLandingUrl,
        )
    }
}

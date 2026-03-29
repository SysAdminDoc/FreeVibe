package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.freesound.FreesoundApi
import com.freevibe.data.remote.freesound.FreesoundSound
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreesoundRepository @Inject constructor(
    private val api: FreesoundApi,
    private val prefs: PreferencesManager,
) {
    private suspend fun authHeader(): String {
        val key = prefs.freesoundApiKey.first()
        return "Token $key"
    }

    suspend fun search(
        query: String,
        minDuration: Double = 0.0,
        maxDuration: Double = 60.0,
        sort: String = "score",
        page: Int = 1,
    ): SearchResult<Sound> {
        val key = prefs.freesoundApiKey.first()
        if (key.isBlank()) return SearchResult(emptyList(), 0, 1, false)

        val filter = buildString {
            append("duration:[${minDuration} TO ${maxDuration}]")
        }

        val response = api.search(
            auth = "Token $key",
            query = query,
            filter = filter,
            sort = sort,
            page = page,
        )

        return SearchResult(
            items = response.results.map { it.toSound() },
            totalCount = response.count,
            currentPage = page,
            hasMore = response.next != null,
        )
    }

    /** Get trending/popular sounds */
    suspend fun getTrending(
        minDuration: Double = 0.0,
        maxDuration: Double = 30.0,
        page: Int = 1,
    ): SearchResult<Sound> = search(
        query = "sound effect",
        minDuration = minDuration,
        maxDuration = maxDuration,
        sort = "downloads_desc",
        page = page,
    )

    /** Get sounds for ringtones */
    suspend fun getRingtones(page: Int = 1): SearchResult<Sound> = search(
        query = "ringtone melody tone",
        minDuration = 5.0,
        maxDuration = 30.0,
        sort = "rating_desc",
        page = page,
    )

    /** Get sounds for notifications */
    suspend fun getNotifications(page: Int = 1): SearchResult<Sound> = search(
        query = "notification alert beep chime",
        minDuration = 0.5,
        maxDuration = 5.0,
        sort = "rating_desc",
        page = page,
    )

    /** Get sounds for alarms */
    suspend fun getAlarms(page: Int = 1): SearchResult<Sound> = search(
        query = "alarm buzzer bell wake",
        minDuration = 3.0,
        maxDuration = 30.0,
        sort = "rating_desc",
        page = page,
    )

    private fun FreesoundSound.toSound(): Sound {
        val licenseName = when {
            license.contains("CC0") || license.contains("Creative Commons 0") -> "CC0"
            license.contains("Attribution") && license.contains("NonCommercial") -> "CC BY-NC"
            license.contains("Attribution") -> "CC BY"
            else -> license.substringAfterLast("/").take(20)
        }
        return Sound(
            id = "fs_$id",
            source = ContentSource.FREESOUND,
            name = name.replace("_", " ").replace(".${type}", "").trim(),
            description = description.take(200),
            previewUrl = previews.previewHqMp3.ifEmpty { previews.previewLqMp3 },
            downloadUrl = previews.previewHqMp3.ifEmpty { previews.previewLqMp3 },
            duration = duration,
            sampleRate = samplerate,
            fileType = type.uppercase(),
            fileSize = filesize,
            tags = tags.take(10),
            license = licenseName,
            uploaderName = username,
            sourcePageUrl = "https://freesound.org/people/$username/sounds/$id/",
        )
    }
}

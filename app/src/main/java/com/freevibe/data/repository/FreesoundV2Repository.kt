package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.freesound.FreesoundV2Api
import com.freevibe.data.remote.freesound.FreesoundV2Sound
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreesoundV2Repository @Inject constructor(
    private val api: FreesoundV2Api,
    private val prefs: PreferencesManager,
) {
    private suspend fun apiKey(): String = prefs.freesoundApiKey.first().ifBlank { BuildConfig.FREESOUND_API_KEY }

    suspend fun search(
        query: String,
        minDuration: Double = 0.0,
        maxDuration: Double = 60.0,
        page: Int = 1,
        sort: String = "score"
    ): SearchResult<Sound> {
        val key = apiKey()
        if (key.isBlank()) {
            return SearchResult(items = emptyList(), totalCount = 0, currentPage = page, hasMore = false)
        }

        val filter = "duration:[$minDuration TO $maxDuration]"
        val response = api.search(
            query = query,
            filter = filter,
            page = page,
            sort = sort,
            token = key
        )

        val sounds = response.results
            .filter { it.duration > 0 && it.previews.previewHqMp3.isNotBlank() }
            .map { it.toDomain() }

        return SearchResult(
            items = sounds,
            totalCount = response.count,
            currentPage = page,
            hasMore = response.next != null,
        )
    }

    suspend fun getRingtones(page: Int = 1): SearchResult<Sound> =
        search("ringtone melody tone", 5.0, 45.0, page, "downloads_desc")

    suspend fun getNotifications(page: Int = 1): SearchResult<Sound> =
        search("notification chime beep ding alert", 0.5, 10.0, page, "downloads_desc")

    suspend fun getAlarms(page: Int = 1): SearchResult<Sound> =
        search("alarm clock buzzer morning wake", 5.0, 60.0, page, "downloads_desc")

    suspend fun getTrending(page: Int = 1): SearchResult<Sound> =
        search("sound effect", 1.0, 30.0, page, "downloads_desc")

    private fun FreesoundV2Sound.toDomain(): Sound = Sound(
        id = "fs_${id}",
        source = ContentSource.FREESOUND,
        name = name,
        description = description.take(200),
        previewUrl = previews.previewHqMp3,
        downloadUrl = previews.previewHqMp3,
        duration = duration,
        tags = tags.take(10),
        license = normalizeLicense(license),
        uploaderName = username,
        sourcePageUrl = "https://freesound.org/people/${username}/sounds/${id}/"
    )

    private fun normalizeLicense(license: String): String = when {
        license.contains("Attribution", ignoreCase = true) &&
            !license.contains("NonCommercial", ignoreCase = true) &&
            !license.contains("ShareAlike", ignoreCase = true) -> "CC BY"
        license.contains("Creative Commons 0", ignoreCase = true) ||
            license.contains("CC0", ignoreCase = true) -> "CC0"
        license.contains("Attribution-NonCommercial", ignoreCase = true) -> "CC BY-NC"
        license.contains("Attribution-ShareAlike", ignoreCase = true) -> "CC BY-SA"
        else -> license.take(8).uppercase()
    }
}

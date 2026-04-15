package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundUrlResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val youtubeRepo: YouTubeRepository,
) {
    suspend fun resolve(sound: Sound): String? = withContext(Dispatchers.IO) {
        val directCandidates = listOf(sound.downloadUrl, sound.previewUrl)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        sound.youtubeVideoId()?.let { videoId ->
            youtubeRepo.getAudioStreamUrl(videoId)?.let { return@withContext it }
            directCandidates.firstOrNull { candidate -> canFetch(candidate) }?.let { return@withContext it }
            return@withContext directCandidates.firstOrNull()
        }

        if (!shouldValidate(sound)) {
            return@withContext directCandidates.firstOrNull()
        }

        directCandidates.firstOrNull { candidate -> canFetch(candidate) }?.let { return@withContext it }

        resolveFromSourcePage(sound.sourcePageUrl)?.let { return@withContext it }

        directCandidates.firstOrNull()
    }

    private fun shouldValidate(sound: Sound): Boolean =
        sound.source == ContentSource.BUNDLED ||
            sound.source == ContentSource.FREESOUND ||
            sound.sourcePageUrl.contains("freesound.org", ignoreCase = true)

    private fun canFetch(url: String): Boolean {
        val request = Request.Builder().url(url).head().build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 405
            }
        }.getOrDefault(false)
    }

    private fun resolveFromSourcePage(pageUrl: String): String? {
        if (!pageUrl.contains("freesound.org", ignoreCase = true)) return null
        val request = Request.Builder().url(pageUrl).build()
        val html = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        }.getOrNull().orEmpty()
        if (html.isBlank()) return null

        val patterns = HTML_AUDIO_URL_PATTERNS
        return patterns
            .asSequence()
            .mapNotNull { pattern -> pattern.find(html)?.groupValues?.getOrNull(1) }
            .map(::normalizeUrl)
            .firstOrNull { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String): String {
        var normalized = raw.replace("&amp;", "&")
        normalized = normalized
            .replace("https://freesound.orghttps://", "https://")
            .replace("https://freesound.orghttp://", "https://")
        if (normalized.startsWith("//")) {
            normalized = "https:$normalized"
        }
        return normalized.trim()
    }

    companion object {
        private val HTML_AUDIO_URL_PATTERNS = listOf(
            Regex("""data-static-file-url="([^"]+)""""),
            Regex("""twitter:player:stream" content="([^"]+)""""),
            Regex("""data-mp3="([^"]+)""""),
            Regex("""og:audio" content="([^"]+)""""),
        )
    }
}

private fun Sound.youtubeVideoId(): String? =
    takeIf { source == ContentSource.YOUTUBE }
        ?.id
        ?.removePrefix("yt_")
        ?.takeIf { it.isNotBlank() && it != id }

package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube search + stream extraction via NewPipe Extractor.
 * Scrapes YouTube directly — no API key, no Piped instances, no quotas.
 */
@Singleton
class YouTubeRepository @Inject constructor() {

    // Cache resolved stream URLs to avoid re-extracting on replay (bounded LRU, synchronized)
    private val streamCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 50
        }
    )

    /** Check if a video's audio URL is already cached (ready for instant playback) */
    fun isCached(videoId: String): Boolean = streamCache.containsKey(videoId)

    init {
        try {
            NewPipe.init(DownloaderImpl.instance)
            Log.d("YouTubeRepo", "NewPipe Extractor initialized")
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Failed to init NewPipe: ${e.message}", e)
        }
    }

    companion object {
        private val junkPatterns = listOf(
            "top \\d+", "\\d+ best", "compilation", "mix 20\\d\\d", "playlist",
            "ranked", "tier list", "reaction", "review", "tutorial", "how to",
            "part \\d+", "episode", "ep\\.", "podcast", "interview", "live stream",
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }

    suspend fun searchSounds(
        query: String,
        maxDuration: Int = 240,
        minDuration: Int = 0,
    ): SearchResult<Sound> = withContext(Dispatchers.IO) {
        try {
            Log.d("YouTubeRepo", "Searching YouTube for: $query")
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            Log.d("YouTubeRepo", "Got YouTube service")
            val searchExtractor = service.getSearchExtractor(query)
            Log.d("YouTubeRepo", "Created search extractor, fetching page...")
            searchExtractor.fetchPage()
            Log.d("YouTubeRepo", "Page fetched, items: ${searchExtractor.initialPage.items.size}")

            val sounds = searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0 }
                .filter { it.duration in minDuration.toLong()..maxDuration.toLong() }
                .filter { item -> junkPatterns.none { it.containsMatchIn(item.name) } }
                .filter { !it.name.contains("#") } // Skip hashtag-heavy titles
                .map { it.toSound() }

            SearchResult(
                items = sounds,
                totalCount = sounds.size,
                currentPage = 1,
                hasMore = searchExtractor.initialPage.hasNextPage(),
            )
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Search failed for '$query': ${e.javaClass.simpleName}: ${e.message}")
            SearchResult(items = emptyList(), totalCount = 0, currentPage = 1, hasMore = false)
        }
    }

    /** Fast preview URL — checks cache first, then extracts via yt-dlp with worstaudio for speed */
    suspend fun getAudioPreviewUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        streamCache[videoId]?.let { return@withContext it }
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            Log.d("YouTubeRepo", "Extracting preview audio via yt-dlp for $videoId")
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
            request.addOption("-f", "worstaudio") // Fastest to resolve + smallest to buffer
            request.addOption("--get-url")
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out?.trim()?.takeIf { it.isNotBlank() }
            Log.d("YouTubeRepo", "yt-dlp preview result: ${streamUrl?.take(80) ?: "NULL"}")
            streamUrl?.also { streamCache[videoId] = it }
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "getAudioPreviewUrl failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** High quality URL for download/apply — uses bestaudio */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            Log.d("YouTubeRepo", "Extracting best audio via yt-dlp for $videoId")
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
            request.addOption("-f", "bestaudio")
            request.addOption("--get-url")
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out?.trim()
            if (streamUrl.isNullOrBlank()) null else streamUrl
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "getAudioStreamUrl failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun getVideoStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
            request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            request.addOption("--get-url")
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out?.trim()?.lines()?.firstOrNull()
            if (streamUrl.isNullOrBlank()) null else streamUrl
        } catch (_: Exception) { null }
    }

    private fun StreamInfoItem.toSound() = Sound(
        id = "yt_${url.substringAfter("v=").substringBefore("&")}",
        source = ContentSource.YOUTUBE,
        name = name,
        description = "by ${uploaderName ?: "Unknown"}",
        previewUrl = "",
        downloadUrl = "",
        duration = duration.toDouble(),
        tags = emptyList(),
        license = "YouTube",
        uploaderName = uploaderName ?: "Unknown",
        sourcePageUrl = url,
    )
}

/**
 * Minimal Downloader implementation required by NewPipe Extractor.
 * Uses Java's built-in HTTP client.
 */
class DownloaderImpl private constructor() : org.schabi.newpipe.extractor.downloader.Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val url = java.net.URL(request.url())
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = request.httpMethod()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            request.headers().forEach { (key, values) ->
                values.forEach { conn.addRequestProperty(key, it) }
            }

            request.dataToSend()?.let { data ->
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }

            val responseCode = conn.responseCode
            val responseHeaders = conn.headerFields
                .filterKeys { it != null }
                .mapValues { (_, v) -> v }
            val responseBody = try {
                (if (responseCode < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }

            return org.schabi.newpipe.extractor.downloader.Response(
                responseCode,
                conn.responseMessage ?: "",
                responseHeaders,
                responseBody,
                request.url(),
            )
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}

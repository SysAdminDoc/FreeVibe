package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.VoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoWallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val pexelsApi: PexelsApi,
    private val pixabayApi: com.freevibe.data.remote.pixabay.PixabayApi,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    val voteRepo: VoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoWallpapersState())
    val state = _state.asStateFlow()

    // Cache of resolved video stream URLs
    // Bounded cache — evict oldest when exceeding 200 entries
    private val streamUrls = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }.let { java.util.Collections.synchronizedMap(it) }
    private val _resolvedIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvedIds = _resolvedIds.asStateFlow()

    private val junkPatterns = listOf(
        "top \\d+", "\\d+ best", "how to", "tutorial", "review", "setup",
        "compilation", "reaction", "podcast", "interview", "unboxing",
        "FAQ", "help", "guide", "install", "download app", "engine",
        "ranked", "tier list", "vs\\.", "comparison", "explained",
        "official", "trailer", "teaser", "behind the scenes",
        "i tested", "i tried", "i bought", "i found", "must have",
        "you need", "don.?t buy", "worth it", "honest",
        "\\bmake\\b", "\\bfor\\b", "\\byour\\b",
        "3d live", "app demo", "free download", "link in",
        "showing my", "on my phone", "on my android", "on my iphone",
        "samsung galaxy", "\\bios\\b", "\\bsettings\\b", "\\bidea\\b",
        "\\bbackgrounds\\b",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val youtubePortraitQueries = listOf(
        "phone live wallpaper vertical loop",
        "AMOLED live wallpaper vertical phone loop",
        "vertical video wallpaper phone 4K loop",
        "live wallpaper android vertical abstract",
    )
    private val youtubeLandscapeQueries = listOf(
        "live wallpaper desktop 4K loop",
        "landscape live wallpaper widescreen loop",
        "cinematic background loop 4K",
        "nature landscape video wallpaper loop",
    )
    private val youtubeAllQueries = listOf(
        "live wallpaper loop 4K",
        "AMOLED live wallpaper loop",
        "abstract video wallpaper loop",
        "live wallpaper android loop",
    )

    private val pexelsQueries = listOf(
        "mobile wallpaper", "phone wallpaper", "abstract background",
        "nature loop", "neon lights", "space", "ocean waves",
    )

    private val redditSubs = listOf("livewallpapers", "LiveWallpaper", "Cinemagraphs", "perfectloops")

    init { load() }

    fun clearError() = _state.update { it.copy(error = null) }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, pexelsPage = 1, pixabayPage = 1, ytQueryIndex = 0, redditSubIndex = 0, redditAfters = emptyMap(), items = emptyList(), emptyLoadCount = 0) }
        streamUrls.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.isLoadingMore || !_state.value.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        load(loadMore = true)
    }

    fun setOrientation(orientation: OrientationFilter) {
        _state.update { it.copy(orientation = orientation, items = emptyList(), pexelsPage = 1, ytQueryIndex = 0, redditSubIndex = 0, redditAfters = emptyMap(), emptyLoadCount = 0) }
        streamUrls.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query, items = emptyList(), pexelsPage = 1, ytQueryIndex = 0, redditSubIndex = 0, redditAfters = emptyMap(), emptyLoadCount = 0) }
        streamUrls.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun getStreamUrl(id: String): String? = streamUrls[id]

    fun upvote(id: String) { viewModelScope.launch { voteRepo.upvote(id) } }
    fun downvote(id: String) { viewModelScope.launch { voteRepo.downvote(id) } }

    fun applyVideoWallpaper(item: VideoWallpaperItem) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = item.id) }
            try {
                // Get stream URL (cached or resolve)
                val videoUrl = streamUrls[item.id] ?: run {
                    if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Resolving stream URL for apply: ${item.videoId}")
                    val url = youtubeRepo.getVideoStreamUrl(item.videoId)
                    if (url != null) { streamUrls[item.id] = url }
                    url
                }

                if (videoUrl == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not get video URL", Toast.LENGTH_SHORT).show()
                    }
                    _state.update { it.copy(isApplying = null) }
                    return@launch
                }

                if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Downloading video (source: ${item.source})...")
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.filesDir, "live_wallpaper.mp4")
                    if (item.source == "YouTube" && item.videoId.isNotEmpty()) {
                        // YouTube: use yt-dlp for download
                        try {
                            val ytUrl = "https://www.youtube.com/watch?v=${item.videoId}"
                            val request = com.yausername.youtubedl_android.YoutubeDLRequest(ytUrl)
                            request.addOption("-f", "bestvideo[ext=mp4][height<=1080]/best[ext=mp4]/best")
                            request.addOption("-o", cacheFile.absolutePath)
                            request.addOption("--force-overwrites")
                            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                        } catch (e: Exception) {
                            if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "yt-dlp download failed: ${e.message}, using stream URL")
                            okHttpClient.newCall(Request.Builder().url(videoUrl).build()).execute().use { resp ->
                                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                                val body = resp.body ?: throw Exception("Empty response body")
                                body.byteStream().use { input ->
                                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        }
                    } else {
                        // Pexels / direct URL: simple download
                        okHttpClient.newCall(Request.Builder().url(videoUrl).build()).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body ?: throw Exception("Empty response body")
                            body.byteStream().use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                    if (cacheFile.length() < 1024) {
                        throw Exception("Downloaded file too small (${cacheFile.length()} bytes) — likely an error page")
                    }
                    if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Downloaded: ${cacheFile.length() / 1024}KB")
                    cacheFile
                }

                launchOrExportVideoWallpaper(context, file)
                _state.update { it.copy(isApplying = null) }
            } catch (e: Exception) {
                if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Apply failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                _state.update { it.copy(isApplying = null) }
            }
        }
    }

    private var loadJob: Job? = null

    private fun load(loadMore: Boolean = false) {
        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
            if (!loadMore) {
                _state.update { it.copy(isLoading = !it.isRefreshing, error = null) }
            }

            val s = _state.value
            val searchQ = s.searchQuery.ifBlank { null }
            val newItems = mutableListOf<VideoWallpaperItem>()

            kotlinx.coroutines.supervisorScope {
                // 1. Pexels
                val pexelsJob = async(Dispatchers.IO) {
                    try {
                        val key = prefs.pexelsApiKey.first()
                        if (key.isBlank()) return@async emptyList()
                        val query = searchQ ?: pexelsQueries[s.pexelsPage % pexelsQueries.size]
                        val orientation = when (s.orientation) {
                            OrientationFilter.PORTRAIT -> "portrait"
                            OrientationFilter.LANDSCAPE -> "landscape"
                            OrientationFilter.ALL -> "portrait"
                        }
                        val response = pexelsApi.searchVideos(apiKey = key, query = query, orientation = orientation, perPage = 15, page = s.pexelsPage)
                        response.videos.filter { it.duration in 5..120 }.mapNotNull { video ->
                            val file = video.videoFiles
                                .filter { it.fileType == "video/mp4" || it.link.endsWith(".mp4") }
                                .sortedByDescending { it.height ?: 0 }
                                .firstOrNull { (it.height ?: 0) <= 1920 }
                                ?: video.videoFiles.firstOrNull { it.link.endsWith(".mp4") }
                            file?.let {
                                val item = VideoWallpaperItem(id = "px_${video.id}", title = "by ${video.user.name}", thumbnailUrl = video.image, source = "Pexels", duration = video.duration.toLong(), uploaderName = video.user.name, videoWidth = video.width, videoHeight = video.height)
                                streamUrls[item.id] = it.link
                                _resolvedIds.update { it + item.id }
                                item
                            }
                        }
                    } catch (e: Throwable) { if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pexels: ${e.message}"); emptyList() }
                }

                // 2. Reddit — one subreddit per load, rotating, with per-sub pagination
                val redditJob = async(Dispatchers.IO) {
                    val items = mutableListOf<VideoWallpaperItem>()
                    // Load 2 subreddits per call for variety
                    for (offset in 0..1) {
                        val subIdx = (s.redditSubIndex + offset) % redditSubs.size
                        val sub = redditSubs[subIdx]
                        try {
                            val after = s.redditAfters[sub]
                            val query = if (searchQ != null) "search.json?q=${java.net.URLEncoder.encode(searchQ, "UTF-8")}&restrict_sr=on&sort=top&t=all&type=link&limit=25&raw_json=1" else "top.json?t=all&limit=25&raw_json=1"
                            val url = "https://www.reddit.com/r/$sub/$query" + (if (after != null) "&after=$after" else "")
                            val req = Request.Builder().url(url).header("User-Agent", "Aura/${com.freevibe.BuildConfig.VERSION_NAME} (Android; Open Source)").build()
                            val resp = okHttpClient.newCall(req).execute()
                            if (!resp.isSuccessful) { resp.close(); continue }
                            val body = resp.use { it.body?.string() } ?: continue

                            // Per-subreddit after token
                            val afterToken = Regex(""""after"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            _state.update { it.copy(redditAfters = it.redditAfters + (sub to afterToken)) }

                            // Extract video posts with dimensions
                            val videoRegex = Regex(""""fallback_url"\s*:\s*"(https://v\.redd\.it/[^"]+)"""")
                            val titleRegex = Regex(""""title"\s*:\s*"([^"]{2,200})"""")
                            val upsRegex = Regex(""""ups"\s*:\s*(\d+)""")
                            val thumbRegex = Regex(""""thumbnail"\s*:\s*"(https://[^"]+)"""")
                            val widthRegex = Regex(""""reddit_video"\s*:\s*\{[^}]*"width"\s*:\s*(\d+)""")
                            val heightRegex = Regex(""""reddit_video"\s*:\s*\{[^}]*"height"\s*:\s*(\d+)""")

                            val videos = videoRegex.findAll(body).toList()
                            val titles = titleRegex.findAll(body).toList()
                            val upsList = upsRegex.findAll(body).toList()
                            val thumbList = thumbRegex.findAll(body).map { it.groupValues[1].replace("&amp;", "&") }.toList()
                            val widths = widthRegex.findAll(body).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()
                            val heights = heightRegex.findAll(body).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()

                            for (i in videos.indices) {
                                val videoUrl = videos[i].groupValues[1]
                                val title = titles.getOrNull(i + 1)?.groupValues?.getOrNull(1) ?: "Video from r/$sub" // +1 to skip subreddit title
                                val ups = upsList.getOrNull(i)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0
                                val thumb = thumbList.getOrNull(i) ?: ""
                                val vw = widths.getOrNull(i) ?: 0
                                val vh = heights.getOrNull(i) ?: 0

                                if (junkPatterns.none { it.containsMatchIn(title) } && !title.contains("#")) {
                                    val item = VideoWallpaperItem(id = "rd_${videoUrl.hashCode()}", title = title, thumbnailUrl = thumb, source = "Reddit", uploaderName = "r/$sub", popularity = ups, videoWidth = vw, videoHeight = vh)
                                    items.add(item)
                                    streamUrls[item.id] = videoUrl.trimEnd('/') + "/DASH_720.mp4"
                                    _resolvedIds.update { it + item.id }
                                }
                            }
                        } catch (e: Throwable) { if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Reddit $sub: ${e.message}"); continue }
                    }
                    items
                }

                // 3. YouTube
                val ytJob = async(Dispatchers.IO) {
                    try {
                        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                        val ytQueries = when (s.orientation) {
                            OrientationFilter.PORTRAIT -> youtubePortraitQueries
                            OrientationFilter.LANDSCAPE -> youtubeLandscapeQueries
                            OrientationFilter.ALL -> youtubeAllQueries
                        }
                        val orientSuffix = when (s.orientation) {
                            OrientationFilter.PORTRAIT -> " vertical wallpaper"
                            OrientationFilter.LANDSCAPE -> " landscape wallpaper"
                            OrientationFilter.ALL -> " wallpaper"
                        }
                        val query = searchQ?.let { "$it$orientSuffix" } ?: ytQueries[s.ytQueryIndex % ytQueries.size]
                        val extractor = service.getSearchExtractor(query)
                        extractor.fetchPage()
                        extractor.initialPage.items
                            .filterIsInstance<StreamInfoItem>()
                            .filter { it.duration in 5..120 }
                            .filter { item -> junkPatterns.none { it.containsMatchIn(item.name) } }
                            .filter { !it.name.contains("#") }
                            .sortedByDescending { it.viewCount }
                            .map { item ->
                                val vid = item.url.substringAfter("v=").substringBefore("&")
                                // Use thumbnail dimensions as proxy for video orientation
                                val thumb = item.thumbnails.firstOrNull { it.width > 0 && it.height > 0 }
                                    ?: item.thumbnails.firstOrNull()
                                val tw = thumb?.width?.takeIf { it > 0 } ?: 0
                                val th = thumb?.height?.takeIf { it > 0 } ?: 0
                                VideoWallpaperItem(id = "yt_$vid", title = item.name, thumbnailUrl = thumb?.url ?: "", source = "YouTube", duration = item.duration, uploaderName = item.uploaderName ?: "", videoId = vid, popularity = item.viewCount, videoWidth = tw, videoHeight = th)
                            }
                    } catch (e: Throwable) { if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "YouTube: ${e.message}"); emptyList() }
                }

                // 4. Pixabay Videos (animated loops + short videos)
                val pixabayJob = async(Dispatchers.IO) {
                    try {
                        val pbKey = prefs.pixabayApiKey.first()
                        if (pbKey.isBlank()) return@async emptyList<VideoWallpaperItem>()
                        val query = searchQ ?: "abstract loop"
                        val response = pixabayApi.searchVideos(
                            apiKey = pbKey,
                            query = query,
                            videoType = if (searchQ == null) "animation" else "all",
                            page = s.pixabayPage,
                            perPage = 15,
                        )
                        response.hits.filter { it.duration in 2..60 }.mapNotNull { video ->
                            val file = video.videos.medium ?: video.videos.small ?: video.videos.large
                            file?.let {
                                val item = VideoWallpaperItem(
                                    id = "pbv_${video.id}",
                                    title = video.tags.split(",").take(3).joinToString(" ") { it.trim() },
                                    thumbnailUrl = video.thumbnailUrl,
                                    source = "Pixabay",
                                    duration = video.duration.toLong(),
                                    uploaderName = video.user,
                                    popularity = video.views.toLong(),
                                    videoWidth = it.width,
                                    videoHeight = it.height,
                                )
                                streamUrls[item.id] = it.url
                                _resolvedIds.update { it + item.id }
                                item
                            }
                        }
                    } catch (e: Throwable) { if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pixabay: ${e.message}"); emptyList() }
                }

                newItems.addAll(pexelsJob.await())
                newItems.addAll(pixabayJob.await())
                newItems.addAll(redditJob.await())
                newItems.addAll(ytJob.await())
            }

            // Filter by orientation (items with known dimensions are filtered; unknown pass through)
            val orientedItems = when (s.orientation) {
                OrientationFilter.ALL -> newItems
                OrientationFilter.PORTRAIT -> newItems.filter { !it.hasDimensions || it.isPortrait }
                OrientationFilter.LANDSCAPE -> newItems.filter { !it.hasDimensions || it.isLandscape }
            }

            // Deduplicate against existing items
            val existingIds = s.items.map { it.id }.toSet()
            val deduped = orientedItems.filter { it.id !in existingIds }.distinctBy { it.id }

            // Interleave: Pexels, Pixabay, Reddit, YouTube round-robin
            val px = deduped.filter { it.source == "Pexels" }.toMutableList()
            val pb = deduped.filter { it.source == "Pixabay" }.toMutableList()
            val rd = deduped.filter { it.source == "Reddit" }.sortedByDescending { it.popularity }.toMutableList()
            val yt = deduped.filter { it.source == "YouTube" }.sortedByDescending { it.popularity }.toMutableList()
            val mixed = mutableListOf<VideoWallpaperItem>()
            while (px.isNotEmpty() || pb.isNotEmpty() || rd.isNotEmpty() || yt.isNotEmpty()) {
                if (px.isNotEmpty()) mixed.add(px.removeAt(0))
                if (pb.isNotEmpty()) mixed.add(pb.removeAt(0))
                if (rd.isNotEmpty()) mixed.add(rd.removeAt(0))
                if (yt.isNotEmpty()) mixed.add(yt.removeAt(0))
            }

            val newEmptyCount = if (mixed.isEmpty()) s.emptyLoadCount + 1 else 0

            _state.update {
                it.copy(
                    items = if (loadMore) it.items + mixed else mixed,
                    isLoading = false, isLoadingMore = false, isRefreshing = false,
                    hasMore = newEmptyCount < 3,
                    pexelsPage = it.pexelsPage + 1,
                    pixabayPage = it.pixabayPage + 1,
                    ytQueryIndex = it.ytQueryIndex + 1,
                    redditSubIndex = it.redditSubIndex + 2,
                    emptyLoadCount = newEmptyCount,
                )
            }

            // Pre-resolve YouTube URLs
            mixed.filter { it.source == "YouTube" && !streamUrls.containsKey(it.id) }.let { ytItems ->
                val sem = kotlinx.coroutines.sync.Semaphore(5)
                ytItems.forEach { item ->
                    launch {
                        sem.acquire()
                        try { youtubeRepo.getVideoStreamUrl(item.videoId)?.let { streamUrls[item.id] = it; _resolvedIds.update { it + item.id } } } catch (_: Throwable) {} finally { sem.release() }
                    }
                }
            }
            } finally {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false) }
            }
        }
    }
}

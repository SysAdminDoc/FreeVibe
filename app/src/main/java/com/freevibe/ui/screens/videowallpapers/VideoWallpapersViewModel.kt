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

internal data class VideoLoadProgress(
    val hasMore: Boolean,
    val emptyLoadCount: Int,
)

internal fun resolveVideoLoadProgress(
    previousEmptyLoadCount: Int,
    newItemCount: Int,
): VideoLoadProgress {
    val emptyLoadCount = if (newItemCount == 0) previousEmptyLoadCount + 1 else 0
    return VideoLoadProgress(
        hasMore = emptyLoadCount < 3,
        emptyLoadCount = emptyLoadCount,
    )
}

internal fun resolvePexelsVideoQuery(
    page: Int,
    searchQuery: String?,
    fallbackQueries: List<String>,
): String {
    val normalizedQuery = searchQuery?.takeIf { it.isNotBlank() }
    if (normalizedQuery != null) return normalizedQuery
    if (fallbackQueries.isEmpty()) return "mobile wallpaper"
    val queryIndex = (page - 1).coerceAtLeast(0) % fallbackQueries.size
    return fallbackQueries[queryIndex]
}

internal fun resolvePexelsOrientationParam(
    orientation: OrientationFilter,
): String? = when (orientation) {
    OrientationFilter.PORTRAIT -> "portrait"
    OrientationFilter.LANDSCAPE -> "landscape"
    OrientationFilter.ALL -> null
}

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
        _state.update {
            it.copy(
                isRefreshing = true,
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        load()
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.isLoadingMore || !_state.value.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        load(loadMore = true)
    }

    fun setOrientation(orientation: OrientationFilter) {
        _state.update {
            it.copy(
                orientation = orientation,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        streamUrls.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun setFocusFilter(filter: VideoFocusFilter) {
        _state.update {
            it.copy(
                focusFilter = filter,
                items = rankVideoWallpapers(it.items, filter, it.orientation),
            )
        }
    }

    fun search(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
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
                _state.update {
                    it.copy(
                        isLoading = !it.isRefreshing && it.items.isEmpty(),
                        error = null,
                        degradedSources = emptyList(),
                    )
                }
            }

            val s = _state.value
            val searchQ = s.searchQuery.ifBlank { null }
            val newItems = mutableListOf<VideoWallpaperItem>()
            val attemptedSources = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            val failedSources = java.util.Collections.synchronizedSet(mutableSetOf<String>())

            kotlinx.coroutines.supervisorScope {
                // 1. Pexels
                val pexelsJob = async(Dispatchers.IO) {
                    try {
                        val key = prefs.pexelsApiKey.first()
                        if (key.isBlank()) return@async emptyList()
                        attemptedSources += "Pexels"
                        val query = resolvePexelsVideoQuery(
                            page = s.pexelsPage,
                            searchQuery = searchQ,
                            fallbackQueries = pexelsQueries,
                        )
                        val orientation = resolvePexelsOrientationParam(s.orientation)
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
                    } catch (e: Throwable) {
                        failedSources += "Pexels"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pexels: ${e.message}")
                        emptyList()
                    }
                }

                // 2. Reddit — one subreddit per load, rotating, with per-sub pagination
                val redditJob = async(Dispatchers.IO) {
                    val items = mutableListOf<VideoWallpaperItem>()
                    attemptedSources += "Reddit"
                    var redditReached = false
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
                            redditReached = true

                            // Per-subreddit after token
                            val afterToken = REDDIT_AFTER_REGEX.find(body)?.groupValues?.get(1)
                            _state.update { it.copy(redditAfters = it.redditAfters + (sub to afterToken)) }

                            // Extract video posts with dimensions (regexes hoisted to companion)

                            val videos = REDDIT_VIDEO_REGEX.findAll(body).toList()
                            val titles = REDDIT_TITLE_REGEX.findAll(body).toList()
                            val upsList = REDDIT_UPS_REGEX.findAll(body).toList()
                            val thumbList = REDDIT_THUMB_REGEX.findAll(body).map { it.groupValues[1].replace("&amp;", "&") }.toList()
                            val widths = REDDIT_WIDTH_REGEX.findAll(body).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()
                            val heights = REDDIT_HEIGHT_REGEX.findAll(body).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()

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
                    if (!redditReached) failedSources += "Reddit"
                    items
                }

                // 3. YouTube
                val ytJob = async(Dispatchers.IO) {
                    try {
                        attemptedSources += "YouTube"
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
                    } catch (e: Throwable) {
                        failedSources += "YouTube"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "YouTube: ${e.message}")
                        emptyList()
                    }
                }

                // 4. Pixabay Videos (animated loops + short videos)
                val pixabayJob = async(Dispatchers.IO) {
                    try {
                        val pbKey = prefs.pixabayApiKey.first()
                        if (pbKey.isBlank()) return@async emptyList<VideoWallpaperItem>()
                        attemptedSources += "Pixabay"
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
                    } catch (e: Throwable) {
                        failedSources += "Pixabay"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pixabay: ${e.message}")
                        emptyList()
                    }
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

            // Deduplicate against existing items, then rank by fit / loop / battery heuristics.
            val existingIds = if (loadMore) s.items.map { it.id }.toSet() else emptySet()
            val deduped = orientedItems.filter { it.id !in existingIds }.distinctBy { it.id }
            val mixed = rankVideoWallpapers(
                items = deduped,
                filter = s.focusFilter,
                orientation = s.orientation,
            )

            val loadProgress = resolveVideoLoadProgress(
                previousEmptyLoadCount = s.emptyLoadCount,
                newItemCount = mixed.size,
            )
            val sourceFailures = failedSources.toList().sorted()
            val sourceFailureSet = sourceFailures.toSet()
            val attemptedCount = attemptedSources.size
            val allAttemptedFailed = attemptedCount > 0 && sourceFailures.size == attemptedCount
            val preserveCurrentFeed = !loadMore && mixed.isEmpty() && s.items.isNotEmpty()

            _state.update {
                it.copy(
                    items = when {
                        loadMore -> it.items + mixed
                        preserveCurrentFeed -> it.items
                        else -> mixed
                    },
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = when {
                        allAttemptedFailed && preserveCurrentFeed -> "Video sources are unavailable right now. Showing your last good results."
                        allAttemptedFailed -> "Video sources are unavailable right now."
                        sourceFailures.isNotEmpty() && mixed.isEmpty() && !preserveCurrentFeed -> "Limited source availability right now."
                        else -> null
                    },
                    degradedSources = sourceFailures,
                    hasMore = loadProgress.hasMore,
                    pexelsPage = if ("Pexels" !in sourceFailureSet && "Pexels" in attemptedSources) it.pexelsPage + 1 else it.pexelsPage,
                    pixabayPage = if ("Pixabay" !in sourceFailureSet && "Pixabay" in attemptedSources) it.pixabayPage + 1 else it.pixabayPage,
                    ytQueryIndex = if ("YouTube" !in sourceFailureSet && "YouTube" in attemptedSources) it.ytQueryIndex + 1 else it.ytQueryIndex,
                    redditSubIndex = if ("Reddit" !in sourceFailureSet && "Reddit" in attemptedSources) it.redditSubIndex + 2 else it.redditSubIndex,
                    emptyLoadCount = loadProgress.emptyLoadCount,
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

    companion object {
        // Precompiled Reddit JSON parsing regexes — previously allocated per subreddit per fetch.
        private val REDDIT_AFTER_REGEX = Regex(""""after"\s*:\s*"([^"]+)"""")
        private val REDDIT_VIDEO_REGEX = Regex(""""fallback_url"\s*:\s*"(https://v\.redd\.it/[^"]+)"""")
        private val REDDIT_TITLE_REGEX = Regex(""""title"\s*:\s*"([^"]{2,200})"""")
        private val REDDIT_UPS_REGEX = Regex(""""ups"\s*:\s*(\d+)""")
        private val REDDIT_THUMB_REGEX = Regex(""""thumbnail"\s*:\s*"(https://[^"]+)"""")
        private val REDDIT_WIDTH_REGEX = Regex(""""reddit_video"\s*:\s*\{[^}]*"width"\s*:\s*(\d+)""")
        private val REDDIT_HEIGHT_REGEX = Regex(""""reddit_video"\s*:\s*\{[^}]*"height"\s*:\s*(\d+)""")
    }
}

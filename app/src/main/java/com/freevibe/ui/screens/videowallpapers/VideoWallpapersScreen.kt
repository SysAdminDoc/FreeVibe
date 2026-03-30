package com.freevibe.ui.screens.videowallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pexels.PexelsVideo
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.VoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class VideoWallpaperItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val source: String,
    val duration: Long = 0,
    val uploaderName: String = "",
    val videoId: String = "",
    val popularity: Long = 0, // Views (YouTube), upvotes (Reddit), or 0 (Pexels)
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
) {
    val isPortrait: Boolean get() = videoHeight > videoWidth
    val isLandscape: Boolean get() = videoWidth > videoHeight
    val hasDimensions: Boolean get() = videoWidth > 0 && videoHeight > 0
}

enum class OrientationFilter { ALL, PORTRAIT, LANDSCAPE }

data class VideoWallpapersState(
    val items: List<VideoWallpaperItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApplying: String? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val pexelsPage: Int = 1,
    val pixabayPage: Int = 1,
    val ytQueryIndex: Int = 0,
    val redditSubIndex: Int = 0,
    val redditAfters: Map<String, String?> = emptyMap(),
    val hasMore: Boolean = true,
    val emptyLoadCount: Int = 0,
    val orientation: OrientationFilter = OrientationFilter.PORTRAIT,
)

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

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, pexelsPage = 1, pixabayPage = 1, ytQueryIndex = 0, redditSubIndex = 0, redditAfters = emptyMap(), items = emptyList(), emptyLoadCount = 0) }
        streamUrls.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
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
                                resp.body?.byteStream()?.use { input ->
                                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        }
                    } else {
                        // Pexels / direct URL: simple download
                        okHttpClient.newCall(Request.Builder().url(videoUrl).build()).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            resp.body?.byteStream()?.use { input ->
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

                context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                    .edit()
                    .putString("video_path", file.absolutePath)
                    .putString("scale_mode", "zoom")
                    .apply()

                // Save video to Downloads so Gallery can access it and set as wallpaper
                val savedUri = withContext(Dispatchers.IO) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "Aura_Wallpaper_${System.currentTimeMillis()}.mp4")
                            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/Aura")
                        }
                        val uri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let { destUri ->
                            context.contentResolver.openOutputStream(destUri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                        }
                        uri
                    } catch (e: Exception) {
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Failed to save to MediaStore: ${e.message}")
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    // Launch live wallpaper picker directly by component (bypasses intent interceptors)
                    try {
                        val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            setComponent(android.content.ComponentName(
                                "com.android.wallpaper.livepicker",
                                "com.android.wallpaper.livepicker.LiveWallpaperActivity",
                            ))
                            putExtra(
                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                android.content.ComponentName(context, com.freevibe.service.VideoWallpaperService::class.java),
                            )
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Tap 'Aura Video Wallpaper' then 'Set wallpaper'", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Live wallpaper picker failed: ${e.message}")
                        // Fallback: save to gallery
                        if (savedUri != null) {
                            Toast.makeText(context, "Video saved to Movies/Aura. Open in Gallery > Set as wallpaper", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Video saved. Go to Settings > Wallpaper > Live Wallpapers", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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

    private fun load(loadMore: Boolean = false) {
        viewModelScope.launch {
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
                                _resolvedIds.value = _resolvedIds.value + item.id
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
                            val req = Request.Builder().url(url).header("User-Agent", "Aura/5.0.0 (Android; Open Source)").build()
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
                                    _resolvedIds.value = _resolvedIds.value + item.id
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
                                _resolvedIds.value = _resolvedIds.value + item.id
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
                if (px.isNotEmpty()) mixed.add(px.removeFirst())
                if (pb.isNotEmpty()) mixed.add(pb.removeFirst())
                if (rd.isNotEmpty()) mixed.add(rd.removeFirst())
                if (yt.isNotEmpty()) mixed.add(yt.removeFirst())
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
                        try { youtubeRepo.getVideoStreamUrl(item.videoId)?.let { streamUrls[item.id] = it; _resolvedIds.value = _resolvedIds.value + item.id } } catch (_: Throwable) {} finally { sem.release() }
                    }
                }
            }
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoWallpapersScreen(
    viewModel: VideoWallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val resolvedIds by viewModel.resolvedIds.collectAsState()
    val hiddenIds by viewModel.voteRepo.hiddenIds.collectAsState(initial = emptySet())
    val voteCounts by remember(state.items) {
        if (state.items.isNotEmpty()) viewModel.voteRepo.getVoteCounts(state.items.map { it.id })
        else kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())
    val context = LocalContext.current
    var confirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }
    var cropItem by remember { mutableStateOf<Pair<VideoWallpaperItem, String>?>(null) }
    val appContext = context.applicationContext

    // Video crop editor
    cropItem?.let { (item, streamUrl) ->
        VideoCropScreen(
            videoUrl = streamUrl,
            videoTitle = item.title,
            onBack = { cropItem = null },
            onCropped = { croppedFile ->
                cropItem = null
                appContext.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                    .edit()
                    .putString("video_path", croppedFile.absolutePath)
                    .putString("scale_mode", "zoom")
                    .apply()
                try {
                    val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        setComponent(android.content.ComponentName(
                            "com.android.wallpaper.livepicker",
                            "com.android.wallpaper.livepicker.LiveWallpaperActivity",
                        ))
                        putExtra(
                            android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(appContext, com.freevibe.service.VideoWallpaperService::class.java),
                        )
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    Toast.makeText(appContext, "Tap 'Aura Video Wallpaper' then 'Set wallpaper'", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(appContext, "Cropped video saved. Go to Settings > Wallpaper > Live Wallpapers", Toast.LENGTH_LONG).show()
                }
            },
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search video wallpapers...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; viewModel.search(""); focusManager.clearFocus() }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.search(searchQuery); focusManager.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        // Orientation filter + gallery picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrientationFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.orientation == filter,
                    onClick = { viewModel.setOrientation(filter) },
                    label = {
                        Text(when (filter) {
                            OrientationFilter.PORTRAIT -> "Portrait"
                            OrientationFilter.LANDSCAPE -> "Landscape"
                            OrientationFilter.ALL -> "All"
                        })
                    },
                    leadingIcon = if (state.orientation == filter) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
            Spacer(Modifier.weight(1f))
            // Gallery video picker
            val galleryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    // Copy to app storage and set as live wallpaper
                    val path = try {
                        val input = context.contentResolver.openInputStream(it)
                        val cacheFile = java.io.File(context.filesDir, "live_wallpaper.mp4")
                        input?.use { inp -> cacheFile.outputStream().use { out -> inp.copyTo(out) } }
                        cacheFile.absolutePath
                    } catch (_: Exception) { null }
                    if (path != null) {
                        context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                            .edit().putString("video_path", path).apply()
                        try {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, com.freevibe.service.VideoWallpaperService::class.java))
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Go to Settings > Wallpaper > Live Wallpapers", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { galleryLauncher.launch("video/*") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.FolderOpen, "From gallery", modifier = Modifier.size(18.dp))
            }
        }

        // Video category quick-search chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val categories = listOf(
                "Nature" to "nature calm loop",
                "Abstract" to "abstract particles loop",
                "Space" to "space galaxy stars loop",
                "Neon" to "neon lights glow loop",
                "Ocean" to "ocean waves water loop",
                "Fire" to "fire flames embers loop",
                "Cinemagraph" to "cinemagraph subtle motion",
                "Sci-Fi" to "sci-fi futuristic loop",
                "Rain" to "rain drops window loop",
                "Clouds" to "clouds sky timelapse loop",
            )
            categories.forEach { (label, query) ->
                item {
                    AssistChip(
                        onClick = { viewModel.search(query) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Finding video wallpapers...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("No video wallpapers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                    ) {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                        // Infinite scroll
                        val shouldLoadMore by remember {
                            androidx.compose.runtime.derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= listState.layoutInfo.totalItemsCount - 3
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore && !state.isLoadingMore) viewModel.loadMore()
                        }

                        val visibleItems = state.items
                            .filter { it.id !in hiddenIds }
                            .sortedByDescending { voteCounts[it.id] ?: 0 }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleItems, key = { it.id }) { item ->
                                val isResolved = item.id in resolvedIds
                                VideoCard(
                                    item = item,
                                    streamUrl = if (isResolved) viewModel.getStreamUrl(item.id) else null,
                                    isApplying = state.isApplying == item.id,
                                    voteCount = voteCounts[item.id] ?: 0,
                                    onApply = { confirmItem = item },
                                    onUpvote = { viewModel.upvote(item.id) },
                                    onDownvote = { viewModel.downvote(item.id) },
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Applying overlay
    if (state.isApplying != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Downloading & applying...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("This may take a moment", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // Confirmation dialog with crop option
    confirmItem?.let { item ->
        val streamUrl = viewModel.getStreamUrl(item.id)
        val needsCrop = item.hasDimensions && item.isLandscape
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text("Video Wallpaper") },
            text = {
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    if (item.hasDimensions) {
                        Spacer(Modifier.height(2.dp))
                        Text("${item.videoWidth}x${item.videoHeight} (${if (item.isPortrait) "Portrait" else "Landscape"})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (needsCrop) {
                        Text("This is a landscape video. Crop recommended to avoid stretching.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Choose how to apply this video wallpaper.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (streamUrl != null) {
                        if (needsCrop) {
                            // Landscape: Crop is primary action
                            OutlinedButton(onClick = { viewModel.applyVideoWallpaper(item); confirmItem = null }) { Text("Apply Anyway") }
                            Button(onClick = {
                                confirmItem = null
                                cropItem = item to streamUrl
                            }) {
                                Icon(Icons.Default.Crop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Crop")
                            }
                        } else {
                            // Portrait/unknown: Apply is primary, Crop is secondary
                            OutlinedButton(onClick = {
                                confirmItem = null
                                cropItem = item to streamUrl
                            }) {
                                Icon(Icons.Default.Crop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Crop")
                            }
                            Button(onClick = { viewModel.applyVideoWallpaper(item); confirmItem = null }) { Text("Apply") }
                        }
                    } else {
                        Button(onClick = { viewModel.applyVideoWallpaper(item); confirmItem = null }) { Text("Apply") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmItem = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VideoCard(
    item: VideoWallpaperItem,
    streamUrl: String?,
    isApplying: Boolean,
    voteCount: Int = 0,
    onApply: () -> Unit,
    onUpvote: () -> Unit = {},
    onDownvote: () -> Unit = {},
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            // ExoPlayer video or loading placeholder
            if (streamUrl != null) {
                val exoPlayer = remember(streamUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(streamUrl))
                        repeatMode = Player.REPEAT_MODE_ALL
                        volume = 0f
                        prepare()
                        play()
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }

                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                )
            } else {
                // Loading placeholder with thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    coil.compose.AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Loading spinner overlay
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                    }
                }
            }

            // Duration + orientation badges
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.hasDimensions) {
                    Surface(
                        color = if (item.isLandscape) Color(0xFFFF6D00).copy(alpha = 0.85f) else Color(0xFF2979FF).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            if (item.isPortrait) "Portrait" else "Landscape",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = Color.White,
                        )
                    }
                }
                if (item.duration > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("${item.duration}s", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }

            // Source badge
            Surface(
                color = when (item.source) {
                    "Pexels" -> Color(0xFF05A081)
                    "Pixabay" -> Color(0xFF00AB6C)
                    "YouTube" -> Color(0xFFFF0000)
                    "Reddit" -> Color(0xFFFF4500)
                    "Klipy" -> Color(0xFFE040FB)
                    else -> Color(0xFF7C5CFC)
                }.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Text(item.source, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        // Title + Vote + Apply button
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.uploaderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Vote buttons
            IconButton(onClick = onUpvote, modifier = Modifier.size(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ThumbUp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    if (voteCount > 0) Text("$voteCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                }
            }
            IconButton(onClick = onDownvote, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onApply,
                enabled = !isApplying,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isApplying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Wallpaper, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply")
                }
            }
        }
    }
}

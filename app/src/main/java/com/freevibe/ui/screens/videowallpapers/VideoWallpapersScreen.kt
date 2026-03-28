package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val streamUrl: String = "", // Resolved stream URL for playback
)

data class VideoWallpapersState(
    val items: List<VideoWallpaperItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApplying: String? = null,
    val error: String? = null,
)

@HiltViewModel
class VideoWallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val pexelsApi: PexelsApi,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoWallpapersState())
    val state = _state.asStateFlow()

    // Cache of resolved video stream URLs
    private val streamUrls = ConcurrentHashMap<String, String>()
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
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val youtubeQueries = listOf(
        "phone live wallpaper vertical loop",
        "AMOLED live wallpaper vertical phone loop",
        "vertical video wallpaper phone 4K loop",
        "live wallpaper android vertical abstract",
    )

    private val pexelsQueries = listOf(
        "mobile wallpaper", "phone wallpaper", "abstract background",
        "nature loop", "neon lights", "space", "ocean waves",
    )

    init { load() }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    fun getStreamUrl(id: String): String? = streamUrls[id]

    fun applyVideoWallpaper(item: VideoWallpaperItem) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = item.id) }
            try {
                // Get stream URL (cached or resolve)
                val videoUrl = streamUrls[item.id] ?: run {
                    Log.d("VideoWP", "Resolving stream URL for apply: ${item.videoId}")
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

                Log.d("VideoWP", "Downloading video (source: ${item.source})...")
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
                            Log.e("VideoWP", "yt-dlp download failed: ${e.message}, using stream URL")
                            val req = Request.Builder().url(videoUrl).build()
                            val resp = okHttpClient.newCall(req).execute()
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            resp.body?.byteStream()?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    } else {
                        // Pexels / direct URL: simple download
                        val req = Request.Builder().url(videoUrl).build()
                        val resp = okHttpClient.newCall(req).execute()
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.byteStream()?.use { input ->
                            cacheFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    Log.d("VideoWP", "Downloaded: ${cacheFile.length() / 1024}KB")
                    cacheFile
                }

                context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                    .edit().putString("video_path", file.absolutePath).apply()

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
                        Log.e("VideoWP", "Failed to save to MediaStore: ${e.message}")
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
                        Log.e("VideoWP", "Live wallpaper picker failed: ${e.message}")
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
                Log.e("VideoWP", "Apply failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                _state.update { it.copy(isApplying = null) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !it.isRefreshing, error = null) }
            val results = mutableListOf<VideoWallpaperItem>()

            // Pexels — instant, direct MP4 URLs, no extraction needed
            withContext(Dispatchers.IO) {
                try {
                    val key = prefs.pexelsApiKey.first()
                    if (key.isNotBlank()) {
                        val query = pexelsQueries.random()
                        val response = pexelsApi.searchVideos(
                            apiKey = key,
                            query = query,
                            orientation = "portrait",
                            perPage = 20,
                        )
                        response.videos
                            .filter { it.duration in 3..120 }
                            .forEach { video ->
                                val bestFile = video.videoFiles
                                    .filter { it.fileType == "video/mp4" }
                                    .sortedByDescending { it.height }
                                    .firstOrNull { it.height <= 1920 }
                                    ?: video.videoFiles.firstOrNull { it.fileType == "video/mp4" }

                                if (bestFile != null) {
                                    val item = VideoWallpaperItem(
                                        id = "px_${video.id}",
                                        title = "by ${video.user.name}",
                                        thumbnailUrl = video.image,
                                        source = "Pexels",
                                        duration = video.duration.toLong(),
                                        uploaderName = video.user.name,
                                        videoId = "",
                                    )
                                    results.add(item)
                                    // Direct URL — no extraction needed!
                                    streamUrls[item.id] = bestFile.link
                                    _resolvedIds.value = _resolvedIds.value + item.id
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.d("VideoWP", "Pexels failed: ${e.message}")
                }
            }

            // YouTube search
            withContext(Dispatchers.IO) {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                for (query in youtubeQueries) {
                    try {
                        val extractor = service.getSearchExtractor(query)
                        extractor.fetchPage()
                        val items = extractor.initialPage.items
                            .filterIsInstance<StreamInfoItem>()
                            .filter { it.duration in 5..120 }
                            .filter { item -> junkPatterns.none { it.containsMatchIn(item.name) } }
                            .filter { !it.name.contains("#") }
                            .map { item ->
                                val vid = item.url.substringAfter("v=").substringBefore("&")
                                VideoWallpaperItem(
                                    id = "yt_$vid",
                                    title = item.name,
                                    thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                    source = "YouTube",
                                    duration = item.duration,
                                    uploaderName = item.uploaderName ?: "",
                                    videoId = vid,
                                )
                            }
                        results.addAll(items)
                    } catch (_: Exception) { continue }
                }
            }

            // Reddit video wallpapers (r/livewallpapers, r/LiveWallpaper, etc.)
            withContext(Dispatchers.IO) {
                val subs = listOf("livewallpapers", "LiveWallpaper", "Amoledbackgrounds")
                for (sub in subs) {
                    try {
                        val url = "https://www.reddit.com/r/$sub/hot.json?limit=25&raw_json=1"
                        val req = Request.Builder().url(url).header("User-Agent", "Aura/3.0.0 (Android)").build()
                        val resp = okHttpClient.newCall(req).execute()
                        if (!resp.isSuccessful) continue
                        val body = resp.body?.string() ?: continue

                        // Extract video posts with fallback_url (v.redd.it videos)
                        val postRegex = Regex(""""title"\s*:\s*"([^"]{3,120})"[^}]*?"fallback_url"\s*:\s*"(https://v\.redd\.it/[^"]+)"""")
                        val thumbRegex = Regex(""""url_overridden_by_dest"\s*:\s*"(https://(?:preview\.redd\.it|i\.redd\.it)[^"]*\.(?:jpg|png)[^"]*)"""")
                        val thumbs = thumbRegex.findAll(body).toList()

                        postRegex.findAll(body).forEachIndexed { i, match ->
                            val title = match.groupValues[1]
                            val videoUrl = match.groupValues[2]
                            val thumb = thumbs.getOrNull(i)?.groupValues?.getOrNull(1)?.replace("&amp;", "&") ?: ""

                            if (junkPatterns.none { it.containsMatchIn(title) } && !title.contains("#")) {
                                val item = VideoWallpaperItem(
                                    id = "rd_${videoUrl.hashCode()}",
                                    title = title,
                                    thumbnailUrl = thumb,
                                    source = "Reddit",
                                    uploaderName = "r/$sub",
                                    videoId = "",
                                )
                                results.add(item)
                                // Reddit v.redd.it URLs are direct — add DASH_720.mp4 for playback
                                val directUrl = videoUrl.trimEnd('/') + "/DASH_720.mp4"
                                streamUrls[item.id] = directUrl
                                _resolvedIds.value = _resolvedIds.value + item.id
                            }
                        }
                    } catch (_: Exception) { continue }
                }
            }

            // Deduplicate
            val seen = mutableSetOf<String>()
            results.retainAll { seen.add(it.id) }

            _state.update {
                it.copy(items = results, isLoading = false, isRefreshing = false)
            }

            // Pre-resolve YouTube video stream URLs (Pexels + Reddit already have direct URLs)
            val ytItems = results.filter { it.source == "YouTube" && it.videoId.isNotEmpty() && !streamUrls.containsKey(it.id) }
            val semaphore = kotlinx.coroutines.sync.Semaphore(5)
            ytItems.forEach { item ->
                launch {
                    semaphore.acquire()
                    try {
                        val url = youtubeRepo.getVideoStreamUrl(item.videoId)
                        if (url != null) {
                            streamUrls[item.id] = url
                            _resolvedIds.value = _resolvedIds.value + item.id
                        }
                    } catch (_: Exception) {} finally {
                        semaphore.release()
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
                    .edit().putString("video_path", croppedFile.absolutePath).apply()
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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Video Wallpapers") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

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
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                val isResolved = item.id in resolvedIds
                                VideoCard(
                                    item = item,
                                    streamUrl = if (isResolved) viewModel.getStreamUrl(item.id) else null,
                                    isApplying = state.isApplying == item.id,
                                    onApply = { confirmItem = item },
                                )
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
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text("Video Wallpaper") },
            text = {
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Choose how to apply this video wallpaper.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (streamUrl != null) {
                        OutlinedButton(onClick = {
                            confirmItem = null
                            cropItem = item to streamUrl
                        }) {
                            Icon(Icons.Default.Crop, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Crop")
                        }
                    }
                    Button(onClick = { viewModel.applyVideoWallpaper(item); confirmItem = null }) { Text("Apply") }
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
    onApply: () -> Unit,
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

                DisposableEffect(streamUrl) {
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

            // Duration badge
            if (item.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text("${item.duration}s", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            // Source badge
            Surface(
                color = when (item.source) {
                    "Pexels" -> Color(0xFF05A081)
                    "YouTube" -> Color(0xFFFF0000)
                    "Reddit" -> Color(0xFFFF4500)
                    else -> Color(0xFF7C5CFC)
                }.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Text(item.source, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        // Title + Apply button
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.uploaderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
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

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
import com.freevibe.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

                Log.d("VideoWP", "Downloading video...")
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.filesDir, "live_wallpaper.mp4")
                    val request = Request.Builder().url(videoUrl).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.byteStream()?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.d("VideoWP", "Downloaded ${cacheFile.length() / 1024}KB")
                    cacheFile
                }

                context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                    .edit().putString("video_path", file.absolutePath).apply()

                // Extract first frame and set as static wallpaper immediately
                withContext(Dispatchers.IO) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val frame = retriever.getFrameAtTime(0)
                        retriever.release()
                        if (frame != null) {
                            val wm = android.app.WallpaperManager.getInstance(context)
                            wm.setBitmap(
                                frame,
                                null,
                                true,
                                android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK,
                            )
                            frame.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e("VideoWP", "Failed to set static frame: ${e.message}")
                    }
                }

                // Also try to launch live wallpaper picker for animated version
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Video wallpaper applied!", Toast.LENGTH_SHORT).show()
                    try {
                        val intent = android.content.Intent(
                            android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
                        ).apply {
                            putExtra(
                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                android.content.ComponentName(context, com.freevibe.service.VideoWallpaperService::class.java),
                            )
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
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

            // Deduplicate
            val seen = mutableSetOf<String>()
            results.retainAll { seen.add(it.id) }

            _state.update {
                it.copy(items = results, isLoading = false, isRefreshing = false)
            }

            // Pre-resolve video stream URLs in background for ExoPlayer playback
            val semaphore = kotlinx.coroutines.sync.Semaphore(5)
            results.forEach { item ->
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
    var confirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }

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

    // Confirmation dialog
    confirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text("Apply Video Wallpaper") },
            text = {
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("This will download and set as your live wallpaper.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { viewModel.applyVideoWallpaper(item); confirmItem = null }) { Text("Apply") } },
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
                color = Color(0xFFFF0000).copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Text("YouTube", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
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

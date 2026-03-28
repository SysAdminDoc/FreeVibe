package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.WallpaperApplier
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
import javax.inject.Inject

data class VideoWallpaperItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val source: String, // "YouTube" or "Reddit"
    val duration: Long = 0,
    val uploaderName: String = "",
    val sourceUrl: String = "",
    val videoId: String = "", // YouTube video ID
    val directVideoUrl: String = "", // Reddit direct video URL
)

data class VideoWallpapersState(
    val items: List<VideoWallpaperItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApplying: String? = null, // ID of item being applied
    val error: String? = null,
    val selectedTab: Int = 0, // 0=All, 1=YouTube, 2=Reddit
)

@HiltViewModel
class VideoWallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoWallpapersState())
    val state = _state.asStateFlow()

    private val redditSubreddits = listOf(
        "LiveWallpaper", "videowallpapers", "Amoledbackgrounds",
        "wallpaperengine", "PhoneWallpapers",
    )

    private val youtubeQueries = listOf(
        "phone live wallpaper vertical loop no text",
        "AMOLED live wallpaper vertical phone loop",
        "vertical video wallpaper phone 4K loop",
        "live wallpaper android vertical abstract",
    )

    private val junkTitlePatterns = listOf(
        "top \\d+", "\\d+ best", "how to", "tutorial", "review", "setup",
        "compilation", "reaction", "podcast", "interview", "unboxing",
        "FAQ", "help", "guide", "install", "download app", "engine",
        "ranked", "tier list", "vs\\.", "comparison", "explained",
        "official", "trailer", "teaser", "behind the scenes",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    init { load() }

    fun selectTab(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
        load()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    fun applyVideoWallpaper(item: VideoWallpaperItem) {
        viewModelScope.launch {
            _state.update { it.copy(isApplying = item.id) }
            try {
                val videoUrl = if (item.source == "YouTube" && item.videoId.isNotEmpty()) {
                    youtubeRepo.getVideoStreamUrl(item.videoId)
                        ?: youtubeRepo.getAudioStreamUrl(item.videoId) // fallback
                } else {
                    item.directVideoUrl.ifEmpty { null }
                }

                if (videoUrl == null) {
                    _state.update { it.copy(isApplying = null, error = "Could not get video URL") }
                    return@launch
                }

                // Download video to internal storage
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.filesDir, "live_wallpaper.mp4")
                    val request = Request.Builder().url(videoUrl).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.byteStream()?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    cacheFile
                }

                // Save path to SharedPreferences (VideoWallpaperService reads from here)
                context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
                    .edit().putString("video_path", file.absolutePath).apply()

                // Launch live wallpaper picker
                withContext(Dispatchers.Main) {
                    val intent = android.content.Intent(
                        android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
                    ).apply {
                        putExtra(
                            android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(context, com.freevibe.service.VideoWallpaperService::class.java),
                        )
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {
                        Toast.makeText(context, "Video wallpaper saved", Toast.LENGTH_SHORT).show()
                    }
                }

                _state.update { it.copy(isApplying = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isApplying = null, error = "Failed: ${e.message}") }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !it.isRefreshing, error = null) }

            val results = mutableListOf<VideoWallpaperItem>()
            val tab = _state.value.selectedTab

            // YouTube results
            if (tab == 0 || tab == 1) {
                try {
                    val query = youtubeQueries.random()
                    val ytResults = withContext(Dispatchers.IO) {
                        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                        val extractor = service.getSearchExtractor(query)
                        extractor.fetchPage()
                        extractor.initialPage.items
                            .filterIsInstance<StreamInfoItem>()
                            .filter { it.duration in 5..120 }
                            .filter { item -> junkTitlePatterns.none { it.containsMatchIn(item.name) } }
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
                                    sourceUrl = item.url,
                                    videoId = vid,
                                )
                            }
                    }
                    results.addAll(ytResults)
                } catch (_: Exception) {}
            }

            // Reddit results
            if (tab == 0 || tab == 2) {
                try {
                    val redditResults = withContext(Dispatchers.IO) {
                        fetchRedditVideos()
                    }
                    results.addAll(redditResults)
                } catch (_: Exception) {}
            }

            // Shuffle combined results for variety
            _state.update {
                it.copy(
                    items = if (tab == 0) results.shuffled() else results,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
        }
    }

    private fun fetchRedditVideos(): List<VideoWallpaperItem> {
        val items = mutableListOf<VideoWallpaperItem>()
        for (sub in redditSubreddits) {
            try {
                val url = "https://www.reddit.com/r/$sub/hot.json?limit=25&raw_json=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "FreeVibe/2.7.0 (Android)")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue
                val body = response.body?.string() ?: continue

                // Parse Reddit JSON for video posts
                val adapter = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()

                // Simple extraction: find reddit_video URLs
                val videoUrlRegex = Regex(""""fallback_url"\s*:\s*"(https://v\.redd\.it/[^"]+)"""")
                val titleRegex = Regex(""""title"\s*:\s*"([^"]{3,100})"""")
                val thumbnailRegex = Regex(""""url_overridden_by_dest"\s*:\s*"(https://[^"]*(?:preview\.redd\.it|i\.redd\.it)[^"]*\.(?:jpg|png|gif)[^"]*)"""")
                val permalinkRegex = Regex(""""permalink"\s*:\s*"([^"]+)"""")
                val authorRegex = Regex(""""author"\s*:\s*"([^"]+)"""")

                val videos = videoUrlRegex.findAll(body).toList()
                val titles = titleRegex.findAll(body).toList()
                val thumbnails = thumbnailRegex.findAll(body).toList()
                val permalinks = permalinkRegex.findAll(body).toList()
                val authors = authorRegex.findAll(body).toList()

                for (i in videos.indices) {
                    val videoUrl = videos[i].groupValues[1]
                    val title = titles.getOrNull(i)?.groupValues?.getOrNull(1) ?: "Video from r/$sub"
                    val thumb = thumbnails.getOrNull(i)?.groupValues?.getOrNull(1) ?: ""
                    val permalink = permalinks.getOrNull(i)?.groupValues?.getOrNull(1) ?: ""
                    val author = authors.getOrNull(i)?.groupValues?.getOrNull(1) ?: ""

                    items.add(
                        VideoWallpaperItem(
                            id = "rd_${videoUrl.hashCode()}",
                            title = title,
                            thumbnailUrl = thumb.replace("&amp;", "&"),
                            source = "Reddit",
                            uploaderName = "u/$author in r/$sub",
                            sourceUrl = "https://www.reddit.com$permalink",
                            directVideoUrl = videoUrl,
                        )
                    )
                }
            } catch (_: Exception) { continue }
        }
        return items
            .filter { item -> junkTitlePatterns.none { it.containsMatchIn(item.title) } }
            .filter { !it.title.contains("#") }
            .distinctBy { it.directVideoUrl }
    }
}

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoWallpapersScreen(
    viewModel: VideoWallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val tabs = listOf("All", "YouTube", "Reddit")

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Video Wallpapers") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        TabRow(selectedTabIndex = state.selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = state.selectedTab == i,
                    onClick = { viewModel.selectTab(i) },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
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
                state.error != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("No video wallpapers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                    ) {
                        var confirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                VideoWallpaperCard(
                                    item = item,
                                    isApplying = state.isApplying == item.id,
                                    onApply = { confirmItem = item },
                                )
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
                                        Text(
                                            "This will download the video and set it as your live wallpaper.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        viewModel.applyVideoWallpaper(item)
                                        confirmItem = null
                                    }) { Text("Apply") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmItem = null }) { Text("Cancel") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoWallpaperCard(
    item: VideoWallpaperItem,
    isApplying: Boolean,
    onApply: () -> Unit,
) {
    Card(
        onClick = onApply,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        enabled = !isApplying,
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.56f) // Phone portrait ratio
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )

            // Duration badge
            if (item.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        "${item.duration}s",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }

            // Source badge
            Surface(
                color = when (item.source) {
                    "YouTube" -> Color(0xFFFF0000)
                    "Reddit" -> Color(0xFFFF4500)
                    else -> MaterialTheme.colorScheme.primary
                }.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                Text(
                    item.source,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            // Apply overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(8.dp),
            ) {
                if (isApplying) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Applying...", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

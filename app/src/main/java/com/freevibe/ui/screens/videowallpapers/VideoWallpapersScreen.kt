package com.freevibe.ui.screens.videowallpapers

import android.content.Context
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.service.VideoWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.launchLiveWallpaperPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

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

internal fun persistSelectedVideoWallpaper(context: Context, file: File) {
    context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
        .edit()
        .putString("video_path", file.absolutePath)
        .putString("scale_mode", "zoom")
        .apply()
}

internal suspend fun exportVideoToGallery(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
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
        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Failed to export video fallback", e)
        null
    }
}

internal suspend fun launchOrExportVideoWallpaper(context: Context, file: File, isCropped: Boolean = false) {
    persistSelectedVideoWallpaper(context, file)
    when (
        withContext(Dispatchers.Main) {
            launchLiveWallpaperPicker(
                context = context,
                serviceComponent = android.content.ComponentName(context, VideoWallpaperService::class.java),
                tag = "VideoWallpaper",
            )
        }
    ) {
        LiveWallpaperLaunchMode.DIRECT -> {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Aura Video Wallpaper opened. Set wallpaper to finish.", Toast.LENGTH_LONG).show()
            }
        }
        LiveWallpaperLaunchMode.CHOOSER -> {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Choose 'Aura Video Wallpaper' in the picker, then tap Set wallpaper.", Toast.LENGTH_LONG).show()
            }
        }
        null -> {
            val savedUri = exportVideoToGallery(context, file)
            withContext(Dispatchers.Main) {
                val prefix = if (isCropped) "Cropped video" else "Video"
                val message = if (savedUri != null) {
                    "$prefix saved to Movies/Aura for manual setup."
                } else {
                    "$prefix ready. Open Settings > Wallpaper > Live Wallpapers to finish setup."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
    val itemIds = remember(state.items) { state.items.map { it.id } }
    val voteCounts by remember(itemIds) {
        if (itemIds.isNotEmpty()) viewModel.voteRepo.getVoteCounts(itemIds)
        else kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())
    val context = LocalContext.current
    var confirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }
    var cropItem by remember { mutableStateOf<Pair<VideoWallpaperItem, String>?>(null) }
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    // Video crop editor
    cropItem?.let { (item, streamUrl) ->
        VideoCropScreen(
            videoUrl = streamUrl,
            videoTitle = item.title,
            onBack = { cropItem = null },
            onCropped = { croppedFile ->
                cropItem = null
                scope.launch {
                    launchOrExportVideoWallpaper(appContext, croppedFile, isCropped = true)
                }
            },
        )
        return
    }

    var searchQuery by rememberSaveable(state.searchQuery) { mutableStateOf(state.searchQuery) }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
    Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search video wallpapers...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
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
                        { Icon(Icons.Default.Check, "Selected orientation", Modifier.size(16.dp)) }
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
                    val cacheFile = try {
                        val input = context.contentResolver.openInputStream(it)
                        val cacheFile = java.io.File(context.filesDir, "live_wallpaper.mp4")
                        input?.use { inp -> cacheFile.outputStream().use { out -> inp.copyTo(out) } }
                        cacheFile
                    } catch (_: Exception) { null }
                    if (cacheFile != null) {
                        persistSelectedVideoWallpaper(context, cacheFile)
                        when (
                            launchLiveWallpaperPicker(
                                context = context,
                                serviceComponent = android.content.ComponentName(context, VideoWallpaperService::class.java),
                                tag = "VideoWallpaperGallery",
                            )
                        ) {
                            LiveWallpaperLaunchMode.DIRECT -> {
                                Toast.makeText(context, "Aura Video Wallpaper opened. Set wallpaper to finish.", Toast.LENGTH_LONG).show()
                            }
                            LiveWallpaperLaunchMode.CHOOSER -> {
                                Toast.makeText(context, "Choose 'Aura Video Wallpaper' in the picker, then tap Set wallpaper.", Toast.LENGTH_LONG).show()
                            }
                            null -> {
                                Toast.makeText(context, "Video selected. Open Settings > Wallpaper > Live Wallpapers to finish setup.", Toast.LENGTH_LONG).show()
                            }
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
                state.error != null && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            Spacer(Modifier.height(12.dp))
                            Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = { viewModel.refresh() }) { Text("Retry") }
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
                        val activePreviewId by remember(visibleItems, listState) {
                            androidx.compose.runtime.derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val viewportCenter =
                                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                layoutInfo.visibleItemsInfo
                                    .mapNotNull { info ->
                                        visibleItems.getOrNull(info.index)?.id?.let { id ->
                                            id to abs((info.offset + (info.size / 2)) - viewportCenter)
                                        }
                                    }
                                    .minByOrNull { it.second }
                                    ?.first
                            }
                        }

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
                                    shouldPreview = item.id == activePreviewId,
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
                                Icon(Icons.Default.Crop, "Crop video", Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Crop")
                            }
                        } else {
                            // Portrait/unknown: Apply is primary, Crop is secondary
                            OutlinedButton(onClick = {
                                confirmItem = null
                                cropItem = item to streamUrl
                            }) {
                                Icon(Icons.Default.Crop, "Crop video", Modifier.size(16.dp))
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
    } // end Scaffold
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoCard(
    item: VideoWallpaperItem,
    streamUrl: String?,
    shouldPreview: Boolean,
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
            if (streamUrl != null && shouldPreview) {
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
                // Static thumbnail for non-focused cards; show spinner only while unresolved
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
                    if (streamUrl == null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    "Preview video",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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
                    Icon(Icons.Default.ThumbUp, "Upvote video", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    if (voteCount > 0) Text("$voteCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp))
                }
            }
            IconButton(onClick = onDownvote, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.VisibilityOff, "Hide video", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

package com.freevibe.ui.screens.sounds

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.ui.components.SearchHistoryDropdown
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    onCreateRingtone: () -> Unit = {},
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val cachedYtIds by viewModel.cachedYtIds.collectAsState()
    val topHits by viewModel.topHits.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    var quickApplySound by remember { mutableStateOf<Sound?>(null) }
    val focusManager = LocalFocusManager.current
    val isYouTubeTab = state.selectedTab == SoundTab.YOUTUBE

    // Upload state
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { selectedAudioUri = uri; showUploadDialog = true } }

    // Upload dialog
    if (showUploadDialog && selectedAudioUri != null) {
        UploadDialog(
            isUploading = state.isUploading,
            uploadProgress = state.uploadProgress,
            onUpload = { name, category ->
                viewModel.uploadSound(selectedAudioUri!!, name, category)
                showUploadDialog = false
                selectedAudioUri = null
            },
            onDismiss = { showUploadDialog = false; selectedAudioUri = null },
        )
    }

    // Quick Apply bottom sheet
    if (quickApplySound != null) {
        QuickApplySheet(
            sound = quickApplySound!!,
            canApply = viewModel.canWriteSettings(),
            isApplying = state.isApplying,
            onApply = { sound, type -> viewModel.applySound(sound, type); quickApplySound = null },
            onDownload = { viewModel.downloadSound(it); quickApplySound = null },
            onDismiss = { quickApplySound = null },
        )
    }

    // Snackbar for success/error feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        if (state.sounds.isNotEmpty()) {
            state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(Icons.Default.Upload, "Upload Sound")
                }
                ExtendedFloatingActionButton(
                    onClick = onCreateRingtone,
                    icon = { Icon(Icons.Default.ContentCut, null) },
                    text = { Text("Create") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (isYouTubeTab) "Search YouTube or paste URL..."
                            else "Search sounds..."
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isYouTubeTab) Icons.Default.SmartDisplay else Icons.Default.Search,
                            null,
                            tint = if (isYouTubeTab) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""; showSearchHistory = false; focusManager.clearFocus()
                            }) { Icon(Icons.Default.Clear, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            if (isYouTubeTab && isYouTubeUrl(searchQuery)) {
                                viewModel.importYouTubeUrl(searchQuery)
                            } else if (isYouTubeTab) {
                                viewModel.searchYouTube(searchQuery)
                            } else {
                                viewModel.search(searchQuery)
                            }
                        }
                        showSearchHistory = false; focusManager.clearFocus()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }

            // Search history dropdown
            if (showSearchHistory && searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
                SearchHistoryDropdown(
                    recentQueries = recentSearches, isVisible = true,
                    onQueryClick = {
                        searchQuery = it
                        if (isYouTubeTab) viewModel.searchYouTube(it) else viewModel.search(it)
                        showSearchHistory = false; focusManager.clearFocus()
                    },
                    onDeleteQuery = { viewModel.removeSearch(it) },
                    onClearAll = { viewModel.clearSearchHistory() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            // Tab row
            val visibleTabs = SoundTab.entries.filter {
                it != SoundTab.SEARCH || state.selectedTab == SoundTab.SEARCH
            }
            key(visibleTabs.size) {
                ScrollableTabRow(
                    selectedTabIndex = visibleTabs.indexOf(state.selectedTab).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp, divider = {},
                ) {
                    visibleTabs.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.error != null && state.sounds.isEmpty() && !state.isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.selectTab(state.selectedTab) }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }) {
                        SoundsList(
                            sounds = state.sounds,
                            isLoading = state.isLoading,
                            playingId = state.playingId,
                            isLoadingMore = state.isLoadingMore,
                            cachedYtIds = cachedYtIds,
                            filterKey = state.filterKey,
                            onSoundClick = { viewModel.selectSound(it); onSoundClick(it) },
                            onLongPress = { quickApplySound = it },
                            onPlayClick = { viewModel.togglePlayback(it) },
                            onLoadMore = { viewModel.loadMore() },
                            playbackProgress = playbackProgress,
                            topHits = if (state.selectedTab == SoundTab.RINGTONES && state.query.isBlank()) topHits else emptyList(),
                        )
                    }
                }
            }
        }
    }
}

private fun isYouTubeUrl(text: String): Boolean {
    val t = text.trim()
    return t.contains("youtube.com/") || t.contains("youtu.be/") || t.contains("youtube.com/shorts/")
}

// -- Sounds List --

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    isLoading: Boolean,
    playingId: String?,
    isLoadingMore: Boolean,
    cachedYtIds: Set<String>,
    filterKey: Int,
    onSoundClick: (Sound) -> Unit,
    onLongPress: (Sound) -> Unit,
    onPlayClick: (Sound) -> Unit,
    onLoadMore: () -> Unit,
    playbackProgress: Float,
    topHits: List<Sound>,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(filterKey) { listState.scrollToItem(0) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 5 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Top 5 This Week (Ringtones tab only)
        if (topHits.isNotEmpty()) {
            item(key = "tophits_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(20.dp), tint = Color(0xFFFF4444))
                    Text("Top 5 This Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(topHits, key = { "hit_${it.id}" }) { sound ->
                SoundCard(
                    sound = sound,
                    isPlaying = playingId == sound.id,
                    isResolving = sound.id.startsWith("yt_") && sound.id !in cachedYtIds && playingId == sound.id,
                    playbackProgress = if (playingId == sound.id) playbackProgress else 0f,
                    onClick = { onSoundClick(sound) },
                    onLongPress = { onLongPress(sound) },
                    onPlayClick = { onPlayClick(sound) },
                )
            }
            item(key = "tophits_divider") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }

        // Main list
        val topHitIds = topHits.map { it.id }.toSet()
        val filteredSounds = sounds.filter { it.id !in topHitIds }

        items(filteredSounds, key = { it.id }) { sound ->
            SoundCard(
                sound = sound,
                isPlaying = playingId == sound.id,
                isResolving = sound.id.startsWith("yt_") && sound.id !in cachedYtIds && playingId == sound.id,
                playbackProgress = if (playingId == sound.id) playbackProgress else 0f,
                onClick = { onSoundClick(sound) },
                onLongPress = { onLongPress(sound) },
                onPlayClick = { onPlayClick(sound) },
            )
        }

        // Loading spinner
        if (isLoading && sounds.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
        }

        // Empty state
        if (!isLoading && sounds.isEmpty() && topHits.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("No sounds found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Load more spinner
        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        // Bottom spacer for FAB
        item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
    }
}

// -- Sound Card --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundCard(
    sound: Sound,
    isPlaying: Boolean,
    isResolving: Boolean = false,
    playbackProgress: Float = 0f,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onPlayClick: () -> Unit,
) {
    Surface(
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        sound.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Source badge
                        if (sound.source == ContentSource.BUNDLED) {
                            Surface(color = Color(0xFFFFB300).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Aura Picks", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                            }
                        } else if (sound.source == ContentSource.YOUTUBE) {
                            Surface(color = Color(0xFFFF0000).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text("YT", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF0000))
                            }
                        } else if (sound.source == ContentSource.SOUNDCLOUD) {
                            Surface(color = Color(0xFFFF5500).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text("SC", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5500))
                            }
                        } else if (sound.source == ContentSource.COMMUNITY) {
                            Surface(color = Color(0xFF4CAF50).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text("Community", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            }
                        }
                        Text(
                            formatDuration(sound.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") {
                            Text(
                                sound.uploaderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }

                // Chevron
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }

            // Playback waveform
            if (isPlaying && sound.duration > 0) {
                Spacer(Modifier.height(6.dp))
                MiniWaveform(sound.duration, true, playbackProgress, Modifier.fillMaxWidth().padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun MiniWaveform(duration: Double, isPlaying: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val activeColor = MaterialTheme.colorScheme.primary
    val barCount = 50
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i -> (0.2f + 0.8f * ((sin((seed + i * 37) % 360 * 0.0174533) + 1f) / 2f).toFloat()) }
    }
    Canvas(modifier.height(24.dp)) {
        val barWidth = size.width / barCount
        val gap = 1.dp.toPx()
        heights.forEachIndexed { i, height ->
            val x = i * barWidth + barWidth / 2
            val barH = size.height * height
            drawLine(
                color = if (isPlaying && (i.toFloat() / barCount) < progress) activeColor else inactiveColor,
                start = Offset(x, size.height / 2 - barH / 2),
                end = Offset(x, size.height / 2 + barH / 2),
                strokeWidth = (barWidth - gap).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "0:${s.toString().padStart(2, '0')}"
}

// -- Quick Apply Bottom Sheet --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickApplySheet(
    sound: Sound,
    canApply: Boolean,
    isApplying: Boolean,
    onApply: (Sound, ContentType) -> Unit,
    onDownload: (Sound) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(sound.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDuration(sound.duration)}${if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") " - ${sound.uploaderName}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            QuickApplyRow("Set as Ringtone", Icons.Default.Call, canApply && !isApplying) { onApply(sound, ContentType.RINGTONE) }
            QuickApplyRow("Set as Notification", Icons.Default.Notifications, canApply && !isApplying) { onApply(sound, ContentType.NOTIFICATION) }
            QuickApplyRow("Set as Alarm", Icons.Default.Alarm, canApply && !isApplying) { onApply(sound, ContentType.ALARM) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            QuickApplyRow("Download", Icons.Default.Download, !isApplying) { onDownload(sound) }

            if (isApplying) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun QuickApplyRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

// -- Upload Dialog --

@Composable
private fun UploadDialog(
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: (name: String, category: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ringtone") }
    val categories = listOf("ringtone" to "Ringtone", "notification" to "Notification", "alarm" to "Alarm")

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Upload Sound") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sound Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) },
                        )
                    }
                }
                if (isUploading) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(uploadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpload(name.ifBlank { "Untitled" }, selectedCategory) },
                enabled = !isUploading,
            ) { Text("Upload") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
            ) { Text("Cancel") }
        },
    )
}

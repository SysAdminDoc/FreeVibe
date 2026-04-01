package com.freevibe.ui.screens.sounds

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.SearchHistoryDropdown
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    onCreateRingtone: () -> Unit = {},
    initialQuery: String? = null,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val cachedYtIds by viewModel.cachedYtIds.collectAsState()
    val topHits by viewModel.topHits.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val displayTopHits = remember(topHits, state.selectedTab, state.query, state.qualityFilter) {
        if (state.selectedTab == SoundTab.RINGTONES && state.query.isBlank()) {
            rankSounds(topHits, SoundTab.RINGTONES, state.qualityFilter).take(5)
        } else {
            emptyList()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(state.query) { searchQuery = state.query }
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && state.query != initialQuery) {
            viewModel.search(initialQuery)
        }
    }
    var showSearchHistory by remember { mutableStateOf(false) }
    var quickApplySound by remember { mutableStateOf<Sound?>(null) }
    val focusManager = LocalFocusManager.current
    val isYouTubeTab = state.selectedTab == SoundTab.YOUTUBE

    // Upload state
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var awaitingUploadResult by remember { mutableStateOf(false) }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { selectedAudioUri = uri; showUploadDialog = true } }

    // Upload dialog
    if (showUploadDialog && selectedAudioUri != null) {
        UploadDialog(
            isUploading = state.isUploading,
            uploadProgress = state.uploadProgress,
            onUpload = { name, category ->
                awaitingUploadResult = true
                viewModel.uploadSound(selectedAudioUri!!, name, category)
            },
            onDismiss = {
                if (!state.isUploading) {
                    showUploadDialog = false; selectedAudioUri = null
                    awaitingUploadResult = false
                }
            },
        )
        // Auto-dismiss when upload completes
        LaunchedEffect(state.isUploading, state.applySuccess, state.error) {
            if (awaitingUploadResult && !state.isUploading) {
                awaitingUploadResult = false
            }
            if (!state.isUploading && showUploadDialog && state.applySuccess == "Upload complete") {
                showUploadDialog = false
                selectedAudioUri = null
            }
        }
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
        if (state.sounds.isNotEmpty() || displayTopHits.isNotEmpty()) {
            state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(Icons.Default.Upload, "Upload Sound", modifier = Modifier.size(20.dp))
                }
                SmallFloatingActionButton(
                    onClick = onCreateRingtone,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Default.ContentCut, "Create Sound", modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            val visibleTabs = SoundTab.entries.filter {
                it != SoundTab.SEARCH || state.selectedTab == SoundTab.SEARCH
            }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (isYouTubeTab) "YouTube Import" else "Sounds",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                Box {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (isYouTubeTab) "Search YouTube or paste URL..."
                                else "Search sounds, artists, moods"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isYouTubeTab) Icons.Default.SmartDisplay else Icons.Default.Search,
                                null,
                                tint = if (isYouTubeTab) Color(0xFFFF6A5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    showSearchHistory = false
                                    focusManager.clearFocus()
                                    when (state.selectedTab) {
                                        SoundTab.SEARCH -> viewModel.clearSearchMode()
                                        SoundTab.YOUTUBE -> viewModel.clearYouTubeSearch()
                                        else -> Unit
                                    }
                                }) { Icon(Icons.Default.Clear, "Clear") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
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
                            showSearchHistory = false
                            focusManager.clearFocus()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    )
                    SearchHistoryDropdown(
                        recentQueries = recentSearches,
                        isVisible = showSearchHistory && searchQuery.isEmpty(),
                        onQueryClick = {
                            searchQuery = it
                            if (isYouTubeTab) viewModel.searchYouTube(it) else viewModel.search(it)
                            showSearchHistory = false
                            focusManager.clearFocus()
                        },
                        onDeleteQuery = { viewModel.removeSearch(it) },
                        onClearAll = { viewModel.clearSearchHistory() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleTabs, key = { it.name }) { tab ->
                        FilterChip(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = {
                                Text(
                                    soundTabLabel(tab),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            leadingIcon = if (state.selectedTab == tab) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(18.dp),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = state.selectedTab == tab,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                                disabledSelectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SoundQualityFilter.entries, key = { it.name }) { filter ->
                        AssistChip(
                            onClick = { viewModel.setQualityFilter(filter) },
                            label = { Text(soundFilterLabel(filter)) },
                            leadingIcon = if (state.qualityFilter == filter) {
                                { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (state.qualityFilter == filter) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
                                },
                            ),
                        )
                    }
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.error != null && state.sounds.isEmpty() && displayTopHits.isEmpty() && !state.isLoading && !state.isRefreshing) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = "Error", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }) {
                        SoundsList(
                            sounds = state.sounds,
                            selectedTab = state.selectedTab,
                            query = state.query,
                            isLoading = state.isLoading,
                            isRefreshing = state.isRefreshing,
                            playingId = state.playingId,
                            resolvingId = state.resolvingId,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            cachedYtIds = cachedYtIds,
                            filterKey = state.filterKey,
                            onSoundClick = { viewModel.selectSound(it); onSoundClick(it) },
                            onLongPress = { quickApplySound = it },
                            onPlayClick = { viewModel.togglePlayback(it) },
                            onLoadMore = { viewModel.loadMore() },
                            playbackProgress = playbackProgress,
                            topHits = displayTopHits,
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

private fun soundTabLabel(tab: SoundTab): String = when (tab) {
    SoundTab.RINGTONES -> "Ringtones"
    SoundTab.NOTIFICATIONS -> "Notifications"
    SoundTab.ALARMS -> "Alarms"
    SoundTab.YOUTUBE -> "YouTube"
    SoundTab.COMMUNITY -> "Community"
    SoundTab.SEARCH -> "Search"
}

// -- Sounds List --

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    selectedTab: SoundTab,
    query: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    playingId: String?,
    resolvingId: String?,
    isLoadingMore: Boolean,
    hasMore: Boolean,
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
    LaunchedEffect(shouldLoadMore, hasMore) {
        if (hasMore && shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Top 5 This Week (Ringtones tab only)
        if (topHits.isNotEmpty()) {
            item(key = "tophits_header", contentType = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trending", Modifier.size(20.dp), tint = Color(0xFFFF4444))
                    Text("Top 5 This Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(topHits, key = { "hit_${it.id}" }, contentType = { "sound_card" }) { sound ->
                SoundCard(
                    sound = sound,
                    tab = SoundTab.RINGTONES,
                    isPlaying = playingId == sound.id,
                    isResolving = sound.id == resolvingId,
                    playbackProgress = if (playingId == sound.id) playbackProgress else 0f,
                    onClick = { onSoundClick(sound) },
                    onLongPress = { onLongPress(sound) },
                    onPlayClick = { onPlayClick(sound) },
                )
            }
            item(key = "tophits_divider", contentType = "divider") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }

        // Main list
        val topHitIds = topHits.map { it.id }.toSet()
        val filteredSounds = sounds.filter { it.id !in topHitIds }

        items(filteredSounds, key = { it.id }, contentType = { "sound_card" }) { sound ->
            SoundCard(
                sound = sound,
                tab = selectedTab,
                isPlaying = playingId == sound.id,
                isResolving = sound.id == resolvingId,
                playbackProgress = if (playingId == sound.id) playbackProgress else 0f,
                onClick = { onSoundClick(sound) },
                onLongPress = { onLongPress(sound) },
                onPlayClick = { onPlayClick(sound) },
            )
        }

        // Loading spinner
        if ((isLoading || isRefreshing) && sounds.isEmpty() && topHits.isEmpty()) {
            item(key = "loading", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
        }

        // Empty state
        if (!isLoading && !isRefreshing && sounds.isEmpty() && topHits.isEmpty()) {
            item(key = "empty") {
                val (icon, title, supportingText) = soundsEmptyState(selectedTab, query)
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = title, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    supportingText?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        // Load more spinner
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

    }
}

private fun soundsEmptyState(
    selectedTab: SoundTab,
    query: String,
): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String?> = when {
    selectedTab == SoundTab.YOUTUBE && query.isBlank() -> Triple(
        Icons.Default.SmartDisplay,
        "Search YouTube or paste a video URL",
        "Import audio from a specific video or try a short search like calm ringtone.",
    )
    selectedTab == SoundTab.COMMUNITY -> Triple(
        Icons.Default.UploadFile,
        "No community sounds yet",
        "Uploads will appear here once the community feed has content.",
    )
    selectedTab == SoundTab.SEARCH && query.isNotBlank() -> Triple(
        Icons.Default.MusicOff,
        "No sounds found for \"$query\"",
        "Try fewer words, a broader mood, or another source tab.",
    )
    else -> Triple(
        Icons.Default.MusicOff,
        "No sounds found",
        "Try another tab or switch to a different quality filter.",
    )
}

// -- Sound Card --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundCard(
    sound: Sound,
    tab: SoundTab,
    isPlaying: Boolean,
    isResolving: Boolean = false,
    playbackProgress: Float = 0f,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onPlayClick: () -> Unit,
) {
    val showUploader = sound.uploaderName.isNotEmpty() &&
        sound.uploaderName != "Unknown" &&
        !(sound.source == ContentSource.BUNDLED && sound.uploaderName == "Aura Picks")
    val (sourceLabel, sourceColor) = soundSourceTone(sound.source)
    val badges = remember(sound, tab) { soundBadges(sound, tab) }
    Surface(
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
        shadowElevation = if (isPlaying) 12.dp else 6.dp,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 14.dp)) {
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
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        sound.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Source badge
                        Surface(color = sourceColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                sourceLabel,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = if (sound.source == ContentSource.BUNDLED) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        Text(
                            formatDuration(sound.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showUploader) {
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
                    if (badges.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(badges, key = { badge -> "${sound.id}_$badge" }) { badge ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        badge,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Chevron
                Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }

            // Resolving indicator
            if (isResolving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Resolving audio...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 56.dp),
                )
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

private fun soundFilterLabel(filter: SoundQualityFilter): String = when (filter) {
    SoundQualityFilter.BEST -> "Best"
    SoundQualityFilter.CLEAN -> "Clean"
    SoundQualityFilter.SHORT -> "Short"
    SoundQualityFilter.CALM -> "Calm"
    SoundQualityFilter.PUNCHY -> "Punchy"
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
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

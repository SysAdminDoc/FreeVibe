package com.freevibe.ui.screens.sounds

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.SearchHistoryDropdown
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    onCreateRingtone: (Uri) -> Unit = {},
    initialQuery: String? = null,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val previewReadyIds by viewModel.previewReadyIds.collectAsStateWithLifecycle()
    val topHits by viewModel.topHits.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
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
    var quickApplyActionInFlight by remember { mutableStateOf(false) }
    var quickApplyObservedApplying by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isYouTubeTab = state.selectedTab == SoundTab.YOUTUBE
    val soundFilterCount = remember(state.qualityFilter) {
        if (state.qualityFilter != SoundQualityFilter.BEST) 1 else 0
    }

    // Upload state
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var awaitingUploadResult by remember { mutableStateOf(false) }
    val uploadAudioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { selectedAudioUri = uri; showUploadDialog = true } }
    val createAudioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onCreateRingtone) }

    // Upload dialog
    val uploadUri = selectedAudioUri
    if (showUploadDialog && uploadUri != null) {
        UploadDialog(
            isUploading = state.isUploading,
            uploadProgress = state.uploadProgress,
            onUpload = { name, category ->
                awaitingUploadResult = true
                viewModel.uploadSound(uploadUri, name, category)
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
    val currentQuickApplySound = quickApplySound
    if (currentQuickApplySound != null) {
        QuickApplySheet(
            sound = currentQuickApplySound,
            canApply = viewModel.canWriteSettings(),
            isApplying = state.isApplying,
            onApply = { sound, type ->
                quickApplyActionInFlight = true
                quickApplyObservedApplying = false
                viewModel.applySound(sound, type)
            },
            onDownload = { viewModel.downloadSound(it); quickApplySound = null },
            onGrantPermission = { context.startActivity(viewModel.requestWriteSettings()) },
            onDismiss = {
                if (!state.isApplying) {
                    quickApplySound = null
                    quickApplyActionInFlight = false
                    quickApplyObservedApplying = false
                }
            },
        )
        LaunchedEffect(quickApplyActionInFlight, state.isApplying, state.applySuccess, state.error) {
            if (quickApplyActionInFlight && state.isApplying) {
                quickApplyObservedApplying = true
            }
            if (
                quickApplyActionInFlight &&
                quickApplyObservedApplying &&
                !state.isApplying &&
                (state.applySuccess != null || state.error != null)
            ) {
                quickApplySound = null
                quickApplyActionInFlight = false
                quickApplyObservedApplying = false
            }
        }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { uploadAudioPickerLauncher.launch("audio/*") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(Icons.Default.Upload, "Upload community sound", modifier = Modifier.size(20.dp))
                }
                SmallFloatingActionButton(
                    onClick = { createAudioPickerLauncher.launch("audio/*") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Default.ContentCut, "Create from music", modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                highlightHeight = 56.dp,
                shadowElevation = 6.dp,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            placeholder = if (isYouTubeTab) "Search YouTube or paste URL..." else "Search sounds or artists",
                            leadingIcon = if (isYouTubeTab) Icons.Default.SmartDisplay else Icons.Default.Search,
                            leadingTint = if (isYouTubeTab) Color(0xFFFF6A5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClear = {
                                searchQuery = ""
                                showSearchHistory = false
                                focusManager.clearFocus()
                                when (state.selectedTab) {
                                    SoundTab.SEARCH -> viewModel.clearSearchMode()
                                    SoundTab.YOUTUBE -> viewModel.clearYouTubeSearch()
                                    else -> Unit
                                }
                            },
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
                        )
                        SoundFilterButton(
                            filterCount = soundFilterCount,
                            onClick = { showFiltersSheet = true },
                        )
                    }
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
                            .padding(top = 42.dp),
                    )
                }

                Spacer(Modifier.height(6.dp))
                SoundModeBar(
                    selectedTab = state.selectedTab,
                    onSelectTab = viewModel::selectTab,
                )
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
                            previewReadyIds = previewReadyIds,
                            filterKey = state.filterKey,
                            onSoundClick = { viewModel.selectSound(it); onSoundClick(it) },
                            onLongPress = { quickApplySound = it },
                            onPlayClick = { viewModel.togglePlayback(it) },
                            onLoadMore = { viewModel.loadMore() },
                            playbackProgress = playbackProgress,
                            topHits = displayTopHits,
                            collections = if (state.query.isBlank()) {
                                val base = soundCollectionsFor(state.selectedTab)
                                val seasonal = viewModel.seasonalTheme
                                if (seasonal != null && base.isNotEmpty()) {
                                    val seasonalSpec = SoundCollectionSpec(
                                        title = seasonal.title,
                                        subtitle = seasonal.subtitle,
                                        query = seasonal.soundQuery,
                                        tone = SoundCollectionTone.SEASONAL,
                                    )
                                    listOf(seasonalSpec) + base
                                } else {
                                    base
                                }
                            } else emptyList(),
                            onCollectionClick = { collection -> viewModel.search(collection.query) },
                            onUploadClick = { uploadAudioPickerLauncher.launch("audio/*") },
                        )
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            SoundFiltersSheet(
                qualityFilter = state.qualityFilter,
                onSelectQuality = { filter ->
                    viewModel.setQualityFilter(filter)
                    showFiltersSheet = false
                },
                onReset = if (soundFilterCount > 0) {
                    {
                        viewModel.setQualityFilter(SoundQualityFilter.BEST)
                        showFiltersSheet = false
                    }
                } else null,
            )
        }
    }
}

private fun isYouTubeUrl(text: String): Boolean {
    val t = text.trim()
    return t.contains("youtube.com/") || t.contains("youtu.be/") || t.contains("youtube.com/shorts/")
}

internal val coreSoundTabs: List<SoundTab> = listOf(
    SoundTab.RINGTONES,
    SoundTab.NOTIFICATIONS,
    SoundTab.ALARMS,
)

internal fun secondarySoundTabs(selectedTab: SoundTab): List<SoundTab> = buildList {
    add(SoundTab.YOUTUBE)
    add(SoundTab.COMMUNITY)
    if (selectedTab == SoundTab.SEARCH) add(SoundTab.SEARCH)
}

private fun soundTabLabel(tab: SoundTab): String = when (tab) {
    SoundTab.RINGTONES -> "Ringtones"
    SoundTab.NOTIFICATIONS -> "Notifications"
    SoundTab.ALARMS -> "Alarms"
    SoundTab.YOUTUBE -> "YouTube"
    SoundTab.COMMUNITY -> "Community"
    SoundTab.SEARCH -> "Search"
}

private fun soundTabIcon(tab: SoundTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    SoundTab.RINGTONES -> Icons.Default.Call
    SoundTab.NOTIFICATIONS -> Icons.Default.Notifications
    SoundTab.ALARMS -> Icons.Default.Alarm
    SoundTab.YOUTUBE -> Icons.Default.SmartDisplay
    SoundTab.COMMUNITY -> Icons.Default.Groups
    SoundTab.SEARCH -> Icons.Default.Search
}

@Composable
private fun SoundFilterButton(
    filterCount: Int,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        BadgedBox(
            badge = {
                if (filterCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$filterCount") }
                }
            },
        ) {
            Icon(Icons.Default.Tune, contentDescription = "Refine sounds", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SoundModeBar(
    selectedTab: SoundTab,
    onSelectTab: (SoundTab) -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val secondaryTabs = remember(selectedTab) { secondarySoundTabs(selectedTab) }
    val secondarySelected = selectedTab in secondaryTabs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coreSoundTabs.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(soundTabLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        Box {
            FilterChip(
                selected = secondarySelected,
                onClick = { showMoreMenu = true },
                label = {
                    Text(
                        if (secondarySelected) soundTabLabel(selectedTab) else "More",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                secondaryTabs.forEach { tab ->
                    DropdownMenuItem(
                        text = { Text(soundTabLabel(tab)) },
                        onClick = {
                            showMoreMenu = false
                            onSelectTab(tab)
                        },
                        leadingIcon = {
                            Icon(
                                if (selectedTab == tab) Icons.Default.Check else soundTabIcon(tab),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

    }
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
    previewReadyIds: Set<String>,
    filterKey: Int,
    onSoundClick: (Sound) -> Unit,
    onLongPress: (Sound) -> Unit,
    onPlayClick: (Sound) -> Unit,
    onLoadMore: () -> Unit,
    playbackProgress: Float,
    topHits: List<Sound>,
    collections: List<SoundCollectionSpec>,
    onCollectionClick: (SoundCollectionSpec) -> Unit,
    onUploadClick: (() -> Unit)? = null,
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (collections.isNotEmpty()) {
            item(key = "sound_collections", contentType = "collections") {
                SoundCollectionCarousel(
                    collections = collections,
                    onCollectionClick = onCollectionClick,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        // Top 5 This Week (Ringtones tab only)
        if (topHits.isNotEmpty()) {
            item(key = "tophits_header", contentType = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trending", Modifier.size(20.dp), tint = Color(0xFFFF4444))
                    Text("Top 5 This Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(topHits, key = { "hit_${it.stableKey()}" }, contentType = { "sound_card" }) { sound ->
                SoundCard(
                    sound = sound,
                    tab = SoundTab.RINGTONES,
                    isPlaying = playingId == sound.stableKey(),
                    isResolving = sound.stableKey() == resolvingId,
                    isPreviewReady = sound.stableKey() in previewReadyIds,
                    playbackProgress = if (playingId == sound.stableKey()) playbackProgress else 0f,
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
        val topHitIds = topHits.map { it.stableKey() }.toSet()
        val filteredSounds = sounds.filter { it.stableKey() !in topHitIds }

        items(filteredSounds, key = { it.stableKey() }, contentType = { "sound_card" }) { sound ->
            SoundCard(
                sound = sound,
                tab = selectedTab,
                isPlaying = playingId == sound.stableKey(),
                isResolving = sound.stableKey() == resolvingId,
                isPreviewReady = sound.stableKey() in previewReadyIds,
                playbackProgress = if (playingId == sound.stableKey()) playbackProgress else 0f,
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                    if (selectedTab == SoundTab.COMMUNITY && onUploadClick != null) {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onUploadClick) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload a sound")
                        }
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

@Composable
private fun SoundCollectionCarousel(
    collections: List<SoundCollectionSpec>,
    onCollectionClick: (SoundCollectionSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Collections",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(collections, key = { it.title }) { collection ->
                SoundCollectionCard(
                    collection = collection,
                    onClick = { onCollectionClick(collection) },
                )
            }
        }
    }
}

@Composable
private fun SoundCollectionCard(
    collection: SoundCollectionSpec,
    onClick: () -> Unit,
) {
    val accent = collectionToneColor(collection.tone)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .height(104.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        collectionToneIcon(collection.tone),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    collection.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun collectionToneColor(tone: SoundCollectionTone): Color = when (tone) {
    SoundCollectionTone.MINIMAL -> MaterialTheme.colorScheme.primary
    SoundCollectionTone.CALM -> MaterialTheme.colorScheme.tertiary
    SoundCollectionTone.RETRO -> Color(0xFFFFB74D)
    SoundCollectionTone.NATURE -> Color(0xFF66BB6A)
    SoundCollectionTone.PUNCHY -> Color(0xFFFF6B6B)
    SoundCollectionTone.MELODIC -> Color(0xFF64B5F6)
    SoundCollectionTone.SEASONAL -> Color(0xFFFFCA28) // amber-gold accent
}

private fun collectionToneIcon(tone: SoundCollectionTone): androidx.compose.ui.graphics.vector.ImageVector = when (tone) {
    SoundCollectionTone.MINIMAL -> Icons.Default.RadioButtonUnchecked
    SoundCollectionTone.CALM -> Icons.Default.Spa
    SoundCollectionTone.RETRO -> Icons.Default.PhoneInTalk
    SoundCollectionTone.NATURE -> Icons.Default.WaterDrop
    SoundCollectionTone.PUNCHY -> Icons.Default.Bolt
    SoundCollectionTone.MELODIC -> Icons.Default.GraphicEq
    SoundCollectionTone.SEASONAL -> Icons.Default.Celebration
}

// -- Sound Card --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundCard(
    sound: Sound,
    tab: SoundTab,
    isPlaying: Boolean,
    isResolving: Boolean = false,
    isPreviewReady: Boolean = false,
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
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(40.dp)
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
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        sound.name,
                        style = MaterialTheme.typography.titleSmall,
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
                        if (isPreviewReady) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    "Ready",
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
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
                            items(badges, key = { badge -> "${sound.stableKey()}_$badge" }) { badge ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        badge,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Chevron
                Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
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
                MiniWaveform(sound.duration, true, playbackProgress, Modifier.fillMaxWidth().padding(start = 52.dp))
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
    Canvas(modifier.height(20.dp)) {
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

@Composable
private fun SoundFiltersSheet(
    qualityFilter: SoundQualityFilter,
    onSelectQuality: (SoundQualityFilter) -> Unit,
    onReset: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Refine sounds", style = MaterialTheme.typography.titleMedium)
        Text(
            "Quality bias",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoundQualityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = qualityFilter == filter,
                    onClick = { onSelectQuality(filter) },
                    label = { Text(soundFilterLabel(filter)) },
                    leadingIcon = if (qualityFilter == filter) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
        onReset?.let {
            TextButton(onClick = it) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset filters")
            }
        }
    }
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
    onGrantPermission: () -> Unit,
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

            if (!canApply) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "System settings permission is required before applying.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onGrantPermission, enabled = !isApplying) {
                            Text("Grant")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

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

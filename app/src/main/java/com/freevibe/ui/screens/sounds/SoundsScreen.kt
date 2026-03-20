package com.freevibe.ui.screens.sounds

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.Sound
import com.freevibe.ui.components.SearchHistoryDropdown
import com.freevibe.ui.components.ShimmerSoundList
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar with history
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search sounds...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            showSearchHistory = false
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.search(searchQuery)
                        showSearchHistory = false
                        focusManager.clearFocus()
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            SearchHistoryDropdown(
                recentQueries = recentSearches,
                isVisible = showSearchHistory && searchQuery.isEmpty(),
                onQueryClick = { query ->
                    searchQuery = query
                    viewModel.search(query)
                    showSearchHistory = false
                    focusManager.clearFocus()
                },
                onDeleteQuery = { viewModel.removeSearch(it) },
                onClearAll = { viewModel.clearSearchHistory() },
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
            )
        }

        // Tab row
        val visibleTabs = SoundTab.entries.filter {
            it != SoundTab.SEARCH || state.selectedTab == SoundTab.SEARCH
        }
        ScrollableTabRow(
            selectedTabIndex = visibleTabs.indexOf(state.selectedTab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
        ) {
            visibleTabs.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        // Duration filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DurationFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.durationFilter == filter,
                    onClick = { viewModel.setDurationFilter(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (state.durationFilter == filter) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp),
                )
            }
        }

        // Category chips (horizontal scroll)
        AnimatedVisibility(
            visible = state.selectedTab != SoundTab.SEARCH,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundCategory.entries.forEach { cat ->
                    AssistChip(
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Text(cat.emoji, style = MaterialTheme.typography.labelSmall)
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(32.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (state.selectedCategory == cat)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    ShimmerSoundList(Modifier.fillMaxSize())
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.selectTab(state.selectedTab) }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
                state.sounds.isEmpty() && !state.isRefreshing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Default.MusicOff,
                                null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.selectedTab == SoundTab.SEARCH) "No results for \"${state.query}\""
                                else if (state.selectedCategory != null) "No ${state.selectedCategory!!.label.lowercase()} sounds found"
                                else "No sounds found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.durationFilter != DurationFilter.ALL) {
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = { viewModel.setDurationFilter(DurationFilter.ALL) }) {
                                    Text("Clear duration filter")
                                }
                            }
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                    ) {
                        SoundsList(
                            sounds = state.sounds,
                            playingId = state.playingId,
                            isLoadingMore = state.isLoadingMore,
                            onSoundClick = { sound ->
                                viewModel.selectSound(sound)
                                onSoundClick(sound)
                            },
                            onPlayClick = { viewModel.togglePlayback(it) },
                            onLoadMore = { viewModel.loadMore() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    playingId: String?,
    isLoadingMore: Boolean,
    onSoundClick: (Sound) -> Unit,
    onPlayClick: (Sound) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sounds, key = { it.id }) { sound ->
            SoundCard(
                sound = sound,
                isPlaying = playingId == sound.id,
                onClick = { onSoundClick(sound) },
                onPlayClick = { onPlayClick(sound) },
            )
        }

        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SoundCard(
    sound: Sound,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Play/pause button
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
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Sound info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sound.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = formatDuration(sound.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sound.uploaderName.isNotEmpty()) {
                            Text(
                                text = sound.uploaderName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Source + license badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Source badge
                    val sourceLabel = when (sound.source.name) {
                        "FREESOUND" -> "FS"
                        "INTERNET_ARCHIVE" -> "IA"
                        else -> sound.source.name.take(2)
                    }
                    val sourceColor = when (sound.source.name) {
                        "FREESOUND" -> Color(0xFF3DB2CE)
                        "INTERNET_ARCHIVE" -> Color(0xFFFF8C00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = sourceColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            sourceLabel,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = sourceColor,
                        )
                    }

                    if (sound.license.contains("CC0", ignoreCase = true)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                "CC0",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Mini waveform
            if (sound.duration > 0) {
                Spacer(Modifier.height(6.dp))
                MiniWaveform(
                    duration = sound.duration,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniWaveform(
    duration: Double,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isPlaying) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val activeColor = MaterialTheme.colorScheme.primary

    val barCount = 40
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i ->
            val angle = (seed + i * 37) % 360
            (0.2f + 0.8f * ((sin(angle * 0.0174533) + 1f) / 2f).toFloat())
        }
    }

    val progress = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveform")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (duration * 1000).toInt().coerceIn(2000, 30000),
                    easing = LinearEasing,
                ),
            ),
            label = "waveform_progress",
        ).value
    } else 0f

    Canvas(
        modifier = modifier.height(20.dp),
    ) {
        val barWidth = size.width / barCount
        val gap = 1.dp.toPx()

        heights.forEachIndexed { i, height ->
            val x = i * barWidth + barWidth / 2
            val barH = size.height * height
            val isActive = isPlaying && (i.toFloat() / barCount) < progress

            drawLine(
                color = if (isActive) activeColor else barColor,
                start = Offset(x, size.height / 2 - barH / 2),
                end = Offset(x, size.height / 2 + barH / 2),
                strokeWidth = (barWidth - gap).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt()
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

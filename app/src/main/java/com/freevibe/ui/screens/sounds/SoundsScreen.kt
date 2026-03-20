package com.freevibe.ui.screens.sounds

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.Sound
import com.freevibe.ui.components.ShimmerSoundList
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search sounds...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; focusManager.clearFocus() }) {
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

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
        ) {
            SoundTab.entries.filter { it != SoundTab.SEARCH || state.selectedTab == SoundTab.SEARCH }
                .forEach { tab ->
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

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    ShimmerSoundList(Modifier.fillMaxSize())
                }
                state.error != null -> {
                    // #5: Error with retry
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
                else -> {
                    // #4: Pull-to-refresh
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

                // License badge
                if (sound.license.contains("CC0", ignoreCase = true)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "CC0",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // #12: Mini waveform / duration bar
            if (sound.duration > 0) {
                Spacer(Modifier.height(6.dp))
                MiniWaveform(
                    duration = sound.duration,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp), // align with text
                )
            }
        }
    }
}

// #12: Mini waveform visualization
@Composable
private fun MiniWaveform(
    duration: Double,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isPlaying) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val activeColor = MaterialTheme.colorScheme.primary

    // Deterministic pseudo-waveform based on duration
    val barCount = 40
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i ->
            val angle = (seed + i * 37) % 360
            (0.2f + 0.8f * ((sin(angle * 0.0174533) + 1f) / 2f).toFloat())
        }
    }

    // Animate playing position
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

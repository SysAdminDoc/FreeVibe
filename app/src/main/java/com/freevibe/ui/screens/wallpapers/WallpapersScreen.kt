package com.freevibe.ui.screens.wallpapers

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.Wallpaper
import com.freevibe.ui.components.DownloadProgressBar
import com.freevibe.ui.components.SearchHistoryDropdown
import com.freevibe.ui.components.ShimmerBox
import com.freevibe.ui.components.ShimmerWallpaperGrid
import com.freevibe.ui.components.SourceBadge

// #9: Wallhaven supported colors
private val WALLHAVEN_COLORS = listOf(
    "660000" to Color(0xFF660000), "990000" to Color(0xFF990000),
    "cc0000" to Color(0xFFCC0000), "cc3333" to Color(0xFFCC3333),
    "ea4c88" to Color(0xFFEA4C88), "993399" to Color(0xFF993399),
    "663399" to Color(0xFF663399), "333399" to Color(0xFF333399),
    "0066cc" to Color(0xFF0066CC), "0099cc" to Color(0xFF0099CC),
    "66cccc" to Color(0xFF66CCCC), "77cc33" to Color(0xFF77CC33),
    "669900" to Color(0xFF669900), "336600" to Color(0xFF336600),
    "666600" to Color(0xFF666600), "999900" to Color(0xFF999900),
    "cccc33" to Color(0xFFCCCC33), "ffff00" to Color(0xFFFFFF00),
    "ffcc33" to Color(0xFFFFCC33), "ff6600" to Color(0xFFFF6600),
    "cc6633" to Color(0xFFCC6633), "996633" to Color(0xFF996633),
    "663300" to Color(0xFF663300), "000000" to Color(0xFF000000),
    "999999" to Color(0xFF999999), "cccccc" to Color(0xFFCCCCCC),
    "ffffff" to Color(0xFFFFFFFF), "424153" to Color(0xFF424153),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpapersScreen(
    onWallpaperClick: (Wallpaper) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val downloads by viewModel.activeDownloads.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val dailyPick by viewModel.dailyPick.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()

    // Vote counts for visible wallpapers
    val voteCounts by remember(state.wallpapers) {
        if (state.wallpapers.isNotEmpty()) {
            viewModel.voteRepo.getVoteCounts(state.wallpapers.map { it.id })
        } else {
            kotlinx.coroutines.flow.flowOf(emptyMap())
        }
    }.collectAsState(initial = emptyMap())
    var searchQuery by remember { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }

    // Pick up pending queries from detail/category screens when screen becomes visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.consumePendingQueries()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar with color filter button

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search wallpapers...") },
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

                // #9: Color filter button
                IconButton(
                    onClick = { showColorPicker = !showColorPicker },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Color filter",
                        tint = if (state.selectedColor != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // #9: Color picker row
            AnimatedVisibility(visible = showColorPicker) {
                ColorPickerRow(
                    selectedColor = state.selectedColor,
                    onColorSelected = { color ->
                        viewModel.searchByColor(color)
                        showColorPicker = false
                    },
                    onClear = if (state.selectedColor != null) {
                        {
                            viewModel.selectTab(WallpaperTab.DISCOVER)
                            showColorPicker = false
                        }
                    } else null,
                )
            }

            // Tab row
            val visibleTabs = WallpaperTab.entries.filter {
                it != WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.SEARCH
            }.filter {
                it != WallpaperTab.COLOR || state.selectedTab == WallpaperTab.COLOR
            }

            key(visibleTabs.size) {
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
                                    text = when (tab) {
                                        WallpaperTab.DISCOVER -> "Discover"
                                        WallpaperTab.PEXELS -> "Pexels"
                                        WallpaperTab.PIXABAY -> "Pixabay"
                                        WallpaperTab.COLOR -> "Color"
                                        else -> tab.name.lowercase().replaceFirstChar { it.uppercase() }
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                }
            }
            }

            // Download progress
            DownloadProgressBar(
                downloads = downloads,
                onDismiss = { viewModel.dismissDownload(it) },
            )

            // Content with pull-to-refresh (#4)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        ShimmerWallpaperGrid(Modifier.fillMaxSize())
                    }
                    state.error != null -> {
                        // #5: Source-specific error with retry
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = state.errorSource?.let { "Failed to load from ${it.lowercase()}" }
                                    ?: "Something went wrong",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = { viewModel.selectTab(state.selectedTab) }) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                    state.wallpapers.isEmpty() && !state.isRefreshing -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.ImageNotSupported,
                                    null,
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    when {
                                        state.selectedTab == WallpaperTab.SEARCH -> "No results for \"${state.query}\""
                                        state.selectedTab == WallpaperTab.COLOR -> "No wallpapers with this color"
                                        state.selectedTab == WallpaperTab.PIXABAY -> "Add your Pixabay API key in Settings"
                                        else -> "No wallpapers found"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.selectedColor != null) {
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(onClick = { viewModel.selectTab(WallpaperTab.DISCOVER) }) {
                                        Text("Back to Discover")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // #4: Pull-to-refresh wrapper
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                        ) {
                            WallpaperGrid(
                                dailyPick = if (state.selectedTab == WallpaperTab.DISCOVER) dailyPick else null,
                                wallpapers = state.wallpapers,
                                isLoadingMore = state.isLoadingMore,
                                columns = gridColumns,
                                onWallpaperClick = { wp ->
                                    viewModel.selectWallpaper(wp)
                                    onWallpaperClick(wp)
                                },
                                onLongPress = { wp ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(wp)
                                },
                                favoriteIds = favoriteIds,
                                hiddenIds = hiddenIds,
                                onUpvote = { id -> viewModel.upvote(id) },
                                onDownvote = { id -> viewModel.downvote(id) },
                                voteCounts = voteCounts,
                                onLoadMore = { viewModel.loadMore() },
                            )
                        }
                    }
                }
            }
        }

        // Random wallpaper FAB
        if (state.wallpapers.isNotEmpty() && !state.isLoading) {
            SmallFloatingActionButton(
                onClick = { viewModel.applyRandom() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Random wallpaper")
            }
        }
    }
}

// #9: Color picker row (scrollable — 28 swatches won't fit in a single screen)
@Composable
private fun ColorPickerRow(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClear != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear color filter",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        WALLHAVEN_COLORS.forEach { (hex, color) ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selectedColor == hex) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
                    .clickable { onColorSelected(hex) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    isLoadingMore: Boolean,
    columns: Int = 2,
    dailyPick: Wallpaper? = null,
    onWallpaperClick: (Wallpaper) -> Unit,
    onLongPress: ((Wallpaper) -> Unit)? = null,
    favoriteIds: Set<String> = emptySet(),
    hiddenIds: Set<String> = emptySet(),
    onUpvote: ((String) -> Unit)? = null,
    onDownvote: ((String) -> Unit)? = null,
    voteCounts: Map<String, Int> = emptyMap(),
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 4)),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        // Wallpaper of the Day banner — Reddit's most upvoted today
        if (dailyPick != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "daily_pick") {
                val pick = dailyPick
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWallpaperClick(pick) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SubcomposeAsyncImage(
                            model = pick.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Wallpaper of the Day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(pick.category.ifEmpty { "Top voted on Reddit" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        val visibleWallpapers = wallpapers
            .filter { it.id !in hiddenIds }
            .sortedByDescending { voteCounts[it.id] ?: 0 }
        items(visibleWallpapers, key = { it.id }) { wallpaper ->
            val isFav = wallpaper.id in favoriteIds
            WallpaperCard(
                wallpaper = wallpaper,
                isFavorite = isFav,
                voteCount = voteCounts[wallpaper.id] ?: 0,
                onClick = { onWallpaperClick(wallpaper) },
                onFavoriteClick = onLongPress?.let { { it(wallpaper) } },
                onLongPress = onDownvote?.let { { it(wallpaper.id) } },
                onUpvote = onUpvote?.let { { it(wallpaper.id) } },
                onDownvote = onDownvote?.let { { it(wallpaper.id) } },
            )
        }

        if (isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean = false,
    voteCount: Int = 0,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onUpvote: (() -> Unit)? = null,
    onDownvote: (() -> Unit)? = null,
) {
    val aspectRatio = if (wallpaper.width > 0 && wallpaper.height > 0) {
        wallpaper.width.toFloat() / wallpaper.height.toFloat()
    } else 0.67f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            SubcomposeAsyncImage(
                model = wallpaper.thumbnailUrl,
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.5f, 1.0f)),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                        )
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.BrokenImage, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // Bottom gradient with info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceBadge(wallpaper.source.name)
                        if (onUpvote != null) {
                            Surface(
                                onClick = onUpvote,
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Default.ThumbUp, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.9f))
                                    if (voteCount > 0) {
                                        Text("$voteCount", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                                    }
                                }
                            }
                        }
                    }
                    if (wallpaper.width > 0 && wallpaper.height > 0) {
                        Text(
                            "${wallpaper.width}x${wallpaper.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Favorite heart overlay (top-right)
            if (onFavoriteClick != null) {
                val heartScale by animateFloatAsState(
                    targetValue = if (isFavorite) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                    label = "heart",
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .padding(4.dp),
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = heartScale; scaleY = heartScale },
                    )
                }
            }
        }
    }
}

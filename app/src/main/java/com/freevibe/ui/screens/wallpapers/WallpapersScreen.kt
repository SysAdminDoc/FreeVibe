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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.Wallpaper
import com.freevibe.ui.components.DownloadProgressBar
import com.freevibe.ui.components.GlassCard
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
    initialQuery: String? = null,
    initialColor: String? = null,
    onWallpaperClick: (Wallpaper) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val downloads by viewModel.activeDownloads.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val dailyPick by viewModel.dailyPick.collectAsState()
    val topVoted by viewModel.topVoted.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()

    // Vote counts for visible wallpapers — use derivedStateOf to avoid recomputing on referential inequality
    val wallpaperIds by remember { derivedStateOf { state.wallpapers.map { it.id } } }
    val voteCountsFlow = remember(wallpaperIds) {
        if (wallpaperIds.isNotEmpty()) {
            viewModel.voteRepo.getVoteCounts(wallpaperIds)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyMap())
        }
    }
    val voteCounts by voteCountsFlow.collectAsState(initial = emptyMap())
    var searchQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { searchQuery = state.query }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuery, initialColor) {
        viewModel.handleRouteFilters(initialQuery, initialColor)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val visibleTabs = WallpaperTab.entries.filter {
                it != WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.SEARCH
            }.filter {
                it != WallpaperTab.COLOR || state.selectedTab == WallpaperTab.COLOR
            }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (state.selectedTab == WallpaperTab.DISCOVER) "Discover" else wallpaperTabLabel(state.selectedTab),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search wallpapers, moods, colors") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        showSearchHistory = false
                                        focusManager.clearFocus()
                                        viewModel.selectTab(WallpaperTab.DISCOVER)
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.search(searchQuery)
                                    showSearchHistory = false
                                    focusManager.clearFocus()
                                },
                            ),
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
                            onQueryClick = { query ->
                                searchQuery = query
                                viewModel.search(query)
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

                    Surface(
                        onClick = { showColorPicker = !showColorPicker },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (state.selectedColor != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = 72.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Color filter",
                                tint = if (state.selectedColor != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (state.selectedColor != null) "Tone" else "Filter",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.selectedColor != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

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

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleTabs.forEach { tab ->
                        FilterChip(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = {
                                Text(
                                    wallpaperTabLabel(tab),
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
            }

            // Download progress
            DownloadProgressBar(
                downloads = downloads,
                onDismiss = { viewModel.dismissDownload(it) },
            )

            // Wallhaven toplist time-range filter chips
            if (state.selectedTab == WallpaperTab.WALLHAVEN) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("1d" to "Today", "1w" to "Week", "1M" to "Month", "6M" to "6 Months", "1y" to "Year").forEach { (range, label) ->
                        FilterChip(
                            selected = state.topRange == range,
                            onClick = { viewModel.setTopRange(range) },
                            label = { Text(label) },
                            leadingIcon = if (state.topRange == range) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }

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
                                onSearch = { query -> viewModel.search(query) },
                                isDiscoverTab = state.selectedTab == WallpaperTab.DISCOVER,
                                topVoted = if (state.selectedTab == WallpaperTab.DISCOVER) topVoted else emptyList(),
                            )
                        }
                    }
                }
            }
        }

        // Compact FABs
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (state.selectedTab == WallpaperTab.DISCOVER) {
                SmallFloatingActionButton(
                    onClick = { viewModel.matchMyTheme(context) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Match my theme", modifier = Modifier.size(20.dp))
                }
            }
            SmallFloatingActionButton(
                onClick = { viewModel.loadRandom() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Surprise me", modifier = Modifier.size(20.dp))
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
    onSearch: ((String) -> Unit)? = null,
    isDiscoverTab: Boolean = false,
    topVoted: List<Pair<Wallpaper, Int>> = emptyList(),
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
        contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        // Wallpaper of the Day — hero card with full image
        if (dailyPick != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "daily_pick") {
                val pick = dailyPick
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWallpaperClick(pick) },
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    ) {
                        SubcomposeAsyncImage(
                            model = pick.fullUrl.ifEmpty { pick.thumbnailUrl },
                            contentDescription = "Wallpaper of the Day",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                        startY = 80f,
                                    ),
                                ),
                        )
                        // Text overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Featured", Modifier.size(16.dp), tint = Color(0xFFFFD700))
                                Text("Wallpaper of the Day", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            }
                            Text(
                                pick.category.ifEmpty { "Top voted on Reddit" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                        // Arrow
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View wallpaper",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(20.dp),
                        )
                    }
                }
            }
        }

        // Curated collections carousel (Discover tab only)
        if (isDiscoverTab) {
            item(span = StaggeredGridItemSpan.FullLine, key = "curated_collections") {
                Column {
                    Text(
                        "Explore Collections",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val collections = listOf(
                            "AMOLED Black" to "amoled black dark",
                            "Minimal" to "minimal clean simple",
                            "Nature 4K" to "nature landscape 4k",
                            "Cyberpunk" to "cyberpunk neon city",
                            "Space" to "space galaxy nebula",
                            "Abstract" to "abstract colorful gradient",
                            "Anime" to "anime art illustration",
                            "Ocean" to "ocean sea waves beach",
                            "Mountains" to "mountain peak scenic",
                            "Urban" to "city skyline urban night",
                        )
                        collections.forEach { (name, query) ->
                            item {
                                ElevatedFilterChip(
                                    selected = false,
                                    onClick = { onSearch?.invoke(query) },
                                    label = { Text(name) },
                                    leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null, Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Trending searches
            item(span = StaggeredGridItemSpan.FullLine, key = "trending_searches") {
                Column {
                    Text(
                        "Trending",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val trending = listOf(
                            "nature 4k", "dark aesthetic", "gradient", "retro wave",
                            "studio ghibli", "moody forest", "neon lights", "sakura",
                            "sunset golden hour", "geometric art", "lofi vibes",
                        )
                        trending.forEach { term ->
                            item {
                                AssistChip(
                                    onClick = { onSearch?.invoke(term) },
                                    label = { Text(term) },
                                    leadingIcon = { Icon(Icons.Default.Whatshot, contentDescription = null, Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top upvoted wallpapers section (Discover only, from community votes across all tabs)
        if (isDiscoverTab && topVoted.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "top_voted_header") {
                Text(
                    "Community Favorites",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            val topVotedIds = topVoted.map { it.first.id }.toSet()
            topVoted.filter { it.first.id !in hiddenIds }.take(10).forEach { (wp, votes) ->
                item(key = "top_${wp.id}") {
                    val isFav = wp.id in favoriteIds
                    WallpaperCard(
                        wallpaper = wp,
                        isFavorite = isFav,
                        voteCount = votes,
                        onClick = { onWallpaperClick(wp) },
                        onFavoriteClick = onLongPress?.let { { it(wp) } },
                        onLongPress = onDownvote?.let { { it(wp.id) } },
                        onUpvote = onUpvote?.let { { it(wp.id) } },
                    )
                }
            }
        }

        val topVotedIds = if (isDiscoverTab) topVoted.map { it.first.id }.toSet() else emptySet()
        val visibleWallpapers = wallpapers
            .filter { it.id !in hiddenIds && it.id !in topVotedIds }
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
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
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
                            Icon(Icons.Default.BrokenImage, contentDescription = "Failed to load", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.06f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.52f),
                            ),
                        ),
                    ),
            )

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
                        if (onUpvote != null && voteCount > 0) {
                            Surface(
                                onClick = onUpvote,
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = "Upvotes", Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.9f))
                                    Text("$voteCount", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }
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
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.2f)),
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

private fun wallpaperTabLabel(tab: WallpaperTab): String =
    when (tab) {
        WallpaperTab.DISCOVER -> "Discover"
        WallpaperTab.PEXELS -> "Pexels"
        WallpaperTab.PIXABAY -> "Pixabay"
        WallpaperTab.REDDIT -> "Reddit"
        WallpaperTab.WALLHAVEN -> "Wallhaven"
        WallpaperTab.UNSPLASH -> "Picsum"
        WallpaperTab.COLOR -> "Color"
        WallpaperTab.SEARCH -> "Search"
    }

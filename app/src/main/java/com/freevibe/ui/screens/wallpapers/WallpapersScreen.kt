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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.matchesHiddenIds
import com.freevibe.service.SeasonalTheme
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.DownloadProgressBar
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
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
    initialSimilarId: String? = null,
    initialSimilarSource: String? = null,
    initialSimilarFullUrl: String? = null,
    onWallpaperClick: (Wallpaper) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val favoriteIdentities by viewModel.favoriteIdentities.collectAsStateWithLifecycle()
    val dailyPick by viewModel.dailyPick.collectAsStateWithLifecycle()
    val topVoted by viewModel.topVoted.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val visibleSections = remember(state.wallpapers, hiddenIds, topVoted, dailyPick, state.selectedTab) {
        computeVisibleWallpaperSections(
            wallpapers = state.wallpapers,
            hiddenIds = hiddenIds,
            topVoted = topVoted,
            dailyPick = dailyPick,
            isDiscoverTab = state.selectedTab == WallpaperTab.DISCOVER,
        )
    }

    // Vote counts for visible wallpapers — use derivedStateOf to avoid recomputing on referential inequality
    val wallpaperIds by remember { derivedStateOf { state.wallpapers.map { it.stableKey() } } }
    val voteCountsFlow = remember(wallpaperIds) {
        if (wallpaperIds.isNotEmpty()) {
            viewModel.voteRepo.getVoteCounts(wallpaperIds)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyMap())
        }
    }
    val voteCounts by voteCountsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var searchQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { searchQuery = state.query }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchHistory by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    val wallpaperFilterCount = remember(state.selectedTab, state.selectedColor, state.discoverFilter, state.topRange) {
        buildList {
            if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                add("discover")
            }
            if (state.selectedColor != null) add("color")
            if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") add("range")
        }.size
    }

    LaunchedEffect(initialQuery, initialColor, initialSimilarId, initialSimilarSource, initialSimilarFullUrl) {
        viewModel.handleRouteFilters(
            query = initialQuery,
            color = initialColor,
            similarId = initialSimilarId,
            similarSource = initialSimilarSource,
            similarFullUrl = initialSimilarFullUrl,
        )
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.error, state.wallpapers.isNotEmpty()) {
        if (state.wallpapers.isNotEmpty()) {
            state.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val visibleTabs = remember(state.selectedTab) {
                WallpaperTab.entries.filter {
                    it != WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.SEARCH
                }.filter {
                    it != WallpaperTab.COLOR || state.selectedTab == WallpaperTab.COLOR
                }
            }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                highlightHeight = 120.dp,
                shadowElevation = 6.dp,
            ) {
                HighlightPill(
                    label = wallpaperHeaderEyebrow(
                        tab = state.selectedTab,
                        discoverFilter = state.discoverFilter,
                    ),
                    icon = wallpaperTabIcon(state.selectedTab),
                    tint = if (state.selectedTab == WallpaperTab.DISCOVER) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = wallpaperHeaderTitle(
                        tab = state.selectedTab,
                        query = state.query,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = wallpaperHeaderSubtitle(
                        tab = state.selectedTab,
                        discoverFilter = state.discoverFilter,
                        query = state.query,
                        selectedColor = state.selectedColor,
                        wallpaperCount = visibleSections.feedWallpapers.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            placeholder = "Search wallpapers or colors",
                            modifier = Modifier
                                .fillMaxWidth(),
                            onClear = {
                                showSearchHistory = false
                                focusManager.clearFocus()
                                if (state.selectedTab == WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.COLOR) {
                                    searchQuery = ""
                                    viewModel.clearActiveFilter()
                                } else {
                                    searchQuery = ""
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.search(searchQuery)
                                    showSearchHistory = false
                                    focusManager.clearFocus()
                                },
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
                                .padding(top = 42.dp),
                        )
                    }

                    OutlinedIconButton(
                        onClick = { showFiltersSheet = true },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            contentColor = if (wallpaperFilterCount > 0 || state.selectedColor != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (wallpaperFilterCount > 0 || state.selectedColor != null) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            },
                        ),
                    ) {
                        BadgedBox(
                            badge = {
                                if (wallpaperFilterCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("$wallpaperFilterCount")
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Wallpaper filters", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        FilledTonalButton(
                            onClick = { showSourceMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                wallpaperTabLabel(state.selectedTab),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false },
                        ) {
                            visibleTabs.forEach { tab ->
                                DropdownMenuItem(
                                    text = { Text(wallpaperTabLabel(tab)) },
                                    onClick = {
                                        showSourceMenu = false
                                        viewModel.selectTab(tab)
                                    },
                                    leadingIcon = {
                                        if (state.selectedTab == tab) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                        AssistChip(
                            onClick = { viewModel.setDiscoverFilter(WallpaperDiscoverFilter.FOR_YOU) },
                            label = { Text(wallpaperFilterLabel(state.discoverFilter), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(12.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                        )
                    }
                    state.selectedColor?.let { selectedColor ->
                        AssistChip(
                            onClick = { viewModel.clearActiveFilter() },
                            label = { Text("Tone", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(selectedColor)),
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                        )
                    }
                    if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") {
                        AssistChip(
                            onClick = { viewModel.setTopRange("1M") },
                            label = { Text(wallpaperTopRangeLabel(state.topRange), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(12.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
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
                    state.isLoading && state.wallpapers.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WallpaperStateCard(
                                icon = Icons.Default.AutoAwesome,
                                title = "Curating your wallpaper feed",
                                description = "Aura is gathering higher-quality picks from your active sources so the first load feels worth browsing.",
                            )
                            ShimmerWallpaperGrid(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    state.error != null && state.wallpapers.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            WallpaperStateCard(
                                icon = Icons.Default.CloudOff,
                                title = state.errorSource?.let {
                                    "Couldn't refresh ${it.lowercase(java.util.Locale.ROOT)} right now"
                                } ?: "Wallpaper loading hit a snag",
                                description = state.error ?: "Try again in a moment or switch sources.",
                                primaryAction = WallpaperStateAction(
                                    label = "Retry",
                                    icon = Icons.Default.Refresh,
                                    onClick = { viewModel.refresh() },
                                ),
                                secondaryAction = if (state.selectedTab != WallpaperTab.DISCOVER) {
                                    WallpaperStateAction(
                                        label = "Back to Discover",
                                        icon = Icons.Default.Explore,
                                        onClick = { viewModel.selectTab(WallpaperTab.DISCOVER) },
                                    )
                                } else null,
                            )
                        }
                    }
                    !visibleSections.hasRenderableContent && !state.isRefreshing -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            WallpaperStateCard(
                                modifier = Modifier.padding(24.dp),
                                icon = Icons.Default.ImageNotSupported,
                                title = when {
                                    state.selectedTab == WallpaperTab.SEARCH -> "No results for \"${state.query}\""
                                    state.selectedTab == WallpaperTab.COLOR -> "No wallpapers matched this tone"
                                    state.selectedTab == WallpaperTab.PIXABAY -> "Pixabay needs a key before it can load"
                                    else -> "Nothing is ready to show here yet"
                                },
                                description = when {
                                    state.selectedTab == WallpaperTab.SEARCH ->
                                        "Try a broader term, fewer keywords, or jump back into Discover for curated results."
                                    state.selectedTab == WallpaperTab.COLOR ->
                                        "Try another tone or return to Discover for a wider mix."
                                    state.selectedTab == WallpaperTab.PIXABAY ->
                                        "Add your Pixabay API key in Settings to unlock this source."
                                    else ->
                                        "Refresh the feed or switch sources to keep browsing."
                                },
                                primaryAction = WallpaperStateAction(
                                    label = if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                        "Back to Discover"
                                    } else {
                                        "Refresh"
                                    },
                                    icon = if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                        Icons.Default.Explore
                                    } else {
                                        Icons.Default.Refresh
                                    },
                                    onClick = {
                                        if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                            viewModel.selectTab(WallpaperTab.DISCOVER)
                                        } else {
                                            viewModel.refresh()
                                        }
                                    },
                                ),
                            )
                        }
                    }
                    else -> {
                        // #4: Pull-to-refresh wrapper
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                        ) {
                            WallpaperGrid(
                                dailyPick = visibleSections.dailyPick,
                                wallpapers = visibleSections.feedWallpapers,
                                isLoadingMore = state.isLoadingMore,
                                columns = gridColumns,
                                onWallpaperClick = { wp ->
                                    viewModel.selectWallpaper(wp, visibleSections.pagerWallpapers)
                                    onWallpaperClick(wp)
                                },
                                onLongPress = { wp ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(wp)
                                },
                                favoriteIdentities = favoriteIdentities,
                                hiddenIds = hiddenIds,
                                onUpvote = { id -> viewModel.upvote(id) },
                                onDownvote = { id -> viewModel.downvote(id) },
                                voteCounts = voteCounts,
                                onLoadMore = { viewModel.loadMore() },
                                onSearch = { query -> viewModel.search(query) },
                                isDiscoverTab = state.selectedTab == WallpaperTab.DISCOVER,
                                topVoted = visibleSections.topVoted,
                                seasonalTheme = if (state.selectedTab == WallpaperTab.DISCOVER) viewModel.seasonalTheme else null,
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        )

        FloatingActionTray(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            showThemeMatch = state.selectedTab == WallpaperTab.DISCOVER,
            onThemeMatch = { viewModel.matchMyTheme() },
            onSurpriseMe = { viewModel.loadRandom() },
        )
    }

    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            WallpaperFiltersSheet(
                selectedTab = state.selectedTab,
                discoverFilter = state.discoverFilter,
                selectedColor = state.selectedColor,
                topRange = state.topRange,
                onSelectDiscoverFilter = { filter ->
                    viewModel.setDiscoverFilter(filter)
                    showFiltersSheet = false
                },
                onSelectColor = { color ->
                    viewModel.searchByColor(color)
                    showFiltersSheet = false
                },
                onClearColor = if (state.selectedColor != null) {
                    {
                        viewModel.clearActiveFilter()
                        showFiltersSheet = false
                    }
                } else null,
                onSelectTopRange = { range ->
                    viewModel.setTopRange(range)
                    showFiltersSheet = false
                },
                onResetFilters = if (wallpaperFilterCount > 0) {
                    {
                        if (state.selectedColor != null) {
                            viewModel.clearActiveFilter()
                        }
                        if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                            viewModel.setDiscoverFilter(WallpaperDiscoverFilter.FOR_YOU)
                        }
                        if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") {
                            viewModel.setTopRange("1M")
                        }
                        showFiltersSheet = false
                    }
                } else null,
            )
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
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

@Composable
private fun WallpaperFiltersSheet(
    selectedTab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
    selectedColor: String?,
    topRange: String,
    onSelectDiscoverFilter: (WallpaperDiscoverFilter) -> Unit,
    onSelectColor: (String) -> Unit,
    onClearColor: (() -> Unit)?,
    onSelectTopRange: (String) -> Unit,
    onResetFilters: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Refine feed", style = MaterialTheme.typography.titleMedium)
        if (selectedTab == WallpaperTab.DISCOVER) {
            Text(
                "Discover mix",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperDiscoverFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = discoverFilter == filter,
                        onClick = { onSelectDiscoverFilter(filter) },
                        label = { Text(wallpaperFilterLabel(filter)) },
                        leadingIcon = if (discoverFilter == filter) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }

        Text(
            "Color tone",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorPickerRow(
            selectedColor = selectedColor,
            onColorSelected = onSelectColor,
            onClear = onClearColor,
        )

        if (selectedTab == WallpaperTab.WALLHAVEN) {
            Text(
                "Wallhaven time range",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("1d" to "Today", "1w" to "Week", "1M" to "Month", "6M" to "6 Months", "1y" to "Year").forEach { (range, label) ->
                    FilterChip(
                        selected = topRange == range,
                        onClick = { onSelectTopRange(range) },
                        label = { Text(label) },
                        leadingIcon = if (topRange == range) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }

        onResetFilters?.let {
            TextButton(onClick = it) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset filters")
            }
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
    favoriteIdentities: Set<FavoriteIdentity> = emptySet(),
    hiddenIds: Set<String> = emptySet(),
    onUpvote: ((String) -> Unit)? = null,
    onDownvote: ((String) -> Unit)? = null,
    voteCounts: Map<String, Int> = emptyMap(),
    onLoadMore: () -> Unit,
    onSearch: ((String) -> Unit)? = null,
    isDiscoverTab: Boolean = false,
    topVoted: List<Pair<Wallpaper, Int>> = emptyList(),
    seasonalTheme: SeasonalTheme? = null,
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

    val topVotedIds by remember(topVoted, isDiscoverTab) {
        derivedStateOf { if (isDiscoverTab) topVoted.map { it.first.stableKey() }.toSet() else emptySet() }
    }
    val visibleWallpapers by remember(wallpapers, hiddenIds, topVotedIds) {
        derivedStateOf {
            wallpapers
                .filter { !isWallpaperHidden(it, hiddenIds) && it.stableKey() !in topVotedIds }
        }
    }
    // Hoisted to derivedStateOf so the filter+take doesn't reallocate the list on every grid
    // body recomposition (the grid body runs every time any parent state flips).
    val visibleTopVoted by remember(topVoted, hiddenIds, isDiscoverTab) {
        derivedStateOf {
            if (!isDiscoverTab) emptyList()
            else topVoted.filter { !isWallpaperHidden(it.first, hiddenIds) }.take(10)
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 4)),
        state = gridState,
        contentPadding = PaddingValues(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 96.dp),
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
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp),
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
                                        startY = 44f,
                                    ),
                                ),
                        )
                        // Text overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Featured", Modifier.size(14.dp), tint = Color(0xFFFFD700))
                                Text("Wallpaper of the Day", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                            Text(
                                pick.category.ifEmpty { "Top voted on Reddit" },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                        // Arrow
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View wallpaper",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(18.dp),
                        )
                    }
                }
            }
        }

        // Seasonal banner — shown in Discover when a seasonal theme is active
        if (isDiscoverTab && seasonalTheme != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "seasonal_banner") {
                SeasonalBannerCard(
                    theme = seasonalTheme,
                    onClick = { onSearch?.invoke(seasonalTheme.wallpaperQuery) },
                )
            }
        }

        // Curated collections carousel (Discover tab only)
        if (isDiscoverTab) {
            item(span = StaggeredGridItemSpan.FullLine, key = "curated_collections") {
                DiscoverCollectionsRow(onSearch = onSearch)
            }

        }

        // Top upvoted wallpapers section (Discover only, from community votes across all tabs)
        if (isDiscoverTab && visibleTopVoted.isNotEmpty()) {
            visibleTopVoted.forEach { (wp, votes) ->
                item(key = "top_${wp.stableKey()}") {
                    val isFav = wp.favoriteIdentity() in favoriteIdentities
                    WallpaperCard(
                        wallpaper = wp,
                        isFavorite = isFav,
                        voteCount = votes,
                        onClick = { onWallpaperClick(wp) },
                        onFavoriteClick = onLongPress?.let { { it(wp) } },
                        onLongPress = onDownvote?.let { { it(wp.stableKey()) } },
                        onUpvote = onUpvote?.let { { it(wp.stableKey()) } },
                    )
                }
            }
        }

        items(visibleWallpapers, key = { it.stableKey() }, contentType = { "wallpaper_card" }) { wallpaper ->
            val isFav = wallpaper.favoriteIdentity() in favoriteIdentities
            WallpaperCard(
                wallpaper = wallpaper,
                isFavorite = isFav,
                voteCount = voteCounts[wallpaper.stableKey()] ?: 0,
                onClick = { onWallpaperClick(wallpaper) },
                onFavoriteClick = onLongPress?.let { { it(wallpaper) } },
                onLongPress = onDownvote?.let { { it(wallpaper.stableKey()) } },
                onUpvote = onUpvote?.let { { it(wallpaper.stableKey()) } },
                onDownvote = onDownvote?.let { { it(wallpaper.stableKey()) } },
            )
        }

        if (isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine, key = "loading_more") {
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    val hints = wallpaper.qualityHints()
    val badges = buildList {
        add(hints.resolutionLabel)
        add(hints.orientationLabel)
        if (hints.isAmoled) add("AMOLED")
        if (hints.isIconSafe) add("Icon-safe")
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        if (wallpaper.category.isNotBlank()) {
                            Text(
                                wallpaper.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        badges.take(4).forEach { badge ->
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    badge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
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

private data class WallpaperStateAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun WallpaperStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryAction: WallpaperStateAction? = null,
    secondaryAction: WallpaperStateAction? = null,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        highlightHeight = 128.dp,
        shadowElevation = 8.dp,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (primaryAction != null || secondaryAction != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryAction?.let { action ->
                    Button(
                        onClick = action.onClick,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action.label)
                    }
                }
                secondaryAction?.let { action ->
                    OutlinedButton(
                        onClick = action.onClick,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonalBannerCard(
    theme: SeasonalTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = Color(0xFFFFCA28) // warm amber-gold
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.36f)),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.18f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Celebration,
                        contentDescription = theme.label,
                        modifier = Modifier.size(22.dp),
                        tint = accentColor,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    theme.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    theme.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.14f),
            ) {
                Text(
                    "Explore",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
            }
        }
    }
}

@Composable
private fun DiscoverCollectionsRow(
    onSearch: ((String) -> Unit)?,
) {
    val collections = remember {
        listOf(
            DiscoverCollectionShortcut("AMOLED Black", "Deep blacks and low-glow contrast", "amoled black dark", Icons.Default.DarkMode, Color(0xFF7F5AF0)),
            DiscoverCollectionShortcut("Minimal", "Clean lines with quiet composition", "minimal clean simple", Icons.Default.CropSquare, Color(0xFF3A86FF)),
            DiscoverCollectionShortcut("Nature 4K", "Landscapes, forests, and natural depth", "nature landscape 4k", Icons.Default.Landscape, Color(0xFF43AA8B)),
            DiscoverCollectionShortcut("Cyberpunk", "Electric nightlife and neon geometry", "cyberpunk neon city", Icons.Default.Bolt, Color(0xFFFF5D8F)),
            DiscoverCollectionShortcut("Space", "Nebulae, stars, and cinematic skies", "space galaxy nebula", Icons.Default.Public, Color(0xFF8E9AAF)),
            DiscoverCollectionShortcut("Abstract", "Gradients, form studies, and color fields", "abstract colorful gradient", Icons.Default.AutoAwesome, Color(0xFFF4A261)),
            DiscoverCollectionShortcut("Anime", "Illustrated scenes and stylized worlds", "anime art illustration", Icons.Default.Movie, Color(0xFFE76F51)),
            DiscoverCollectionShortcut("Ocean", "Waves, shoreline light, and cool tones", "ocean sea waves beach", Icons.Default.Water, Color(0xFF219EBC)),
            DiscoverCollectionShortcut("Mountains", "Peaks, fog, and wide scenic balance", "mountain peak scenic", Icons.Default.Terrain, Color(0xFF6A994E)),
            DiscoverCollectionShortcut("Urban", "Skylines, streets, and city atmosphere", "city skyline urban night", Icons.Default.LocationCity, Color(0xFF6D6875)),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Explore Collections",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            "Quick routes into polished looks when you want a more directed browse.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(collections.size) { index ->
                val shortcut = collections[index]
                Surface(
                    onClick = { onSearch?.invoke(shortcut.query) },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.width(176.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = shortcut.tint.copy(alpha = 0.14f),
                        ) {
                            Icon(
                                imageVector = shortcut.icon,
                                contentDescription = null,
                                tint = shortcut.tint,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(18.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shortcut.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                shortcut.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DiscoverCollectionShortcut(
    val title: String,
    val description: String,
    val query: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
)

@Composable
private fun FloatingActionTray(
    modifier: Modifier = Modifier,
    showThemeMatch: Boolean,
    onThemeMatch: () -> Unit,
    onSurpriseMe: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (showThemeMatch) {
            FloatingActionRow(
                icon = Icons.Default.Palette,
                label = "Theme match",
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onThemeMatch,
            )
        }
        FloatingActionRow(
            icon = Icons.Default.Shuffle,
            label = "Surprise me",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onSurpriseMe,
        )
    }
}

@Composable
private fun FloatingActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

private fun wallpaperHeaderEyebrow(
    tab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
): String = when (tab) {
    WallpaperTab.DISCOVER -> when (discoverFilter) {
        WallpaperDiscoverFilter.FOR_YOU -> "Curated for your device"
        else -> "${wallpaperFilterLabel(discoverFilter)} discover mix"
    }
    WallpaperTab.SEARCH -> "Cross-source search"
    WallpaperTab.COLOR -> "Color-focused browsing"
    else -> "${wallpaperTabLabel(tab)} source"
}

private fun wallpaperHeaderTitle(
    tab: WallpaperTab,
    query: String,
): String = when (tab) {
    WallpaperTab.DISCOVER -> "Discover wallpapers"
    WallpaperTab.SEARCH -> if (query.isNotBlank()) "Results for \"$query\"" else "Search results"
    WallpaperTab.COLOR -> "Browse by tone"
    else -> wallpaperTabLabel(tab)
}

private fun wallpaperHeaderSubtitle(
    tab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
    query: String,
    selectedColor: String?,
    wallpaperCount: Int,
): String = when (tab) {
    WallpaperTab.DISCOVER -> when {
        wallpaperCount > 0 && discoverFilter == WallpaperDiscoverFilter.FOR_YOU ->
            "$wallpaperCount polished picks are ready across trusted sources, tuned for phone-friendly browsing."
        discoverFilter != WallpaperDiscoverFilter.FOR_YOU ->
            "Showing a ${wallpaperFilterLabel(discoverFilter).lowercase(java.util.Locale.ROOT)} mix while keeping quality and composition in focus."
        else ->
            "Curated wallpaper picks optimized for phone-friendly composition and cleaner home screens."
    }
    WallpaperTab.SEARCH -> if (query.isNotBlank()) {
        "Searching across multiple wallpaper sources for broader, more useful matches."
    } else {
        "Use keywords, themes, or moods to pull in wallpapers from multiple providers."
    }
    WallpaperTab.COLOR -> if (selectedColor != null) {
        "Explore wallpapers built around this tone, then jump back to Discover whenever you want a wider mix."
    } else {
        "Pick a tone to bias results toward a specific visual mood."
    }
    WallpaperTab.WALLHAVEN -> "High-signal artwork and photography with stronger filtering controls."
    WallpaperTab.PEXELS -> "Clean photography and motion-friendly imagery from Pexels."
    WallpaperTab.PIXABAY -> "Free-use imagery with a broad catalog once your source is configured."
    WallpaperTab.REDDIT -> "Community-driven finds, trending daily picks, and unexpected standouts."
}

private fun wallpaperTabIcon(tab: WallpaperTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    WallpaperTab.DISCOVER -> Icons.Default.Explore
    WallpaperTab.PEXELS -> Icons.Default.PhotoLibrary
    WallpaperTab.PIXABAY -> Icons.Default.Collections
    WallpaperTab.REDDIT -> Icons.Default.Public
    WallpaperTab.WALLHAVEN -> Icons.Default.ImageSearch
    WallpaperTab.COLOR -> Icons.Default.Palette
    WallpaperTab.SEARCH -> Icons.Default.Search
}

private fun wallpaperTabLabel(tab: WallpaperTab): String =
    when (tab) {
        WallpaperTab.DISCOVER -> "Discover"
        WallpaperTab.PEXELS -> "Pexels"
        WallpaperTab.PIXABAY -> "Pixabay"
        WallpaperTab.REDDIT -> "Reddit"
        WallpaperTab.WALLHAVEN -> "Wallhaven"
        WallpaperTab.COLOR -> "Color"
        WallpaperTab.SEARCH -> "Search"
    }

private fun wallpaperFilterLabel(filter: WallpaperDiscoverFilter): String = when (filter) {
    WallpaperDiscoverFilter.FOR_YOU -> "For You"
    WallpaperDiscoverFilter.AMOLED -> "AMOLED"
    WallpaperDiscoverFilter.HIGH_RES -> "4K+"
    WallpaperDiscoverFilter.PORTRAIT -> "Portrait"
    WallpaperDiscoverFilter.ICON_SAFE -> "Icon Safe"
}

private fun wallpaperTopRangeLabel(range: String): String = when (range) {
    "1d" -> "Today"
    "1w" -> "Week"
    "1M" -> "Month"
    "6M" -> "6 Months"
    "1y" -> "Year"
    else -> range
}

private fun parseHexColor(hex: String): Color {
    val normalized = hex.removePrefix("#")
    return runCatching {
        Color(android.graphics.Color.parseColor("#$normalized"))
    }.getOrElse { Color(0xFF7C8CFF) }
}

internal data class VisibleWallpaperSections(
    val dailyPick: Wallpaper?,
    val topVoted: List<Pair<Wallpaper, Int>>,
    val feedWallpapers: List<Wallpaper>,
    val pagerWallpapers: List<Wallpaper>,
    val hasRenderableContent: Boolean,
)

internal data class WallpaperPagerItems(
    val wallpapers: List<Wallpaper>,
    val initialPage: Int,
)

internal fun computeVisibleWallpaperSections(
    wallpapers: List<Wallpaper>,
    hiddenIds: Set<String>,
    topVoted: List<Pair<Wallpaper, Int>>,
    dailyPick: Wallpaper?,
    isDiscoverTab: Boolean,
): VisibleWallpaperSections {
    val visibleDailyPick = dailyPick?.takeIf { !isWallpaperHidden(it, hiddenIds) && isDiscoverTab }
    val dailyKey = visibleDailyPick?.stableKey()
    val visibleTopVoted = if (isDiscoverTab) {
        topVoted
            .filter { (wallpaper, _) -> !isWallpaperHidden(wallpaper, hiddenIds) }
            .distinctBy { (wallpaper, _) -> wallpaper.stableKey() }
            .filterNot { (wallpaper, _) -> wallpaper.stableKey() == dailyKey }
    } else {
        emptyList()
    }
    val featuredKeys = buildSet {
        dailyKey?.let(::add)
        addAll(visibleTopVoted.map { (wallpaper, _) -> wallpaper.stableKey() })
    }
    val feedWallpapers = wallpapers
        .filter { !isWallpaperHidden(it, hiddenIds) }
        .distinctBy { it.stableKey() }
        .filterNot { it.stableKey() in featuredKeys }
    val pagerWallpapers = buildList {
        visibleDailyPick?.let(::add)
        addAll(visibleTopVoted.map { (wallpaper, _) -> wallpaper })
        addAll(feedWallpapers)
    }.distinctBy { it.stableKey() }

    return VisibleWallpaperSections(
        dailyPick = visibleDailyPick,
        topVoted = visibleTopVoted,
        feedWallpapers = feedWallpapers,
        pagerWallpapers = pagerWallpapers,
        hasRenderableContent = pagerWallpapers.isNotEmpty(),
    )
}

internal fun computeWallpaperPagerItems(
    currentWallpaper: Wallpaper,
    sharedWallpapers: List<Wallpaper>,
    hiddenIds: Set<String>,
    sharedListAnchorKey: String? = null,
): WallpaperPagerItems {
    val currentKey = currentWallpaper.stableKey()
    val sharedContainsCurrent = sharedWallpapers.any { it.stableKey() == currentKey }
    val includeSharedList = sharedContainsCurrent &&
        (sharedListAnchorKey == null || sharedListAnchorKey == currentKey)
    val wallpapers = (if (includeSharedList) sharedWallpapers else listOf(currentWallpaper))
        .distinctBy { it.stableKey() }
        .filter { !isWallpaperHidden(it, hiddenIds) }
    val initialPage = wallpapers.indexOfFirst { it.stableKey() == currentKey }
        .takeIf { it >= 0 }
        ?: 0
    return WallpaperPagerItems(wallpapers = wallpapers, initialPage = initialPage)
}

internal fun isWallpaperHidden(
    wallpaper: Wallpaper,
    hiddenIds: Set<String>,
): Boolean = matchesHiddenIds(hiddenIds, wallpaper.stableKey(), wallpaper.id)

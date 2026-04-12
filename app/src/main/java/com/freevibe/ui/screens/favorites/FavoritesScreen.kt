package com.freevibe.ui.screens.favorites

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.stableKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onWallpaperClick: (FavoriteEntity) -> Unit,
    onSoundClick: (FavoriteEntity) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val wallpapers by viewModel.wallpapers.collectAsStateWithLifecycle()
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf("recent") } // recent, name, oldest
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = listOf("Wallpapers (${wallpapers.size})", "Sounds (${sounds.size})")

    // -- Bulk selection state (wallpaper tab only in v1) --
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    fun exitSelection() { selectionMode = false; selectedKeys = emptySet() }
    fun toggleSelect(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        if (selectedKeys.isEmpty()) selectionMode = false
    }
    // Exit selection on back before closing the screen.
    BackHandler(enabled = selectionMode) { exitSelection() }
    // Also exit if the user switches tabs — selection is scoped to the wallpaper tab.
    LaunchedEffect(selectedTab) { if (selectedTab != 0) exitSelection() }

    val sortedWallpapers = remember(wallpapers, sortBy) {
        when (sortBy) {
            "name" -> wallpapers.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "oldest" -> wallpapers.sortedBy { it.addedAt }
            else -> wallpapers.sortedByDescending { it.addedAt }
        }
    }
    val sortedSounds = remember(sounds, sortBy) {
        when (sortBy) {
            "name" -> sounds.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "oldest" -> sounds.sortedBy { it.addedAt }
            else -> sounds.sortedByDescending { it.addedAt }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportFavorites(it) } }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFavorites(it) } }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedKeys.size} selected", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        val allKeys = sortedWallpapers.map { it.stableKey() }.toSet()
                        val allSelected = allKeys.isNotEmpty() && selectedKeys.containsAll(allKeys)
                        IconButton(onClick = {
                            selectedKeys = if (allSelected) emptySet() else allKeys
                            if (selectedKeys.isEmpty()) selectionMode = false
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all",
                            )
                        }
                        IconButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                viewModel.bulkDownload(selectedKeys)
                                exitSelection()
                            },
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Download selected")
                        }
                        IconButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                val snapshot = selectedKeys
                                val snapshotItems = sortedWallpapers.filter { it.stableKey() in snapshot }
                                viewModel.bulkDelete(snapshot)
                                exitSelection()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed ${snapshot.size} favorite${if (snapshot.size == 1) "" else "s"}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        snapshotItems.forEach { viewModel.restoreFavorite(it) }
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else {
            TopAppBar(
                title = { Text("Favorites") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export favorites") },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("freevibe_favorites.json")
                                },
                                leadingIcon = { Icon(Icons.Default.Upload, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Import favorites") },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                            )
                            if (wallpapers.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Download all wallpapers") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.downloadAllWallpapers()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sort: Recent first") },
                                onClick = { sortBy = "recent"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Name A-Z") },
                                onClick = { sortBy = "name"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Oldest first") },
                                onClick = { sortBy = "oldest"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.History, null) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            // Batch-download progress banner: previously the "Download all" action started work
            // but surfaced no progress. Now we render a compact linear indicator + counts whenever
            // BatchDownloadService is running.
            if (batchState.isRunning || (batchState.totalCount > 0 && !batchState.isComplete)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Downloading ${batchState.completedCount + batchState.failedCount}/${batchState.totalCount}" +
                                    if (batchState.failedCount > 0) " (${batchState.failedCount} failed)" else "",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (batchState.currentItem.isNotBlank()) {
                                Text(
                                    text = batchState.currentItem,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { batchState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (sortedWallpapers.isEmpty()) {
                        EmptyState("No favorite wallpapers yet", Icons.Default.Wallpaper)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sortedWallpapers, key = { it.stableKey() }, contentType = { "favorite_card" }) { fav ->
                                val key = fav.stableKey()
                                val isSelected = key in selectedKeys
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp),
                                                )
                                            } else Modifier,
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelect(key)
                                                } else {
                                                    viewModel.selectWallpaper(fav, sortedWallpapers)
                                                    onWallpaperClick(fav)
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) selectionMode = true
                                                toggleSelect(key)
                                            },
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = fav.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
                                        )
                                        if (selectionMode) {
                                            // Dim unselected cards to emphasize the selection.
                                            if (!isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                                                )
                                            }
                                            // Selection indicator: filled check for selected, outlined circle otherwise.
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(26.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (sortedSounds.isEmpty()) {
                        EmptyState("No favorite sounds yet", Icons.Default.MusicNote)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(sortedSounds, key = { it.stableKey() }, contentType = { "favorite_card" }) { fav ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value != SwipeToDismissBoxValue.Settled) {
                                            viewModel.removeFavorite(fav)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Removed ${fav.name}",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.restoreFavorite(fav)
                                                }
                                            }
                                            true
                                        } else false
                                    },
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                    enableDismissFromStartToEnd = false,
                                ) {
                                    Surface(
                                        onClick = {
                                            viewModel.selectSound(fav)
                                            onSoundClick(fav)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fav.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (fav.duration > 0) {
                                                    Text("${fav.duration.toInt()}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

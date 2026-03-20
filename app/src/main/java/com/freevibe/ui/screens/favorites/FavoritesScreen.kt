package com.freevibe.ui.screens.favorites

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.BatchDownloadService
import com.freevibe.service.FavoritesExporter
import com.freevibe.service.SelectedContentHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepo: FavoritesRepository,
    private val exporter: FavoritesExporter,
    private val selectedContent: SelectedContentHolder,
    private val batchDownloadService: BatchDownloadService,
) : ViewModel() {
    val wallpapers = favoritesRepo.getWallpapers().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val sounds = favoritesRepo.getSounds().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun removeFavorite(id: String) = viewModelScope.launch { favoritesRepo.remove(id) }
    fun restoreFavorite(entity: FavoriteEntity) = viewModelScope.launch { favoritesRepo.add(entity) }

    /** Convert FavoriteEntity to domain Wallpaper and populate shared holder */
    fun selectWallpaper(fav: FavoriteEntity) {
        selectedContent.selectWallpaper(fav.toWallpaper())
    }

    /** Convert FavoriteEntity to domain Sound and populate shared holder */
    fun selectSound(fav: FavoriteEntity) {
        selectedContent.selectSound(fav.toSound())
    }

    fun exportFavorites(uri: Uri) = viewModelScope.launch {
        exporter.export(uri)
            .onSuccess { count -> _message.value = "Exported $count favorites" }
            .onFailure { _message.value = "Export failed: ${it.message}" }
    }

    fun importFavorites(uri: Uri) = viewModelScope.launch {
        exporter.import(uri)
            .onSuccess { count -> _message.value = "Imported $count favorites" }
            .onFailure { _message.value = "Import failed: ${it.message}" }
    }

    val batchState = batchDownloadService.state

    fun downloadAllWallpapers() {
        val wps = wallpapers.value.map { it.toWallpaper() }
        if (wps.isEmpty()) return
        batchDownloadService.downloadBatch(wps)
        _message.value = "Downloading ${wps.size} wallpapers..."
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onWallpaperClick: (FavoriteEntity) -> Unit,
    onSoundClick: (FavoriteEntity) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val wallpapers by viewModel.wallpapers.collectAsState()
    val sounds by viewModel.sounds.collectAsState()
    val message by viewModel.message.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = listOf("Wallpapers (${wallpapers.size})", "Sounds (${sounds.size})")

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
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )

            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (wallpapers.isEmpty()) {
                        EmptyState("No favorite wallpapers yet", Icons.Default.Wallpaper)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(wallpapers, key = { it.id }) { fav ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.selectWallpaper(fav)
                                                onWallpaperClick(fav)
                                            },
                                            onLongClick = {
                                                viewModel.removeFavorite(fav.id)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Removed from favorites",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.restoreFavorite(fav)
                                                    }
                                                }
                                            },
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    AsyncImage(
                                        model = fav.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (sounds.isEmpty()) {
                        EmptyState("No favorite sounds yet", Icons.Default.MusicNote)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(sounds, key = { it.id }) { fav ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value != SwipeToDismissBoxValue.Settled) {
                                            viewModel.removeFavorite(fav.id)
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

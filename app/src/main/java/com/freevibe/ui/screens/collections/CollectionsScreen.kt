package com.freevibe.ui.screens.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.SelectedContentHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    private val selectedContent: SelectedContentHolder,
) : ViewModel() {

    val collections = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId = _selectedCollectionId.asStateFlow()

    val selectedItems: StateFlow<List<WallpaperCollectionItemEntity>> = _selectedCollectionId
        .flatMapLatest { id ->
            if (id != null) collectionRepo.getItems(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCollection(id: Long) { _selectedCollectionId.value = id }
    fun clearSelection() { _selectedCollectionId.value = null }

    fun selectWallpaper(item: WallpaperCollectionItemEntity, items: List<WallpaperCollectionItemEntity>) {
        val wallpapers = items.map { it.toWallpaper() }
        selectedContent.selectWallpaper(
            item.toWallpaper(),
            wallpapers,
        )
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            collectionRepo.delete(id)
            _selectedCollectionId.value = null
        }
    }

    fun removeItem(collectionId: Long, wallpaperId: String) {
        viewModelScope.launch { collectionRepo.removeWallpaper(collectionId, wallpaperId) }
    }

    fun renameCollection(id: Long, name: String) {
        viewModelScope.launch { collectionRepo.rename(id, name) }
    }

    fun getItemCount(collectionId: Long): Flow<Int> = collectionRepo.getItemCount(collectionId)
    fun getCoverThumbnails(collectionId: Long): Flow<List<String>> = collectionRepo.getCoverThumbnails(collectionId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onWallpaperClick: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsState()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val selectedCollection = collections.find { it.collectionId == selectedCollectionId }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedCollection?.name ?: "Collections")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCollectionId != null) viewModel.clearSelection() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedCollection != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete collection") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deleteCollection(selectedCollection.collectionId)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (selectedCollectionId != null) {
            BackHandler { viewModel.clearSelection() }
            // Collection detail: grid of wallpapers
            if (selectedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This collection is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(selectedItems.size, key = { selectedItems[it].wallpaperId }) { index ->
                        val item = selectedItems[index]
                        @OptIn(ExperimentalFoundationApi::class)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        viewModel.selectWallpaper(item, selectedItems)
                                        onWallpaperClick(item.wallpaperId)
                                    },
                                    onLongClick = {
                                        val cid = selectedCollectionId ?: return@combinedClickable
                                        viewModel.removeItem(cid, item.wallpaperId)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Removed from collection")
                                        }
                                    },
                                ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
                            )
                        }
                    }
                }
            }
        } else {
            // Collection list
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No collections yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Save wallpapers from the detail screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(collections, key = { it.collectionId }) { collection ->
                        CollectionCard(
                            collection = collection,
                            viewModel = viewModel,
                            onClick = { viewModel.selectCollection(collection.collectionId) },
                        )
                    }
                }
            }
        }
    }
}

private fun WallpaperCollectionItemEntity.toWallpaper() = Wallpaper(
    id = wallpaperId,
    source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
)

@Composable
private fun CollectionCard(
    collection: WallpaperCollectionEntity,
    viewModel: CollectionsViewModel,
    onClick: () -> Unit,
) {
    val countFlow = remember(collection.collectionId) { viewModel.getItemCount(collection.collectionId) }
    val coversFlow = remember(collection.collectionId) { viewModel.getCoverThumbnails(collection.collectionId) }
    val count by countFlow.collectAsState(initial = 0)
    val covers by coversFlow.collectAsState(initial = emptyList())

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover preview (2x2 grid of thumbnails)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (covers.isNotEmpty()) {
                    val gridSize = if (covers.size >= 4) 2 else 1
                    val displayCovers = covers.take(gridSize * gridSize)
                    Column {
                        for (row in 0 until gridSize) {
                            Row(modifier = Modifier.weight(1f)) {
                                for (col in 0 until gridSize) {
                                    val idx = row * gridSize + col
                                    if (idx < displayCovers.size) {
                                        AsyncImage(
                                            model = displayCovers[idx],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$count wallpapers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

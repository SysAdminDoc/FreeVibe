package com.freevibe.ui.screens.collections

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.CollectionExporter
import com.freevibe.service.CollectionImportResult
import com.freevibe.service.SelectedContentHolder
import com.freevibe.ui.components.AuraStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareCollectionEvent {
    data class Ready(val intent: Intent, val collectionName: String) : ShareCollectionEvent
    data class Message(val message: String) : ShareCollectionEvent
    data class Failure(val message: String) : ShareCollectionEvent
}

data class CollectionQrState(
    val collectionName: String,
    val shareLink: String,
    val itemCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    private val selectedContent: SelectedContentHolder,
    private val collectionExporter: CollectionExporter,
) : ViewModel() {

    private val _shareEvent = MutableStateFlow<ShareCollectionEvent?>(null)
    val shareEvent: StateFlow<ShareCollectionEvent?> = _shareEvent.asStateFlow()
    fun consumeShareEvent() { _shareEvent.value = null }

    private val _qrState = MutableStateFlow<CollectionQrState?>(null)
    val qrState: StateFlow<CollectionQrState?> = _qrState.asStateFlow()
    fun dismissQr() { _qrState.value = null }

    fun shareCollection(collection: WallpaperCollectionEntity) {
        viewModelScope.launch {
            collectionExporter.prepareShareBundle(collection.collectionId, collection.name)
                .onSuccess { bundle ->
                    _shareEvent.value = ShareCollectionEvent.Ready(
                        collectionExporter.buildShareIntent(bundle),
                        bundle.collectionName,
                    )
                }
                .onFailure { e ->
                    _shareEvent.value = ShareCollectionEvent.Failure(
                        e.message ?: "Couldn't prepare this collection for sharing."
                    )
                }
        }
    }

    fun showQr(collection: WallpaperCollectionEntity) {
        viewModelScope.launch {
            collectionExporter.publishShareLink(collection.collectionId, collection.name)
                .onSuccess { link ->
                    _qrState.value = CollectionQrState(
                        collectionName = link.collectionName,
                        shareLink = link.link,
                        itemCount = link.itemCount,
                    )
                }
                .onFailure { e ->
                    _shareEvent.value = ShareCollectionEvent.Failure(
                        e.message ?: "Couldn't create a share link for this collection.",
                    )
                }
        }
    }

    fun importCollectionLink(input: String) {
        viewModelScope.launch {
            collectionExporter.importFromTokenOrLink(input).handleImportResult()
        }
    }

    fun importCollectionFile(uri: Uri) {
        viewModelScope.launch {
            collectionExporter.importFromUri(uri).handleImportResult()
        }
    }

    fun importCollectionQr(uri: Uri) {
        viewModelScope.launch {
            collectionExporter.importFromQrImage(uri).handleImportResult()
        }
    }

    fun buildQrBitmap(link: String) = collectionExporter.buildQrBitmap(link)

    private fun Result<CollectionImportResult>.handleImportResult() {
        onSuccess { result ->
            _selectedCollectionId.value = result.collectionId
            _shareEvent.value = ShareCollectionEvent.Message(
                "Imported ${result.itemCount} wallpapers into ${result.collectionName}."
            )
        }.onFailure { e ->
            _shareEvent.value = ShareCollectionEvent.Failure(
                e.message ?: "Couldn't import this collection.",
            )
        }
    }

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

    fun removeItem(collectionId: Long, item: WallpaperCollectionItemEntity) {
        viewModelScope.launch { collectionRepo.removeWallpaper(collectionId, item.toWallpaper()) }
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
    onWallpaperClick: (Wallpaper) -> Unit,
    initialImportToken: String? = null,
    initialImportUri: String? = null,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val selectedCollection = collections.find { it.collectionId == selectedCollectionId }
    val qrState by viewModel.qrState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showImportSheet by remember { mutableStateOf(false) }

    // Observe prepared share/import events and keep system intents out of recomposition.
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareEvent by viewModel.shareEvent.collectAsStateWithLifecycle()
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCollectionFile)
    }
    // QR code import via Photo Picker (no READ_MEDIA_IMAGES; scoped-storage compliant).
    val qrImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::importCollectionQr)
    }
    val qrImportPickerRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    LaunchedEffect(initialImportToken, initialImportUri) {
        initialImportToken?.let(viewModel::importCollectionLink)
        initialImportUri?.let { viewModel.importCollectionFile(Uri.parse(it)) }
    }

    LaunchedEffect(shareEvent) {
        val event = shareEvent
        when (event) {
            is ShareCollectionEvent.Ready -> {
                val intent = android.content.Intent.createChooser(
                    event.intent,
                    "Share \"${event.collectionName}\"",
                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { context.startActivity(intent) } catch (_: Exception) {
                    scope.launch { snackbarHostState.showSnackbar("No app to share to") }
                }
                viewModel.consumeShareEvent()
            }
            is ShareCollectionEvent.Message -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.consumeShareEvent()
            }
            is ShareCollectionEvent.Failure -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.consumeShareEvent()
            }
            null -> Unit
        }
    }

    if (showImportSheet) {
        ImportCollectionSheet(
            onDismiss = { showImportSheet = false },
            onImportLink = { link ->
                showImportSheet = false
                viewModel.importCollectionLink(link)
            },
            onOpenFile = {
                showImportSheet = false
                jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
            },
            onOpenQrImage = {
                showImportSheet = false
                qrImportLauncher.launch(qrImportPickerRequest)
            },
        )
    }

    qrState?.let { state ->
        CollectionQrDialog(
            state = state,
            qrBitmap = remember(state.shareLink) { viewModel.buildQrBitmap(state.shareLink).asImageBitmap() },
            onCopyLink = {
                clipboard.setText(AnnotatedString(state.shareLink))
                scope.launch { snackbarHostState.showSnackbar("Collection link copied") }
            },
            onDismiss = viewModel::dismissQr,
        )
    }

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
                                    text = { Text("Share link and file") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.shareCollection(selectedCollection)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Show QR code") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.showQr(selectedCollection)
                                    },
                                    leadingIcon = { Icon(Icons.Default.QrCode2, null) },
                                )
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
                    } else {
                        IconButton(onClick = { showImportSheet = true }) {
                            Icon(Icons.Default.FileDownload, "Import collection")
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
                    AuraStateCard(
                        icon = Icons.Default.Folder,
                        title = "This collection is empty",
                        description = "Save wallpapers from detail pages to turn this into a curated set.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(selectedItems.size, key = { selectedItems[it].stableKey() }) { index ->
                        val item = selectedItems[index]
                        @OptIn(ExperimentalFoundationApi::class)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        val wallpaper = item.toWallpaper()
                                        viewModel.selectWallpaper(item, selectedItems)
                                        onWallpaperClick(wallpaper)
                                    },
                                    onLongClick = {
                                        val cid = selectedCollectionId ?: return@combinedClickable
                                        viewModel.removeItem(cid, item)
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
                    AuraStateCard(
                        icon = Icons.Default.CreateNewFolder,
                        title = "No collections yet",
                        description = "Use collections to group wallpapers by mood, room, season, or setup.",
                        modifier = Modifier.padding(24.dp),
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCollectionSheet(
    onDismiss: () -> Unit,
    onImportLink: (String) -> Unit,
    onOpenFile: () -> Unit,
    onOpenQrImage: () -> Unit,
) {
    var link by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Import collection", style = MaterialTheme.typography.titleLarge)
            Text(
                "Paste an Aura collection link, open a shared JSON file, or scan a QR image.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Aura collection link") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = { onImportLink(link) },
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import link")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("JSON")
                }
                OutlinedButton(
                    onClick = onOpenQrImage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("QR image")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CollectionQrDialog(
    state: CollectionQrState,
    qrBitmap: ImageBitmap,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
        title = { Text("Collection QR code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    tonalElevation = 0.dp,
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR code for ${state.collectionName}",
                        modifier = Modifier
                            .padding(12.dp)
                            .size(220.dp),
                    )
                }
                Text(
                    "${state.collectionName} - ${state.itemCount} wallpapers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.shareLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyLink, shape = RoundedCornerShape(10.dp)) {
                Text("Copy link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Done")
            }
        },
    )
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
    val count by countFlow.collectAsStateWithLifecycle(initialValue = 0)
    val covers by coversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
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

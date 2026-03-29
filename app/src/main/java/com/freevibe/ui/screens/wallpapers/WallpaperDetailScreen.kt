package com.freevibe.ui.screens.wallpapers

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onCrop: () -> Unit = {},
    onFindSimilar: ((String) -> Unit)? = null,
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val wallpaper by viewModel.selectedWallpaper.collectAsState()
    val sharedList by viewModel.sharedWallpaperList.collectAsState()
    val wp = wallpaper ?: return

    val wallpapers = remember(sharedList, wp.id) {
        val others = sharedList.filter { it.id != wp.id }
        listOf(wp) + others
    }

    val pagerState = rememberPagerState(initialPage = 0) { wallpapers.size.coerceAtLeast(1) }

    LaunchedEffect(pagerState.settledPage) {
        wallpapers.getOrNull(pagerState.settledPage)?.let {
            viewModel.selectWallpaperOnly(it)
        }
        if (pagerState.settledPage >= wallpapers.size - 3) viewModel.loadMore()
    }

    val isFavorite by viewModel.isFavorite(wp.id).collectAsState(initial = false)
    val collections by viewModel.collections.collectAsState()
    val colorPalette by viewModel.colorPalette.collectAsState()

    LaunchedEffect(wp.id) {
        viewModel.extractColors(wp.thumbnailUrl.ifEmpty { wp.fullUrl })
    }

    var showApplyOptions by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Wallpaper pager
            if (wallpapers.size > 1) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    val pageUrl = wallpapers.getOrNull(page)?.fullUrl ?: wp.fullUrl
                    WallpaperImage(
                        url = pageUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f + (pageOffset.absoluteValue * 0.15f).coerceAtMost(0.15f)
                                scaleX = scale; scaleY = scale
                                translationY = pageOffset * size.height * 0.06f
                                alpha = 1f - (pageOffset.absoluteValue * 0.3f).coerceAtMost(0.3f)
                            },
                    )
                }
            } else {
                WallpaperImage(url = wp.fullUrl, modifier = Modifier.fillMaxSize())
            }

            // Top: back button + resolution
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
            )
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                if (wp.width > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "${wp.width}x${wp.height}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // Bottom: apply + actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Apply button
                Button(
                    onClick = { showApplyOptions = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !state.isApplying,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state.isApplying) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Wallpaper, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Wallpaper")
                    }
                }

                // Action row: favorite, download, share, more
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionCircle(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        onClick = { viewModel.toggleFavorite(wp) },
                    )
                    ActionCircle(Icons.Default.Download, onClick = { viewModel.downloadWallpaper(wp) })
                    ActionCircle(Icons.Default.Share, onClick = {
                        val shareUrl = wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    })
                    ActionCircle(Icons.Default.MoreHoriz, onClick = { showMoreMenu = true })
                }
            }

            // Apply options sheet
            if (showApplyOptions) {
                ApplyOptionsSheet(
                    onDismiss = { showApplyOptions = false },
                    onApply = { target ->
                        showApplyOptions = false
                        viewModel.applyWallpaper(wp, target)
                    },
                    onSplitCrop = {
                        showApplyOptions = false
                        viewModel.applySplitCrop(wp)
                    },
                )
            }

            // More menu sheet
            if (showMoreMenu) {
                MoreActionsSheet(
                    onDismiss = { showMoreMenu = false },
                    onEdit = { showMoreMenu = false; onEdit() },
                    onCrop = { showMoreMenu = false; onCrop() },
                    onCollection = { showMoreMenu = false; showCollectionPicker = true },
                    onFindSimilar = colorPalette?.dominantColor?.takeIf { it != 0 }?.let { color ->
                        {
                            showMoreMenu = false
                            val hex = String.format("%06x", color and 0xFFFFFF)
                            if (onFindSimilar != null) onFindSimilar(hex)
                            else { viewModel.setPendingColorSearch(hex); onBack() }
                        }
                    },
                    uploaderName = wp.uploaderName,
                )
            }

            // Collection picker
            if (showCollectionPicker) {
                CollectionPickerSheet(
                    collections = collections,
                    onDismiss = { showCollectionPicker = false },
                    onSelectCollection = { id ->
                        showCollectionPicker = false
                        viewModel.addToCollection(id, wp)
                    },
                    onCreateNew = { name ->
                        showCollectionPicker = false
                        viewModel.createCollection(name, wp)
                    },
                )
            }
        }
    }
}

@Composable
private fun WallpaperImage(url: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Wallpaper",
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
                }
            }
            is AsyncImagePainter.State.Error -> {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.BrokenImage, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun ActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyOptionsSheet(
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
    onSplitCrop: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Set wallpaper", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            SheetOption(Icons.Default.Home, "Home screen") { onApply(WallpaperTarget.HOME) }
            SheetOption(Icons.Default.Lock, "Lock screen") { onApply(WallpaperTarget.LOCK) }
            SheetOption(Icons.Default.Smartphone, "Both") { onApply(WallpaperTarget.BOTH) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SheetOption(Icons.Default.Splitscreen, "Split crop (different home & lock)") { onSplitCrop() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsSheet(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCrop: () -> Unit,
    onCollection: () -> Unit,
    onFindSimilar: (() -> Unit)?,
    uploaderName: String = "",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (uploaderName.isNotEmpty()) {
                Text("by $uploaderName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            SheetOption(Icons.Default.Edit, "Edit") { onEdit() }
            SheetOption(Icons.Default.Crop, "Crop & position") { onCrop() }
            SheetOption(Icons.Default.CreateNewFolder, "Save to collection") { onCollection() }
            if (onFindSimilar != null) {
                SheetOption(Icons.Default.ColorLens, "Find similar colors") { onFindSimilar() }
            }
        }
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPickerSheet(
    collections: List<WallpaperCollectionEntity>,
    onDismiss: () -> Unit,
    onSelectCollection: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Save to Collection", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            collections.forEach { collection ->
                SheetOption(Icons.Default.Folder, collection.name) { onSelectCollection(collection.collectionId) }
            }
            if (showCreateField) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Collection name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    FilledTonalButton(onClick = { if (newName.isNotBlank()) onCreateNew(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Create") }
                }
            } else {
                SheetOption(Icons.Default.Add, "New collection") { showCreateField = true }
            }
        }
    }
}

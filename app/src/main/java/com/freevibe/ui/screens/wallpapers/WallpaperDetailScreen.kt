package com.freevibe.ui.screens.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
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
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.launchLiveWallpaperPicker
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperDetailScreen(
    wallpaperId: String,
    fallbackWallpaper: com.freevibe.data.model.Wallpaper? = null,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onCrop: (String) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
    onSearchColor: (String) -> Unit = {},
    onFindSimilar: (String) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sharedList by viewModel.sharedWallpaperList.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()
    var restoreResolved by remember(wallpaperId) { mutableStateOf(false) }
    var resolvedWallpaper by remember(wallpaperId, fallbackWallpaper?.id, fallbackWallpaper?.fullUrl) {
        mutableStateOf(fallbackWallpaper)
    }

    LaunchedEffect(wallpaperId, fallbackWallpaper?.id, fallbackWallpaper?.fullUrl) {
        resolvedWallpaper = viewModel.resolveWallpaper(wallpaperId) ?: fallbackWallpaper
        restoreResolved = true
    }

    val initialWp = resolvedWallpaper
    if (initialWp == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!restoreResolved) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BrokenImage,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Wallpaper unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }

    val wallpapers = remember(initialWp, sharedList, hiddenIds) {
        buildList {
            add(initialWp)
            addAll(sharedList)
        }
            .distinctBy { it.id }
            .filter { it.id !in hiddenIds }
    }

    if (wallpapers.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Track which wallpaper the pager is currently showing
    val pagerState = rememberPagerState(initialPage = 0) { wallpapers.size }

    // Clamp page when list shrinks (e.g. downvote hides item near end)
    LaunchedEffect(wallpapers.size) {
        if (pagerState.currentPage >= wallpapers.size && wallpapers.isNotEmpty()) {
            pagerState.scrollToPage(wallpapers.size - 1)
        }
    }

    val currentWp = wallpapers.getOrNull(pagerState.currentPage) ?: wallpapers.firstOrNull() ?: return

    LaunchedEffect(pagerState.settledPage) {
        wallpapers.getOrNull(pagerState.settledPage)?.let {
            viewModel.selectWallpaperOnly(it)
        }
        if (pagerState.settledPage >= wallpapers.size - 3) viewModel.loadMore()
    }

    // Use pager's current wallpaper for UI (not the reactive wp which causes reorder)
    val wp = currentWp

    val isFavorite by viewModel.isFavorite(wp.id).collectAsState(initial = false)
    val collections by viewModel.collections.collectAsState()
    val voteCount by viewModel.getVoteCount(wp.id).collectAsState(initial = 0)

    var showApplyOptions by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.pendingLiveWallpaperLaunch) {
        if (state.pendingLiveWallpaperLaunch) {
            val message = when (
                launchLiveWallpaperPicker(
                    context = context,
                    serviceComponent = ComponentName(context, ParallaxWallpaperService::class.java),
                    tag = "ParallaxWallpaper",
                )
            ) {
                LiveWallpaperLaunchMode.DIRECT -> "Aura Parallax opened. Set wallpaper to finish."
                LiveWallpaperLaunchMode.CHOOSER -> "Choose 'Aura Parallax' in the picker, then tap Set wallpaper."
                null -> "Parallax wallpaper is ready. Open Settings > Wallpaper > Live Wallpapers to finish setup."
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearPendingLaunch()
        }
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let { msg ->
            snackbarHostState.showSnackbar(msg)
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

            // Right side: vote buttons (always visible)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Upvote
                IconButton(
                    onClick = { viewModel.upvote(wp.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.ThumbUp, "Upvote", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                if (voteCount > 0) {
                    Text(
                        "$voteCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
                // Downvote (hide — item removed from pager list automatically)
                IconButton(
                    onClick = { viewModel.downvote(wp.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.ThumbDown, "Skip", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
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

                // Tag chips (from Wallhaven)
                if (wp.tags.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    ) {
                        wp.tags.take(8).forEach { tag ->
                            item {
                                SuggestionChip(
                                    onClick = { onSearchTag(tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        labelColor = Color.White,
                                    ),
                                    border = null,
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                        }
                    }
                }

                // Color palette dots — tap to search by color
                if (wp.colors.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        wp.colors.take(5).forEach { hex ->
                            val colorInt = runCatching { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrDefault(0)
                            Surface(
                                onClick = { onSearchColor(hex.removePrefix("#")) },
                                color = Color(colorInt),
                                shape = CircleShape,
                                modifier = Modifier.size(22.dp),
                                content = {},
                            )
                        }
                    }
                }

                // Action row: favorite, download, find similar, share, more
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionCircle(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        onClick = { viewModel.toggleFavorite(wp) },
                    )
                    ActionCircle(
                        icon = Icons.Default.Download,
                        contentDescription = "Download wallpaper",
                        onClick = { viewModel.downloadWallpaper(wp) },
                    )
                    ActionCircle(
                        icon = Icons.Default.ImageSearch,
                        contentDescription = "Find similar wallpapers",
                        onClick = { onFindSimilar(wp.id) },
                    )
                    ActionCircle(
                        icon = Icons.Default.Share,
                        contentDescription = "Share wallpaper",
                        onClick = {
                        val shareUrl = wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    })
                    ActionCircle(
                        icon = Icons.Default.MoreHoriz,
                        contentDescription = "More wallpaper actions",
                        onClick = { showMoreMenu = true },
                    )
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
                    onParallax = {
                        showApplyOptions = false
                        viewModel.applyParallax(wp)
                    },
                )
            }

            // More menu sheet
            if (showMoreMenu) {
                MoreActionsSheet(
                    onDismiss = { showMoreMenu = false },
                    onEdit = { showMoreMenu = false; onEdit(wp.id) },
                    onCrop = { showMoreMenu = false; onCrop(wp.id) },
                    onCollection = { showMoreMenu = false; showCollectionPicker = true },
                    onFindSimilar = {
                        showMoreMenu = false
                        onFindSimilar(wp.id)
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
    contentDescription: String,
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
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyOptionsSheet(
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
    onSplitCrop: () -> Unit,
    onParallax: () -> Unit = {},
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
            SheetOption(Icons.Default.Layers, "Parallax depth (3D tilt effect)") { onParallax() }
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
                SheetOption(Icons.Default.ColorLens, "Find similar wallpapers") { onFindSimilar() }
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

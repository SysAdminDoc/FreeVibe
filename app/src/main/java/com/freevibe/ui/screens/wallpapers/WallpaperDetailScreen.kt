package com.freevibe.ui.screens.wallpapers

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.ui.components.SourceBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Build list: selected wallpaper first, then remaining from shared list
    val wallpapers = remember(sharedList, wp.id) {
        val others = sharedList.filter { it.id != wp.id }
        listOf(wp) + others
    }

    val pagerState = rememberPagerState(initialPage = 0) { wallpapers.size.coerceAtLeast(1) }

    // Update selected wallpaper when page changes + load more when near end
    LaunchedEffect(pagerState.settledPage) {
        wallpapers.getOrNull(pagerState.settledPage)?.let {
            viewModel.selectWallpaperOnly(it)
        }
        if (pagerState.settledPage >= wallpapers.size - 3) viewModel.loadMore()
    }

    val isFavorite by viewModel.isFavorite(wp.id).collectAsState(initial = false)
    val collections by viewModel.collections.collectAsState()
    val colorPalette by viewModel.colorPalette.collectAsState()

    // Extract colors when wallpaper changes
    LaunchedEffect(wp.id) {
        viewModel.extractColors(wp.thumbnailUrl.ifEmpty { wp.fullUrl })
    }
    var showApplyOptions by remember { mutableStateOf(false) }
    var showPhonePreview by remember { mutableStateOf(false) }
    var showDualOptions by remember { mutableStateOf(false) }
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
            // Swipeable wallpaper preview (vertical pager)
            if (wallpapers.size > 1) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    val pageUrl = wallpapers.getOrNull(page)?.fullUrl ?: wp.fullUrl
                    SubcomposeAsyncImage(
                        model = pageUrl,
                        contentDescription = "Wallpaper preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f + (pageOffset.absoluteValue * 0.15f).coerceAtMost(0.15f)
                                scaleX = scale
                                scaleY = scale
                                translationY = pageOffset * size.height * 0.06f
                                alpha = 1f - (pageOffset.absoluteValue * 0.3f).coerceAtMost(0.3f)
                            },
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
            } else if (showPhonePreview) {
                PhoneFramePreview(imageUrl = wp.fullUrl)
            } else if (wallpapers.size <= 1) {
                // Single wallpaper (e.g. from favorites/history) — no pager
                SubcomposeAsyncImage(
                    model = wp.fullUrl,
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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

            // Top gradient + back button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    ),
            )
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                // Top-right info + preview toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // #1: Phone preview toggle
                    IconButton(
                        onClick = { showPhonePreview = !showPhonePreview },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                    ) {
                        Icon(
                            if (showPhonePreview) Icons.Default.Fullscreen else Icons.Default.Smartphone,
                            "Preview on device",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SourceBadge(wp.source.name)
                    if (wp.width > 0) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("${wp.width}x${wp.height}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                if (wp.fileSize > 0) {
                                    Text(
                                        formatFileSize(wp.fileSize),
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                if (wp.views > 0) {
                                    Text(
                                        "${formatCount(wp.views)} views",
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                if (wp.favorites > 0) {
                                    Text(
                                        "${formatCount(wp.favorites)} faves",
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom gradient + action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Material You Color Palette Preview + Find Similar
                colorPalette?.let { palette ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val paletteColors = listOf(palette.dominantColor, palette.vibrantColor, palette.vibrantDark,
                            palette.mutedColor, palette.mutedLight).filter { it != 0 }.take(5)
                        Text("Theme", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.width(4.dp))
                        paletteColors.forEach { color ->
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // "Find Similar" by dominant color — sets pending query and navigates back
                        if (palette.dominantColor != 0) {
                            val hex = String.format("%06x", palette.dominantColor and 0xFFFFFF)
                            Surface(
                                onClick = {
                                    if (onFindSimilar != null) onFindSimilar(hex)
                                    else { viewModel.setPendingColorSearch(hex); onBack() }
                                },
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Find similar", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }

                // Tags
                if (wp.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        wp.tags.take(4).forEach { tag ->
                            SuggestionChip(
                                onClick = { viewModel.search(tag) },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    labelColor = Color.White,
                                ),
                                border = null,
                            )
                        }
                    }
                }

                // Attribution
                if (wp.uploaderName.isNotEmpty()) {
                    Text(
                        "by ${wp.uploaderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                // Apply button (prominent)
                Button(
                    onClick = { showApplyOptions = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.isApplying,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Wallpaper, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Wallpaper")
                    }
                }

                // Secondary action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionCircle(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        onClick = { viewModel.toggleFavorite(wp) },
                    )
                    ActionCircle(icon = Icons.Default.Download, label = "Download", onClick = { viewModel.downloadWallpaper(wp) })
                    ActionCircle(icon = Icons.Default.Edit, label = "Edit", onClick = onEdit)
                    ActionCircle(icon = Icons.Default.Crop, label = "Crop", onClick = onCrop)
                    ActionCircle(icon = Icons.Default.Share, label = "Share", onClick = {
                        val shareUrl = wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                            putExtra(Intent.EXTRA_SUBJECT, "Check out this wallpaper")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
                    })
                    ActionCircle(
                        icon = Icons.Default.CreateNewFolder,
                        label = "Save to collection",
                        onClick = { showCollectionPicker = true },
                    )
                }
            }

            // Apply options bottom sheet
            if (showApplyOptions) {
                ApplyOptionsSheet(
                    onDismiss = { showApplyOptions = false },
                    onApply = { target ->
                        showApplyOptions = false
                        viewModel.applyWallpaper(wp, target)
                    },
                    onSplitCrop = {
                        showApplyOptions = false
                        showDualOptions = true
                    },
                )
            }

            // Collection picker
            if (showCollectionPicker) {
                CollectionPickerSheet(
                    collections = collections,
                    onDismiss = { showCollectionPicker = false },
                    onSelectCollection = { collectionId ->
                        showCollectionPicker = false
                        viewModel.addToCollection(collectionId, wp)
                    },
                    onCreateNew = { name ->
                        showCollectionPicker = false
                        viewModel.createCollection(name, wp)
                    },
                )
            }

            // Dual wallpaper (split crop) bottom sheet
            if (showDualOptions) {
                DualWallpaperSheet(
                    onDismiss = { showDualOptions = false },
                    isApplying = state.isApplying,
                    onApplySplitCrop = {
                        showDualOptions = false
                        viewModel.applySplitCrop(wp)
                    },
                )
            }
        }
    }
}

// #1: Phone frame preview composable — #4: uses real device time (updates every minute)
@Composable
private fun PhoneFramePreview(
    imageUrl: String,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(60_000)
        }
    }
    val timeStr = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val dateStr = remember(now) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        // Phone frame mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(500.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            // Wallpaper image (fills the "screen")
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "Preview on device",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // Simulated status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(timeStr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(12.dp), tint = Color.White)
                    Icon(Icons.Default.Wifi, null, Modifier.size(12.dp), tint = Color.White)
                    Icon(Icons.Default.BatteryFull, null, Modifier.size(12.dp), tint = Color.White)
                }
            }

            // Simulated clock widget
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    timeStr,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                )
                Text(
                    dateStr,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }

            // Simulated bottom nav dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                repeat(4) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String = "",
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
        Icon(icon, contentDescription = label.ifEmpty { null }, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DualWallpaperSheet(
    onDismiss: () -> Unit,
    isApplying: Boolean,
    onApplySplitCrop: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Dual Wallpaper",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "Apply this wallpaper with different crops to home and lock screens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ApplyOption(Icons.Default.Splitscreen, "Split Crop") { onApplySplitCrop() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyOptionsSheet(
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
    onSplitCrop: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Set wallpaper",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ApplyOption(Icons.Default.Home, "Home screen") { onApply(WallpaperTarget.HOME) }
            ApplyOption(Icons.Default.Lock, "Lock screen") { onApply(WallpaperTarget.LOCK) }
            ApplyOption(Icons.Default.Smartphone, "Both") { onApply(WallpaperTarget.BOTH) }
            if (onSplitCrop != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ApplyOption(Icons.Default.Splitscreen, "Split crop (different home & lock)") { onSplitCrop() }
            }
        }
    }
}

@Composable
private fun ApplyOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Save to Collection",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            collections.forEach { collection ->
                ApplyOption(Icons.Default.Folder, collection.name) {
                    onSelectCollection(collection.collectionId)
                }
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
                    FilledTonalButton(
                        onClick = {
                            if (newName.isNotBlank()) onCreateNew(newName.trim())
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Text("Create")
                    }
                }
            } else {
                ApplyOption(Icons.Default.Add, "New collection") { showCreateField = true }
            }
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> "$count"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

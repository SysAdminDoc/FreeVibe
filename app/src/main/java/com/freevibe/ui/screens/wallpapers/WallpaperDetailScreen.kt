package com.freevibe.ui.screens.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.stableKey
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.components.SourceBadge
import com.freevibe.ui.launchLiveWallpaperPicker
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpaperDetailScreen(
    wallpaperId: String,
    fallbackWallpaper: com.freevibe.data.model.Wallpaper? = null,
    onBack: () -> Unit,
    onEdit: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onCrop: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onPreview: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
    onSearchColor: (String) -> Unit = {},
    onFindSimilar: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedList by viewModel.sharedWallpaperList.collectAsStateWithLifecycle()
    val sharedListAnchorKey by viewModel.sharedWallpaperListAnchorKey.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val targetSource = fallbackWallpaper?.source
    val targetFullUrl = fallbackWallpaper?.fullUrl
    val detailIdentityKey = remember(wallpaperId, targetSource, targetFullUrl) {
        listOf(
            wallpaperId,
            targetSource?.name.orEmpty(),
            targetFullUrl.orEmpty(),
        ).joinToString("|")
    }
    var restoreResolved by remember(detailIdentityKey) { mutableStateOf(false) }
    var resolvedWallpaper by remember(detailIdentityKey) {
        mutableStateOf(fallbackWallpaper)
    }

    LaunchedEffect(detailIdentityKey) {
        resolvedWallpaper = viewModel.resolveWallpaper(
            id = wallpaperId,
            source = targetSource,
            fullUrl = targetFullUrl,
        ) ?: fallbackWallpaper
        restoreResolved = true
    }

    val initialWp = resolvedWallpaper
    if (initialWp == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!restoreResolved) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    HighlightPill(
                        label = "Loading wallpaper",
                        icon = Icons.Default.AutoAwesome,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Preparing the detail view", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Aura is restoring the image and its metadata so you can preview, save, or apply it cleanly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            } else {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            null,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Wallpaper unavailable", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This item is no longer available from its source or couldn't be restored from local state.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onBack) { Text("Back to feed") }
                }
            }
        }
        return
    }

    val pagerItems = remember(initialWp, sharedList, sharedListAnchorKey, hiddenIds) {
        computeWallpaperPagerItems(
            currentWallpaper = initialWp,
            sharedWallpapers = sharedList,
            hiddenIds = hiddenIds,
            sharedListAnchorKey = sharedListAnchorKey,
        )
    }
    val wallpapers = pagerItems.wallpapers

    if (wallpapers.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Track which wallpaper the pager is currently showing
    val initialPage = pagerItems.initialPage
    val pagerState = rememberPagerState(initialPage = initialPage) { wallpapers.size }

    LaunchedEffect(initialPage, wallpapers.size) {
        if (wallpapers.isNotEmpty()) {
            pagerState.scrollToPage(initialPage.coerceIn(0, wallpapers.lastIndex))
        }
    }

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
    val hints = remember(wp) { wp.qualityHints() }

    val isFavorite by viewModel.isFavorite(wp).collectAsStateWithLifecycle(initialValue = false)
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val voteCount by viewModel.getVoteCount(wp.stableKey()).collectAsStateWithLifecycle(initialValue = 0)

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

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.24f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                    ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailTopIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (wallpapers.size > 1) {
                            DetailOverlayPill(
                                label = "${pagerState.currentPage + 1} of ${wallpapers.size}",
                                icon = Icons.Default.Collections,
                            )
                        }
                        if (wp.width > 0) {
                            DetailOverlayPill(label = "${wp.width} x ${wp.height}")
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(18.dp),
                        highlightHeight = 180.dp,
                        shadowElevation = 10.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SourceBadge(wp.source.name)
                                if (voteCount > 0) {
                                    HighlightPill(
                                        label = "${formatCompactCount(voteCount)} likes",
                                        icon = Icons.Default.ThumbUp,
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = wallpaperDetailTitle(wp),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = wallpaperDetailSubtitle(wp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DetailInfoChip(hints.resolutionLabel)
                            DetailInfoChip(hints.orientationLabel)
                            if (hints.isAmoled) DetailInfoChip("AMOLED-friendly")
                            if (hints.isIconSafe) DetailInfoChip("Icon-safe")
                            if (wp.views > 0) DetailInfoChip("${formatCompactCount(wp.views)} views")
                            if (wp.favorites > 0) DetailInfoChip("${formatCompactCount(wp.favorites)} saves")
                            formatFileTypeLabel(wp.fileType)?.let { DetailInfoChip(it) }
                            formatFileSizeLabel(wp.fileSize)?.let { DetailInfoChip(it) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { showApplyOptions = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                enabled = !state.isApplying,
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                if (state.isApplying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.Wallpaper, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Set wallpaper")
                                }
                            }
                            FilledTonalButton(
                                onClick = { onPreview(wp) },
                                modifier = Modifier
                                    .weight(0.72f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Preview")
                            }
                        }

                        if (wp.colors.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailSectionTitle("Palette")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                wp.colors.take(5).forEach { hex ->
                                    val colorInt = runCatching {
                                        android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                                    }.getOrDefault(0)
                                    Surface(
                                        onClick = { onSearchColor(hex.removePrefix("#")) },
                                        color = Color(colorInt),
                                        shape = CircleShape,
                                        modifier = Modifier.size(24.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                        content = {},
                                    )
                                }
                            }
                        }

                        if (wp.tags.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailSectionTitle("Explore related looks")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                wp.tags.take(8).forEach { tag ->
                                    SuggestionChip(
                                        onClick = { onSearchTag(tag) },
                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        border = null,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DetailActionPill(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = if (isFavorite) "Saved" else "Save",
                                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                onClick = { viewModel.toggleFavorite(wp) },
                            )
                            DetailActionPill(
                                icon = Icons.Default.Download,
                                label = "Download",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = { viewModel.downloadWallpaper(wp) },
                            )
                            DetailActionPill(
                                icon = Icons.Default.ImageSearch,
                                label = "Similar",
                                tint = MaterialTheme.colorScheme.secondary,
                                onClick = { onFindSimilar(wp) },
                            )
                            DetailActionPill(
                                icon = Icons.Default.Share,
                                label = "Share",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    val shareUrl = wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                                    if (shareUrl.isBlank()) return@DetailActionPill
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
                                    } catch (_: Exception) {}
                                },
                            )
                            if (wp.sourcePageUrl.isNotBlank()) {
                                DetailActionPill(
                                    icon = Icons.Default.Link,
                                    label = "Source",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(wp.sourcePageUrl))
                                            )
                                        }
                                    },
                                )
                            }
                            DetailActionPill(
                                icon = Icons.Default.ThumbUp,
                                label = "Like",
                                tint = MaterialTheme.colorScheme.secondary,
                                onClick = { viewModel.upvote(wp.stableKey()) },
                            )
                            DetailActionPill(
                                icon = Icons.Default.ThumbDown,
                                label = "Hide",
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { viewModel.downvote(wp.stableKey()) },
                            )
                            DetailActionPill(
                                icon = Icons.Default.MoreHoriz,
                                label = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { showMoreMenu = true },
                            )
                        }
                    }
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
                    onEdit = { showMoreMenu = false; onEdit(wp) },
                    onCrop = { showMoreMenu = false; onCrop(wp) },
                    onPreview = { showMoreMenu = false; onPreview(wp) },
                    onCollection = { showMoreMenu = false; showCollectionPicker = true },
                    onFindSimilar = {
                        showMoreMenu = false
                        onFindSimilar(wp)
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
private fun DetailTopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DetailOverlayPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.34f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun DetailInfoChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailSectionTitle(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.12f)),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Set wallpaper", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose how Aura should apply this wallpaper across your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SheetOption(Icons.Default.Home, "Home screen", "Apply it to the launcher only") { onApply(WallpaperTarget.HOME) }
            SheetOption(Icons.Default.Lock, "Lock screen", "Keep your launcher as-is and update the lock view") { onApply(WallpaperTarget.LOCK) }
            SheetOption(Icons.Default.Smartphone, "Home and lock", "Use the same wallpaper on both surfaces") { onApply(WallpaperTarget.BOTH) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SheetOption(Icons.Default.Splitscreen, "Split crop", "Create separate home and lock crops from the same image") { onSplitCrop() }
            SheetOption(Icons.Default.Layers, "Parallax depth", "Turn this wallpaper into a subtle 3D tilt effect") { onParallax() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsSheet(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCrop: () -> Unit,
    onPreview: () -> Unit,
    onCollection: () -> Unit,
    onFindSimilar: (() -> Unit)?,
    uploaderName: String = "",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("More actions", style = MaterialTheme.typography.titleLarge)
            if (uploaderName.isNotEmpty()) {
                Text(
                    "Uploaded by $uploaderName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            SheetOption(Icons.Default.Visibility, "Preview on mock lock / home", "See how this wallpaper frames before you apply it") { onPreview() }
            SheetOption(Icons.Default.Edit, "Edit", "Open Aura's wallpaper editor for tone and effect adjustments") { onEdit() }
            SheetOption(Icons.Default.Crop, "Crop & position", "Fine-tune framing for your device before applying") { onCrop() }
            SheetOption(Icons.Default.CreateNewFolder, "Save to collection", "Keep this wallpaper in one of your curated sets") { onCollection() }
            if (onFindSimilar != null) {
                SheetOption(Icons.Default.ColorLens, "Find similar wallpapers", "Search for wallpapers with a related mood or composition") { onFindSimilar() }
            }
        }
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Save to collection", style = MaterialTheme.typography.titleLarge)
            Text(
                "Keep standout wallpapers grouped so rotation and revisit flows stay tidy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            collections.forEach { collection ->
                SheetOption(Icons.Default.Folder, collection.name, "Add this wallpaper to the collection") {
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
                    FilledTonalButton(onClick = { if (newName.isNotBlank()) onCreateNew(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Create") }
                }
            } else {
                SheetOption(Icons.Default.Add, "New collection", "Create a new place to save wallpapers like this") {
                    showCreateField = true
                }
            }
        }
    }
}

internal fun wallpaperDetailTitle(wallpaper: Wallpaper): String =
    when {
        wallpaper.category.isNotBlank() -> wallpaper.category.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        wallpaper.tags.isNotEmpty() -> wallpaper.tags.first().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        else -> "${sourceDisplayName(wallpaper.source)} wallpaper"
    }

internal fun wallpaperDetailSubtitle(wallpaper: Wallpaper): String {
    val sourceLabel = sourceDisplayName(wallpaper.source)
    return when {
        wallpaper.uploaderName.isNotBlank() ->
            "By ${wallpaper.uploaderName} on $sourceLabel"
        wallpaper.sourcePageUrl.isNotBlank() ->
            "Sourced from $sourceLabel with a direct source page available"
        else ->
            "Sourced from $sourceLabel"
    }
}

internal fun sourceDisplayName(source: ContentSource): String = when (source) {
    ContentSource.WALLHAVEN -> "Wallhaven"
    ContentSource.PICSUM -> "Picsum"
    ContentSource.BING -> "Bing"
    ContentSource.WIKIMEDIA -> "Wikimedia"
    ContentSource.INTERNET_ARCHIVE -> "Internet Archive"
    ContentSource.REDDIT -> "Reddit"
    ContentSource.NASA -> "NASA"
    ContentSource.FREESOUND -> "Freesound"
    ContentSource.JAMENDO -> "Jamendo"
    ContentSource.AUDIUS -> "Audius"
    ContentSource.CCMIXTER -> "ccMixter"
    ContentSource.LOCAL -> "Local"
    ContentSource.YOUTUBE -> "YouTube"
    ContentSource.PEXELS -> "Pexels"
    ContentSource.PIXABAY -> "Pixabay"
    ContentSource.KLIPY -> "Klipy"
    ContentSource.SOUNDCLOUD -> "SoundCloud"
    ContentSource.COMMUNITY -> "Community"
    ContentSource.BUNDLED -> "Aura Picks"
}

internal fun formatCompactCount(value: Int): String {
    val root = java.util.Locale.ROOT
    return when {
        value >= 1_000_000 -> String.format(root, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(root, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
}

internal fun formatFileTypeLabel(fileType: String): String? {
    val clean = fileType.trim()
    if (clean.isBlank()) return null
    return when {
        clean.contains("jpeg", ignoreCase = true) || clean.contains("jpg", ignoreCase = true) -> "JPG"
        clean.contains("png", ignoreCase = true) -> "PNG"
        clean.contains("webp", ignoreCase = true) -> "WEBP"
        // Locale.ROOT: MIME-type suffix is ASCII; Turkish locale would corrupt the "i" in "gif".
        else -> clean.substringAfterLast('/').uppercase(java.util.Locale.ROOT)
    }
}

internal fun formatFileSizeLabel(bytes: Long): String? {
    val root = java.util.Locale.ROOT
    return when {
        bytes <= 0L -> null
        bytes >= 1024L * 1024L -> String.format(root, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024L -> String.format(root, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

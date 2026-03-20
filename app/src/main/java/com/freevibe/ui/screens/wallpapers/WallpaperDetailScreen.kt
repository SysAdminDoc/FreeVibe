package com.freevibe.ui.screens.wallpapers

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val wallpaper by viewModel.selectedWallpaper.collectAsState()
    val wp = wallpaper ?: return
    val isFavorite by viewModel.isFavorite(wp.id).collectAsState(initial = false)
    var showApplyOptions by remember { mutableStateOf(false) }
    var showPhonePreview by remember { mutableStateOf(false) }

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
            if (showPhonePreview) {
                // #1: Phone frame preview
                PhoneFramePreview(
                    imageUrl = wp.fullUrl,
                )
            } else {
                // Full-screen wallpaper preview
                AsyncImage(
                    model = wp.fullUrl,
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text("${wp.width}x${wp.height}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                if (wp.fileSize > 0) {
                                    Text(
                                        " ${formatFileSize(wp.fileSize)}",
                                        color = Color.White.copy(alpha = 0.7f),
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

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionCircle(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        onClick = { viewModel.toggleFavorite(wp) },
                    )
                    // #1/#8: Share wallpaper (source page URL or image URL)
                    ActionCircle(icon = Icons.Default.Share, onClick = {
                        val shareUrl = wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                            putExtra(Intent.EXTRA_SUBJECT, "Check out this wallpaper")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
                    })
                    ActionCircle(icon = Icons.Default.Edit, onClick = onEdit)
                    ActionCircle(icon = Icons.Default.Crop, onClick = onCrop)

                    Button(
                        onClick = { showApplyOptions = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
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
                            Icon(Icons.Default.Wallpaper, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply")
                        }
                    }

                    ActionCircle(icon = Icons.Default.Download, onClick = { viewModel.downloadWallpaper(wp) })
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
                )
            }
        }
    }
}

// #1: Phone frame preview composable — #4: uses real device time
@Composable
private fun PhoneFramePreview(
    imageUrl: String,
) {
    val now = remember { Date() }
    val timeStr = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val dateStr = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now) }

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
            AsyncImage(
                model = imageUrl,
                contentDescription = "Preview on device",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )

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
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyOptionsSheet(
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

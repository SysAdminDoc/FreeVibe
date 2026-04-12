package com.freevibe.ui.screens.wallpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full-screen mockup of the user's home / lock screen with this wallpaper behind it, so they
 * can decide whether to commit before actually calling WallpaperManager.setBitmap().
 *
 * Design:
 * - Wallpaper fills the background.
 * - A segmented "Lock / Home" toggle at the top switches between two mock layouts.
 * - Lock mock: large clock + date + lock icon.
 * - Home mock: status bar, 5×5 placeholder icon grid, a dock row.
 * - Clock / icon tint is pulled from ColorExtractor so it stays legible on any wallpaper.
 * - Bottom bar: Cancel, Apply to Lock, Apply to Home, Apply to Both.
 *
 * We deliberately do NOT render real app icons (PackageManager noise, attribution concerns).
 * Placeholder silhouettes give a faithful impression of layout without pretending to be
 * the user's actual home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPreviewScreen(
    wallpaper: Wallpaper,
    onBack: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val palette by viewModel.colorPalette.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(wallpaper.fullUrl) {
        viewModel.extractColors(wallpaper.fullUrl)
    }

    var mode by remember { mutableStateOf(PreviewMode.LOCK) }

    // Tint for mock overlay text — prefer a light tint on dark wallpapers and dark-on-light
    // when the wallpaper is bright. The ColorExtractor dominant color gives us that signal.
    val overlayTint = overlayTintFor(palette?.dominantColor)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Preview") },
                actions = {
                    // Lock / Home segmented toggle
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                    ) {
                        PreviewModeChip(label = "Lock", active = mode == PreviewMode.LOCK) { mode = PreviewMode.LOCK }
                        PreviewModeChip(label = "Home", active = mode == PreviewMode.HOME) { mode = PreviewMode.HOME }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            PreviewApplyBar(
                isApplying = state.isApplying,
                onApply = onApply,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Wallpaper fills the full screen, ignoring the scaffold padding, so we see it
            // behind the translucent top/bottom bars.
            AsyncImage(
                model = wallpaper.fullUrl,
                contentDescription = wallpaper.category.ifBlank { "Wallpaper preview" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Light vignette so the mock text stays readable.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.25f),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (mode) {
                    PreviewMode.LOCK -> LockMock(overlayTint = overlayTint)
                    PreviewMode.HOME -> HomeMock(overlayTint = overlayTint)
                }
            }
        }
    }
}

private enum class PreviewMode { LOCK, HOME }

@Composable
private fun PreviewModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LockMock(overlayTint: Color) {
    val now = remember { Date() }
    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
    val dateFormat = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        // Status bar faux row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = timeFormat.format(now).take(5),
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
            )
            Text(
                text = "●●●●  100%",
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(80.dp))
        // Giant clock
        Text(
            text = timeFormat.format(now),
            color = overlayTint,
            fontSize = 92.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateFormat.format(now),
            color = overlayTint.copy(alpha = 0.85f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeMock(overlayTint: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Status bar with a small time readout on the left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val now = remember { Date() }
            val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
            Text(
                text = timeFormat.format(now),
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
            )
            Text(
                text = "●●●●  100%",
                style = MaterialTheme.typography.labelSmall,
                color = overlayTint,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.weight(0.3f))
        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == 1) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(overlayTint.copy(alpha = if (i == 1) 0.8f else 0.4f)),
                )
            }
        }
        // Icon grid (4 rows × 5 columns). Each cell is a silhouette dot + a short label.
        repeat(4) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) { col ->
                    MockIcon(
                        tint = overlayTint.copy(alpha = 0.88f),
                        label = mockLabel(row, col),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Dock row (5 icons on a translucent background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) {
                    MockIcon(tint = overlayTint, label = null)
                }
            }
        }
    }
}

@Composable
private fun MockIcon(tint: Color, label: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.28f))
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.55f)),
            )
        }
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

private fun mockLabel(row: Int, col: Int): String {
    // Generic placeholders — intentionally abstract, no real app names.
    val labels = listOf(
        "App", "Mail", "Photos", "Clock", "Maps",
        "Music", "Chat", "Notes", "Files", "Calendar",
        "Weather", "Camera", "Store", "News", "Health",
        "Wallet", "Voice", "Calc", "Tools", "Browse",
    )
    val idx = (row * 5 + col) % labels.size
    return labels[idx]
}

@Composable
private fun PreviewApplyBar(
    isApplying: Boolean,
    onApply: (WallpaperTarget) -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApplyActionButton(
                label = "Lock",
                onClick = { onApply(WallpaperTarget.LOCK) },
                enabled = !isApplying,
                tonal = true,
                modifier = Modifier.weight(1f),
            )
            ApplyActionButton(
                label = "Home",
                onClick = { onApply(WallpaperTarget.HOME) },
                enabled = !isApplying,
                tonal = true,
                modifier = Modifier.weight(1f),
            )
            ApplyActionButton(
                label = "Both",
                onClick = { onApply(WallpaperTarget.BOTH) },
                enabled = !isApplying,
                tonal = false,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun ApplyActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tonal: Boolean,
    modifier: Modifier = Modifier,
) {
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(44.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(44.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Pick a legible overlay tint (white on dark wallpapers, near-black on light ones).
 * Falls back to white if we haven't extracted a dominant color yet.
 */
private fun overlayTintFor(dominant: Int?): Color {
    if (dominant == null) return Color.White
    val r = (dominant shr 16) and 0xFF
    val g = (dominant shr 8) and 0xFF
    val b = dominant and 0xFF
    // Perceived luminance (Rec. 601).
    val luma = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luma < 140) Color.White else Color(0xFF1A1A1A)
}

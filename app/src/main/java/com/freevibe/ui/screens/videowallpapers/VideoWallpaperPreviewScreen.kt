package com.freevibe.ui.screens.videowallpapers

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Video-wallpaper variant of the static preview screen shipped in v6.0.0. Renders the video
 * behind a mock lock or home overlay so the user can judge composition, motion, and whether
 * the looping video actually works as a wallpaper before committing it.
 *
 * The video plays center-crop (resize mode ZOOM) the same way VideoWallpaperService renders
 * it at runtime, so what you see here matches what you'll get.
 *
 * Static v1 design (from WallpaperPreviewScreen) carries over: Lock/Home toggle, mock status
 * bar + clock, placeholder icon grid, apply bar at bottom. The only difference is the
 * background is an ExoPlayer PlayerView instead of an AsyncImage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoWallpaperPreviewScreen(
    streamUrl: String,
    title: String,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onCrop: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(VideoPreviewMode.LOCK) }

    // Dedicated ExoPlayer for this screen. Cleaned up on composition exit.
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // Wallpapers are muted
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

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
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                    ) {
                        ModeChip("Lock", mode == VideoPreviewMode.LOCK) { mode = VideoPreviewMode.LOCK }
                        ModeChip("Home", mode == VideoPreviewMode.HOME) { mode = VideoPreviewMode.HOME }
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
            Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onCrop,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text("Crop…", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier.weight(1.2f).height(44.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Background: live video via PlayerView with ZOOM resize (= center-crop).
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Vignette for mock text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.3f),
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.3f),
                        ),
                    ),
            )

            // Mock overlay on top of the video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (mode) {
                    VideoPreviewMode.LOCK -> LockOverlay()
                    VideoPreviewMode.HOME -> HomeOverlay()
                }
            }

            // Title pill at the bottom of the video area, for context
            if (title.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 86.dp + padding.calculateBottomPadding()),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private enum class VideoPreviewMode { LOCK, HOME }

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
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
private fun LockOverlay() {
    val now = remember { Date() }
    val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = timeFormat.format(now), color = Color.White, fontSize = 12.sp)
            Text(text = "●●●●  100%", color = Color.White, fontSize = 10.sp)
        }
        Spacer(Modifier.height(80.dp))
        Text(
            text = timeFormat.format(now),
            color = Color.White,
            fontSize = 92.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = dateFormat.format(now), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
    }
}

@Composable
private fun HomeOverlay() {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val now = remember { Date() }
            val timeFormat = remember { SimpleDateFormat("H:mm", Locale.getDefault()) }
            Text(text = timeFormat.format(now), color = Color.White, fontSize = 12.sp)
            Text(text = "●●●●  100%", color = Color.White, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(0.3f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == 1) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == 1) 0.8f else 0.4f)),
                )
            }
        }
        repeat(4) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(5) { MockHomeIcon() }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(5) { MockHomeIcon() }
            }
        }
    }
}

@Composable
private fun MockHomeIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}

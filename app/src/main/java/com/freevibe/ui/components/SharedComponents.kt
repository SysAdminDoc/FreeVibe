package com.freevibe.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freevibe.service.DownloadProgress

// ── Download Progress Overlay ─────────────────────────────────────

@Composable
fun DownloadProgressBar(
    downloads: Map<String, DownloadProgress>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (downloads.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        downloads.forEach { (id, download) ->
            DownloadItem(download = download, onDismiss = { onDismiss(id) })
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = when {
                        download.isComplete -> Icons.Default.CheckCircle
                        download.error != null -> Icons.Default.Error
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = when {
                        download.isComplete -> MaterialTheme.colorScheme.secondary
                        download.error != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (download.error != null) {
                        Text(
                            download.error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (!download.isComplete) {
                        val pct = (download.progress * 100).toInt()
                        val sizeText = if (download.totalBytes > 0) {
                            "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)} ($pct%)"
                        } else {
                            "${formatBytes(download.downloadedBytes)} ($pct%)"
                        }
                        Text(
                            sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (download.isComplete || download.error != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (!download.isComplete && download.error == null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

// ── Shimmer Loading Effect ────────────────────────────────────────

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim),
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

@Composable
fun ShimmerWallpaperGrid(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (it % 2 == 0) 220.dp else 180.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (it % 2 == 0) 180.dp else 220.dp),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
fun ShimmerSoundList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(8) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBox(modifier = Modifier.width(180.dp).height(14.dp))
                    ShimmerBox(modifier = Modifier.width(100.dp).height(10.dp))
                }
            }
        }
    }
}

// ── Glassmorphic Card ─────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    highlightHeight: Dp = 120.dp,
    shadowElevation: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(highlightHeight)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 520f,
                        ),
                    ),
            )
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search,
    leadingTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClear: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(40.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isFocused) 0.56f else 0.46f),
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (isFocused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingTint,
                modifier = Modifier.size(16.dp),
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (value.isNotEmpty() && onClear != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ── Source Badge ───────────────────────────────────────────────────

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val (color, label) = when (source.uppercase()) {
        "WALLHAVEN" -> MaterialTheme.colorScheme.primary to "Wallhaven"
        "PICSUM" -> MaterialTheme.colorScheme.outline to "Legacy"
        "BING" -> Color(0xFF00809D) to "Bing"
        "WIKIMEDIA" -> Color(0xFF006699) to "Wikimedia"
        "INTERNET_ARCHIVE" -> Color(0xFFFF8C00) to "Archive.org"
        "REDDIT" -> Color(0xFFFF4500) to "Reddit"
        "NASA" -> Color(0xFF0B3D91) to "NASA"
        "FREESOUND" -> Color(0xFF3DB2CE) to "Freesound" // Legacy favorites only
        "JAMENDO" -> Color(0xFF7E57C2) to "Jamendo"
        "AUDIUS" -> Color(0xFF00C2A8) to "Audius"
        "CCMIXTER" -> Color(0xFF8E24AA) to "ccMixter"
        "YOUTUBE" -> Color(0xFFFF0000) to "YouTube"
        "PEXELS" -> Color(0xFF05A081) to "Pexels"
        "PIXABAY" -> Color(0xFF00AB6C) to "Pixabay"
        "KLIPY" -> Color(0xFFE040FB) to "Klipy"
        "SOUNDCLOUD" -> Color(0xFFFF5500) to "SoundCloud"
        "COMMUNITY" -> Color(0xFF4CAF50) to "Community"
        "BUNDLED" -> Color(0xFFFFB300) to "Aura Picks"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to source
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

package com.freevibe.ui.screens.sounds

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundDetailScreen(
    soundId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onContactPicker: (Sound) -> Unit = {},
    onOpenSound: (String) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var restoreResolved by remember(soundId) { mutableStateOf(false) }
    var resolvedSound by remember(soundId) { mutableStateOf<Sound?>(null) }

    LaunchedEffect(soundId) {
        resolvedSound = viewModel.resolveSound(soundId)
        restoreResolved = true
    }

    val s = resolvedSound
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!restoreResolved) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicOff,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Sound unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }
    val isFavorite by viewModel.isFavorite(s.id).collectAsState(initial = false)
    val context = LocalContext.current
    val autoPreview by viewModel.autoPreview.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val isPlaying = state.playingId == s.id

    val similarSounds = remember(s.id) { mutableStateOf<List<Sound>>(emptyList()) }
    val similarLoading = remember(s.id) { mutableStateOf(false) }

    DisposableEffect(s.id) {
        val soundId = s.id
        onDispose { viewModel.stopIfPlaying(soundId) }
    }
    LaunchedEffect(s.id) {
        if (autoPreview && state.playingId != s.id) viewModel.togglePlayback(s)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.applySuccess) { state.applySuccess?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearError() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(s) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Waveform with integrated play button
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (s.duration > 0) {
                    DetailWaveform(
                        duration = s.duration, isPlaying = isPlaying,
                        progress = if (isPlaying) playbackProgress else 0f,
                        onSeek = { frac -> if (!isPlaying) viewModel.togglePlayback(s); viewModel.seekTo(frac) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                }
                // Play overlay
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(48.dp),
                    onClick = { viewModel.togglePlayback(s) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause preview" else "Play preview",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Sound name
            Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            // Metadata row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val (label, color) = when (s.source) {
                    ContentSource.YOUTUBE -> "YouTube" to Color(0xFFFF0000)
                    ContentSource.FREESOUND -> "Freesound" to Color(0xFF4CAF50)
                    else -> s.source.name to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color)
                }
                Text(formatDuration(s.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.uploaderName.isNotEmpty() && s.uploaderName != "Unknown") {
                    Text("by ${s.uploaderName}", modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.license.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(s.license, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // Tags
            if (s.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.tags.take(5).forEach { tag ->
                        Surface(
                            onClick = { onSearchTag(tag) },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("#$tag", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Permission warning
            if (!viewModel.canWriteSettings()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text("Permission needed", style = MaterialTheme.typography.labelLarge)
                            Text("Allow modifying system settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { context.startActivity(viewModel.requestWriteSettings()) }) { Text("Grant") }
                    }
                }
            }

            // 3 Apply buttons side-by-side
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplyButton("Ringtone", Icons.Default.Call, !state.isApplying && viewModel.canWriteSettings(), state.isApplying, Modifier.weight(1f)) { viewModel.applySound(s, ContentType.RINGTONE) }
                ApplyButton("Notification", Icons.Default.Notifications, !state.isApplying && viewModel.canWriteSettings(), state.isApplying, Modifier.weight(1f)) { viewModel.applySound(s, ContentType.NOTIFICATION) }
                ApplyButton("Alarm", Icons.Default.Alarm, !state.isApplying && viewModel.canWriteSettings(), state.isApplying, Modifier.weight(1f)) { viewModel.applySound(s, ContentType.ALARM) }
            }

            // Secondary actions as icon row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { onEdit(s.id) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ContentCut, "Trim sound", Modifier.size(22.dp)); Text("Trim", style = MaterialTheme.typography.labelSmall) } }
                IconButton(onClick = { onContactPicker(s) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Contacts, "Assign to contact", Modifier.size(22.dp)); Text("Contact", style = MaterialTheme.typography.labelSmall) } }
                IconButton(onClick = { viewModel.downloadSound(s) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Download, "Save sound", Modifier.size(22.dp)); Text("Save", style = MaterialTheme.typography.labelSmall) } }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s.sourcePageUrl.ifEmpty { s.downloadUrl }); putExtra(Intent.EXTRA_SUBJECT, s.name) }
                    context.startActivity(Intent.createChooser(intent, "Share sound"))
                }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Share, "Share sound", Modifier.size(22.dp)); Text("Share", style = MaterialTheme.typography.labelSmall) } }
            }

            // More Like This
            Spacer(Modifier.height(4.dp))
            SimilarSoundsSection(s.id, similarSounds, similarLoading, viewModel) { onOpenSound(it.id) }

            Spacer(Modifier.height(80.dp)) // bottom padding for nav bar
        }
    }
}

@Composable
private fun ApplyButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, isLoading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(48.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(text, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun SimilarSoundsSection(
    soundId: String, similarSounds: MutableState<List<Sound>>, isLoading: MutableState<Boolean>,
    viewModel: SoundsViewModel, onSoundClick: (Sound) -> Unit,
) {
    var loaded by remember(soundId) { mutableStateOf(false) }
    LaunchedEffect(soundId) {
        if (!loaded && !isLoading.value) {
            isLoading.value = true; similarSounds.value = emptyList()
            try { similarSounds.value = viewModel.loadSimilar(soundId) } catch (_: Exception) {}
            isLoading.value = false; loaded = true
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("More Like This", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (isLoading.value) {
            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        } else if (similarSounds.value.isNotEmpty()) {
            val currentPlayingId = viewModel.state.collectAsState().value.playingId
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(similarSounds.value, key = { it.id }) { similar ->
                    Surface(
                        onClick = { onSoundClick(similar) }, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.width(160.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.togglePlayback(similar) },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(if (currentPlayingId == similar.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Icon(
                                    if (currentPlayingId == similar.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (currentPlayingId == similar.id) "Pause preview" else "Play preview",
                                    tint = if (currentPlayingId == similar.id) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(similar.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDuration(similar.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (loaded) {
            Text("No similar sounds found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailWaveform(duration: Double, isPlaying: Boolean, progress: Float = 0f, onSeek: ((Float) -> Unit)? = null, modifier: Modifier = Modifier) {
    val barColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val activeColor = MaterialTheme.colorScheme.primary
    val barCount = 60
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i -> (0.15f + 0.85f * ((kotlin.math.sin((seed + i * 37) % 360 * 0.0174533) + 1f) / 2f).toFloat()) }
    }
    Canvas(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer).then(
            if (onSeek != null) Modifier.pointerInput(Unit) { detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) } } else Modifier,
        ),
    ) {
        val barWidth = size.width / barCount
        heights.forEachIndexed { i, height ->
            val x = i * barWidth + barWidth / 2; val barH = size.height * height * 0.85f
            drawLine(
                color = if (isPlaying && (i.toFloat() / barCount) < progress) activeColor else barColor,
                start = Offset(x, size.height / 2 - barH / 2), end = Offset(x, size.height / 2 + barH / 2),
                strokeWidth = (barWidth - 1.5f).coerceAtLeast(1f), cap = StrokeCap.Round,
            )
        }
        if (isPlaying && progress > 0f) {
            drawLine(activeColor, Offset(size.width * progress, 0f), Offset(size.width * progress, size.height), strokeWidth = 2f)
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt(); val m = total / 60; val s = total % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

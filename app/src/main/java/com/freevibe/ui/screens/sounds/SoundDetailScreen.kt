package com.freevibe.ui.screens.sounds

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.ui.components.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onContactPicker: (String) -> Unit = {},
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sound by viewModel.selectedSound.collectAsState()
    val s = sound ?: return
    val isFavorite by viewModel.isFavorite(s.id).collectAsState(initial = false)
    val context = LocalContext.current

    // Similar sounds
    val similarSounds = remember { mutableStateOf<List<Sound>>(emptyList()) }
    val similarLoading = remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sound Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(s) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Playback circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayback(s) },
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        imageVector = if (state.playingId == s.id) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // Sound name
            Text(
                text = s.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Info chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SourceBadge(s.source.name)
                InfoChip(Icons.Default.Timer, formatDuration(s.duration))
                if (s.uploaderName.isNotEmpty()) {
                    InfoChip(Icons.Default.Person, s.uploaderName)
                }
                if (s.license.isNotEmpty()) {
                    InfoChip(Icons.Default.Info, s.license)
                }
            }

            // Tags
            if (s.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    s.tags.take(5).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                "#$tag",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (s.description.isNotEmpty()) {
                Text(
                    text = s.description.take(200),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Permission warning
            if (!viewModel.canWriteSettings()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permission needed", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Allow modifying system settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            context.startActivity(viewModel.requestWriteSettings())
                        }) {
                            Text("Grant")
                        }
                    }
                }
            }

            // Apply buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApplyButton(
                    text = "Set as Ringtone",
                    icon = Icons.Default.Call,
                    enabled = !state.isApplying && viewModel.canWriteSettings(),
                    isLoading = state.isApplying,
                    onClick = { viewModel.applySound(s, ContentType.RINGTONE) },
                )
                ApplyButton(
                    text = "Set as Notification",
                    icon = Icons.Default.Notifications,
                    enabled = !state.isApplying && viewModel.canWriteSettings(),
                    isLoading = state.isApplying,
                    onClick = { viewModel.applySound(s, ContentType.NOTIFICATION) },
                )
                ApplyButton(
                    text = "Set as Alarm",
                    icon = Icons.Default.Alarm,
                    enabled = !state.isApplying && viewModel.canWriteSettings(),
                    isLoading = state.isApplying,
                    onClick = { viewModel.applySound(s, ContentType.ALARM) },
                )

                // Extra actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trim")
                    }
                    OutlinedButton(
                        onClick = { onContactPicker(s.id) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Contacts, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Contact")
                    }
                }

                // Download + Share row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.downloadSound(s) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
                    }
                    OutlinedButton(
                        onClick = {
                            val shareUrl = s.sourcePageUrl.ifEmpty { s.downloadUrl }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                                putExtra(Intent.EXTRA_SUBJECT, s.name)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share sound"))
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }

            // "More Like This" section (Freesound only)
            if (s.id.startsWith("fs_")) {
                Spacer(Modifier.height(8.dp))
                SimilarSoundsSection(
                    soundId = s.id.removePrefix("fs_").toIntOrNull() ?: 0,
                    similarSounds = similarSounds,
                    isLoading = similarLoading,
                    viewModel = viewModel,
                    onSoundClick = { similar ->
                        viewModel.selectSound(similar)
                    },
                )
            }
        }
    }
}

@Composable
private fun SimilarSoundsSection(
    soundId: Int,
    similarSounds: MutableState<List<Sound>>,
    isLoading: MutableState<Boolean>,
    viewModel: SoundsViewModel,
    onSoundClick: (Sound) -> Unit,
) {
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "More Like This",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!loaded && !isLoading.value) {
                TextButton(onClick = {
                    isLoading.value = true
                    scope.launch {
                        try {
                            val result = viewModel.loadSimilar(soundId)
                            similarSounds.value = result
                        } catch (_: Exception) {}
                        isLoading.value = false
                        loaded = true
                    }
                }) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Load")
                }
            }
        }

        if (isLoading.value) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (similarSounds.value.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(similarSounds.value, key = { it.id }) { similar ->
                    SimilarSoundCard(
                        sound = similar,
                        isPlaying = viewModel.state.collectAsState().value.playingId == similar.id,
                        onPlay = { viewModel.togglePlayback(similar) },
                        onClick = { onSoundClick(similar) },
                    )
                }
            }
        } else if (loaded) {
            Text(
                "No similar sounds found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SimilarSoundCard(
    sound: Sound,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(180.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sound.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatDuration(sound.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ApplyButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

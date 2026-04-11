package com.freevibe.ui.screens.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.DownloadEntity
import com.freevibe.service.DownloadProgress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("All", "Wallpapers", "Sounds")
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val displayList = when (selectedTab) {
        1 -> allDownloads.filter { it.type == "WALLPAPER" }
        2 -> allDownloads.filter { it.type == "SOUND" }
        else -> allDownloads
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }

            // Active downloads
            if (activeDownloads.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    activeDownloads.forEach { (id, dl) ->
                        ActiveDownloadCard(dl) { viewModel.dismissActive(id) }
                    }
                }
            }

            if (displayList.isEmpty() && activeDownloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(displayList, key = { it.id }, contentType = { "download_card" }) { download ->
                        DownloadHistoryCard(
                            download = download,
                            onOpen = {
                                try {
                                    val path = download.localPath
                                    if (path.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("File path is missing") }
                                        return@DownloadHistoryCard
                                    }
                                    val uri = Uri.parse(path)
                                    if (uri.scheme == "file") {
                                        val file = java.io.File(uri.path ?: "")
                                        if (!file.exists()) {
                                            scope.launch { snackbarHostState.showSnackbar("File no longer exists") }
                                            return@DownloadHistoryCard
                                        }
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, if (download.type == "WALLPAPER") "image/*" else "audio/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Cannot open file") }
                                }
                            },
                            onDelete = { viewModel.deleteDownload(download.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(dl: DownloadProgress, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (dl.isComplete) Icons.Default.CheckCircle else Icons.Default.Download,
                    null, Modifier.size(18.dp),
                    tint = if (dl.isComplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(dl.fileName, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dl.isComplete || dl.error != null) {
                    IconButton(onClick = onDismiss, Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                }
            }
            if (!dl.isComplete && dl.error == null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { dl.progress },
                    Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun DownloadHistoryCard(
    download: DownloadEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (download.type == "WALLPAPER") Icons.Default.Image else Icons.Default.MusicNote,
                null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(download.name.ifEmpty { download.id }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateFormat.format(Date(download.downloadedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (download.type == "WALLPAPER") "Wallpaper" else "Sound",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete, Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

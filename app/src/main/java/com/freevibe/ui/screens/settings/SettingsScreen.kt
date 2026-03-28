package com.freevibe.ui.screens.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.AutoWallpaperWorker
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: PreferencesManager,
    private val historyManager: WallpaperHistoryManager,
    private val offlineFavorites: OfflineFavoritesManager,
) : ViewModel() {
    val autoWpEnabled = prefs.autoWallpaperEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWpInterval = prefs.autoWallpaperInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12L)
    val autoWpSource = prefs.autoWallpaperSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallhaven")
    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val previewVolume = prefs.soundPreviewVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val redditSubs = prefs.redditSubreddits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallpapers,Amoledbackgrounds,MobileWallpaper")
    val preferredRes = prefs.preferredResolution.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // #11: Wallpaper history
    val wallpaperHistory = historyManager.getRecent(20).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun setAutoWallpaper(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, autoWpInterval.value)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setAutoWpInterval(hours: Long) = viewModelScope.launch {
        prefs.setAutoWallpaperInterval(hours)
        if (autoWpEnabled.value) {
            AutoWallpaperWorker.schedule(context, hours)
        }
    }

    // #10: Set auto-wallpaper source
    fun setAutoWpSource(source: String) = viewModelScope.launch {
        prefs.setAutoWallpaperSource(source)
    }

    fun clearWallpaperHistory() = viewModelScope.launch {
        historyManager.clearAll()
    }

    fun setAutoPreview(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoPreview(enabled)
    }

    fun setGridColumns(columns: Int) = viewModelScope.launch {
        prefs.setGridColumns(columns)
    }

    fun setPreviewVolume(volume: Float) = viewModelScope.launch {
        prefs.setPreviewVolume(volume)
    }

    fun setRedditSubs(subs: String) = viewModelScope.launch {
        prefs.setRedditSubreddits(subs)
    }

    fun setPreferredRes(res: String) = viewModelScope.launch {
        prefs.setPreferredResolution(res)
    }

    fun setVideoWallpaperPath(uri: Uri) {
        val path = try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val cacheFile = java.io.File(context.filesDir, "live_wallpaper.mp4")
            inputStream?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile.absolutePath
        } catch (_: Exception) { null } ?: return

        context.getSharedPreferences("freevibe_live_wp", android.content.Context.MODE_PRIVATE)
            .edit().putString("video_path", path).apply()
    }

    fun getCacheSize(): String {
        val cacheDir = context.cacheDir
        val bytes = cacheDir.walkTopDown().filter { it.isFile && it.parentFile?.name != "trimmed" }.sumOf { it.length() }
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun clearCache() = viewModelScope.launch {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name != "trimmed") {
                file.deleteRecursively()
            }
        }
        offlineFavorites.clearAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDownloadsClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    onCategoriesClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onCollectionsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val autoWpEnabled by viewModel.autoWpEnabled.collectAsState()
    val autoWpInterval by viewModel.autoWpInterval.collectAsState()
    val autoWpSource by viewModel.autoWpSource.collectAsState()
    val autoPreview by viewModel.autoPreview.collectAsState()
    val wallpaperHistory by viewModel.wallpaperHistory.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val previewVolume by viewModel.previewVolume.collectAsState()
    val redditSubs by viewModel.redditSubs.collectAsState()
    val preferredRes by viewModel.preferredRes.collectAsState()

    // Video wallpaper picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setVideoWallpaperPath(it)
            // Launch live wallpaper picker
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, com.freevibe.service.VideoWallpaperService::class.java),
                )
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    // Dialog state
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showColumnsPicker by remember { mutableStateOf(false) }
    var showRedditEditor by remember { mutableStateOf(false) }
    var showResPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text("Settings") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        // Wallpapers
        SettingsSection("Wallpapers") {
            SettingsToggle(
                icon = Icons.Default.AutoAwesome,
                title = "Auto-change wallpaper",
                subtitle = "Periodically rotate wallpapers",
                checked = autoWpEnabled,
                onCheckedChange = { viewModel.setAutoWallpaper(it) },
            )
            if (autoWpEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Change interval",
                    subtitle = "Every $autoWpInterval hours",
                    onClick = { showIntervalPicker = true },
                )
                // #10: Source picker
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = "Wallpaper source",
                    subtitle = autoWpSource.replaceFirstChar { it.uppercase() },
                    onClick = { showSourcePicker = true },
                )
            }
            // #9: Grid columns
            SettingsItem(
                icon = Icons.Default.GridView,
                title = "Grid columns",
                subtitle = "$gridColumns columns",
                onClick = { showColumnsPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.VideoFile,
                title = "Video wallpaper",
                subtitle = "Set a video as live wallpaper",
                onClick = { videoPickerLauncher.launch("video/*") },
            )
            SettingsItem(
                icon = Icons.Default.PhotoSizeSelectLarge,
                title = "Preferred resolution",
                subtitle = if (preferredRes.isEmpty()) "Any resolution" else preferredRes,
                onClick = { showResPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Forum,
                title = "Reddit subreddits",
                subtitle = "${redditSubs.split(",").size} subreddits",
                onClick = { showRedditEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.Category,
                title = "Browse categories",
                subtitle = "Nature, Space, Anime, Dark, Neon + 12 more",
                onClick = onCategoriesClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Collections",
                subtitle = "Organize wallpapers into folders",
                onClick = onCollectionsClick,
            )
            // #2: Wallpaper history — opens browsable grid
            if (wallpaperHistory.isNotEmpty()) {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Wallpaper history",
                    subtitle = "${wallpaperHistory.size} recently applied",
                    onClick = onHistoryClick,
                )
            }
        }

        // Sound settings
        SettingsSection("Sounds") {
            SettingsToggle(
                icon = Icons.Default.PlayCircle,
                title = "Auto-preview sounds",
                subtitle = "Play sound when opening detail",
                checked = autoPreview,
                onCheckedChange = { viewModel.setAutoPreview(it) },
            )
            // Preview volume slider
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preview volume", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = previewVolume,
                            onValueChange = { viewModel.setPreviewVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                    Text("${(previewVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Storage
        SettingsSection("Storage") {
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Download history",
                subtitle = "View and manage downloaded content",
                onClick = onDownloadsClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Clear cache",
                subtitle = "Using ${viewModel.getCacheSize()}",
                onClick = { showClearCacheConfirm = true },
            )
        }

        // About
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "FreeVibe",
                subtitle = "v2.6.0 - Open source device personalization",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/SysAdminDoc/FreeVibe",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/FreeVibe")))
                },
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = "Open source licenses",
                subtitle = "Libraries and content source attributions",
                onClick = onLicensesClick,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // Interval picker
    if (showIntervalPicker) {
        IntervalPickerDialog(
            currentInterval = autoWpInterval,
            onDismiss = { showIntervalPicker = false },
            onSelect = { hours ->
                viewModel.setAutoWpInterval(hours)
                showIntervalPicker = false
            },
        )
    }

    // #10: Source picker dialog
    if (showSourcePicker) {
        SourcePickerDialog(
            currentSource = autoWpSource,
            onDismiss = { showSourcePicker = false },
            onSelect = { source ->
                viewModel.setAutoWpSource(source)
                showSourcePicker = false
            },
        )
    }

    // #9: Grid columns picker
    if (showColumnsPicker) {
        AlertDialog(
            onDismissRequest = { showColumnsPicker = false },
            title = { Text("Grid columns") },
            text = {
                Column {
                    listOf(1 to "1 column", 2 to "2 columns", 3 to "3 columns", 4 to "4 columns").forEach { (count, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = gridColumns == count,
                                onClick = {
                                    viewModel.setGridColumns(count)
                                    showColumnsPicker = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColumnsPicker = false }) { Text("Cancel") }
            },
        )
    }

    // Resolution picker
    if (showResPicker) {
        AlertDialog(
            onDismissRequest = { showResPicker = false },
            title = { Text("Preferred resolution") },
            text = {
                Column {
                    listOf("" to "Any resolution", "1920x1080" to "1920x1080 (FHD)", "2560x1440" to "2560x1440 (QHD)", "3840x2160" to "3840x2160 (4K)").forEach { (res, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preferredRes == res,
                                onClick = {
                                    viewModel.setPreferredRes(res)
                                    showResPicker = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResPicker = false }) { Text("Cancel") }
            },
        )
    }

    // Reddit subreddits editor
    if (showRedditEditor) {
        var subsText by remember { mutableStateOf(redditSubs) }
        AlertDialog(
            onDismissRequest = { showRedditEditor = false },
            title = { Text("Reddit subreddits") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Comma-separated subreddit names (without r/)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = subsText,
                        onValueChange = { subsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("wallpapers,Amoledbackgrounds,...") },
                        singleLine = false,
                        maxLines = 3,
                    )
                    // Quick add chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EarthPorn", "spaceporn", "CityPorn").forEach { sub ->
                            if (!subsText.contains(sub, ignoreCase = true)) {
                                SuggestionChip(
                                    onClick = { subsText = if (subsText.isBlank()) sub else "$subsText,$sub" },
                                    label = { Text(sub, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRedditSubs(subsText.trim())
                    showRedditEditor = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRedditEditor = false }) { Text("Cancel") }
            },
        )
    }

    // Confirm clear cache
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear cache?") },
            text = { Text("This will free ${viewModel.getCacheSize()} by removing cached images and offline favorites. Downloaded files are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
private fun IntervalPickerDialog(
    currentInterval: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val intervals = listOf(1L to "1 hour", 3L to "3 hours", 6L to "6 hours",
        12L to "12 hours", 24L to "24 hours", 48L to "2 days")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wallpaper change interval") },
        text = {
            Column {
                intervals.forEach { (hours, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentInterval == hours,
                            onClick = { onSelect(hours) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(onClick = { onCheckedChange(!checked) }, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SourcePickerDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sources = listOf(
        "discover" to "Discover (mixed)",
        "favorites" to "My Favorites",
        "reddit" to "Reddit",
        "wallhaven" to "Wallhaven",
        "bing" to "Bing Daily",
        "unsplash" to "Unsplash",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-wallpaper source") },
        text = {
            Column {
                sources.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentSource == key,
                            onClick = { onSelect(key) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

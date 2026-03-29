package com.freevibe.ui.screens.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
    // Enhanced scheduler
    val schedulerEnabled = prefs.schedulerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val schedulerInterval = prefs.schedulerIntervalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 360L)
    val schedulerSource = prefs.schedulerSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "discover")
    val schedulerHome = prefs.schedulerHomeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val schedulerLock = prefs.schedulerLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val schedulerShuffle = prefs.schedulerShuffle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val weatherEffects = prefs.weatherEffectsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val adaptiveTint = prefs.adaptiveTintEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val darkModeSwitch = prefs.darkModeAutoSwitch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
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

    fun setPexelsKey(key: String) = viewModelScope.launch {
        prefs.setPexelsKey(key)
    }

    fun setVideoFpsLimit(fps: Int) = viewModelScope.launch {
        prefs.setVideoFpsLimit(fps)
    }

    fun setSchedulerEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSchedulerEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, schedulerInterval.value)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setSchedulerInterval(minutes: Long) = viewModelScope.launch {
        prefs.setSchedulerInterval(minutes)
        if (schedulerEnabled.value) AutoWallpaperWorker.schedule(context, minutes)
    }

    fun setSchedulerSource(source: String) = viewModelScope.launch { prefs.setSchedulerSource(source) }
    fun setSchedulerHome(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerHome(enabled) }
    fun setSchedulerLock(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerLock(enabled) }
    fun setSchedulerShuffle(shuffle: Boolean) = viewModelScope.launch { prefs.setSchedulerShuffle(shuffle) }
    fun setWeatherEffects(enabled: Boolean) = viewModelScope.launch { prefs.setWeatherEffectsEnabled(enabled) }
    fun setAdaptiveTint(enabled: Boolean) = viewModelScope.launch { prefs.setAdaptiveTintEnabled(enabled) }
    fun setDarkModeSwitch(enabled: Boolean) = viewModelScope.launch { prefs.setDarkModeAutoSwitch(enabled) }

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
    val schedulerEnabled by viewModel.schedulerEnabled.collectAsState()
    val schedulerInterval by viewModel.schedulerInterval.collectAsState()
    val schedulerSource by viewModel.schedulerSource.collectAsState()
    val schedulerHome by viewModel.schedulerHome.collectAsState()
    val schedulerLock by viewModel.schedulerLock.collectAsState()
    val schedulerShuffle by viewModel.schedulerShuffle.collectAsState()
    val weatherEffects by viewModel.weatherEffects.collectAsState()
    val adaptiveTint by viewModel.adaptiveTint.collectAsState()
    val darkModeSwitch by viewModel.darkModeSwitch.collectAsState()

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
                subtitle = "Set a video or GIF as live wallpaper",
                onClick = { videoPickerLauncher.launch("video/*") },
            )
            SettingsItem(
                icon = Icons.Default.Gif,
                title = "GIF wallpaper",
                subtitle = "Set an animated GIF as live wallpaper",
                onClick = { videoPickerLauncher.launch("image/gif") },
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

        // Wallpaper Scheduler
        SettingsSection("Wallpaper Scheduler") {
            var showSchedulerInterval by remember { mutableStateOf(false) }
            var showSchedulerSource by remember { mutableStateOf(false) }

            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = "Auto-rotate wallpapers",
                subtitle = if (schedulerEnabled) "Every ${formatInterval(schedulerInterval)}" else "Disabled",
                checked = schedulerEnabled,
                onCheckedChange = { viewModel.setSchedulerEnabled(it) },
            )
            if (schedulerEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Rotation interval",
                    subtitle = formatInterval(schedulerInterval),
                    onClick = { showSchedulerInterval = true },
                )
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = "Source",
                    subtitle = schedulerSource.replaceFirstChar { it.uppercase() },
                    onClick = { showSchedulerSource = true },
                )
                SettingsToggle(
                    icon = Icons.Default.Home,
                    title = "Home screen",
                    subtitle = "Change home screen wallpaper",
                    checked = schedulerHome,
                    onCheckedChange = { viewModel.setSchedulerHome(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Lock,
                    title = "Lock screen",
                    subtitle = "Change lock screen wallpaper",
                    checked = schedulerLock,
                    onCheckedChange = { viewModel.setSchedulerLock(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Shuffle,
                    title = "Shuffle",
                    subtitle = if (schedulerShuffle) "Random order" else "Sequential order",
                    checked = schedulerShuffle,
                    onCheckedChange = { viewModel.setSchedulerShuffle(it) },
                )
            }

            if (showSchedulerInterval) {
                val intervals = listOf(
                    15L to "15 minutes", 30L to "30 minutes", 60L to "1 hour",
                    120L to "2 hours", 360L to "6 hours", 720L to "12 hours",
                    1440L to "24 hours", 2880L to "2 days",
                )
                AlertDialog(
                    onDismissRequest = { showSchedulerInterval = false },
                    title = { Text("Rotation interval") },
                    text = {
                        Column {
                            intervals.forEach { (min, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = schedulerInterval == min, onClick = { viewModel.setSchedulerInterval(min); showSchedulerInterval = false })
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSchedulerInterval = false }) { Text("Cancel") } },
                )
            }

            if (showSchedulerSource) {
                val sources = listOf(
                    "discover" to "Discover (mixed)", "favorites" to "My Favorites",
                    "wallhaven" to "Wallhaven", "pixabay" to "Pixabay", "reddit" to "Reddit",
                    "unsplash" to "Unsplash", "bing" to "Bing Daily", "collection" to "Collection",
                )
                AlertDialog(
                    onDismissRequest = { showSchedulerSource = false },
                    title = { Text("Wallpaper source") },
                    text = {
                        Column {
                            sources.forEach { (key, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = schedulerSource == key, onClick = { viewModel.setSchedulerSource(key); showSchedulerSource = false })
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSchedulerSource = false }) { Text("Cancel") } },
                )
            }
        }

        // Smart Features
        SettingsSection("Smart Features") {
            SettingsToggle(
                icon = Icons.Default.WbSunny,
                title = "Time-of-day tint",
                subtitle = "Warm tones at sunrise/sunset, cool at night",
                checked = adaptiveTint,
                onCheckedChange = { viewModel.setAdaptiveTint(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Cloud,
                title = "Weather effects",
                subtitle = "Rain, snow, fog overlay based on real weather",
                checked = weatherEffects,
                onCheckedChange = { viewModel.setWeatherEffects(it) },
            )
            SettingsToggle(
                icon = Icons.Default.DarkMode,
                title = "Dark/light auto-switch",
                subtitle = "Different wallpaper for dark vs light mode",
                checked = darkModeSwitch,
                onCheckedChange = { viewModel.setDarkModeSwitch(it) },
            )
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

        // Video Wallpapers
        SettingsSection("Video Wallpapers") {
            var showFpsPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "FPS limit",
                subtitle = "Controls battery usage for video wallpapers",
                onClick = { showFpsPicker = true },
            )
            if (showFpsPicker) {
                AlertDialog(
                    onDismissRequest = { showFpsPicker = false },
                    title = { Text("Video FPS limit") },
                    text = {
                        Column {
                            listOf(15 to "15 FPS (battery saver)", 30 to "30 FPS (balanced)", 60 to "60 FPS (smooth)").forEach { (fps, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = false, onClick = {
                                        viewModel.setVideoFpsLimit(fps)
                                        showFpsPicker = false
                                    })
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showFpsPicker = false }) { Text("Cancel") } },
                )
            }
        }

        // API Keys
        SettingsSection("API Keys") {
            var showPexelsKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Pexels API Key",
                subtitle = "Free key for video wallpapers (pexels.com/api)",
                onClick = { showPexelsKey = true },
            )
            if (showPexelsKey) {
                var keyText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showPexelsKey = false },
                    title = { Text("Pexels API Key") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Get a free key at pexels.com/api/new", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = keyText,
                                onValueChange = { keyText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Paste API key here") },
                                singleLine = true,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setPexelsKey(keyText.trim())
                            showPexelsKey = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPexelsKey = false }) { Text("Cancel") }
                    },
                )
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
                title = "Aura",
                subtitle = "v3.0.0 - Open source device personalization",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/SysAdminDoc/Aura",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/Aura")))
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
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EarthPorn", "spaceporn", "CityPorn", "ImaginaryLandscapes",
                            "MinimalWallpaper", "phonewallpapers", "WidescreenWallpaper").forEach { sub ->
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

private fun formatInterval(minutes: Long): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60L -> "1 hour"
    minutes < 1440 -> "${minutes / 60} hours"
    minutes == 1440L -> "1 day"
    else -> "${minutes / 1440} days"
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
        "pixabay" to "Pixabay",
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

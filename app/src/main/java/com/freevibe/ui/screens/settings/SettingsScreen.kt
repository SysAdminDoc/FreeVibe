package com.freevibe.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.service.DailyWallpaperWorker
import com.freevibe.service.WeatherUpdateWorker
import com.freevibe.service.VideoWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.launchLiveWallpaperPicker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val autoWpEnabled by viewModel.autoWpEnabled.collectAsStateWithLifecycle()
    val autoWpInterval by viewModel.autoWpInterval.collectAsStateWithLifecycle()
    val autoWpSource by viewModel.autoWpSource.collectAsStateWithLifecycle()
    val autoPreview by viewModel.autoPreview.collectAsStateWithLifecycle()
    val wallpaperHistory by viewModel.wallpaperHistory.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val ytRingtonesQuery by viewModel.ytRingtonesQuery.collectAsStateWithLifecycle()
    val ytNotificationsQuery by viewModel.ytNotificationsQuery.collectAsStateWithLifecycle()
    val ytAlarmsQuery by viewModel.ytAlarmsQuery.collectAsStateWithLifecycle()
    val ytBlockedWords by viewModel.ytBlockedWords.collectAsStateWithLifecycle()
    val previewVolume by viewModel.previewVolume.collectAsStateWithLifecycle()
    val redditSubs by viewModel.redditSubs.collectAsStateWithLifecycle()
    val preferredRes by viewModel.preferredRes.collectAsStateWithLifecycle()
    val userStyles by viewModel.userStyles.collectAsStateWithLifecycle()
    val schedulerEnabled by viewModel.schedulerEnabled.collectAsStateWithLifecycle()
    val schedulerInterval by viewModel.schedulerInterval.collectAsStateWithLifecycle()
    val schedulerSource by viewModel.schedulerSource.collectAsStateWithLifecycle()
    val schedulerHome by viewModel.schedulerHome.collectAsStateWithLifecycle()
    val schedulerLock by viewModel.schedulerLock.collectAsStateWithLifecycle()
    val schedulerShuffle by viewModel.schedulerShuffle.collectAsStateWithLifecycle()
    val weatherEffects by viewModel.weatherEffects.collectAsStateWithLifecycle()
    val adaptiveTint by viewModel.adaptiveTint.collectAsStateWithLifecycle()
    val darkModeSwitch by viewModel.darkModeSwitch.collectAsStateWithLifecycle()
    val videoFpsLimit by viewModel.videoFpsLimit.collectAsStateWithLifecycle()
    val wallhavenApiKey by viewModel.wallhavenApiKey.collectAsStateWithLifecycle()
    val pexelsApiKey by viewModel.pexelsApiKey.collectAsStateWithLifecycle()
    val pixabayApiKey by viewModel.pixabayApiKey.collectAsStateWithLifecycle()
    val freesoundApiKey by viewModel.freesoundApiKey.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    var dailyWp by remember {
        mutableStateOf(
            context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .getBoolean("daily_wallpaper_enabled", false)
        )
    }

    fun setDailyWallpaperEnabled(enabled: Boolean) {
        dailyWp = enabled
        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
            .edit().putBoolean("daily_wallpaper_enabled", enabled).apply()
        if (enabled) DailyWallpaperWorker.schedule(context)
        else DailyWallpaperWorker.cancel(context)
    }

    fun enableWeatherEffects() {
        viewModel.setWeatherEffects(true)
        WeatherUpdateWorker.schedule(context)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    // Video wallpaper picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setVideoWallpaperPath(it)
            when (
                launchLiveWallpaperPicker(
                    context = context,
                    serviceComponent = ComponentName(context, VideoWallpaperService::class.java),
                    tag = "SettingsVideoWallpaper",
                )
            ) {
                LiveWallpaperLaunchMode.DIRECT -> {
                    Toast.makeText(context, "Aura Video Wallpaper opened. Set wallpaper to finish.", Toast.LENGTH_LONG).show()
                }
                LiveWallpaperLaunchMode.CHOOSER -> {
                    Toast.makeText(context, "Choose 'Aura Video Wallpaper' in the picker, then tap Set wallpaper.", Toast.LENGTH_LONG).show()
                }
                null -> {
                    Toast.makeText(context, "Video selected. Open Settings > Wallpaper > Live Wallpapers to finish setup.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setDailyWallpaperEnabled(true)
        } else {
            setDailyWallpaperEnabled(false)
            openNotificationSettings()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableWeatherEffects()
        } else {
            viewModel.setWeatherEffects(false)
        }
    }

    // Dialog state
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showColumnsPicker by remember { mutableStateOf(false) }
    var showRedditEditor by remember { mutableStateOf(false) }
    var showResPicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showYtSoundEditor by remember { mutableStateOf(false) }
    var showYtBlockedEditor by remember { mutableStateOf(false) }

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
                icon = Icons.Default.Palette,
                title = "Style preferences",
                subtitle = userStylesSummary(userStyles),
                onClick = { showStylePicker = true },
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
                    "bing" to "Bing Daily", "collection" to "Collection",
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
                icon = Icons.Default.Today,
                title = "Daily wallpaper",
                subtitle = "Get a daily wallpaper recommendation notification",
                checked = dailyWp,
                onCheckedChange = {
                    if (!it) {
                        setDailyWallpaperEnabled(false)
                    } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        setDailyWallpaperEnabled(false)
                        openNotificationSettings()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        setDailyWallpaperEnabled(true)
                    }
                },
            )
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
                onCheckedChange = {
                    if (!it) {
                        viewModel.setWeatherEffects(false)
                        WeatherUpdateWorker.cancel(context)
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        enableWeatherEffects()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
            )
            // Dark/light auto-switch toggle removed — DarkModeReceiver deleted in v5.21.0
            // VFX particle overlays
            var showVfxPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.AutoFixHigh,
                title = "Decorative effects",
                subtitle = "Fireflies, sakura, embers, bubbles, leaves, sparkles",
                onClick = { showVfxPicker = true },
            )
            if (showVfxPicker) {
                val effects = listOf(
                    "NONE" to "None", "FIREFLIES" to "Fireflies",
                    "SAKURA" to "Sakura petals", "EMBERS" to "Fire embers",
                    "BUBBLES" to "Bubbles", "LEAVES" to "Autumn leaves",
                    "SPARKLES" to "Sparkles",
                )
                var currentVfx by remember {
                    mutableStateOf(
                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                            .getString("vfx_effect", "NONE") ?: "NONE"
                    )
                }
                AlertDialog(
                    onDismissRequest = { showVfxPicker = false },
                    title = { Text("Decorative overlay") },
                    text = {
                        Column {
                            effects.forEach { (key, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = currentVfx == key, onClick = {
                                        currentVfx = key
                                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                                            .edit().putString("vfx_effect", key).apply()
                                        showVfxPicker = false
                                    })
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showVfxPicker = false }) { Text("Cancel") } },
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
            SettingsItem(
                icon = Icons.Default.SmartDisplay,
                title = "YouTube search queries",
                subtitle = "Customize what YouTube searches for per tab",
                onClick = { showYtSoundEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.Block,
                title = "Blocked words",
                subtitle = "${ytBlockedWords.split(",").size} words filtered from results",
                onClick = { showYtBlockedEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "Sound sources",
                subtitle = "YouTube, Freesound, Openverse, Audius, ccMixter, SoundCloud, community uploads",
                onClick = onLicensesClick,
            )
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
                                    RadioButton(selected = videoFpsLimit == fps, onClick = {
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
            var showWallhavenKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Wallhaven API Key",
                subtitle = "Optional: higher limits + NSFW (wallhaven.cc/settings)",
                onClick = { showWallhavenKey = true },
            )
            if (showWallhavenKey) {
                var keyText by remember { mutableStateOf(wallhavenApiKey) }
                AlertDialog(
                    onDismissRequest = { showWallhavenKey = false },
                    title = { Text("Wallhaven API Key") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Get your key at wallhaven.cc/settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(value = keyText, onValueChange = { keyText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste API key") }, singleLine = true)
                        }
                    },
                    confirmButton = { TextButton(onClick = { viewModel.setWallhavenKey(keyText.trim()); showWallhavenKey = false }) { Text("Save") } },
                    dismissButton = { TextButton(onClick = { showWallhavenKey = false }) { Text("Cancel") } },
                )
            }
            var showPexelsKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Pexels API Key",
                subtitle = "Free key for video wallpapers (pexels.com/api)",
                onClick = { showPexelsKey = true },
            )
            if (showPexelsKey) {
                var keyText by remember { mutableStateOf(pexelsApiKey) }
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
            var showPixabayKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Pixabay API Key",
                subtitle = "Free key for photos + videos (pixabay.com/api/docs)",
                onClick = { showPixabayKey = true },
            )
            if (showPixabayKey) {
                var keyText by remember { mutableStateOf(pixabayApiKey) }
                AlertDialog(
                    onDismissRequest = { showPixabayKey = false },
                    title = { Text("Pixabay API Key") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Get a free key at pixabay.com/api/docs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            viewModel.setPixabayKey(keyText.trim())
                            showPixabayKey = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPixabayKey = false }) { Text("Cancel") }
                    },
                )
            }
            var showFreesoundKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Freesound API Key",
                subtitle = if (freesoundApiKey.isBlank()) {
                    "Optional: higher limits for Freesound v2 (freesound.org/docs/api)"
                } else {
                    "Connected for Freesound v2 searches and similar-sound lookup"
                },
                onClick = { showFreesoundKey = true },
            )
            if (showFreesoundKey) {
                var keyText by remember { mutableStateOf(freesoundApiKey) }
                AlertDialog(
                    onDismissRequest = { showFreesoundKey = false },
                    title = { Text("Freesound API Key") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Add your Freesound token for higher search limits and more reliable related-sound results.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            viewModel.setFreesoundKey(keyText.trim())
                            showFreesoundKey = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFreesoundKey = false }) { Text("Cancel") }
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
                title = "Clear cache and offline saves",
                subtitle = cacheUsageSubtitle(cacheUsage),
                onClick = { showClearCacheConfirm = true },
            )
        }

        // About
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Aura",
                subtitle = "v${com.freevibe.BuildConfig.VERSION_NAME} - Open source device personalization",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/SysAdminDoc/Aura",
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/Aura")))
                    } catch (_: Exception) {}
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

    if (showStylePicker) {
        val styleOptions = remember {
            listOf(
                "minimal",
                "amoled",
                "nature",
                "space",
                "anime",
                "abstract",
                "neon",
                "city",
                "gradient",
                "dark",
            )
        }
        var selectedStyles by remember(showStylePicker, userStyles) {
            mutableStateOf(
                userStyles.split(",")
                    .map { it.trim().lowercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet()
            )
        }
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text("Style preferences") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "These styles are prioritized across wallpaper discovery and ranking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        styleOptions.forEach { style ->
                            FilterChip(
                                selected = style in selectedStyles,
                                onClick = {
                                    selectedStyles = if (style in selectedStyles) {
                                        selectedStyles - style
                                    } else {
                                        selectedStyles + style
                                    }
                                },
                                label = { Text(stylePreferenceLabel(style)) },
                                leadingIcon = if (style in selectedStyles) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUserStyles(selectedStyles.sorted().joinToString(","))
                    showStylePicker = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showStylePicker = false }) { Text("Cancel") }
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
                        placeholder = { Text("wallpapers,MobileWallpaper,...") },
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

    // YouTube sound search queries editor
    if (showYtSoundEditor) {
        var ringQ by remember { mutableStateOf(ytRingtonesQuery) }
        var notifQ by remember { mutableStateOf(ytNotificationsQuery) }
        var alarmQ by remember { mutableStateOf(ytAlarmsQuery) }
        AlertDialog(
            onDismissRequest = { showYtSoundEditor = false },
            title = { Text("YouTube Search Queries") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Customize what YouTube searches for in each sound tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = ringQ, onValueChange = { ringQ = it }, label = { Text("Ringtones") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2)
                    OutlinedTextField(value = notifQ, onValueChange = { notifQ = it }, label = { Text("Notifications") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2)
                    OutlinedTextField(value = alarmQ, onValueChange = { alarmQ = it }, label = { Text("Alarms") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtRingtonesQuery(ringQ.trim())
                    viewModel.setYtNotificationsQuery(notifQ.trim())
                    viewModel.setYtAlarmsQuery(alarmQ.trim())
                    showYtSoundEditor = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showYtSoundEditor = false }) { Text("Cancel") } },
        )
    }

    // YouTube blocked words editor
    if (showYtBlockedEditor) {
        var blockedText by remember { mutableStateOf(ytBlockedWords) }
        AlertDialog(
            onDismissRequest = { showYtBlockedEditor = false },
            title = { Text("Blocked Words") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comma-separated words. YouTube results containing any of these are hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = blockedText, onValueChange = { blockedText = it }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 5, placeholder = { Text("compilation,mix,playlist...") })
                    Text("${blockedText.split(",").filter { it.isNotBlank() }.size} words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtBlockedWords(blockedText.trim())
                    showYtBlockedEditor = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showYtBlockedEditor = false }) { Text("Cancel") } },
        )
    }

    // Confirm clear cache
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear cache and offline saves?") },
            text = { Text(clearCacheConfirmation(cacheUsage)) },
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

private fun userStylesSummary(raw: String): String {
    val styles = raw.split(",")
        .map { it.trim().lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotBlank() }
    if (styles.isEmpty()) return "No style preference"
    return styles.joinToString(" • ") { stylePreferenceLabel(it) }
}

private fun stylePreferenceLabel(style: String): String =
    style.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun formatInterval(minutes: Long): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60L -> "1 hour"
    minutes < 1440 -> "${minutes / 60} hours"
    minutes == 1440L -> "1 day"
    else -> "${minutes / 1440} days"
}

private fun cacheUsageSubtitle(cacheUsage: CacheUsageState): String =
    buildString {
        append("Using ${cacheUsage.fileUsageLabel} of temp files and offline saves")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(" + wallpaper feed cache")
        }
    }

private fun clearCacheConfirmation(cacheUsage: CacheUsageState): String =
    buildString {
        append("This will remove ${cacheUsage.fileUsageLabel} of temporary media and offline favorites")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(", and reset cached wallpaper feeds")
        }
        append(". Downloaded files are not affected.")
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

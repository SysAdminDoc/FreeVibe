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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.service.DailyWallpaperWorker
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.WeatherUpdateWorker
import com.freevibe.service.VideoWallpaperService
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
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
    val autoWpRequiresCharging by viewModel.autoWpRequiresCharging.collectAsStateWithLifecycle()
    val autoWpRequiresWiFi by viewModel.autoWpRequiresWiFi.collectAsStateWithLifecycle()
    val autoWpRequiresIdle by viewModel.autoWpRequiresIdle.collectAsStateWithLifecycle()
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
    val showSketchyContent by viewModel.showSketchyContent.collectAsStateWithLifecycle()
    val showNsfwContent by viewModel.showNsfwContent.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val videoWallpaperSelectionResult by viewModel.videoWallpaperSelectionResult.collectAsStateWithLifecycle()
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
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            // Some OEM Android builds (e.g. custom MIUI/EMUI skins without the stock settings
            // activity) don't handle ACTION_APP_NOTIFICATION_SETTINGS and crash with ANFE.
            // Fall back to the app-details page which every Android install ships.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                )
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // Video wallpaper picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.prepareVideoWallpaperFromUri(it) }
    }
    // Gallery picker for parallax-from-user-photo (v6.1.0)
    val parallaxGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.applyParallaxFromGallery(it) }
    }
    val parallaxGalleryResult by viewModel.parallaxGalleryResult.collectAsStateWithLifecycle()
    LaunchedEffect(parallaxGalleryResult) {
        val result = parallaxGalleryResult
        when (result) {
            com.freevibe.ui.screens.settings.ParallaxGalleryResult.Ready -> {
                when (
                    launchLiveWallpaperPicker(
                        context = context,
                        serviceComponent = ComponentName(context, com.freevibe.service.ParallaxWallpaperService::class.java),
                        tag = "SettingsParallaxGallery",
                    )
                ) {
                    LiveWallpaperLaunchMode.DIRECT -> Toast.makeText(context, "Aura Parallax opened. Set wallpaper to finish.", Toast.LENGTH_LONG).show()
                    LiveWallpaperLaunchMode.CHOOSER -> Toast.makeText(context, "Choose 'Aura Parallax' in the picker, then tap Set wallpaper.", Toast.LENGTH_LONG).show()
                    null -> Toast.makeText(context, "Photo ready. Open Settings > Wallpaper > Live Wallpapers to finish.", Toast.LENGTH_LONG).show()
                }
                viewModel.clearParallaxGalleryResult()
            }
            is com.freevibe.ui.screens.settings.ParallaxGalleryResult.Failure -> {
                Toast.makeText(context, "Couldn't use that photo: ${result.message}", Toast.LENGTH_LONG).show()
                viewModel.clearParallaxGalleryResult()
            }
            else -> Unit
        }
    }
    LaunchedEffect(videoWallpaperSelectionResult) {
        when (val result = videoWallpaperSelectionResult) {
            VideoWallpaperSelectionResult.Ready -> {
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
                viewModel.clearVideoWallpaperSelectionResult()
            }
            is VideoWallpaperSelectionResult.Failure -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearVideoWallpaperSelectionResult()
            }
            else -> Unit
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
    val selectedStyleCount = remember(userStyles) { countSelectedStyles(userStyles) }
    val configuredApiKeys = remember(
        wallhavenApiKey,
        pexelsApiKey,
        pixabayApiKey,
        freesoundApiKey,
    ) {
        listOf(wallhavenApiKey, pexelsApiKey, pixabayApiKey, freesoundApiKey).count { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    ),
                ),
            )
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        SettingsOverviewCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            selectedStyleCount = selectedStyleCount,
            schedulerEnabled = schedulerEnabled,
            schedulerInterval = schedulerInterval,
            weatherEffects = weatherEffects,
            adaptiveTint = adaptiveTint,
            autoPreview = autoPreview,
            videoFpsLimit = videoFpsLimit,
            cacheUsage = cacheUsage,
            configuredApiKeys = configuredApiKeys,
        )

        // Wallpapers
        SettingsSection(
            title = "Wallpapers",
            description = "Tune discovery quality, density, and the overall look of your feed.",
        ) {
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
                // T-7: Rotation execution constraints. WorkManager gates the worker on
                // these — toggle on, then the worker only fires when ALL satisfied.
                // Off-by-default so existing users keep current behavior on upgrade.
                SettingsToggle(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Charging only",
                    subtitle = "Hold rotation until the device is plugged in",
                    checked = autoWpRequiresCharging,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresCharging(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi only",
                    subtitle = "Skip cellular fetches; honors data-saver",
                    checked = autoWpRequiresWiFi,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresWiFiOnly(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Bedtime,
                    title = "Device idle only",
                    subtitle = "Defer rotation until you're not actively using the phone",
                    checked = autoWpRequiresIdle,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresIdle(it) },
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
                subtitle = "Set a local video clip as live wallpaper",
                onClick = { videoPickerLauncher.launch("video/*") },
            )
            SettingsItem(
                icon = Icons.Default.Gif,
                title = "GIF wallpaper",
                subtitle = "Animated GIF import is not supported yet",
                onClick = {
                    Toast.makeText(
                        context,
                        "Animated GIF wallpapers are not supported yet. Pick a video clip instead.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
            // v6.1.0 — parallax from user photo
            SettingsItem(
                icon = Icons.Default.PhotoLibrary,
                title = "Parallax from my photo",
                subtitle = "Turn one of your photos into a depth-tilt live wallpaper",
                onClick = {
                    parallaxGalleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
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
        SettingsSection(
            title = "Wallpaper Scheduler",
            description = "Automate rotation across sources, collections, and screen targets.",
        ) {
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
                val collectionsList by viewModel.collections.collectAsStateWithLifecycle()
                val activeCollectionId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
                val activeCollectionName = remember(collectionsList, activeCollectionId) {
                    collectionsList.firstOrNull { it.collectionId == activeCollectionId }?.name
                }
                val sourceSubtitle = when {
                    schedulerSource == "collection" && activeCollectionName != null ->
                        "Collection: $activeCollectionName"
                    schedulerSource == "collection" ->
                        "Collection (none selected)"
                    else ->
                        schedulerSource.replaceFirstChar { it.uppercase() }
                }
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = "Source",
                    subtitle = sourceSubtitle,
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

            var showCollectionPicker by remember { mutableStateOf(false) }
            if (showSchedulerSource) {
                val sources = listOf(
                    "discover" to "Discover (mixed)", "favorites" to "My Favorites",
                    "wallhaven" to "Wallhaven", "pixabay" to "Pixabay", "reddit" to "Reddit",
                    "bing" to "Bing Daily", "collection" to "A collection…",
                )
                AlertDialog(
                    onDismissRequest = { showSchedulerSource = false },
                    title = { Text("Wallpaper source") },
                    text = {
                        Column {
                            sources.forEach { (key, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = schedulerSource == key,
                                        onClick = {
                                            if (key == "collection") {
                                                showSchedulerSource = false
                                                showCollectionPicker = true
                                            } else {
                                                viewModel.setSchedulerSource(key)
                                                showSchedulerSource = false
                                            }
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSchedulerSource = false }) { Text("Cancel") } },
                )
            }

            if (showCollectionPicker) {
                val collections by viewModel.collections.collectAsStateWithLifecycle()
                val activeId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
                AlertDialog(
                    onDismissRequest = { showCollectionPicker = false },
                    title = { Text("Rotate from which collection?") },
                    text = {
                        if (collections.isEmpty()) {
                            // Empty-state guidance: we can't rotate through something that
                            // doesn't exist yet.
                            Column {
                                Text(
                                    "You haven't created any collections yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Save wallpapers to a collection from the wallpaper detail screen, then come back here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                collections.forEach { c ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setSchedulerCollection(c.collectionId)
                                                showCollectionPicker = false
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = activeId == c.collectionId, onClick = {
                                            viewModel.setSchedulerCollection(c.collectionId)
                                            showCollectionPicker = false
                                        })
                                        Spacer(Modifier.width(8.dp))
                                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showCollectionPicker = false }) { Text("Cancel") } },
                )
            }
        }

        // Smart Features
        SettingsSection(
            title = "Smart Features",
            description = "Ambient enhancements that make Aura feel more adaptive and alive.",
        ) {
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
        SettingsSection(
            title = "Sounds",
            description = "Control previews, search quality, and how results are filtered before playback.",
        ) {
            SettingsToggle(
                icon = Icons.Default.PlayCircle,
                title = "Auto-preview sounds",
                subtitle = if (autoPreview) "Starts playback when you open sound details" else "Open sound details without autoplay",
                checked = autoPreview,
                onCheckedChange = { viewModel.setAutoPreview(it) },
            )
            // Preview volume slider
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Default.VolumeUp,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preview volume", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Choose how assertive previews should feel while browsing sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                subtitle = "Refine ringtone, notification, and alarm searches for each tab",
                onClick = { showYtSoundEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.Block,
                title = "Blocked words",
                subtitle = "${ytBlockedWords.split(",").count { it.isNotBlank() }} words filtered from YouTube results",
                onClick = { showYtBlockedEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "Sound sources",
                subtitle = "Review the providers Aura uses for search, downloads, and attributions",
                onClick = onLicensesClick,
            )
        }

        // Video Wallpapers
        SettingsSection(
            title = "Video Wallpapers",
            description = "Keep motion smooth without spending battery on unnecessary frames.",
        ) {
            var showFpsPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "FPS limit",
                subtitle = "$videoFpsLimit FPS • balance smooth motion against battery use",
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
        SettingsSection(
            title = "API Keys",
            description = "Optional provider credentials unlock higher limits, richer search, and advanced filters.",
        ) {
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
            // Wallhaven SafeSearch toggles. Without an API key both remain UI-visible
            // but ineffective — Wallhaven rejects non-SFW requests when unauthenticated,
            // and computeWallhavenPurity coerces back to "100" so the user still sees
            // results instead of an empty grid.
            SettingsToggle(
                icon = Icons.Default.Visibility,
                title = "Show sketchy wallpapers",
                subtitle = if (wallhavenApiKey.isBlank())
                    "Requires a Wallhaven API key to take effect"
                else
                    "Suggestive imagery short of explicit nudity",
                checked = showSketchyContent,
                onCheckedChange = { viewModel.setShowSketchy(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Warning,
                title = "Show NSFW wallpapers",
                subtitle = if (wallhavenApiKey.isBlank())
                    "Requires a Wallhaven API key to take effect"
                else
                    "Explicit content from authenticated Wallhaven account",
                checked = showNsfwContent,
                onCheckedChange = { viewModel.setShowNsfw(it) },
            )
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
        SettingsSection(
            title = "Storage",
            description = "Keep downloads accessible while trimming temporary media and cached feeds when needed.",
        ) {
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Downloads",
                subtitle = "Review wallpapers, sounds, and videos saved by Aura",
                onClick = onDownloadsClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Free up storage",
                subtitle = cacheUsageSubtitle(cacheUsage),
                onClick = { showClearCacheConfirm = true },
            )
        }

        // Diagnostics — opt-in surface for "why is X tab loading slowly?".
        // Reads in-memory metrics collected by SourceMetrics; resets on process death.
        var showDiagnostics by remember { mutableStateOf(false) }
        SettingsSection(
            title = "Diagnostics",
            description = "Per-source request counts and latency snapshots for this session.",
        ) {
            SettingsItem(
                icon = Icons.Default.MonitorHeart,
                title = "Source diagnostics",
                subtitle = "View success ratio, p50/p95 latency, and last error per content source",
                onClick = { showDiagnostics = true },
            )
        }
        if (showDiagnostics) {
            val snapshots = viewModel.diagnosticsSnapshot()
            AlertDialog(
                onDismissRequest = { showDiagnostics = false },
                title = { Text("Source diagnostics") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (snapshots.isEmpty()) {
                            Text(
                                "No source activity recorded yet — open the Wallpapers or Sounds tab to populate stats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            snapshots.forEach { stat ->
                                Column {
                                    Text(stat.source.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                                    val pct = (stat.successRatio * 100).toInt()
                                    val latency = if (stat.p50Ms != null) "p50 ${stat.p50Ms}ms / p95 ${stat.p95Ms}ms" else "no latency yet"
                                    Text(
                                        "${stat.totalRequests} requests • $pct% success • $latency",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (stat.lastErrorClass != null) {
                                        Text(
                                            "Last error: ${stat.lastErrorClass} — ${stat.lastErrorMessage ?: "no detail"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.resetDiagnostics()
                        showDiagnostics = false
                    }) { Text("Reset") }
                },
            )
        }

        // About
        SettingsSection(
            title = "About",
            description = "Project details, source code, and the open-source building blocks behind Aura.",
        ) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Aura",
                subtitle = "Version ${com.freevibe.BuildConfig.VERSION_NAME} • Open-source device personalization studio",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "Browse the project on GitHub",
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/Aura")))
                    } catch (_: Exception) {}
                },
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = "Open source licenses",
                subtitle = "See library licenses and content-source attributions",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsOverviewCard(
    modifier: Modifier = Modifier,
    selectedStyleCount: Int,
    schedulerEnabled: Boolean,
    schedulerInterval: Long,
    weatherEffects: Boolean,
    adaptiveTint: Boolean,
    autoPreview: Boolean,
    videoFpsLimit: Int,
    cacheUsage: CacheUsageState,
    configuredApiKeys: Int,
) {
    val setupSummary = remember(
        selectedStyleCount,
        schedulerEnabled,
        schedulerInterval,
        weatherEffects,
        adaptiveTint,
        autoPreview,
    ) {
        buildList {
            if (selectedStyleCount > 0) add("$selectedStyleCount style preferences")
            if (schedulerEnabled) add("rotation every ${formatInterval(schedulerInterval)}")
            if (weatherEffects) add("weather overlays")
            if (adaptiveTint) add("time-of-day tint")
            if (autoPreview) add("sound previews")
        }.joinToString(" • ").ifBlank {
            "Aura is set up with calm defaults. Adjust discovery, automation, and playback here whenever you want."
        }
    }

    GlassCard(modifier = modifier) {
        HighlightPill(
            label = "Personalization overview",
            icon = Icons.Default.Tune,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Make Aura feel intentional",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = setupSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HighlightPill(
                label = if (selectedStyleCount == 0) "No style bias yet" else "$selectedStyleCount styles selected",
                icon = Icons.Default.Wallpaper,
                tint = MaterialTheme.colorScheme.primary,
            )
            HighlightPill(
                label = if (schedulerEnabled) "Rotation on" else "Rotation off",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.secondary,
            )
            HighlightPill(
                label = "$videoFpsLimit FPS video",
                icon = Icons.Default.VideoLibrary,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            HighlightPill(
                label = "$configuredApiKeys provider keys",
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = "Automation",
                value = if (schedulerEnabled) formatInterval(schedulerInterval) else "Manual",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.primary,
            )
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = "Storage",
                value = cacheUsage.fileUsageLabel,
                icon = Icons.Default.Folder,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SettingsMetric(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.14f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp),
                )
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(top = 26.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
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
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                },
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
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

private fun countSelectedStyles(raw: String): Int =
    raw.split(",").count { it.trim().isNotBlank() }

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

package com.freevibe.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val wallhavenKey = prefs.wallhavenApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val autoWpEnabled = prefs.autoWallpaperEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWpInterval = prefs.autoWallpaperInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12L)
    val autoWpSource = prefs.autoWallpaperSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallhaven")
    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    // #11: Wallpaper history
    val wallpaperHistory = historyManager.getRecent(20).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun saveKey(type: String, value: String) = viewModelScope.launch {
        when (type) {
            "wallhaven" -> prefs.setWallhavenKey(value)
        }
    }

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

    fun clearCache() = viewModelScope.launch {
        // Clear only image/http caches, not trimmed audio or other app state
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            // Preserve trimmed audio cache; clear everything else (Coil, OkHttp, etc.)
            if (file.name != "trimmed") {
                file.deleteRecursively()
            }
        }
        // Also clear offline favorite files
        offlineFavorites.clearAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDownloadsClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    onCategoriesClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val wallhavenKey by viewModel.wallhavenKey.collectAsState()
    val autoWpEnabled by viewModel.autoWpEnabled.collectAsState()
    val autoWpInterval by viewModel.autoWpInterval.collectAsState()
    val autoWpSource by viewModel.autoWpSource.collectAsState()
    val autoPreview by viewModel.autoPreview.collectAsState()
    val wallpaperHistory by viewModel.wallpaperHistory.collectAsState()

    // Dialog state
    var editingKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text("Settings") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        // API Keys (optional)
        SettingsSection("Content Sources") {
            ApiKeyItem(
                title = "Wallhaven API Key",
                subtitle = if (wallhavenKey.isNotEmpty()) "Configured" else "Optional - enables higher rate limits",
                isSet = wallhavenKey.isNotEmpty(),
                onClick = { editingKey = "wallhaven" to wallhavenKey },
            )
        }

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
            SettingsItem(
                icon = Icons.Default.Category,
                title = "Browse categories",
                subtitle = "Nature, Space, Anime, Dark, Neon + 12 more",
                onClick = onCategoriesClick,
            )
            // #11: Wallpaper history
            if (wallpaperHistory.isNotEmpty()) {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Wallpaper history",
                    subtitle = "${wallpaperHistory.size} recently applied",
                    onClick = { showClearHistoryConfirm = true },
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
                subtitle = "Free up disk space used by cached content",
                onClick = { showClearCacheConfirm = true },
            )
        }

        // About
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "FreeVibe",
                subtitle = "v0.8.0 - Open source device personalization",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "github.com/SysAdminDoc/FreeVibe",
                onClick = {},
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

    // API Key edit dialog
    editingKey?.let { (type, currentValue) ->
        ApiKeyDialog(
            title = "${type.replaceFirstChar { it.uppercase() }} API Key",
            currentValue = currentValue,
            onDismiss = { editingKey = null },
            onSave = { newValue ->
                viewModel.saveKey(type, newValue)
                editingKey = null
            },
        )
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

    // Confirm clear cache
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear cache?") },
            text = { Text("This will remove all cached images and offline favorites. Downloaded files are not affected.") },
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

    // Confirm clear wallpaper history
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear wallpaper history?") },
            text = { Text("This will remove all records of previously applied wallpapers.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearWallpaperHistory()
                    showClearHistoryConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ApiKeyDialog(
    title: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentValue) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) { Text("Save") }
        },
        dismissButton = {
            if (currentValue.isNotEmpty()) {
                TextButton(onClick = { onSave(""); onDismiss() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun ApiKeyItem(
    title: String,
    subtitle: String,
    isSet: Boolean,
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
            Icon(
                Icons.Default.Key,
                contentDescription = null,
                tint = if (isSet) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSet) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSet) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
        "wallhaven" to "Wallhaven",
        "unsplash" to "Unsplash",
        "bing" to "Bing Daily",
        "wikimedia" to "Wikimedia Commons",
        "reddit" to "Reddit",
        "nasa" to "NASA APOD",
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

package com.freevibe.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.AutoWallpaperWorker
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CacheUsageState(
    val fileUsageLabel: String = "Calculating...",
    val hasWallpaperMetadataCache: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: PreferencesManager,
    private val historyManager: WallpaperHistoryManager,
    private val offlineFavorites: OfflineFavoritesManager,
    private val wallpaperCacheManager: WallpaperCacheManager,
    private val collectionRepo: CollectionRepository,
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
    val redditSubs = prefs.redditSubreddits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallpapers,MobileWallpaper,MinimalWallpaper")
    val preferredRes = prefs.preferredResolution.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val userStyles = prefs.userStyles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ytRingtonesQuery = prefs.ytSoundQueryRingtones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultRingtoneQuery())
    val ytNotificationsQuery = prefs.ytSoundQueryNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultNotificationQuery())
    val ytAlarmsQuery = prefs.ytSoundQueryAlarms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultAlarmQuery())
    val ytBlockedWords = prefs.ytSoundBlockedWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode")
    val videoFpsLimit = prefs.videoFpsLimit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    val wallhavenApiKey = prefs.wallhavenApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pexelsApiKey = prefs.pexelsApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pixabayApiKey = prefs.pixabayApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val freesoundApiKey = prefs.freesoundApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setYtRingtonesQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryRingtones(q) }
    fun setYtNotificationsQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryNotifications(q) }
    fun setYtAlarmsQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryAlarms(q) }
    fun setYtBlockedWords(w: String) = viewModelScope.launch { prefs.setYtSoundBlockedWords(w) }

    // #11: Wallpaper history
    val wallpaperHistory = historyManager.getRecent(20).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    private val _cacheUsage = MutableStateFlow(CacheUsageState())
    val cacheUsage: StateFlow<CacheUsageState> = _cacheUsage.asStateFlow()

    init {
        refreshCacheUsage()
    }

    fun setAutoWallpaper(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setAutoWpInterval(hours: Long) = viewModelScope.launch {
        prefs.setAutoWallpaperInterval(hours)
        if (autoWpEnabled.value) {
            AutoWallpaperWorker.schedule(context, hours * 60)
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

    fun setUserStyles(styles: String) = viewModelScope.launch {
        prefs.setUserStyles(styles)
    }

    fun setWallhavenKey(key: String) = viewModelScope.launch {
        prefs.setWallhavenKey(key)
    }

    fun setPexelsKey(key: String) = viewModelScope.launch {
        prefs.setPexelsKey(key)
    }

    fun setPixabayKey(key: String) = viewModelScope.launch {
        prefs.setPixabayKey(key)
    }

    fun setFreesoundKey(key: String) = viewModelScope.launch {
        prefs.setFreesoundKey(key)
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

    // Collection rotation ----------------------------------------------------
    val collections: StateFlow<List<WallpaperCollectionEntity>> = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedulerCollectionId = prefs.schedulerCollectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1L)

    /**
     * Pick a specific collection to rotate from. Also flips the source to "collection" so
     * the next scheduler tick actually reads from it.
     */
    fun setSchedulerCollection(id: Long) = viewModelScope.launch {
        prefs.setSchedulerCollection(id)
        prefs.setSchedulerSource("collection")
    }
    fun setSchedulerHome(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerHome(enabled) }
    fun setSchedulerLock(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerLock(enabled) }
    fun setSchedulerShuffle(shuffle: Boolean) = viewModelScope.launch { prefs.setSchedulerShuffle(shuffle) }
    fun setWeatherEffects(enabled: Boolean) = viewModelScope.launch { prefs.setWeatherEffectsEnabled(enabled) }
    fun setAdaptiveTint(enabled: Boolean) = viewModelScope.launch { prefs.setAdaptiveTintEnabled(enabled) }
    fun setDarkModeSwitch(enabled: Boolean) = viewModelScope.launch { prefs.setDarkModeAutoSwitch(enabled) }

    fun setVideoWallpaperPath(uri: Uri) {
        val path = try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return
            val cacheFile = java.io.File(context.filesDir, "live_wallpaper.mp4")
            inputStream.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile.absolutePath
        } catch (_: Exception) { null } ?: return

        context.getSharedPreferences("freevibe_live_wp", android.content.Context.MODE_PRIVATE)
            .edit().putString("video_path", path).apply()
    }

    fun clearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != "trimmed") {
                    file.deleteRecursively()
                }
            }
            offlineFavorites.clearAll()
            wallpaperCacheManager.clearAll()
        }
        refreshCacheUsage()
    }

    private fun refreshCacheUsage() = viewModelScope.launch {
        _cacheUsage.value = withContext(Dispatchers.IO) {
            val cacheBytes = context.cacheDir
                .takeIf { it.exists() }
                ?.walkTopDown()
                ?.filter { it.isFile && it.parentFile?.name != "trimmed" }
                ?.sumOf { it.length() }
                ?: 0L
            CacheUsageState(
                fileUsageLabel = formatBytes(cacheBytes + offlineFavorites.getCacheSize()),
                hasWallpaperMetadataCache = wallpaperCacheManager.countEntries() > 0,
            )
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

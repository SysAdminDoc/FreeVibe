package com.freevibe.service

import android.content.Context
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared singleton that holds the currently selected wallpaper/sound for detail screens.
 * Required because each NavBackStackEntry gets its own ViewModel instance via hiltViewModel(),
 * so list-screen and detail-screen ViewModels cannot share state directly.
 *
 * **NX-4 partial (rev4-impl):** the single selected wallpaper and selected sound now
 * persist across process death via a small SharedPreferences-backed JSON snapshot.
 * The pager-supporting `wallpaperList` is intentionally NOT persisted — it can be
 * huge, and on resume after process death the pager would restart at index 0
 * anyway. Anchor key is also reset on cold start; the detail screen handles the
 * "list lost" case by falling back to single-item display.
 *
 * Full sweep — moving to a nav-graph-scoped `SelectionViewModel` with
 * `SavedStateHandle` — remains queued as the wider refactor.
 */
@Singleton
class SelectedContentHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val wallpaperAdapter by lazy { moshi.adapter(Wallpaper::class.java) }
    private val soundAdapter by lazy { moshi.adapter(Sound::class.java) }
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selectedWallpaper = MutableStateFlow<Wallpaper?>(loadWallpaper())
    val selectedWallpaper: StateFlow<Wallpaper?> = _selectedWallpaper.asStateFlow()

    /** Wallpaper list from the source screen, for pager in detail screen */
    private val _wallpaperList = MutableStateFlow<List<Wallpaper>>(emptyList())
    val wallpaperList: StateFlow<List<Wallpaper>> = _wallpaperList.asStateFlow()
    private val _wallpaperListAnchorKey = MutableStateFlow<String?>(null)
    val wallpaperListAnchorKey: StateFlow<String?> = _wallpaperListAnchorKey.asStateFlow()

    private val _selectedSound = MutableStateFlow<Sound?>(loadSound())
    val selectedSound: StateFlow<Sound?> = _selectedSound.asStateFlow()

    @Synchronized
    fun selectWallpaper(wallpaper: Wallpaper, wallpapers: List<Wallpaper>) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
        if (wallpapers.isNotEmpty()) {
            _wallpaperList.value = wallpapers
            _wallpaperListAnchorKey.value = wallpaper.stableKey()
        } else {
            _wallpaperList.value = emptyList()
            _wallpaperListAnchorKey.value = null
        }
    }

    @Synchronized
    fun selectWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
        _wallpaperList.value = emptyList()
        _wallpaperListAnchorKey.value = null
    }

    @Synchronized
    fun updateSelectedWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
    }

    @Synchronized
    fun selectSound(sound: Sound) {
        _selectedSound.value = sound
        persistSound(sound)
    }

    private fun loadWallpaper(): Wallpaper? = runCatching {
        prefs.getString(KEY_WALLPAPER, null)?.let { wallpaperAdapter.fromJson(it) }
    }.getOrNull()

    private fun loadSound(): Sound? = runCatching {
        prefs.getString(KEY_SOUND, null)?.let { soundAdapter.fromJson(it) }
    }.getOrNull()

    private fun persistWallpaper(w: Wallpaper) {
        persistScope.launch {
            runCatching {
                prefs.edit().putString(KEY_WALLPAPER, wallpaperAdapter.toJson(w)).apply()
            }
        }
    }

    private fun persistSound(s: Sound) {
        persistScope.launch {
            runCatching {
                prefs.edit().putString(KEY_SOUND, soundAdapter.toJson(s)).apply()
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "freevibe_selected_content"
        const val KEY_WALLPAPER = "selected_wallpaper_json"
        const val KEY_SOUND = "selected_sound_json"
    }
}

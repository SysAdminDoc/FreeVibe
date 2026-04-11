package com.freevibe.service

import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared singleton that holds the currently selected wallpaper/sound for detail screens.
 * Required because each NavBackStackEntry gets its own ViewModel instance via hiltViewModel(),
 * so list-screen and detail-screen ViewModels cannot share state directly.
 */
@Singleton
class SelectedContentHolder @Inject constructor() {

    private val _selectedWallpaper = MutableStateFlow<Wallpaper?>(null)
    val selectedWallpaper: StateFlow<Wallpaper?> = _selectedWallpaper.asStateFlow()

    /** Wallpaper list from the source screen, for pager in detail screen */
    private val _wallpaperList = MutableStateFlow<List<Wallpaper>>(emptyList())
    val wallpaperList: StateFlow<List<Wallpaper>> = _wallpaperList.asStateFlow()
    private val _wallpaperListAnchorKey = MutableStateFlow<String?>(null)
    val wallpaperListAnchorKey: StateFlow<String?> = _wallpaperListAnchorKey.asStateFlow()

    private val _selectedSound = MutableStateFlow<Sound?>(null)
    val selectedSound: StateFlow<Sound?> = _selectedSound.asStateFlow()

    @Synchronized
    fun selectWallpaper(wallpaper: Wallpaper, wallpapers: List<Wallpaper>) {
        _selectedWallpaper.value = wallpaper
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
        _wallpaperList.value = emptyList()
        _wallpaperListAnchorKey.value = null
    }

    @Synchronized
    fun updateSelectedWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
    }

    @Synchronized
    fun selectSound(sound: Sound) {
        _selectedSound.value = sound
    }
}

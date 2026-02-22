package com.freevibe.service

import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
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

    private val _selectedSound = MutableStateFlow<Sound?>(null)
    val selectedSound: StateFlow<Sound?> = _selectedSound.asStateFlow()

    fun selectWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
    }

    fun selectSound(sound: Sound) {
        _selectedSound.value = sound
    }
}

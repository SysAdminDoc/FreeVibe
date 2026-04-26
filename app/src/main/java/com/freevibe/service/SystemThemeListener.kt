package com.freevibe.service

import android.content.Context
import android.content.res.Configuration
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors system dark mode changes and auto-applies the corresponding wallpaper
 * when the user has dark/light mode auto-switch enabled.
 */
@Singleton
class SystemThemeListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val wallpaperApplier: WallpaperApplier,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastNightMode = isNightMode()

    fun startListening() {
        scope.launch {
            // Monitor dark mode auto-switch preference
            prefs.darkModeAutoSwitch.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    monitorSystemTheme()
                }
            }
        }
    }

    private suspend fun monitorSystemTheme() {
        while (true) {
            val currentNightMode = isNightMode()
            if (currentNightMode != lastNightMode) {
                lastNightMode = currentNightMode
                val wallpaperId = if (currentNightMode) {
                    prefs.darkModeWallpaperId.first()
                } else {
                    prefs.lightModeWallpaperId.first()
                }
                if (wallpaperId.isNotEmpty()) {
                    applyStoredWallpaper(wallpaperId)
                }
            }
            // Check every 500ms for theme changes (lightweight check)
            kotlinx.coroutines.delay(500)
        }
    }

    private suspend fun applyStoredWallpaper(wallpaperId: String) {
        try {
            // Wallpaper ID format: "source|id|url" (stored when user sets wallpaper)
            val parts = wallpaperId.split("|")
            if (parts.size >= 3) {
                val url = parts[2]
                wallpaperApplier.applyFromUrl(url, WallpaperTarget.BOTH)
            }
        } catch (e: Exception) {
            // Silently fail; auto-apply should not crash the system
        }
    }

    private fun isNightMode(): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}

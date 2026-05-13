package com.freevibe.service

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors system dark mode changes and auto-applies the corresponding wallpaper when
 * the user has dark/light mode auto-switch enabled.
 *
 * Implementation note: this uses [ComponentCallbacks.onConfigurationChanged] (an event)
 * rather than the original 500 ms polling loop. The polling design had three real
 * problems:
 *  1. It never stopped — toggling the feature off in Settings left the loop running.
 *  2. It woke the CPU twice a second forever, which is noticeable on battery dashboards.
 *  3. The collect-then-while(true) structure trapped the outer Flow collector so further
 *     pref emissions couldn't propagate.
 * Configuration changes are delivered to ComponentCallbacks regardless of which Activity
 * is foreground, so this works even while the app is fully backgrounded.
 */
@Singleton
class SystemThemeListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val wallpaperApplier: WallpaperApplier,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    @Volatile private var enabled = false
    @Volatile private var lastNightMode = isNightMode()
    private var prefJob: Job? = null

    private val callbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (!enabled) return
            val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            if (isNight == lastNightMode) return
            lastNightMode = isNight
            scope.launch { applyForMode(isNight) }
        }

        override fun onLowMemory() {}
    }

    fun startListening() {
        // Track the pref so toggling the feature off cancels in-flight work cleanly.
        prefJob?.cancel()
        prefJob = scope.launch {
            prefs.darkModeAutoSwitch.distinctUntilChanged().collect { isOn ->
                enabled = isOn
                if (isOn) {
                    // Resync the baseline so we don't re-apply on the very next config change
                    // when the user enables the toggle while already in (say) dark mode.
                    lastNightMode = isNightMode()
                }
            }
        }
        // ComponentCallbacks registration is idempotent if we ever wire startListening
        // to be re-entrant; Application.registerComponentCallbacks tolerates a re-add
        // but we keep one registration for the singleton's life regardless.
        runCatching { context.applicationContext.registerComponentCallbacks(callbacks) }
    }

    /** Test/debug entrypoint — apply whichever wallpaper matches the requested mode. */
    suspend fun applyForMode(isNight: Boolean) {
        try {
            val wallpaperId = if (isNight) {
                prefs.darkModeWallpaperId.first()
            } else {
                prefs.lightModeWallpaperId.first()
            }
            if (wallpaperId.isBlank()) return
            applyStoredWallpaper(wallpaperId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Auto-apply must not crash the host app. The Settings UI surfaces the
            // current selection so the user can re-pick if a stored URL is no longer valid.
        }
    }

    private suspend fun applyStoredWallpaper(wallpaperId: String) {
        // Wallpaper ID format: "source|id|url" (stored when user applies a wallpaper).
        // Split with limit=3 so URLs that happen to contain "|" stay intact.
        val parts = wallpaperId.split("|", limit = 3)
        if (parts.size < 3) return
        val url = parts[2]
        if (url.isBlank()) return
        // applyByLocator handles http(s) URLs, file:// URIs (AI-generated / parallax-cached),
        // and content:// URIs (uploads / gallery picks). Earlier revisions called
        // applyFromUrl which only spoke HTTP and threw IllegalArgumentException for any
        // other scheme — silently breaking auto-switch for AI-generated wallpapers.
        wallpaperApplier.applyByLocator(url, WallpaperTarget.BOTH)
    }

    private fun isNightMode(): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}

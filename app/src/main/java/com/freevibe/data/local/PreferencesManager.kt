package com.freevibe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freevibe_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    // ── API Keys (optional, for higher rate limits) ────────────────

    val wallhavenApiKey: Flow<String> = get(Keys.WALLHAVEN_KEY, "")
    val pexelsApiKey: Flow<String> = get(Keys.PEXELS_KEY, "3AN2RtNJNs6cT4M04xUzN1EuojlmC9283l6l3yPKaYQ7ez0rcFLwvpHP")

    suspend fun setWallhavenKey(key: String) = set(Keys.WALLHAVEN_KEY, key)
    suspend fun setPexelsKey(key: String) = set(Keys.PEXELS_KEY, key)

    // ── Auto-wallpaper ────────────────────────────────────────────

    val autoWallpaperEnabled: Flow<Boolean> = get(Keys.AUTO_WP_ENABLED, false)
    val autoWallpaperInterval: Flow<Long> = get(Keys.AUTO_WP_INTERVAL, 12L)
    val autoWallpaperSource: Flow<String> = get(Keys.AUTO_WP_SOURCE, "wallhaven")
    val autoWallpaperTarget: Flow<String> = get(Keys.AUTO_WP_TARGET, "BOTH")

    suspend fun setAutoWallpaperEnabled(enabled: Boolean) = set(Keys.AUTO_WP_ENABLED, enabled)
    suspend fun setAutoWallpaperInterval(hours: Long) = set(Keys.AUTO_WP_INTERVAL, hours)
    suspend fun setAutoWallpaperSource(source: String) = set(Keys.AUTO_WP_SOURCE, source)
    suspend fun setAutoWallpaperTarget(target: String) = set(Keys.AUTO_WP_TARGET, target)

    // ── Sound settings ────────────────────────────────────────────

    val autoPreviewSounds: Flow<Boolean> = get(Keys.AUTO_PREVIEW, true)
    val soundPreviewVolume: Flow<Float> = get(Keys.PREVIEW_VOLUME, 0.7f)

    suspend fun setAutoPreview(enabled: Boolean) = set(Keys.AUTO_PREVIEW, enabled)
    suspend fun setPreviewVolume(volume: Float) = set(Keys.PREVIEW_VOLUME, volume)

    // ── Display settings ──────────────────────────────────────────

    val wallpaperGridColumns: Flow<Int> = get(Keys.GRID_COLUMNS, 2)
    val showNsfwContent: Flow<Boolean> = get(Keys.SHOW_NSFW, false)
    val preferredResolution: Flow<String> = get(Keys.PREF_RESOLUTION, "")

    suspend fun setGridColumns(columns: Int) = set(Keys.GRID_COLUMNS, columns)
    suspend fun setShowNsfw(show: Boolean) = set(Keys.SHOW_NSFW, show)
    suspend fun setPreferredResolution(res: String) = set(Keys.PREF_RESOLUTION, res)

    // ── Reddit settings ───────────────────────────────────────────

    val redditSubreddits: Flow<String> = get(Keys.REDDIT_SUBS, "wallpapers,Amoledbackgrounds,MobileWallpaper")

    suspend fun setRedditSubreddits(subs: String) = set(Keys.REDDIT_SUBS, subs)

    // ── Generic helpers ───────────────────────────────────────────

    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val WALLHAVEN_KEY = stringPreferencesKey("wallhaven_api_key")
        val PEXELS_KEY = stringPreferencesKey("pexels_api_key")
        val AUTO_WP_ENABLED = booleanPreferencesKey("auto_wp_enabled")
        val AUTO_WP_INTERVAL = longPreferencesKey("auto_wp_interval")
        val AUTO_WP_SOURCE = stringPreferencesKey("auto_wp_source")
        val AUTO_WP_TARGET = stringPreferencesKey("auto_wp_target")
        val AUTO_PREVIEW = booleanPreferencesKey("auto_preview")
        val PREVIEW_VOLUME = floatPreferencesKey("preview_volume")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val PREF_RESOLUTION = stringPreferencesKey("pref_resolution")
        val REDDIT_SUBS = stringPreferencesKey("reddit_subreddits")
    }
}

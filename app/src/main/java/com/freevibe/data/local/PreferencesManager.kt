package com.freevibe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freevibe_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        fun defaultRingtoneQuery(year: Int = Year.now().value): String =
            "best ringtone $year trending phone ringtone download"

        fun defaultNotificationQuery(): String =
            "short notification sound effect beep chime ding alert"

        fun defaultAlarmQuery(): String =
            "alarm clock tone buzzer morning wake up sound"

        fun defaultTopHitQueries(year: Int = Year.now().value): List<String> = listOf(
            "top songs this week $year ringtone",
            "billboard hot 100 this week ringtone $year",
            "most popular songs right now ringtone $year",
        )
    }

    private val dataStore = context.dataStore

    // ── API Keys (optional, for higher rate limits) ────────────────

    val wallhavenApiKey: Flow<String> = get(Keys.WALLHAVEN_KEY, "")
    val pexelsApiKey: Flow<String> = get(Keys.PEXELS_KEY, com.freevibe.BuildConfig.PEXELS_API_KEY)
    val pixabayApiKey: Flow<String> = get(Keys.PIXABAY_KEY, com.freevibe.BuildConfig.PIXABAY_API_KEY)
    val freesoundApiKey: Flow<String> = get(Keys.FREESOUND_KEY, com.freevibe.BuildConfig.FREESOUND_API_KEY)

    // Sanitize API keys: strip surrounding whitespace and reject any control chars (including CR/LF
    // which OkHttp would throw on when placed in a request header — prefer to drop them here with
    // a clean user-facing validation instead of a crash at request time).
    private fun sanitizeApiKey(key: String): String =
        key.trim().filter { it.code >= 0x20 && it.code != 0x7F }

    suspend fun setWallhavenKey(key: String) = set(Keys.WALLHAVEN_KEY, sanitizeApiKey(key))
    suspend fun setPexelsKey(key: String) = set(Keys.PEXELS_KEY, sanitizeApiKey(key))
    suspend fun setPixabayKey(key: String) = set(Keys.PIXABAY_KEY, sanitizeApiKey(key))
    suspend fun setFreesoundKey(key: String) = set(Keys.FREESOUND_KEY, sanitizeApiKey(key))

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

    val redditSubreddits: Flow<String> = get(Keys.REDDIT_SUBS, "wallpapers,MobileWallpaper,wallpaper,WQHD_Wallpaper,MinimalWallpaper,phonewallpapers,iWallpaper")
    val redditVideoSubreddits: Flow<String> = get(Keys.REDDIT_VIDEO_SUBS, "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops")

    suspend fun setRedditSubreddits(subs: String) = set(Keys.REDDIT_SUBS, subs)
    suspend fun setRedditVideoSubreddits(subs: String) = set(Keys.REDDIT_VIDEO_SUBS, subs)

    // ── YouTube sound search ──────────────────────────────────────

    val ytSoundQueryRingtones: Flow<String> = get(Keys.YT_SOUND_RINGTONES, defaultRingtoneQuery())
    val ytSoundQueryNotifications: Flow<String> = get(Keys.YT_SOUND_NOTIFICATIONS, defaultNotificationQuery())
    val ytSoundQueryAlarms: Flow<String> = get(Keys.YT_SOUND_ALARMS, defaultAlarmQuery())
    val ytSoundBlockedWords: Flow<String> = get(Keys.YT_SOUND_BLOCKED, "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode")

    suspend fun setYtSoundQueryRingtones(q: String) = set(Keys.YT_SOUND_RINGTONES, q)
    suspend fun setYtSoundQueryNotifications(q: String) = set(Keys.YT_SOUND_NOTIFICATIONS, q)
    suspend fun setYtSoundQueryAlarms(q: String) = set(Keys.YT_SOUND_ALARMS, q)
    suspend fun setYtSoundBlockedWords(words: String) = set(Keys.YT_SOUND_BLOCKED, words)

    // ── Wallpaper scheduler ─────────────────────────────────────

    val schedulerEnabled: Flow<Boolean> = get(Keys.SCHEDULER_ENABLED, false)
    val schedulerIntervalMinutes: Flow<Long> = get(Keys.SCHEDULER_INTERVAL, 360L) // 6hr default
    val schedulerSource: Flow<String> = get(Keys.SCHEDULER_SOURCE, "discover")
    val schedulerHomeEnabled: Flow<Boolean> = get(Keys.SCHEDULER_HOME, true)
    val schedulerLockEnabled: Flow<Boolean> = get(Keys.SCHEDULER_LOCK, true)
    val schedulerShuffle: Flow<Boolean> = get(Keys.SCHEDULER_SHUFFLE, true)
    val schedulerCollectionId: Flow<Long> = get(Keys.SCHEDULER_COLLECTION, -1L)
    val schedulerDaySource: Flow<String> = get(Keys.SCHEDULER_DAY_SOURCE, "")
    val schedulerNightSource: Flow<String> = get(Keys.SCHEDULER_NIGHT_SOURCE, "")

    suspend fun setSchedulerEnabled(enabled: Boolean) = set(Keys.SCHEDULER_ENABLED, enabled)
    suspend fun setSchedulerInterval(minutes: Long) = set(Keys.SCHEDULER_INTERVAL, minutes)
    suspend fun setSchedulerSource(source: String) = set(Keys.SCHEDULER_SOURCE, source)
    suspend fun setSchedulerHome(enabled: Boolean) = set(Keys.SCHEDULER_HOME, enabled)
    suspend fun setSchedulerLock(enabled: Boolean) = set(Keys.SCHEDULER_LOCK, enabled)
    suspend fun setSchedulerShuffle(shuffle: Boolean) = set(Keys.SCHEDULER_SHUFFLE, shuffle)
    suspend fun setSchedulerCollection(id: Long) = set(Keys.SCHEDULER_COLLECTION, id)
    suspend fun setSchedulerDaySource(source: String) = set(Keys.SCHEDULER_DAY_SOURCE, source)
    suspend fun setSchedulerNightSource(source: String) = set(Keys.SCHEDULER_NIGHT_SOURCE, source)

    // ── Video wallpaper settings ────────────────────────────────

    val videoFpsLimit: Flow<Int> = get(Keys.VIDEO_FPS_LIMIT, 30)
    val videoPlaybackSpeed: Flow<Float> = get(Keys.VIDEO_PLAYBACK_SPEED, 1.0f)

    suspend fun setVideoFpsLimit(fps: Int) {
        val sanitized = sanitizeVideoFpsLimit(fps)
        // Write SharedPreferences FIRST so VideoWallpaperService (which reads SP) always sees
        // the new value even if the suspending DataStore write gets cancelled mid-flight.
        // If cancellation happens between, DataStore catches up on the next successful write.
        context.getSharedPreferences("freevibe_prefs", Context.MODE_PRIVATE)
            .edit().putInt("video_fps_limit", sanitized).apply()
        set(Keys.VIDEO_FPS_LIMIT, sanitized)
    }
    suspend fun setVideoPlaybackSpeed(speed: Float) {
        // SharedPreferences first — same rationale as setVideoFpsLimit. WallpaperService
        // cannot easily subscribe to DataStore, so SP is the source of truth for the runtime.
        context.getSharedPreferences("freevibe_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("video_playback_speed", speed).apply()
        set(Keys.VIDEO_PLAYBACK_SPEED, speed)
    }

    // ── Effects / adaptive settings ─────────────────────────────

    val adaptiveTintEnabled: Flow<Boolean> = get(Keys.ADAPTIVE_TINT, false)
    val adaptiveTintIntensity: Flow<Float> = get(Keys.ADAPTIVE_TINT_INTENSITY, 0.3f)
    val weatherEffectsEnabled: Flow<Boolean> = get(Keys.WEATHER_EFFECTS, false)
    val darkModeAutoSwitch: Flow<Boolean> = get(Keys.DARK_MODE_SWITCH, false)
    val darkModeWallpaperId: Flow<String> = get(Keys.DARK_WALLPAPER_ID, "")
    val lightModeWallpaperId: Flow<String> = get(Keys.LIGHT_WALLPAPER_ID, "")

    suspend fun setAdaptiveTintEnabled(enabled: Boolean) = set(Keys.ADAPTIVE_TINT, enabled)
    suspend fun setAdaptiveTintIntensity(intensity: Float) = set(Keys.ADAPTIVE_TINT_INTENSITY, intensity)
    suspend fun setWeatherEffectsEnabled(enabled: Boolean) = set(Keys.WEATHER_EFFECTS, enabled)
    suspend fun setDarkModeAutoSwitch(enabled: Boolean) = set(Keys.DARK_MODE_SWITCH, enabled)
    suspend fun setDarkModeWallpaperId(id: String) = set(Keys.DARK_WALLPAPER_ID, id)
    suspend fun setLightModeWallpaperId(id: String) = set(Keys.LIGHT_WALLPAPER_ID, id)

    // ── Personalization ──────────────────────────────────────────

    val userStyles: Flow<String> = get(Keys.USER_STYLES, "")
    suspend fun setUserStyles(styles: String) = set(Keys.USER_STYLES, styles)

    // ── Generic helpers ───────────────────────────────────────────

    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.catch { emit(emptyPreferences()) }.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun sanitizeVideoFpsLimit(fps: Int): Int = when {
        fps <= 15 -> 15
        fps >= 60 -> 60
        else -> 30
    }

    private object Keys {
        val WALLHAVEN_KEY = stringPreferencesKey("wallhaven_api_key")
        val PEXELS_KEY = stringPreferencesKey("pexels_api_key")
        val PIXABAY_KEY = stringPreferencesKey("pixabay_api_key")
        val FREESOUND_KEY = stringPreferencesKey("freesound_api_key")
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
        val REDDIT_VIDEO_SUBS = stringPreferencesKey("reddit_video_subreddits")
        // YouTube sound search
        val YT_SOUND_RINGTONES = stringPreferencesKey("yt_sound_ringtones")
        val YT_SOUND_NOTIFICATIONS = stringPreferencesKey("yt_sound_notifications")
        val YT_SOUND_ALARMS = stringPreferencesKey("yt_sound_alarms")
        val YT_SOUND_BLOCKED = stringPreferencesKey("yt_sound_blocked")
        // Scheduler
        val SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val SCHEDULER_INTERVAL = longPreferencesKey("scheduler_interval_min")
        val SCHEDULER_SOURCE = stringPreferencesKey("scheduler_source")
        val SCHEDULER_HOME = booleanPreferencesKey("scheduler_home")
        val SCHEDULER_LOCK = booleanPreferencesKey("scheduler_lock")
        val SCHEDULER_SHUFFLE = booleanPreferencesKey("scheduler_shuffle")
        val SCHEDULER_COLLECTION = longPreferencesKey("scheduler_collection_id")
        val SCHEDULER_DAY_SOURCE = stringPreferencesKey("scheduler_day_source")
        val SCHEDULER_NIGHT_SOURCE = stringPreferencesKey("scheduler_night_source")
        // Video wallpaper
        val VIDEO_FPS_LIMIT = intPreferencesKey("video_fps_limit")
        val VIDEO_PLAYBACK_SPEED = floatPreferencesKey("video_playback_speed")
        // Effects / adaptive
        val ADAPTIVE_TINT = booleanPreferencesKey("adaptive_tint_enabled")
        val ADAPTIVE_TINT_INTENSITY = floatPreferencesKey("adaptive_tint_intensity")
        val WEATHER_EFFECTS = booleanPreferencesKey("weather_effects_enabled")
        val DARK_MODE_SWITCH = booleanPreferencesKey("dark_mode_auto_switch")
        val DARK_WALLPAPER_ID = stringPreferencesKey("dark_mode_wallpaper_id")
        val LIGHT_WALLPAPER_ID = stringPreferencesKey("light_mode_wallpaper_id")
        val USER_STYLES = stringPreferencesKey("user_styles")
    }
}

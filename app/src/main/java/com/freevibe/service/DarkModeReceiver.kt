package com.freevibe.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Listens for system UI mode changes (dark/light) and applies the
 * corresponding wallpaper stored in preferences.
 *
 * Registered dynamically by FreeVibeApp when dark mode auto-switch is enabled.
 */
class DarkModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_CONFIGURATION_CHANGED) return

        val prefs = context.getSharedPreferences("freevibe_dark_mode", Context.MODE_PRIVATE)
        val darkUrl = prefs.getString("dark_wallpaper_url", null)
        val lightUrl = prefs.getString("light_wallpaper_url", null)

        if (darkUrl.isNullOrEmpty() && lightUrl.isNullOrEmpty()) return

        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        // Skip if mode hasn't actually changed (avoids re-downloading on screen rotation, etc.)
        val lastMode = prefs.getBoolean("last_was_dark", false)
        if (isDark == lastMode) return
        prefs.edit().putBoolean("last_was_dark", isDark).apply()

        val url = if (isDark) darkUrl else lightUrl
        if (url.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sharedClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val bytes = resp.body?.bytes()
                    if (bytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            try {
                                val wm = WallpaperManager.getInstance(context)
                                wm.setBitmap(bitmap, null, true,
                                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val sharedClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

package com.freevibe

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.freevibe.data.local.IAAudioCacheDao
import com.freevibe.data.local.WallpaperCacheManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FreeVibeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var wallpaperCacheManager: WallpaperCacheManager

    @Inject
    lateinit var iaAudioCacheDao: IAAudioCacheDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        evictStaleCaches()
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = File(filesDir, "crash.log")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                logFile.appendText("--- Crash at $timestamp on thread ${thread.name} ---\n$sw\n")
                // Keep log file reasonable (max 500KB)
                if (logFile.length() > 512_000) {
                    val trimmed = logFile.readText().takeLast(256_000)
                    logFile.writeText(trimmed)
                }
            } catch (_: Exception) {
                // Don't let crash logging itself crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun evictStaleCaches() {
        appScope.launch {
            try {
                wallpaperCacheManager.evictExpired()
                // IA audio cache: evict entries older than 7 days
                val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                iaAudioCacheDao.evictOlderThan(sevenDaysAgo)
            } catch (e: Exception) {
                Log.w("FreeVibeApp", "Cache eviction failed", e)
            }
        }
    }
}

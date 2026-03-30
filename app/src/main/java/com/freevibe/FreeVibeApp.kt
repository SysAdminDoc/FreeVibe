package com.freevibe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        createMediaNotificationChannel()
        evictStaleCaches()
        initYtDlp()
    }

    private fun initYtDlp() {
        appScope.launch {
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this@FreeVibeApp)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(this@FreeVibeApp)
                Log.d("AuraApp", "yt-dlp + FFmpeg initialized")
            } catch (e: Throwable) {
                Log.e("AuraApp", "yt-dlp init failed: ${e.message}")
            }
        }
    }

    private fun createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_playback",
                "Sound Preview",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "Playback controls for sound previews"
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
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
                // Keep log file reasonable (max 500KB) — trim tail without full read
                if (logFile.length() > 512_000) {
                    try {
                        val raf = java.io.RandomAccessFile(logFile, "r")
                        val keepBytes = 256_000
                        val skipTo = raf.length() - keepBytes
                        raf.seek(skipTo.coerceAtLeast(0))
                        val tail = ByteArray(keepBytes.coerceAtMost(raf.length().toInt()))
                        raf.readFully(tail)
                        raf.close()
                        logFile.writeBytes(tail)
                    } catch (_: Exception) {}
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
            } catch (e: Exception) {
                Log.w("FreeVibeApp", "Cache eviction failed", e)
            }
        }
    }
}

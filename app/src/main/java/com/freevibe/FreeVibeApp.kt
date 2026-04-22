package com.freevibe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.OfflineFavoritesManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FreeVibeApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var wallpaperCacheManager: WallpaperCacheManager

    @Inject
    lateinit var offlineFavoritesManager: OfflineFavoritesManager

    @Inject
    lateinit var communityIdentityProvider: CommunityIdentityProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25) // 25% of available app memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "coil_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256 MB — wallpaper app needs generous image cache
                .build()
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        createMediaNotificationChannel()
        evictStaleCaches()
        warmCommunityIdentity()
        initYtDlp()
    }

    private fun initYtDlp() {
        appScope.launch {
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this@FreeVibeApp)
                com.yausername.ffmpeg.FFmpeg.getInstance().init(this@FreeVibeApp)
                if (BuildConfig.DEBUG) Log.d("AuraApp", "yt-dlp + FFmpeg initialized")
            } catch (e: Throwable) {
                // Must catch CancellationException-as-Throwable here? Throwable catches it, but
                // we never cancel appScope before process death, so swallowing is OK. We do NOT
                // rethrow: init-failure of yt-dlp should degrade the YouTube tab, not kill the app.
                if (BuildConfig.DEBUG) Log.e("AuraApp", "yt-dlp init failed: ${e.message}")
            }
        }
    }

    private fun createMediaNotificationChannel() {
        val channel = NotificationChannel(
            "media_playback",
            "Sound Preview",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Playback controls for sound previews"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
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
                        java.io.RandomAccessFile(logFile, "r").use { raf ->
                            val keepBytes = 256_000
                            val skipTo = raf.length() - keepBytes
                            raf.seek(skipTo.coerceAtLeast(0))
                            val tail = ByteArray(keepBytes.coerceAtMost(raf.length().toInt()))
                            raf.readFully(tail)
                            logFile.writeBytes(tail)
                        }
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
                offlineFavoritesManager.pruneOrphans()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "Cache eviction failed", e)
            }
        }
    }

    private fun warmCommunityIdentity() {
        appScope.launch {
            try {
                communityIdentityProvider.ensureSignedIn()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Firebase auth can fail at launch (airplane mode, cold-start network race, etc).
                // Swallow + log — community features degrade gracefully when unsigned; this must
                // not crash the app at startup or it'll hit the uncaught-handler we just wired up.
                if (BuildConfig.DEBUG) Log.w("FreeVibeApp", "Community warm-up failed", e)
            }
        }
    }
}

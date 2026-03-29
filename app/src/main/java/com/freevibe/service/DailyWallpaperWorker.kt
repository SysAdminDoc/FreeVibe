package com.freevibe.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.MainActivity
import com.freevibe.R
import com.freevibe.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Daily wallpaper notification — shows a curated "Wallpaper of the Day"
 * from Wallhaven's daily top-rated list with a preview thumbnail.
 */
@HiltWorker
class DailyWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepo: WallpaperRepository,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = wallpaperRepo.getWallhaven(page = 1)
            val wallpaper = result.items.firstOrNull() ?: return Result.retry()

            // Download thumbnail for notification
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(wallpaper.thumbnailUrl).build()
                    okHttpClient.newCall(request).execute().use { resp ->
                        resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                } catch (_: Exception) { null }
            }

            createNotificationChannel()

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Wallpaper of the Day")
                .setContentText("${wallpaper.width}x${wallpaper.height} from ${wallpaper.source.name.lowercase()}")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply {
                    bitmap?.let {
                        setLargeIcon(it)
                        setStyle(NotificationCompat.BigPictureStyle().bigPicture(it))
                    }
                }
                .build()

            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Daily Wallpaper",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Daily wallpaper recommendation" }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "daily_wallpaper"
        const val CHANNEL_ID = "daily_wallpaper"
        const val NOTIFICATION_ID = 42

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(
                24, TimeUnit.HOURS,
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(8, TimeUnit.HOURS) // First notification ~8 hours after enable
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

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
import com.freevibe.data.remote.reddit.RedditApi
import com.freevibe.data.remote.toWallpaper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Daily wallpaper notification — shows Reddit's most upvoted wallpaper of the day.
 * Checks r/wallpapers + r/MobileWallpaper sorted by top/day,
 * picks the single highest-upvoted image post. Upvote count provides a real
 * crowd-sourced quality metric.
 */
@HiltWorker
class DailyWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val redditApi: RedditApi,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Fetch today's top image posts from multiple wallpaper subreddits
            val subreddits = listOf("wallpapers", "MobileWallpaper")
            val topPost = subreddits.flatMap { sub ->
                try {
                    redditApi.getSubredditPosts(
                        subreddit = sub,
                        sort = "top",
                        timeRange = "day",
                        limit = 5,
                    ).data.children.map { it.data }
                } catch (_: Exception) { emptyList() }
            }
                .filter { it.isImage && !it.over18 }
                .maxByOrNull { it.ups }
                ?: return Result.retry()

            val wallpaper = topPost.toWallpaper()

            // Download thumbnail for notification
            val thumbUrl = topPost.thumbUrl.takeIf { it.startsWith("http") }
                ?: wallpaper.thumbnailUrl
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    okHttpClient.newCall(Request.Builder().url(thumbUrl).build()).execute().use { resp ->
                        resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                } catch (_: Exception) { null }
            }

            try {
                createNotificationChannel()

                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return Result.success()
                }

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("daily_wallpaper_id", wallpaper.id)
                    putExtra("daily_wallpaper_url", wallpaper.fullUrl)
                    putExtra("daily_wallpaper_thumb", wallpaper.thumbnailUrl)
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val upvotes = formatUpvotes(topPost.ups)
                val res = topPost.parsedResolution
                val resText = if (res != null) "${res.first}x${res.second} " else ""
                val subName = "r/${topPost.subreddit}"

                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Wallpaper of the Day")
                    .setContentText("$upvotes upvotes on $subName ${resText}- tap to preview")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .apply {
                        bitmap?.let {
                            setLargeIcon(it)
                            setStyle(NotificationCompat.BigPictureStyle()
                                .bigPicture(it)
                                .setSummaryText("$upvotes upvotes on $subName"))
                        }
                    }
                    .build()

                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            } finally {
                bitmap?.recycle()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun formatUpvotes(ups: Int): String = when {
        ups >= 10_000 -> String.format(java.util.Locale.US, "%.1fk", ups / 1000f)
        ups >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", ups / 1000f)
        else -> "$ups"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Daily Wallpaper",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Today's most upvoted wallpaper from Reddit" }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
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
                .setInitialDelay(8, TimeUnit.HOURS)
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

package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.NasaRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
    private val nasaRepo: NasaRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val historyManager: WallpaperHistoryManager,
    private val prefs: PreferencesManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // #10: Read preferred source and target from settings
            val source = prefs.autoWallpaperSource.first()
            val targetStr = prefs.autoWallpaperTarget.first()
            val target = WallpaperTarget.entries.find { it.name == targetStr }
                ?: WallpaperTarget.BOTH

            val result = when (source) {
                "wallhaven" -> wallpaperRepo.getWallhaven(page = 1)
                "unsplash" -> wallpaperRepo.getPicsum(page = 1)
                "bing" -> wallpaperRepo.getBingDaily(page = 1)
                "wikimedia" -> wallpaperRepo.getWikimedia(page = 1)
                "reddit" -> redditRepo.getMultiSubreddit()
                "nasa" -> nasaRepo.getRandom(count = 10)
                "discover" -> wallpaperRepo.getDiscover(page = 1)
                else -> wallpaperRepo.getWallhaven(page = 1)
            }

            val wallpaper = result.items.randomOrNull()
                ?: return Result.retry()

            wallpaperApplier.applyFromUrl(wallpaper.fullUrl, target)
                .fold(
                    onSuccess = {
                        // #11: Record in history
                        historyManager.record(wallpaper, target)
                        Result.success()
                    },
                    onFailure = { Result.retry() },
                )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_wallpaper"

        fun schedule(context: Context, intervalHours: Long = 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoWallpaperWorker>(
                intervalHours, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

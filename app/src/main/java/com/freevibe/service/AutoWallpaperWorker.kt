package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
    private val favoritesRepo: FavoritesRepository,
    private val collectionRepo: CollectionRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val historyManager: WallpaperHistoryManager,
    private val prefs: PreferencesManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val schedulerEnabled = prefs.schedulerEnabled.first()
            val legacyEnabled = prefs.autoWallpaperEnabled.first()

            if (schedulerEnabled) {
                doSchedulerWork()
            } else if (legacyEnabled) {
                doLegacyWork()
            } else {
                Result.success()
            }
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure()
        }
    }

    /** Enhanced scheduler with separate home/lock, collections, day/night */
    private suspend fun doSchedulerWork(): Result {
        val homeEnabled = prefs.schedulerHomeEnabled.first()
        val lockEnabled = prefs.schedulerLockEnabled.first()
        val shuffle = prefs.schedulerShuffle.first()

        // Determine source based on time-of-day if day/night sources are configured
        val daySource = prefs.schedulerDaySource.first()
        val nightSource = prefs.schedulerNightSource.first()
        val source = if (daySource.isNotEmpty() && nightSource.isNotEmpty()) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour in 6..17) daySource else nightSource
        } else {
            prefs.schedulerSource.first()
        }

        val wallpapers = fetchWallpapers(source)
        if (wallpapers.isEmpty()) return Result.retry()

        val pick = if (shuffle) wallpapers.random() else wallpapers.first()

        if (homeEnabled && lockEnabled) {
            applyAndRecord(pick, WallpaperTarget.BOTH)
        } else {
            if (homeEnabled) applyAndRecord(pick, WallpaperTarget.HOME)
            if (lockEnabled) {
                // Pick a different wallpaper for lock if available
                val others = wallpapers.filter { it.id != pick.id }
                val lockPick = if (others.isNotEmpty()) others.random() else pick
                applyAndRecord(lockPick, WallpaperTarget.LOCK)
            }
        }
        return Result.success()
    }

    /** Legacy auto-wallpaper (backward compatible) */
    private suspend fun doLegacyWork(): Result {
        val source = prefs.autoWallpaperSource.first()
        val targetStr = prefs.autoWallpaperTarget.first()
        val target = WallpaperTarget.entries.find { it.name == targetStr } ?: WallpaperTarget.BOTH

        val wallpapers = fetchWallpapers(source)
        val wallpaper = wallpapers.randomOrNull() ?: return Result.retry()

        return applyAndRecord(wallpaper, target)
    }

    private suspend fun fetchWallpapers(source: String): List<Wallpaper> {
        val collectionId = prefs.schedulerCollectionId.first()
        return when (source) {
            "collection" -> {
                if (collectionId > 0) {
                    collectionRepo.getItems(collectionId).first().map {
                        Wallpaper(
                            id = it.wallpaperId,
                            source = try { com.freevibe.data.model.ContentSource.valueOf(it.source) }
                                catch (_: Exception) { com.freevibe.data.model.ContentSource.WALLHAVEN },
                            thumbnailUrl = it.thumbnailUrl,
                            fullUrl = it.fullUrl,
                            width = it.width,
                            height = it.height,
                        )
                    }
                } else emptyList()
            }
            "favorites" -> favoritesRepo.getWallpapers().first().map { it.toWallpaper() }
            "wallhaven" -> wallpaperRepo.getWallhaven(page = (1..5).random()).items
            "unsplash" -> wallpaperRepo.getDiscover(page = (1..3).random()).items
            "bing" -> wallpaperRepo.getBingDaily(page = 1).items
            "reddit" -> redditRepo.getMultiSubreddit().items
            "pixabay" -> wallpaperRepo.getPixabay(page = (1..5).random()).items
            "discover" -> wallpaperRepo.getDiscover(page = (1..3).random()).items
            else -> wallpaperRepo.getDiscover(page = 1).items
        }
    }

    private suspend fun applyAndRecord(wallpaper: Wallpaper, target: WallpaperTarget): Result {
        return wallpaperApplier.applyFromUrl(wallpaper.fullUrl, target).fold(
            onSuccess = {
                historyManager.record(wallpaper, target)
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val WORK_NAME = "auto_wallpaper"

        /** Schedule with minute-based intervals (minimum 15 min due to WorkManager) */
        fun schedule(context: Context, intervalMinutes: Long = 360) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoWallpaperWorker>(
                intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Legacy schedule for backward compatibility */
        fun scheduleHours(context: Context, intervalHours: Long = 12) {
            schedule(context, intervalHours * 60)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

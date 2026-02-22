package com.freevibe.service

import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #11: Tracks wallpaper application history so users can go back
 * to previously applied wallpapers.
 */
@Singleton
class WallpaperHistoryManager @Inject constructor(
    private val dao: WallpaperHistoryDao,
) {
    /** Get recent wallpaper history as a Flow */
    fun getRecent(limit: Int = 50): Flow<List<WallpaperHistoryEntity>> =
        dao.getRecent(limit)

    /** Record a wallpaper application */
    suspend fun record(wallpaper: Wallpaper, target: WallpaperTarget) {
        dao.insert(
            WallpaperHistoryEntity(
                wallpaperId = wallpaper.id,
                source = wallpaper.source.name,
                thumbnailUrl = wallpaper.thumbnailUrl,
                fullUrl = wallpaper.fullUrl,
                width = wallpaper.width,
                height = wallpaper.height,
                target = target.name,
            )
        )
        dao.pruneOld() // Keep only 100 most recent
    }

    suspend fun clearAll() = dao.clearAll()
}

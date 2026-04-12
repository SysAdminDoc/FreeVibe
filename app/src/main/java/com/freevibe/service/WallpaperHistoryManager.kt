package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #11: Tracks wallpaper application history so users can go back
 * to previously applied wallpapers.
 */
@Singleton
class WallpaperHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: WallpaperHistoryDao,
) {
    /** Get recent wallpaper history as a Flow */
    fun getRecent(limit: Int = 50): Flow<List<WallpaperHistoryEntity>> =
        dao.getRecent(limit)

    /** The most recently applied wallpaper, or null if history is empty. */
    fun mostRecent(): Flow<WallpaperHistoryEntity?> =
        dao.getRecent(limit = 1).map { it.firstOrNull() }

    /**
     * The second-most-recently-applied wallpaper — i.e. the wallpaper that was active
     * BEFORE the current one. Used as the target for the "Undo last apply" action.
     */
    fun secondMostRecent(): Flow<WallpaperHistoryEntity?> =
        dao.getRecent(limit = 2).map { it.getOrNull(1) }

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
        // Refresh the home-screen widget so its background thumbnail reflects the new
        // "current" wallpaper. Swallow errors — the widget is a nice-to-have, a failure
        // here shouldn't surface to the apply path.
        try {
            with(com.freevibe.widget.FreeVibeWidget()) {
                androidx.glance.appwidget.GlanceAppWidgetManager(context)
                    .getGlanceIds(com.freevibe.widget.FreeVibeWidget::class.java)
                    .forEach { id -> update(context, id) }
            }
        } catch (_: Throwable) {}
    }

    /**
     * One-shot snapshot of the previous wallpaper (entry index 1). Used by the Undo
     * action at the moment the snackbar is tapped — we don't want a Flow subscription
     * here, we want exactly the entry that was second-most-recent when Undo was posted.
     */
    suspend fun previousSnapshot(): WallpaperHistoryEntity? =
        dao.getRecentSnapshot(2).getOrNull(1)

    suspend fun clearAll() = dao.clearAll()
}

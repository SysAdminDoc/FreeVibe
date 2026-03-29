package com.freevibe.data.local

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperCacheManager @Inject constructor(
    private val cacheDao: WallpaperCacheDao,
) {
    companion object {
        const val TTL_DEFAULT = 6 * 60 * 60 * 1000L    // 6 hours
        const val TTL_REDDIT = 2 * 60 * 60 * 1000L     // 2 hours (fast-changing)
        const val TTL_PICSUM = 24 * 60 * 60 * 1000L    // 24 hours (static catalog)
        const val TTL_BING = 4 * 60 * 60 * 1000L       // 4 hours (daily rotation)
    }

    /** Get cached wallpapers if fresh enough */
    suspend fun getCached(cacheKey: String, source: ContentSource): List<Wallpaper>? {
        val timestamp = cacheDao.getCacheTimestamp(cacheKey) ?: return null
        val ttl = getTtl(source)
        if (System.currentTimeMillis() - timestamp > ttl) {
            cacheDao.evictByKey(cacheKey)
            return null
        }
        return cacheDao.getByCacheKey(cacheKey).map { it.toWallpaper() }
    }

    /** Get cached wallpapers even if expired (offline fallback) */
    suspend fun getStaleCached(cacheKey: String): List<Wallpaper>? {
        val entries = cacheDao.getByCacheKey(cacheKey)
        return if (entries.isNotEmpty()) entries.map { it.toWallpaper() } else null
    }

    /** Cache wallpapers with key */
    suspend fun cache(cacheKey: String, wallpapers: List<Wallpaper>) {
        cacheDao.evictByKey(cacheKey)
        val now = System.currentTimeMillis()
        cacheDao.insertAll(wallpapers.map { it.toCacheEntity(cacheKey, now) })
    }

    /** Remove all expired entries (uses longest TTL — per-source freshness is checked in getCached) */
    suspend fun evictExpired() {
        val oldest = System.currentTimeMillis() - TTL_PICSUM
        cacheDao.evictOlderThan(oldest)
    }

    /** Full cache clear */
    suspend fun clearAll() = cacheDao.clearAll()

    private fun getTtl(source: ContentSource): Long = when (source) {
        ContentSource.PICSUM -> TTL_PICSUM
        ContentSource.BING -> TTL_BING
        ContentSource.REDDIT -> TTL_REDDIT
        else -> TTL_DEFAULT
    }

    private fun WallpaperCacheEntity.toWallpaper() = Wallpaper(
        id = id,
        source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        category = category,
        tags = if (tags.isBlank()) emptyList() else tags.split(","),
        fileSize = fileSize,
        fileType = fileType,
        uploaderName = uploaderName,
    )

    private fun Wallpaper.toCacheEntity(cacheKey: String, timestamp: Long) = WallpaperCacheEntity(
        id = id,
        source = source.name,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        category = category,
        tags = tags.joinToString(","),
        fileSize = fileSize,
        fileType = fileType,
        uploaderName = uploaderName,
        cacheKey = cacheKey,
        cachedAt = timestamp,
    )
}

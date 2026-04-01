package com.freevibe.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// -- Unified content types --

enum class ContentSource { WALLHAVEN, PICSUM, BING, WIKIMEDIA, INTERNET_ARCHIVE, REDDIT, NASA, FREESOUND, JAMENDO, AUDIUS, CCMIXTER, LOCAL, YOUTUBE, PEXELS, PIXABAY, KLIPY, SOUNDCLOUD, COMMUNITY, BUNDLED }
enum class ContentType { WALLPAPER, LIVE_WALLPAPER, RINGTONE, NOTIFICATION, ALARM }
enum class WallpaperTarget { HOME, LOCK, BOTH }

// -- Wallpaper --

data class Wallpaper(
    val id: String,
    val source: ContentSource,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val fileSize: Long = 0,
    val fileType: String = "",
    val sourcePageUrl: String = "",
    val uploaderName: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
)

// -- Sound --

data class Sound(
    val id: String,
    val source: ContentSource,
    val name: String,
    val description: String = "",
    val previewUrl: String,
    val downloadUrl: String,
    val duration: Double = 0.0,
    val sampleRate: Int = 0,
    val fileType: String = "",
    val fileSize: Long = 0,
    val tags: List<String> = emptyList(),
    val license: String = "",
    val uploaderName: String = "",
    val sourcePageUrl: String = "",
)

// -- Favorites (Room entity) --

@Entity(tableName = "favorites", indices = [Index("type"), Index("addedAt"), Index("type", "addedAt")])
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,           // WALLPAPER or SOUND
    val thumbnailUrl: String,
    val fullUrl: String,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis(),
    val offlinePath: String = "",   // #3: local file path for offline access
    val tags: String? = null,               // comma-separated
    val colors: String? = null,             // comma-separated
    val category: String? = null,
    val uploaderName: String? = null,
    val sourcePageUrl: String? = null,
    val fileSize: Long? = null,
    val fileType: String? = null,
    val views: Long? = null,
    val favoritesCount: Long? = null,
)

// -- Download history (Room entity) --

@Entity(tableName = "downloads", indices = [Index("type"), Index("downloadedAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,
    val localPath: String,
    val name: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
)

// -- Search results wrapper --

data class SearchResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val currentPage: Int,
    val hasMore: Boolean,
)

// -- Search history (Room entity) --

@Entity(
    tableName = "search_history",
    primaryKeys = ["query", "type"],
)
data class SearchHistoryEntity(
    val query: String,
    val type: String,           // WALLPAPER or SOUND
    val timestamp: Long = System.currentTimeMillis(),
)

// -- Cached content (Room entity) --

@Entity(
    tableName = "wallpaper_cache",
    primaryKeys = ["id", "cacheKey"],
    indices = [Index("cacheKey")],
)
data class WallpaperCacheEntity(
    val id: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val category: String = "",
    val tags: String = "",
    val fileSize: Long = 0,
    val fileType: String = "",
    val uploaderName: String = "",
    val colors: String = "",
    val sourcePageUrl: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    val cacheKey: String = "",       // e.g. "wallhaven_1", "search_nature_1"
    val cachedAt: Long = System.currentTimeMillis(),
)

// -- Wallpaper history (#11) --

@Entity(tableName = "wallpaper_history", indices = [Index("appliedAt")])
data class WallpaperHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val target: String = "BOTH",       // HOME, LOCK, BOTH
    val appliedAt: Long = System.currentTimeMillis(),
)

// -- Wallpaper collections --

@Entity(tableName = "wallpaper_collections")
data class WallpaperCollectionEntity(
    @PrimaryKey(autoGenerate = true) val collectionId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "wallpaper_collection_items",
    primaryKeys = ["collectionId", "wallpaperId"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WallpaperCollectionEntity::class,
            parentColumns = ["collectionId"],
            childColumns = ["collectionId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index("collectionId")],
)
data class WallpaperCollectionItemEntity(
    val collectionId: Long,
    val wallpaperId: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val source: String,
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)

// -- Dual wallpaper pair --

data class WallpaperPair(
    val home: Wallpaper,
    val lock: Wallpaper,
    val name: String = "",
)

package com.freevibe.data.local

import androidx.room.*
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.WallpaperCacheEntity
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.freevibe.data.model.WallpaperHistoryEntity
import kotlinx.coroutines.flow.Flow

// -- Database --

@Database(
    entities = [
        FavoriteEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class,
        WallpaperCacheEntity::class,
        WallpaperHistoryEntity::class,
        WallpaperCollectionEntity::class,
        WallpaperCollectionItemEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class FreeVibeDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun wallpaperCacheDao(): WallpaperCacheDao
    abstract fun wallpaperHistoryDao(): WallpaperHistoryDao
    abstract fun collectionDao(): CollectionDao
}

// -- Favorite DAO --

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    fun getByType(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id AND source = :source AND type = :type)")
    fun isFavorite(id: String, source: String, type: String): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE id = :id AND source = :source AND type = :type LIMIT 1")
    suspend fun getByIdentity(id: String, source: String, type: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id ORDER BY addedAt DESC LIMIT 1")
    suspend fun getLatestById(id: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id AND type = :type ORDER BY addedAt DESC LIMIT 1")
    suspend fun getLatestByIdAndType(id: String, type: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND source = :source AND type = :type")
    suspend fun deleteByIdentity(id: String, source: String, type: String)

    @Query("UPDATE favorites SET offlinePath = :path WHERE id = :id AND source = :source AND type = :type")
    suspend fun updateOfflinePath(id: String, source: String, type: String, path: String)

    @Query("SELECT id, source, type FROM favorites")
    fun allIdentities(): Flow<List<FavoriteIdentity>>

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Flow<Int>
}

// -- Download DAO --

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE type = :type ORDER BY downloadedAt DESC")
    fun getByType(type: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE type = :type AND (id = :legacyId OR id = :scopedId) ORDER BY downloadedAt DESC")
    suspend fun findMatching(type: String, legacyId: String, scopedId: String): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}

// -- Search History DAO --

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(type: String, limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE type = :type AND query LIKE '%' || :prefix || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(type: String, prefix: String, limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query AND type = :type")
    suspend fun delete(query: String, type: String)

    @Query("DELETE FROM search_history WHERE type = :type")
    suspend fun clearAll(type: String)

    @Query("SELECT COUNT(*) FROM search_history WHERE type = :type")
    suspend fun count(type: String): Int
}

// -- Wallpaper Cache DAO --

@Dao
interface WallpaperCacheDao {

    @Query("SELECT * FROM wallpaper_cache WHERE cacheKey = :cacheKey ORDER BY rowid ASC")
    suspend fun getByCacheKey(cacheKey: String): List<WallpaperCacheEntity>

    @Query("SELECT * FROM wallpaper_cache WHERE rowid IN (SELECT MAX(rowid) FROM wallpaper_cache WHERE id IN (:ids) GROUP BY id, source)")
    suspend fun getByIds(ids: List<String>): List<WallpaperCacheEntity>

    @Query("SELECT MAX(cachedAt) FROM wallpaper_cache WHERE cacheKey = :cacheKey")
    suspend fun getCacheTimestamp(cacheKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WallpaperCacheEntity>)

    @Query("DELETE FROM wallpaper_cache WHERE cacheKey = :cacheKey")
    suspend fun evictByKey(cacheKey: String)

    @Query("DELETE FROM wallpaper_cache WHERE cachedAt < :threshold")
    suspend fun evictOlderThan(threshold: Long)

    @Query("DELETE FROM wallpaper_cache WHERE rowid NOT IN (SELECT rowid FROM wallpaper_cache ORDER BY cachedAt DESC LIMIT :maxEntries)")
    suspend fun pruneToMaxEntries(maxEntries: Int)

    @Query("DELETE FROM wallpaper_cache WHERE cacheKey = :cacheKey AND rowid NOT IN (SELECT rowid FROM wallpaper_cache WHERE cacheKey = :cacheKey ORDER BY cachedAt DESC LIMIT :maxEntries)")
    suspend fun pruneCacheKey(cacheKey: String, maxEntries: Int)

    @Query("DELETE FROM wallpaper_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM wallpaper_cache")
    suspend fun countEntries(): Int
}

// -- Wallpaper History DAO (#11) --

@Dao
interface WallpaperHistoryDao {

    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WallpaperHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WallpaperHistoryEntity)

    @Query("DELETE FROM wallpaper_history WHERE historyId NOT IN (SELECT historyId FROM wallpaper_history ORDER BY appliedAt DESC LIMIT 100)")
    suspend fun pruneOld()

    @Query("DELETE FROM wallpaper_history")
    suspend fun clearAll()
}

// -- Wallpaper Collections DAO --

@Dao
interface CollectionDao {

    @Query("SELECT * FROM wallpaper_collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<WallpaperCollectionEntity>>

    @Query("SELECT * FROM wallpaper_collection_items WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun getCollectionItems(collectionId: Long): Flow<List<WallpaperCollectionItemEntity>>

    @Query("SELECT COUNT(*) FROM wallpaper_collection_items WHERE collectionId = :collectionId")
    fun getItemCount(collectionId: Long): Flow<Int>

    @Query("SELECT thumbnailUrl FROM wallpaper_collection_items WHERE collectionId = :collectionId ORDER BY addedAt DESC LIMIT 4")
    fun getCoverThumbnails(collectionId: Long): Flow<List<String>>

    @Insert
    suspend fun createCollection(collection: WallpaperCollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: WallpaperCollectionItemEntity)

    @Query("DELETE FROM wallpaper_collection_items WHERE collectionId = :collectionId AND wallpaperId = :wallpaperId AND source = :source")
    suspend fun removeItem(collectionId: Long, wallpaperId: String, source: String)

    @Query("DELETE FROM wallpaper_collections WHERE collectionId = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Query("DELETE FROM wallpaper_collection_items WHERE collectionId = :collectionId")
    suspend fun deleteCollectionItems(collectionId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM wallpaper_collection_items WHERE collectionId = :collectionId AND wallpaperId = :wallpaperId AND source = :source)")
    suspend fun isInCollection(collectionId: Long, wallpaperId: String, source: String): Boolean

    @Query("UPDATE wallpaper_collections SET name = :name WHERE collectionId = :collectionId")
    suspend fun renameCollection(collectionId: Long, name: String)
}

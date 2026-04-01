package com.freevibe.data.repository

import com.freevibe.data.local.CollectionDao
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val dao: CollectionDao,
) {
    fun getAll(): Flow<List<WallpaperCollectionEntity>> = dao.getAllCollections()

    fun getItems(collectionId: Long): Flow<List<WallpaperCollectionItemEntity>> =
        dao.getCollectionItems(collectionId)

    fun getItemCount(collectionId: Long): Flow<Int> = dao.getItemCount(collectionId)

    fun getCoverThumbnails(collectionId: Long): Flow<List<String>> =
        dao.getCoverThumbnails(collectionId)

    suspend fun create(name: String): Long =
        dao.createCollection(WallpaperCollectionEntity(name = name))

    suspend fun addWallpaper(collectionId: Long, wallpaper: Wallpaper) {
        dao.addItem(
            WallpaperCollectionItemEntity(
                collectionId = collectionId,
                wallpaperId = wallpaper.id,
                thumbnailUrl = wallpaper.thumbnailUrl,
                fullUrl = wallpaper.fullUrl,
                source = wallpaper.source.name,
                width = wallpaper.width,
                height = wallpaper.height,
            )
        )
    }

    suspend fun removeWallpaper(collectionId: Long, wallpaperId: String) =
        dao.removeItem(collectionId, wallpaperId)

    suspend fun delete(collectionId: Long) {
        dao.deleteCollectionItems(collectionId) // Explicit delete (PRAGMA foreign_keys is per-connection)
        dao.deleteCollection(collectionId)
    }

    suspend fun rename(collectionId: Long, name: String) =
        dao.renameCollection(collectionId, name)

    suspend fun isInCollection(collectionId: Long, wallpaperId: String): Boolean =
        dao.isInCollection(collectionId, wallpaperId)
}

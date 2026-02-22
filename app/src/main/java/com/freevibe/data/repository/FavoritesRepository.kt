package com.freevibe.data.repository

import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
) {
    fun getAll(): Flow<List<FavoriteEntity>> = dao.getAll()
    fun getWallpapers(): Flow<List<FavoriteEntity>> = dao.getByType("WALLPAPER")
    fun getSounds(): Flow<List<FavoriteEntity>> = dao.getByType("SOUND")
    fun isFavorite(id: String): Flow<Boolean> = dao.isFavorite(id)
    fun count(): Flow<Int> = dao.count()
    suspend fun add(favorite: FavoriteEntity) = dao.insert(favorite)
    suspend fun remove(id: String) = dao.deleteById(id)
    suspend fun toggle(favorite: FavoriteEntity, isFav: Boolean) {
        if (isFav) dao.deleteById(favorite.id) else dao.insert(favorite)
    }
}

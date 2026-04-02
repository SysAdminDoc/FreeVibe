package com.freevibe.data.repository

import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.favoriteIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
) {
    fun getAll(): Flow<List<FavoriteEntity>> = dao.getAll()
    fun getWallpapers(): Flow<List<FavoriteEntity>> = dao.getByType("WALLPAPER")
    fun getSounds(): Flow<List<FavoriteEntity>> = dao.getByType("SOUND")
    fun isFavorite(identity: FavoriteIdentity): Flow<Boolean> = dao.isFavorite(identity.id, identity.source, identity.type)
    fun allIdentities(): Flow<Set<FavoriteIdentity>> = dao.allIdentities().map { it.toSet() }
    fun count(): Flow<Int> = dao.count()
    suspend fun getByIdentity(identity: FavoriteIdentity): FavoriteEntity? =
        dao.getByIdentity(identity.id, identity.source, identity.type)

    suspend fun getLatestById(id: String): FavoriteEntity? = dao.getLatestById(id)

    suspend fun add(favorite: FavoriteEntity) = dao.insert(favorite)
    suspend fun remove(identity: FavoriteIdentity) = dao.deleteByIdentity(identity.id, identity.source, identity.type)

    suspend fun toggle(favorite: FavoriteEntity, isFav: Boolean) {
        if (isFav) {
            remove(favorite.favoriteIdentity())
        } else {
            dao.insert(favorite)
        }
    }
}

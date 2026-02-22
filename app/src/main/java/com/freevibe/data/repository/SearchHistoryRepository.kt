package com.freevibe.data.repository

import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.model.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val dao: SearchHistoryDao,
) {
    fun getRecentWallpaperSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>> =
        dao.getRecent("WALLPAPER", limit)

    fun getRecentSoundSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>> =
        dao.getRecent("SOUND", limit)

    fun searchSuggestions(type: String, prefix: String): Flow<List<SearchHistoryEntity>> =
        dao.search(type, prefix)

    suspend fun addWallpaperSearch(query: String) {
        if (query.isBlank()) return
        dao.insert(SearchHistoryEntity(query = query.trim(), type = "WALLPAPER"))
    }

    suspend fun addSoundSearch(query: String) {
        if (query.isBlank()) return
        dao.insert(SearchHistoryEntity(query = query.trim(), type = "SOUND"))
    }

    suspend fun removeSearch(query: String, type: String) = dao.delete(query, type)

    suspend fun clearWallpaperHistory() = dao.clearAll("WALLPAPER")

    suspend fun clearSoundHistory() = dao.clearAll("SOUND")
}

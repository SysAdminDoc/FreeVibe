package com.freevibe.data.repository

import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.nasa.NasaApodApi
import com.freevibe.data.remote.toWallpaper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasaRepository @Inject constructor(
    private val nasaApi: NasaApodApi,
) {
    /** Get today's APOD as a wallpaper */
    suspend fun getToday(): Wallpaper? {
        val response = nasaApi.getToday()
        return if (response.isImage) response.toWallpaper() else null
    }

    /** Get random APOD wallpapers */
    suspend fun getRandom(count: Int = 20): SearchResult<Wallpaper> {
        val responses = nasaApi.getRandom(count = count)
        val wallpapers = responses.filter { it.isImage }.map { it.toWallpaper() }
        return SearchResult(
            items = wallpapers,
            totalCount = wallpapers.size,
            currentPage = 1,
            hasMore = false,
        )
    }

    /** Get APOD range (for browsing history) */
    suspend fun getRange(startDate: String, endDate: String? = null): SearchResult<Wallpaper> {
        val responses = nasaApi.getRange(startDate = startDate, endDate = endDate)
        val wallpapers = responses.filter { it.isImage }.map { it.toWallpaper() }.reversed()
        return SearchResult(
            items = wallpapers,
            totalCount = wallpapers.size,
            currentPage = 1,
            hasMore = false,
        )
    }
}

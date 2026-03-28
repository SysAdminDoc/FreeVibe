package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.FavoriteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #3: Caches favorite wallpaper/sound files locally so they work offline.
 * Downloads the full-res file to internal storage and updates the FavoriteEntity
 * with the local path.
 */
@Singleton
class OfflineFavoritesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val favoriteDao: FavoriteDao,
) {
    private val offlineDir = File(context.filesDir, "offline_favorites").apply { mkdirs() }

    /** Cache a favorite's content locally. Returns local file path or null. */
    suspend fun cacheOffline(id: String, url: String, type: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val ext = when {
                    type == "SOUND" -> url.substringAfterLast(".", "mp3")
                    url.contains(".png", true) -> "png"
                    url.contains(".webp", true) -> "webp"
                    else -> "jpg"
                }
                val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val file = File(offlineDir, "$safeId.$ext")
                if (file.exists() && file.length() > 0) {
                    // Already cached
                    favoriteDao.updateOfflinePath(id, file.absolutePath)
                    return@withContext file.absolutePath
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (file.exists() && file.length() > 0) {
                    favoriteDao.updateOfflinePath(id, file.absolutePath)
                    file.absolutePath
                } else null
            } catch (_: Exception) { null }
        }

    /** Remove offline cache for an item */
    suspend fun removeOffline(id: String) = withContext(Dispatchers.IO) {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        offlineDir.listFiles()?.filter { it.name.startsWith("$safeId.") }
            ?.forEach { it.delete() }
        favoriteDao.updateOfflinePath(id, null)
    }

    /** Get total cache size in bytes */
    fun getCacheSize(): Long = offlineDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Clear all offline caches */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        offlineDir.listFiles()?.forEach { it.delete() }
    }
}

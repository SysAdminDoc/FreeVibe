package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.FavoriteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    companion object {
        private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    }

    private val offlineDir = File(context.filesDir, "offline_favorites").apply { mkdirs() }

    /** Cache a favorite's content locally. Returns local file path or null. */
    suspend fun cacheOffline(id: String, url: String, type: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val ext = when {
                    type == "SOUND" -> url.substringBefore("?").substringAfterLast(".", "mp3")
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
                    val tmpFile = java.io.File(file.parent, file.name + ".tmp")
                    resp.body?.byteStream()?.use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tmpFile.length() > 0) {
                        if (!tmpFile.renameTo(file)) {
                            // Rename failed (cross-filesystem, locked) — copy + delete
                            tmpFile.copyTo(file, overwrite = true)
                            tmpFile.delete()
                        }
                    } else {
                        tmpFile.delete()
                    }
                }

                if (file.exists() && file.length() > 0) {
                    favoriteDao.updateOfflinePath(id, file.absolutePath)
                    enforceStorageBudget(protectedPaths = setOf(file.absolutePath))
                    file.absolutePath
                } else null
            } catch (e: Exception) {
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.w("OfflineFavMgr", "cacheOffline failed for $id: ${e.message}")
                null
            }
        }

    /** Remove offline cache for an item */
    suspend fun removeOffline(id: String) = withContext(Dispatchers.IO) {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        offlineDir.listFiles()?.filter { it.name.startsWith("$safeId.") }
            ?.forEach { it.delete() }
        favoriteDao.updateOfflinePath(id, "")
    }

    /** Get total cache size in bytes */
    fun getCacheSize(): Long = offlineDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Remove orphaned temp/files and clear stale DB references. */
    suspend fun pruneOrphans() = withContext(Dispatchers.IO) {
        val favorites = favoriteDao.getAll().first()
        val validPaths = favorites.mapNotNull { it.offlinePath.takeIf { path -> path.isNotBlank() } }.toSet()

        offlineDir.listFiles()?.forEach { file ->
            val shouldDelete = file.extension.equals("tmp", true) || file.absolutePath !in validPaths
            if (shouldDelete) file.delete()
        }

        favorites
            .filter { it.offlinePath.isNotBlank() && !File(it.offlinePath).exists() }
            .forEach { favoriteDao.updateOfflinePath(it.id, "") }

        enforceStorageBudget()
    }

    private suspend fun enforceStorageBudget(protectedPaths: Set<String> = emptySet()) {
        val favoritesByPath = favoriteDao.getAll().first()
            .filter { it.offlinePath.isNotBlank() }
            .associateBy { it.offlinePath }
        val files = offlineDir.listFiles()
            ?.filter { it.isFile && !it.extension.equals("tmp", true) }
            ?.sortedWith(compareBy<File>({ it.absolutePath in protectedPaths }, { it.lastModified() }))
            ?: return

        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        for (file in files) {
            if (totalBytes <= MAX_CACHE_BYTES || file.absolutePath in protectedPaths) continue
            val fileSize = file.length()
            if (file.delete()) {
                favoritesByPath[file.absolutePath]?.let { favoriteDao.updateOfflinePath(it.id, "") }
                totalBytes -= fileSize
            }
        }
    }

    /** Clear all offline caches and reset DB paths */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        offlineDir.listFiles()?.forEach { it.delete() }
        // Clear stale offlinePath values in DB so favorites don't reference deleted files
        val allFavs = favoriteDao.getAll().first()
        allFavs.filter { it.offlinePath.isNotEmpty() }.forEach { fav ->
            favoriteDao.updateOfflinePath(fav.id, "")
        }
    }
}

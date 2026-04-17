package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.stableKey
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
private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")

@Singleton
class OfflineFavoritesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val favoriteDao: FavoriteDao,
) {
    companion object {
        private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L

        /** Max bytes for a single offline-cached favorite (wallpaper or sound). Prevents one
         *  hostile URL from consuming the entire 512 MB budget in one shot. */
        private const val MAX_PER_FILE_BYTES = 80L * 1024L * 1024L
    }

    private val offlineDir = File(context.filesDir, "offline_favorites").apply { mkdirs() }

    /** Cache a favorite's content locally. Returns local file path or null. */
    suspend fun cacheOffline(favorite: FavoriteEntity, url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val ext = when {
                    favorite.type == "SOUND" -> url.substringBefore("?").substringAfterLast(".", "mp3")
                    url.contains(".png", true) -> "png"
                    url.contains(".webp", true) -> "webp"
                    else -> "jpg"
                }
                val safeKey = favorite.stableKey().replace(SANITIZE_REGEX, "_")
                val legacySafeId = favorite.id.replace(SANITIZE_REGEX, "_")
                val file = File(offlineDir, "$safeKey.$ext")
                val legacyFile = offlineDir.listFiles()
                    ?.firstOrNull { it.name.startsWith("$legacySafeId.") && it.length() > 0 }
                if (file.exists() && file.length() > 0) {
                    // Already cached
                    favoriteDao.updateOfflinePath(favorite.id, favorite.source, favorite.type, file.absolutePath)
                    return@withContext file.absolutePath
                }
                if (legacyFile != null) {
                    favoriteDao.updateOfflinePath(favorite.id, favorite.source, favorite.type, legacyFile.absolutePath)
                    return@withContext legacyFile.absolutePath
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    // Early reject when the server advertises an oversized payload.
                    val advertised = body.contentLength()
                    if (advertised in 1..Long.MAX_VALUE && advertised > MAX_PER_FILE_BYTES) {
                        return@withContext null
                    }
                    val tmpFile = java.io.File(file.parent, file.name + ".tmp")
                    var abort = false
                    body.byteStream().use { input ->
                        tmpFile.outputStream().use { output ->
                            var copied = 0L
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                copied += n
                                if (copied > MAX_PER_FILE_BYTES) {
                                    abort = true
                                    break
                                }
                                output.write(buf, 0, n)
                            }
                        }
                    }
                    if (abort) {
                        try { tmpFile.delete() } catch (_: Exception) {}
                        return@withContext null
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
                    favoriteDao.updateOfflinePath(favorite.id, favorite.source, favorite.type, file.absolutePath)
                    enforceStorageBudget(protectedPaths = setOf(file.absolutePath))
                    file.absolutePath
                } else null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.w("OfflineFavMgr", "cacheOffline failed for ${favorite.id}: ${e.message}")
                null
            }
        }

    /** Remove offline cache for an item */
    suspend fun removeOffline(favorite: FavoriteEntity) = withContext(Dispatchers.IO) {
        val safeKey = favorite.stableKey().replace(SANITIZE_REGEX, "_")
        val legacySafeId = favorite.id.replace(SANITIZE_REGEX, "_")
        offlineDir.listFiles()?.filter {
            it.name.startsWith("$safeKey.") || it.name.startsWith("$legacySafeId.")
        }
            ?.forEach { it.delete() }
        favoriteDao.updateOfflinePath(favorite.id, favorite.source, favorite.type, "")
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
            .forEach { favoriteDao.updateOfflinePath(it.id, it.source, it.type, "") }

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
                favoritesByPath[file.absolutePath]?.let {
                    favoriteDao.updateOfflinePath(it.id, it.source, it.type, "")
                }
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
            favoriteDao.updateOfflinePath(fav.id, fav.source, fav.type, "")
        }
    }
}

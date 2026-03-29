package com.freevibe.service

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val moshi: Moshi,
) {
    private val listType = Types.newParameterizedType(List::class.java, FavoriteExportItem::class.java)
    private val adapter = moshi.adapter<List<FavoriteExportItem>>(listType)

    /** Export all favorites to a JSON file, returns URI */
    suspend fun export(outputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val favorites = favoriteDao.getAll().first()
            val items = favorites.map { it.toExportItem() }
            val json = adapter.indent("  ").toJson(items)
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                out.write(json.toByteArray())
            } ?: throw IllegalStateException("Failed to open output stream")
            items.size
        }
    }

    /** Import favorites from a JSON file */
    suspend fun import(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(inputUri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { it.readText() }
            } ?: throw IllegalStateException("Failed to read file")

            val items = try {
                adapter.fromJson(json) ?: throw IllegalStateException("Invalid JSON format")
            } catch (e: Exception) {
                throw IllegalStateException("Invalid favorites file: ${e.message}")
            }
            var count = 0
            items.forEach { item ->
                favoriteDao.insert(item.toEntity())
                count++
            }
            count
        }
    }

    /** Generate export as string (for sharing) */
    suspend fun exportToString(): String = withContext(Dispatchers.IO) {
        val favorites = favoriteDao.getAll().first()
        val items = favorites.map { it.toExportItem() }
        adapter.indent("  ").toJson(items)
    }
}

// ── Export data model (clean JSON without Room annotations) ───────

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class FavoriteExportItem(
    val id: String,
    val source: String,
    val type: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val name: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
)

private fun FavoriteEntity.toExportItem() = FavoriteExportItem(
    id = id, source = source, type = type, thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl, name = name, width = width, height = height, duration = duration,
)

private fun FavoriteExportItem.toEntity() = FavoriteEntity(
    id = id, source = source, type = type, thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl, name = name, width = width, height = height, duration = duration,
)

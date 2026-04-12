package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.freevibe.data.local.CollectionDao
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection sharing (v6.1.0). Serializes a collection + its items to JSON, writes to the
 * app's scoped `share_out/` cache directory, returns a `content://` URI via FileProvider
 * that can be handed to Android's share sheet (`Intent.ACTION_SEND`).
 *
 * The export format mirrors FavoritesExporter — same `@JsonClass(generateAdapter = true)`
 * pattern, indented JSON, Moshi codegen adapter. A round-trip import path is intentionally
 * NOT shipped in v6.1.0; "share my collection to a friend" is the actual use case we need
 * today. Import via intent-filter is a v6.2+ feature.
 */
@Singleton
class CollectionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionDao: CollectionDao,
    private val moshi: Moshi,
) {
    private val adapter = moshi.adapter(CollectionExportFile::class.java).indent("  ")

    /**
     * Serialize the collection with [collectionId] and return a content URI the caller can
     * hand to the share sheet. Null if the collection has no items (nothing to share) or
     * doesn't exist.
     */
    suspend fun prepareShareUri(collectionId: Long, collectionName: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val items = collectionDao.getCollectionItems(collectionId).first()
            if (items.isEmpty()) throw IllegalStateException("This collection is empty — add a wallpaper first.")

            val exportItems = items.map {
                CollectionExportItem(
                    wallpaperId = it.wallpaperId,
                    source = it.source,
                    thumbnailUrl = it.thumbnailUrl,
                    fullUrl = it.fullUrl,
                    width = it.width,
                    height = it.height,
                )
            }
            val file = CollectionExportFile(
                version = CURRENT_VERSION,
                exportedAt = System.currentTimeMillis(),
                collectionName = collectionName,
                items = exportItems,
            )
            val json = adapter.toJson(file)

            val shareDir = File(context.cacheDir, "share_out").apply { mkdirs() }
            // Sanitize filename to [a-zA-Z0-9_-] so the share intent's title looks clean
            // in the destination app (e.g. Gmail subject, Messenger file name).
            val safeName = collectionName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "collection" }
            val shareFile = File(shareDir, "aura_${safeName}_${collectionId}.json").apply {
                writeBytes(json.toByteArray())
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile,
            )
        }
    }

    /** Build an ACTION_SEND intent targeting the collection JSON. Caller starts it. */
    fun buildShareIntent(uri: Uri, collectionName: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Aura collection: $collectionName")
            putExtra(
                Intent.EXTRA_TEXT,
                "Collection from the Aura wallpaper app — tap the attached file to view the list.",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    companion object {
        private const val CURRENT_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class CollectionExportFile(
    val version: Int,
    val exportedAt: Long,
    val collectionName: String,
    val items: List<CollectionExportItem>,
)

@JsonClass(generateAdapter = true)
data class CollectionExportItem(
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

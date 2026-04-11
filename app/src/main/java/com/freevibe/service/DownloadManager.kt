package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val id: String,
    val fileName: String,
    val progress: Float,       // 0.0 - 1.0
    val totalBytes: Long,
    val downloadedBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null,
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao,
) {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    /** Download a wallpaper image to the Pictures directory */
    suspend fun downloadWallpaper(
        id: String,
        url: String,
        fileName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val contentType = "WALLPAPER"
        downloadFile(
            contentId = id,
            historyId = buildHistoryId(contentType, id),
            url = url,
            fileName = sanitize(fileName),
            mimeType = guessMimeType(url),
            relativePath = Environment.DIRECTORY_PICTURES + "/Aura",
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentType = contentType,
        )
    }

    /** Download a sound to the appropriate audio directory */
    suspend fun downloadSound(
        id: String,
        url: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val contentType = "SOUND"
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC
        } + "/Aura"

        downloadFile(
            contentId = id,
            historyId = buildHistoryId(contentType, id),
            url = url,
            fileName = sanitize(fileName),
            mimeType = guessAudioMime(url),
            relativePath = relativePath,
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            contentType = contentType,
        )
    }

    private suspend fun downloadFile(
        contentId: String,
        historyId: String,
        url: String,
        fileName: String,
        mimeType: String,
        relativePath: String,
        collection: Uri,
        contentType: String,
    ): Result<Uri> = runCatching {
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0))

        // Start HTTP download
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body
                ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength().let { if (it <= 0) 0L else it }

            // Create MediaStore entry
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore entry")

            var success = false
            try {
                // Stream data with progress tracking
                var downloadedBytes = 0L
                val outputStream = resolver.openOutputStream(uri)
                if (outputStream == null) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    throw IllegalStateException("Failed to open output stream")
                }
                outputStream.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            updateProgress(
                                historyId,
                                DownloadProgress(historyId, fileName, progress, totalBytes, downloadedBytes),
                            )
                        }
                    }
                }

                // Mark as complete in MediaStore
                if (Build.VERSION.SDK_INT >= 29) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }

                val existingEntries = downloadDao.findMatching(
                    type = contentType,
                    legacyId = contentId,
                    scopedId = historyId,
                )

                // Record in local database
                downloadDao.insert(
                    DownloadEntity(
                        id = historyId,
                        source = contentType,
                        type = contentType,
                        localPath = uri.toString(),
                        name = fileName,
                    )
                )

                existingEntries
                    .map { it.localPath }
                    .filter { it.isNotBlank() && it != uri.toString() }
                    .distinct()
                    .forEach(::deleteStoredContent)

                existingEntries
                    .map { it.id }
                    .filter { it != historyId }
                    .distinct()
                    .forEach { existingId ->
                        downloadDao.deleteById(existingId)
                    }

                // Mark download complete
                updateProgress(
                    historyId,
                    DownloadProgress(historyId, fileName, 1f, totalBytes, downloadedBytes, isComplete = true),
                )
                success = true
            } finally {
                if (!success) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }

            uri
        }
    }.onFailure { e ->
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0, error = e.message))
    }

    fun clearCompleted(id: String) {
        _activeDownloads.update { it - id }
    }

    suspend fun deleteDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = downloadDao.getById(id)
            existing?.localPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::deleteStoredContent)
            downloadDao.deleteById(id)
            clearCompleted(id)
        }
    }

    private fun updateProgress(id: String, progress: DownloadProgress) {
        _activeDownloads.update { it + (id to progress) }
    }

    private fun deleteStoredContent(rawPath: String) {
        val uri = runCatching { Uri.parse(rawPath) }.getOrNull()
        when {
            uri == null -> java.io.File(rawPath).takeIf { it.exists() }?.delete()
            uri.scheme == null || uri.scheme.equals("file", ignoreCase = true) -> {
                val path = uri.path ?: rawPath
                java.io.File(path).takeIf { it.exists() }?.delete()
            }
            uri.scheme.equals("content", ignoreCase = true) -> {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
            }
            else -> {
                java.io.File(rawPath).takeIf { it.exists() }?.delete()
            }
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun buildHistoryId(type: String, id: String): String = "${type.lowercase(java.util.Locale.ROOT)}:$id"

    private fun guessMimeType(url: String): String {
        val path = url.substringBefore("?").substringBefore("#").lowercase(java.util.Locale.ROOT)
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun guessAudioMime(url: String): String {
        val path = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".m4a") -> "audio/mp4"
            else -> "audio/mpeg"
        }
    }
}

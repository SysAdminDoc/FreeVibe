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
        downloadFile(
            id = id,
            url = url,
            fileName = sanitize(fileName),
            mimeType = guessMimeType(url),
            relativePath = Environment.DIRECTORY_PICTURES + "/Aura",
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentType = "WALLPAPER",
        )
    }

    /** Download a sound to the appropriate audio directory */
    suspend fun downloadSound(
        id: String,
        url: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC
        } + "/Aura"

        downloadFile(
            id = id,
            url = url,
            fileName = sanitize(fileName),
            mimeType = guessAudioMime(url),
            relativePath = relativePath,
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            contentType = "SOUND",
        )
    }

    private suspend fun downloadFile(
        id: String,
        url: String,
        fileName: String,
        mimeType: String,
        relativePath: String,
        collection: Uri,
        contentType: String,
    ): Result<Uri> = runCatching {
        updateProgress(id, DownloadProgress(id, fileName, 0f, 0, 0))

        // Start HTTP download
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Download failed: HTTP ${response.code}")
        }
        val body = response.body ?: run {
            response.close()
            throw IllegalStateException("Empty response body")
        }
        val totalBytes = body.contentLength()

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

        // Stream data with progress tracking
        var downloadedBytes = 0L
        resolver.openOutputStream(uri)?.use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    updateProgress(id, DownloadProgress(id, fileName, progress, totalBytes, downloadedBytes))
                }
            }
        }

        // Mark as complete in MediaStore
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        // Record in local database
        downloadDao.insert(
            DownloadEntity(
                id = id,
                source = contentType,
                type = contentType,
                localPath = uri.toString(),
                name = fileName,
            )
        )

        // Mark download complete
        updateProgress(id, DownloadProgress(id, fileName, 1f, totalBytes, downloadedBytes, isComplete = true))

        uri
    }.onFailure { e ->
        updateProgress(id, DownloadProgress(id, fileName, 0f, 0, 0, error = e.message))
    }

    fun clearCompleted(id: String) {
        _activeDownloads.value = _activeDownloads.value - id
    }

    private fun updateProgress(id: String, progress: DownloadProgress) {
        _activeDownloads.value = _activeDownloads.value + (id to progress)
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun guessMimeType(url: String): String {
        val path = url.substringBefore("?").substringBefore("#").lowercase()
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

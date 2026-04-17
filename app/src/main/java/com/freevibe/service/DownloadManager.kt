package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
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
            maxBytes = MAX_IMAGE_DOWNLOAD_BYTES,
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
            maxBytes = MAX_AUDIO_DOWNLOAD_BYTES,
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
        maxBytes: Long,
    ): Result<Uri> = try {
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0))

        // Start HTTP download
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        Result.success(response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body
                ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength().let { if (it <= 0) 0L else it }
            // Reject oversized downloads up front when the server advertises a size, so we
            // don't allocate a MediaStore entry we're immediately going to delete.
            if (totalBytes in 1..Long.MAX_VALUE && totalBytes > maxBytes) {
                throw IllegalStateException(
                    "Download exceeds size limit (${totalBytes} > ${maxBytes})"
                )
            }

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
                            // Abort before writing if a malicious/broken server exceeds the
                            // advertised content length (or never sends Content-Length).
                            if (downloadedBytes + bytesRead > maxBytes) {
                                throw IllegalStateException(
                                    "Download exceeds size limit ($maxBytes bytes)"
                                )
                            }
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
        })
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        updateProgress(historyId, DownloadProgress(historyId, fileName, 0f, 0, 0, error = e.message))
        Result.failure(e)
    }

    fun clearCompleted(id: String) {
        _activeDownloads.update { it - id }
    }

    suspend fun deleteDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = downloadDao.getById(id)
            existing?.localPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::deleteStoredContent)
            downloadDao.deleteById(id)
            clearCompleted(id)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun updateProgress(id: String, progress: DownloadProgress) {
        _activeDownloads.update { it + (id to progress) }
    }

    private fun deleteStoredContent(rawPath: String) {
        val uri = runCatching { Uri.parse(rawPath) }.getOrNull()
        when {
            uri == null -> resolveManagedLocalDeletionTarget(rawPath)?.takeIf { it.exists() }?.delete()
            uri.scheme == null || uri.scheme.equals("file", ignoreCase = true) -> {
                resolveManagedLocalDeletionTarget(uri.path ?: rawPath)?.takeIf { it.exists() }?.delete()
            }
            uri.scheme.equals("content", ignoreCase = true) -> {
                if (isAuraManagedContentUri(uri, context)) {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                } else if (com.freevibe.BuildConfig.DEBUG) {
                    Log.w("DownloadManager", "Skipping deletion for unmanaged content URI: $uri")
                }
            }
            else -> resolveManagedLocalDeletionTarget(rawPath)?.takeIf { it.exists() }?.delete()
        }
    }

    private fun resolveManagedLocalDeletionTarget(rawPath: String): File? {
        val canonicalTarget = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        val managedRoots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null),
        ).mapNotNull { root ->
            runCatching { root.canonicalFile }.getOrNull()
        }
        val isAppManaged = managedRoots.any { root ->
            canonicalTarget == root || canonicalTarget.path.startsWith(root.path + File.separator)
        }
        if (isAppManaged || isAuraManagedAbsolutePath(canonicalTarget.path)) {
            return canonicalTarget
        }
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.w("DownloadManager", "Skipping deletion for unmanaged local path: $rawPath")
        }
        return null
    }

    private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")

    private fun sanitize(name: String) = name.replace(SANITIZE_REGEX, "_")

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
        val path = url.substringBefore("?").substringBefore("#").lowercase(java.util.Locale.ROOT)
        return when {
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".m4a") -> "audio/mp4"
            else -> "audio/mpeg"
        }
    }
}

/** Hard cap on wallpaper downloads — ~64 MB covers any realistic 8K JPG/PNG/WEBP. */
private const val MAX_IMAGE_DOWNLOAD_BYTES = 64L * 1024 * 1024

/** Hard cap on audio downloads — matches the 20 MB community upload ceiling + headroom. */
private const val MAX_AUDIO_DOWNLOAD_BYTES = 64L * 1024 * 1024

private val AURA_MEDIA_DIRECTORIES = listOfNotNull(
    Environment.DIRECTORY_PICTURES,
    Environment.DIRECTORY_RINGTONES,
    Environment.DIRECTORY_NOTIFICATIONS,
    Environment.DIRECTORY_ALARMS,
    Environment.DIRECTORY_MUSIC,
    Environment.DIRECTORY_MOVIES,
).ifEmpty {
    listOf("Pictures", "Ringtones", "Notifications", "Alarms", "Music", "Movies")
}

internal fun isAuraManagedRelativePath(relativePath: String?): Boolean {
    val normalized = relativePath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.lowercase(Locale.ROOT)
        ?: return false
    return AURA_MEDIA_DIRECTORIES.any { directory ->
        val managedRoot = "${directory.lowercase(Locale.ROOT)}/aura"
        normalized == managedRoot || normalized.startsWith("$managedRoot/")
    }
}

internal fun isAuraManagedAbsolutePath(path: String?): Boolean {
    val normalized = path
        ?.replace('\\', '/')
        ?.lowercase(Locale.ROOT)
        ?: return false
    return AURA_MEDIA_DIRECTORIES.any { directory ->
        normalized.contains("/${directory.lowercase(Locale.ROOT)}/aura/")
    }
}

internal fun isAuraManagedContentUri(uri: Uri, context: Context): Boolean {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) {
                    false
                } else {
                    isAuraManagedRelativePath(cursor.getString(0))
                }
            }
            ?: false
    }.getOrDefault(false)
}

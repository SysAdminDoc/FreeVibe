package com.freevibe.service

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VideoWallpaperSelectionResult {
    data object Preparing : VideoWallpaperSelectionResult
    data object Ready : VideoWallpaperSelectionResult
    data class Failure(val message: String) : VideoWallpaperSelectionResult
}

internal fun isGifVideoWallpaperSelection(
    mimeType: String?,
    fileName: String?,
): Boolean {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val normalizedName = fileName?.lowercase(Locale.ROOT).orEmpty()
    return normalizedMime == "image/gif" || normalizedName.endsWith(".gif")
}

internal fun resolveVideoWallpaperExtension(
    mimeType: String?,
    fileName: String?,
): String {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val normalizedName = fileName?.lowercase(Locale.ROOT).orEmpty()
    return when {
        normalizedMime == "video/webm" || normalizedName.endsWith(".webm") -> "webm"
        normalizedMime == "video/3gpp" || normalizedName.endsWith(".3gp") -> "3gp"
        normalizedMime == "video/ogg" || normalizedName.endsWith(".ogv") -> "ogv"
        else -> "mp4"
    }
}

@Singleton
class VideoWallpaperStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun prepareFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            if (isGifVideoWallpaperSelection(mimeType, uri.lastPathSegment)) {
                throw UnsupportedOperationException(
                    "Animated GIF wallpapers are not supported yet. Pick a video clip instead.",
                )
            }

            val extension = resolveVideoWallpaperExtension(mimeType, uri.lastPathSegment)
            val targetFile = managedVideoFile(extension)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            try {
                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Could not open the selected file")

                validatePreparedVideo(tempFile)
                commitPreparedVideo(tempFile, targetFile)
                persistSelectedVideoWallpaper(targetFile)
                targetFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
    }

    suspend fun prepareDownloadedVideo(
        extension: String = "mp4",
        writer: suspend (File) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = managedVideoFile(extension)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            try {
                writer(tempFile)
                validatePreparedVideo(tempFile)
                commitPreparedVideo(tempFile, targetFile)
                persistSelectedVideoWallpaper(targetFile)
                targetFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
    }

    private fun validatePreparedVideo(file: File) {
        if (!file.exists() || file.length() < 1024) {
            throw IOException("Selected file is empty or invalid")
        }
    }

    private fun commitPreparedVideo(tempFile: File, targetFile: File) {
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        pruneOlderManagedCopies(targetFile)
    }

    private fun pruneOlderManagedCopies(activeFile: File) {
        context.filesDir.listFiles()
            ?.filter { candidate ->
                candidate.isFile &&
                    candidate.name.startsWith("live_wallpaper.") &&
                    candidate.absolutePath != activeFile.absolutePath
            }
            ?.forEach { stale ->
                try {
                    stale.delete()
                } catch (_: Exception) {
                }
            }
    }

    private fun managedVideoFile(extension: String): File =
        File(context.filesDir, "live_wallpaper.$extension")

    private fun persistSelectedVideoWallpaper(file: File) {
        context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
            .edit()
            .putString("video_path", file.absolutePath)
            .putString("scale_mode", "zoom")
            .apply()
    }
}

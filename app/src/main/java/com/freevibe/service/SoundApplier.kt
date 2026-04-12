package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.freevibe.data.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")

@Singleton
class SoundApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /** Check if app has WRITE_SETTINGS permission */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    /** Launch system settings to grant WRITE_SETTINGS */
    fun requestWriteSettings(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Download audio from URL, save to MediaStore, and set as system sound */
    suspend fun downloadAndApply(
        url: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }

            // Determine MIME type from URL/extension
            val mimeType = guessMimeType(url)
            val safeFileName = ensureFileNameExtension(
                fileName.replace(SANITIZE_REGEX, "_"),
                mimeType,
            )

            // Save to MediaStore
            val uri = saveUrlToMediaStore(safeFileName, mimeType, type, url)
                ?: throw IllegalStateException("Failed to save audio to MediaStore")

            // Set as system sound
            val ringtoneType = when (type) {
                ContentType.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                ContentType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                ContentType.ALARM -> RingtoneManager.TYPE_ALARM
                else -> throw IllegalArgumentException("Invalid sound type: $type")
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)

            uri
        }
    }

    /** Save audio without applying - just download to storage */
    suspend fun downloadOnly(
        url: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = guessMimeType(url)
            val safeFileName = ensureFileNameExtension(
                fileName.replace(SANITIZE_REGEX, "_"),
                mimeType,
            )
            saveUrlToMediaStore(safeFileName, mimeType, type, url)
                ?: throw IllegalStateException("Failed to save audio to MediaStore")
        }
    }

    /** Apply a local audio file (e.g. trimmed output) as system sound */
    suspend fun applyFromLocalFile(
        filePath: String,
        fileName: String,
        type: ContentType,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canWriteSettings()) {
                throw SecurityException("WRITE_SETTINGS permission not granted")
            }

            val mimeType = guessMimeType(filePath)
            val safeFileName = ensureFileNameExtension(
                fileName.replace(SANITIZE_REGEX, "_"),
                mimeType,
            )

            val uri = saveLocalFileToMediaStore(safeFileName, mimeType, type, File(filePath))
                ?: throw IllegalStateException("Failed to save audio to MediaStore")

            val ringtoneType = when (type) {
                ContentType.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                ContentType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                ContentType.ALARM -> RingtoneManager.TYPE_ALARM
                else -> throw IllegalArgumentException("Invalid sound type: $type")
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)

            uri
        }
    }

    private fun saveToMediaStore(
        fileName: String,
        mimeType: String,
        type: ContentType,
        writeContent: (OutputStream) -> Unit,
    ): Uri? {
        val relativePath = when (type) {
            ContentType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ContentType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ContentType.ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_MUSIC
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_RINGTONE, type == ContentType.RINGTONE)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, type == ContentType.NOTIFICATION)
            put(MediaStore.Audio.Media.IS_ALARM, type == ContentType.ALARM)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        val written = try {
            resolver.openOutputStream(uri)?.use {
                writeContent(it)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }

        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }

        // Mark as complete
        if (Build.VERSION.SDK_INT >= 29) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }

    private fun saveUrlToMediaStore(
        fileName: String,
        mimeType: String,
        type: ContentType,
        url: String,
    ): Uri? {
        val request = Request.Builder().url(url).build()
        return okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            saveToMediaStore(fileName, mimeType, type) { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private fun saveLocalFileToMediaStore(
        fileName: String,
        mimeType: String,
        type: ContentType,
        file: File,
    ): Uri? = saveToMediaStore(fileName, mimeType, type) { output ->
        FileInputStream(file).use { input -> input.copyTo(output) }
    }

    private fun guessMimeType(url: String): String {
        val path = url.substringBefore("?").substringBefore("#").lowercase(java.util.Locale.ROOT)
        return when {
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".flac") -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    private fun ensureFileNameExtension(fileName: String, mimeType: String): String {
        if (fileName.substringAfterLast('.', "").isNotBlank()) return fileName
        val extension = when (mimeType.lowercase(java.util.Locale.ROOT)) {
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac" -> "flac"
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            else -> "mp3"
        }
        return "$fileName.$extension"
    }
}

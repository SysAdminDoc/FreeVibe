package com.freevibe.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val UPLOAD_NAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\- ]")
private val UPLOAD_TAG_SANITIZE_REGEX = Regex("[^a-z0-9_\\- ]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val STORAGE_SEGMENT_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val ALLOWED_UPLOAD_AUDIO_MIMES = setOf(
    "audio/mpeg",
    "audio/mp3",
    "audio/wav",
    "audio/x-wav",
    "audio/ogg",
    "audio/flac",
    "audio/aac",
    "audio/mp4",
    "audio/x-m4a",
    "audio/m4a",
)
private val ALLOWED_UPLOAD_CATEGORIES = setOf("ringtone", "notification", "alarm")
private const val MAX_AUDIO_UPLOAD_BYTES = 20L * 1024L * 1024L
private const val MAX_UPLOAD_TAGS = 8
private const val MAX_UPLOAD_TAG_LENGTH = 24

@Singleton
class UploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
) {
    private data class UploadFileInfo(
        val baseName: String,
        val originalFileName: String,
        val extension: String,
        val mimeType: String,
    )

    private val storage by lazy {
        try { Firebase.storage } catch (_: Exception) { null }
    }
    private val database by lazy {
        try { Firebase.database } catch (_: Exception) { null }
    }
    private val uploadsRef by lazy { database?.reference?.child("community_sounds") }

    /**
     * Upload an audio file to Firebase Storage and create metadata in RTDB.
     * Returns the download URL on success.
     */
    suspend fun uploadSound(
        localUri: Uri,
        name: String,
        category: String, // ringtone, notification, alarm
        tags: List<String>,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = try {
        val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
        val uploadsRefInstance = uploadsRef ?: throw IllegalStateException("Firebase Database not available")

        // Validate name length
        val sanitizedName = name.trim().take(100)
        if (sanitizedName.isBlank()) {
            throw IllegalArgumentException("Sound name cannot be empty")
        }

        val normalizedCategory = normalizeUploadCategory(category)
        val normalizedTags = sanitizeUploadTags(tags)
        val uploadInfo = resolveUploadFileInfo(localUri, sanitizedName)
        val normalizedMimeType = uploadInfo.mimeType.lowercase(java.util.Locale.ROOT)
        if (!isSupportedAudioUploadMime(normalizedMimeType)) {
            throw IllegalArgumentException("Unsupported audio format")
        }

        val fileSize = resolveUploadSize(localUri)
        validateUploadSize(fileSize)
        ensureReadableUpload(localUri)

        val timestamp = System.currentTimeMillis()
        val uploaderId = identityProvider.ensureSignedIn()
        val uploaderLabel = identityProvider.currentUploaderLabel()
        val storagePath = "sounds/${sanitizeUploadStorageSegment(uploaderId)}/${timestamp}_${uploadInfo.baseName}.${uploadInfo.extension}"
        val storageRef = storageInstance.reference.child(storagePath)
        var metadataSaved = false

        try {
            // Upload file
            val uploadTask = storageRef.putFile(localUri)
            uploadTask.addOnProgressListener { snapshot ->
                val totalByteCount = snapshot.totalByteCount.takeIf { it > 0 } ?: 0L
                val progress = if (totalByteCount > 0) {
                    snapshot.bytesTransferred.toFloat() / totalByteCount
                } else {
                    0f
                }
                onProgress(progress.coerceIn(0f, 1f))
            }
            uploadTask.await()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Write metadata to RTDB
            val pushRef = uploadsRefInstance.push()
            val metadata = mapOf(
                "name" to sanitizedName,
                "category" to normalizedCategory,
                "tags" to normalizedTags,
                "downloadUrl" to downloadUrl,
                "fileType" to normalizedMimeType,
                "originalFileName" to uploadInfo.originalFileName,
                "uploadedAt" to timestamp,
                "uploaderId" to uploaderId,
                "uploaderLabel" to uploaderLabel,
                "votes" to 0,
            )
            pushRef.setValue(metadata).await()
            metadataSaved = true

            Result.success(downloadUrl)
        } finally {
            if (!metadataSaved) {
                runCatching { storageRef.delete().await() }
            }
        }
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }

    /**
     * Get community-uploaded sounds, sorted by votes descending (client-side).
     */
    fun getCommunityUploads(category: String? = null, limit: Int = 30): Flow<List<Sound>> = callbackFlow {
        val uploadsRefInstance = uploadsRef
        if (uploadsRefInstance == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val ref = if (category != null) {
            uploadsRefInstance.orderByChild("category").equalTo(category).limitToLast(limit)
        } else {
            uploadsRefInstance.limitToLast(limit)
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val votesByKey = snapshot.children.associate { child ->
                    (child.key ?: "") to (child.child("votes").getValue(Int::class.java) ?: 0)
                }
                val sounds = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val nameVal = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val downloadUrl = child.child("downloadUrl").getValue(String::class.java) ?: return@mapNotNull null
                    val cat = child.child("category").getValue(String::class.java) ?: ""
                    val uploaderId = child.child("uploaderId").getValue(String::class.java) ?: ""
                    val uploaderLabel = child.child("uploaderLabel").getValue(String::class.java) ?: ""
                    val tags = child.child("tags").children.mapNotNull { it.getValue(String::class.java) }
                    val fileType = child.child("fileType").getValue(String::class.java) ?: ""

                    Sound(
                        id = "cu_$key",
                        source = ContentSource.COMMUNITY,
                        name = nameVal,
                        description = cat,
                        previewUrl = downloadUrl,
                        downloadUrl = downloadUrl,
                        duration = 0.0,
                        fileType = fileType,
                        tags = tags,
                        license = "User Upload",
                        uploaderName = uploaderLabel.ifBlank { uploaderId.take(8) },
                        sourcePageUrl = "",
                    )
                }.sortedByDescending { sound ->
                    votesByKey[sound.id.removePrefix("cu_")] ?: 0
                }.take(limit)

                trySend(sounds)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun resolveUploadFileInfo(localUri: Uri, fallbackName: String): UploadFileInfo {
        val resolver = context.contentResolver
        val originalFileName = resolver.query(localUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
            ?: (localUri.lastPathSegment?.substringAfterLast('/') ?: fallbackName)

        val inferredExtension = originalFileName.substringAfterLast('.', "")
            .lowercase(java.util.Locale.ROOT)
            .takeIf { it.isNotBlank() }
        val mimeType = resolver.getType(localUri)
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(java.util.Locale.ROOT)
            ?: inferredExtension
                ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
                ?.lowercase(java.util.Locale.ROOT)
            ?: ""

        val extension = inferredExtension
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: defaultExtensionForMimeType(mimeType)

        val baseName = originalFileName.substringBeforeLast('.')
            .ifBlank { fallbackName }
            .replace(UPLOAD_NAME_SANITIZE_REGEX, "")
            .take(40)
            .ifBlank { "audio" }

        return UploadFileInfo(
            baseName = baseName,
            originalFileName = originalFileName,
            extension = extension,
            mimeType = mimeType,
        )
    }

    private fun resolveUploadSize(localUri: Uri): Long? {
        val resolver = context.contentResolver
        val descriptorSize = resolver.openAssetFileDescriptor(localUri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it >= 0 } ?: descriptor.parcelFileDescriptor?.statSize?.takeIf { it >= 0 }
        }
        if (descriptorSize != null) return descriptorSize

        return resolver.query(localUri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    private fun validateUploadSize(fileSize: Long?) {
        when {
            fileSize == 0L -> throw IllegalArgumentException("Selected audio file is empty")
            fileSize != null && fileSize > MAX_AUDIO_UPLOAD_BYTES -> {
                throw IllegalArgumentException("File too large (max 20MB)")
            }
        }
    }

    private fun ensureReadableUpload(localUri: Uri) {
        val firstByte = context.contentResolver.openInputStream(localUri)?.use { input ->
            input.read()
        } ?: -1
        if (firstByte == -1) {
            throw IllegalArgumentException("Selected audio file is empty or unreadable")
        }
    }

    private fun defaultExtensionForMimeType(mimeType: String): String = when (mimeType.lowercase(java.util.Locale.ROOT)) {
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/mp4", "audio/aac" -> "m4a"
        else -> "mp3"
    }
}

internal fun normalizeUploadCategory(category: String): String {
    val normalized = category.trim().lowercase(java.util.Locale.ROOT)
    require(normalized in ALLOWED_UPLOAD_CATEGORIES) { "Invalid sound category" }
    return normalized
}

internal fun sanitizeUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            tag.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(UPLOAD_TAG_SANITIZE_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
        }
        .filter { it.length in 2..MAX_UPLOAD_TAG_LENGTH }
        .distinct()
        .take(MAX_UPLOAD_TAGS)
        .toList()

internal fun isSupportedAudioUploadMime(mimeType: String): Boolean =
    mimeType.lowercase(java.util.Locale.ROOT) in ALLOWED_UPLOAD_AUDIO_MIMES

internal fun sanitizeUploadStorageSegment(segment: String): String =
    segment.trim()
        .replace(STORAGE_SEGMENT_SANITIZE_REGEX, "_")
        .trim('_')
        .ifBlank { "user" }

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

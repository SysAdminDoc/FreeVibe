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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
private val UPLOAD_NAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\- ]")

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

        // Validate file size (max 20MB)
        val fileSize = context.contentResolver.openFileDescriptor(localUri, "r")?.use { it.statSize } ?: 0
        if (fileSize > 20 * 1024 * 1024) {
            throw IllegalArgumentException("File too large (max 20MB)")
        }

        // Validate audio MIME type — strict whitelist
        val fileMime = context.contentResolver.getType(localUri) ?: ""
        val allowedMimes = setOf(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/ogg", "audio/flac", "audio/aac", "audio/mp4",
            "audio/x-m4a", "audio/m4a",
        )
        if (fileMime.isNotBlank() && fileMime !in allowedMimes) {
            throw IllegalArgumentException("Unsupported audio format: $fileMime")
        }

        // Validate name length
        val sanitizedName = name.trim().take(100)
        if (sanitizedName.isBlank()) {
            throw IllegalArgumentException("Sound name cannot be empty")
        }

        val timestamp = System.currentTimeMillis()
        val uploaderId = identityProvider.ensureSignedIn()
        val uploaderLabel = identityProvider.currentUploaderLabel()
        val uploadInfo = resolveUploadFileInfo(localUri, sanitizedName)
        val storagePath = "sounds/$uploaderId/${timestamp}_${uploadInfo.baseName}.${uploadInfo.extension}"
        val storageRef = storageInstance.reference.child(storagePath)

        // Upload file
        val uploadTask = storageRef.putFile(localUri)
        uploadTask.addOnProgressListener { snapshot ->
            val progress = snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount
            onProgress(progress)
        }
        uploadTask.await()

        // Get download URL
        val downloadUrl = storageRef.downloadUrl.await().toString()

        // Write metadata to RTDB
        val pushRef = uploadsRefInstance.push()
        val metadata = mapOf(
            "name" to sanitizedName,
            "category" to category,
            "tags" to tags,
            "downloadUrl" to downloadUrl,
            "fileType" to uploadInfo.mimeType,
            "originalFileName" to uploadInfo.originalFileName,
            "uploadedAt" to timestamp,
            "uploaderId" to uploaderId,
            "uploaderLabel" to uploaderLabel,
            "votes" to 0,
        )
        pushRef.setValue(metadata).await()

        Result.success(downloadUrl)
    } catch (e: Exception) {
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
                    // Sort by votes (stored in description temporarily as category)
                    snapshot.children.firstOrNull { "cu_${it.key}" == sound.id }
                        ?.child("votes")?.getValue(Int::class.java) ?: 0
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

        val mimeType = resolver.getType(localUri)
            ?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(originalFileName.substringAfterLast('.', ""))
            ?: "audio/mpeg"

        val extension = originalFileName.substringAfterLast('.', "")
            .lowercase(java.util.Locale.ROOT)
            .takeIf { it.isNotBlank() }
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

    private fun defaultExtensionForMimeType(mimeType: String): String = when (mimeType.lowercase(java.util.Locale.ROOT)) {
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/mp4", "audio/aac" -> "m4a"
        else -> "mp3"
    }
}

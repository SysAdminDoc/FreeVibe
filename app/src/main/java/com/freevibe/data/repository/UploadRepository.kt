package com.freevibe.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
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
class UploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val storage = Firebase.storage
    private val database = Firebase.database
    private val uploadsRef = database.reference.child("community_sounds")

    @Suppress("HardwareIds")
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

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
        val timestamp = System.currentTimeMillis()
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40)
        val storagePath = "sounds/$deviceId/${timestamp}_${sanitizedName}.mp3"
        val storageRef = storage.reference.child(storagePath)

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
        val pushRef = uploadsRef.push()
        val metadata = mapOf(
            "name" to name,
            "category" to category,
            "tags" to tags,
            "downloadUrl" to downloadUrl,
            "uploadedAt" to timestamp,
            "uploaderId" to deviceId,
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
        val ref = if (category != null) {
            uploadsRef.orderByChild("category").equalTo(category)
        } else {
            uploadsRef.limitToLast(limit)
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sounds = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val nameVal = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val downloadUrl = child.child("downloadUrl").getValue(String::class.java) ?: return@mapNotNull null
                    val cat = child.child("category").getValue(String::class.java) ?: ""
                    val votes = child.child("votes").getValue(Int::class.java) ?: 0
                    val uploaderId = child.child("uploaderId").getValue(String::class.java) ?: ""
                    val uploadedAt = child.child("uploadedAt").getValue(Long::class.java) ?: 0L

                    Sound(
                        id = "cu_$key",
                        source = ContentSource.COMMUNITY,
                        name = nameVal,
                        description = cat,
                        previewUrl = downloadUrl,
                        downloadUrl = downloadUrl,
                        duration = 0.0,
                        tags = emptyList(),
                        license = "User Upload",
                        uploaderName = uploaderId.take(8),
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
                trySend(emptyList())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

package com.freevibe.service

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityIdentityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val auth by lazy {
        try { Firebase.auth } catch (_: Exception) { null }
    }
    private val prefs by lazy {
        context.getSharedPreferences("aura_community_identity", Context.MODE_PRIVATE)
    }

    @Suppress("HardwareIds")
    val legacyDeviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private val fallbackId: String by lazy {
        prefs.getString(KEY_FALLBACK_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
            }
    }

    fun currentUserId(): String = auth?.currentUser?.uid ?: fallbackId

    fun currentUploaderLabel(): String =
        auth?.currentUser?.uid?.take(8) ?: "local-${fallbackId.take(6)}"

    fun knownIdentityIds(): List<String> =
        listOf(auth?.currentUser?.uid, fallbackId, legacyDeviceId)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()

    suspend fun ensureSignedIn(): String {
        auth?.currentUser?.uid?.let { return it }

        val signedInUid = try {
            auth?.signInAnonymously()?.await()?.user?.uid
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }

        return signedInUid?.takeIf { it.isNotBlank() } ?: fallbackId
    }

    companion object {
        private const val KEY_FALLBACK_ID = "fallback_id"
    }
}

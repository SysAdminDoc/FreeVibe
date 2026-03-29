package com.freevibe.data.repository

import android.content.Context
import android.provider.Settings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Community voting system backed by Firebase Realtime Database.
 *
 * Structure: /votes/{contentId}/upvotes = Int
 *            /voters/{contentId}/{deviceId} = true  (prevents double-voting)
 *
 * Downvoting is local-only — hides the post for this device via DataStore.
 */
@Singleton
class VoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val db = FirebaseDatabase.getInstance().reference
    private val votesRef = db.child("votes")
    private val votersRef = db.child("voters")

    // Stable anonymous device ID (no account needed)
    @Suppress("HardwareIds")
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    // Local cache of hidden (downvoted) content IDs
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: Flow<Set<String>> = _hiddenIds

    init {
        // Load hidden IDs from SharedPreferences
        val prefs = context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
        _hiddenIds.value = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()
    }

    /** Get live upvote count for a content item */
    fun getVoteCount(contentId: String): Flow<Int> = callbackFlow {
        val safeId = sanitizeKey(contentId)
        val ref = votesRef.child(safeId).child("upvotes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Int::class.java) ?: 0)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(0)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Check if current device already voted on this item */
    suspend fun hasVoted(contentId: String): Boolean {
        val safeId = sanitizeKey(contentId)
        return try {
            val snap = votersRef.child(safeId).child(deviceId).get().await()
            snap.exists()
        } catch (_: Exception) { false }
    }

    /** Upvote a content item (atomic increment, one vote per device) */
    suspend fun upvote(contentId: String): Boolean {
        val safeId = sanitizeKey(contentId)
        // Check if already voted
        if (hasVoted(safeId)) return false

        return try {
            // Atomic increment via transaction
            votesRef.child(safeId).child("upvotes").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = current + 1
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
            // Mark this device as having voted
            votersRef.child(safeId).child(deviceId).setValue(true).await()
            true
        } catch (_: Exception) { false }
    }

    /** Remove upvote (atomic decrement) */
    suspend fun removeUpvote(contentId: String): Boolean {
        val safeId = sanitizeKey(contentId)
        if (!hasVoted(safeId)) return false

        return try {
            votesRef.child(safeId).child("upvotes").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = (current - 1).coerceAtLeast(0)
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
            votersRef.child(safeId).child(deviceId).removeValue().await()
            true
        } catch (_: Exception) { false }
    }

    /** Downvote = hide locally (not stored in Firebase) */
    fun hideContent(contentId: String) {
        val updated = _hiddenIds.value + contentId
        _hiddenIds.value = updated
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit().putStringSet("hidden_ids", updated).apply()
    }

    /** Unhide a previously downvoted item */
    fun unhideContent(contentId: String) {
        val updated = _hiddenIds.value - contentId
        _hiddenIds.value = updated
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit().putStringSet("hidden_ids", updated).apply()
    }

    fun isHidden(contentId: String): Boolean = contentId in _hiddenIds.value

    /** Batch fetch vote counts for a list of IDs (for grid display) */
    fun getVoteCounts(contentIds: List<String>): Flow<Map<String, Int>> = callbackFlow {
        val counts = mutableMapOf<String, Int>()
        val listeners = mutableListOf<Pair<String, ValueEventListener>>()

        contentIds.take(50).forEach { id -> // Limit to prevent excessive listeners
            val safeId = sanitizeKey(id)
            val ref = votesRef.child(safeId).child("upvotes")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    counts[id] = snapshot.getValue(Int::class.java) ?: 0
                    trySend(counts.toMap())
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(listener)
            listeners.add(safeId to listener)
        }

        awaitClose {
            listeners.forEach { (safeId, listener) ->
                votesRef.child(safeId).child("upvotes").removeEventListener(listener)
            }
        }
    }

    // Firebase keys can't contain . # $ [ ] /
    private fun sanitizeKey(id: String): String =
        id.replace(Regex("[.#$\\[\\]/]"), "_")
}

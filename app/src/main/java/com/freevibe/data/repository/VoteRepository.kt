package com.freevibe.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Community voting + admin moderation via Firebase Realtime Database.
 *
 * Firebase structure:
 *   /votes/{contentId}/upvotes = Int           (community vote tally)
 *   /voters/{contentId}/{deviceId} = true      (prevents double-voting)
 *   /moderation/{contentId} = true             (admin global hide — removes for ALL users)
 *
 * Regular downvote = local-only hide (SharedPreferences).
 * Admin downvote = global hide via /moderation (visible to no one).
 */
@Singleton
class VoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val db by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }
    private val votesRef get() = db?.child("votes")
    private val votersRef get() = db?.child("voters")
    private val moderationRef get() = db?.child("moderation")

    @Suppress("HardwareIds")
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    /** Admin device IDs that can globally moderate content */
    private val adminDeviceIds = setOf(
        "9eb287ea039a5b73", // SysAdminDoc (adb)
        "abed3c69ec4115f2", // SysAdminDoc (app runtime)
    )

    val isAdmin: Boolean get() = deviceId in adminDeviceIds

    // ── Local hidden IDs (user's personal downvotes) ──

    private val _localHiddenIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        val prefs = context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
        _localHiddenIds.value = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()
    }

    // ── Global moderation list (admin-hidden, synced from Firebase) ──

    private val _moderatedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Listen for moderation list changes
        try {
            moderationRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _moderatedIds.value = snapshot.children.mapNotNull { it.key }.toSet()
                }
                override fun onCancelled(error: DatabaseError) {
                    if (com.freevibe.BuildConfig.DEBUG) Log.w("VoteRepo", "Moderation listener cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) Log.w("VoteRepo", "Firebase init failed: ${e.message}")
        }
    }

    /** Combined hidden IDs: local downvotes + global moderation */
    val hiddenIds: Flow<Set<String>> = combine(_localHiddenIds, _moderatedIds) { local, moderated ->
        local + moderated
    }

    // ── Voting ──

    fun getVoteCount(contentId: String): Flow<Int> = callbackFlow {
        val votesRefInstance = votesRef
        if (votesRefInstance == null) { trySend(0); awaitClose {}; return@callbackFlow }
        val safeId = sanitizeKey(contentId)
        val ref = votesRefInstance.child(safeId).child("upvotes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Int::class.java) ?: 0)
            }
            override fun onCancelled(error: DatabaseError) { trySend(0) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun hasVoted(contentId: String): Boolean {
        val votersRefInstance = votersRef ?: return false
        val safeId = sanitizeKey(contentId)
        return try {
            votersRefInstance.child(safeId).child(deviceId).get().await().exists()
        } catch (_: Exception) { false }
    }

    suspend fun upvote(contentId: String): Boolean {
        val votesRefInstance = votesRef ?: return false
        val votersRefInstance = votersRef ?: return false
        val safeId = sanitizeKey(contentId)
        if (hasVoted(safeId)) return false
        return try {
            votesRefInstance.child(safeId).child("upvotes").runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    data.value = (data.getValue(Int::class.java) ?: 0) + 1
                    return Transaction.success(data)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
            })
            votersRefInstance.child(safeId).child(deviceId).setValue(true).await()
            true
        } catch (_: Exception) { false }
    }

    // ── Downvote / Hide ──

    /** Regular user: hide locally. Admin: hide globally for everyone. */
    suspend fun downvote(contentId: String) {
        if (com.freevibe.BuildConfig.DEBUG) Log.d("VoteRepo", "downvote($contentId) deviceId=$deviceId isAdmin=$isAdmin")
        if (isAdmin) {
            moderateHide(contentId)
        } else {
            hideLocally(contentId)
        }
    }

    /** Local-only hide (regular users) */
    fun hideLocally(contentId: String) {
        val updated = _localHiddenIds.value + contentId
        _localHiddenIds.value = updated
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit().putStringSet("hidden_ids", updated).apply()
    }

    /** Admin: globally hide content for ALL users via Firebase */
    suspend fun moderateHide(contentId: String) {
        val moderationRefInstance = moderationRef
        if (moderationRefInstance == null) { hideLocally(contentId); return }
        val safeId = sanitizeKey(contentId)
        if (com.freevibe.BuildConfig.DEBUG) Log.d("VoteRepo", "moderateHide: safeId=$safeId path=moderation/$safeId")
        try {
            moderationRefInstance.child(safeId).setValue(true).await()
            if (com.freevibe.BuildConfig.DEBUG) Log.d("VoteRepo", "Admin moderated OK: $contentId")
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) Log.e("VoteRepo", "Moderation FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            hideLocally(contentId)
        }
    }

    /** Admin: remove global moderation (unhide for everyone) */
    suspend fun moderateUnhide(contentId: String) {
        val moderationRefInstance = moderationRef ?: return
        val safeId = sanitizeKey(contentId)
        try {
            moderationRefInstance.child(safeId).removeValue().await()
        } catch (_: Exception) {}
    }

    /** Unhide locally */
    fun unhideLocally(contentId: String) {
        val updated = _localHiddenIds.value - contentId
        _localHiddenIds.value = updated
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit().putStringSet("hidden_ids", updated).apply()
    }

    fun isHidden(contentId: String): Boolean =
        contentId in _localHiddenIds.value || contentId in _moderatedIds.value

    // ── Batch ──

    fun getVoteCounts(contentIds: List<String>): Flow<Map<String, Int>> = callbackFlow {
        val votesRefInstance = votesRef
        if (votesRefInstance == null) { trySend(emptyMap()); awaitClose {}; return@callbackFlow }
        val counts = mutableMapOf<String, Int>()
        val listeners = mutableListOf<Pair<String, ValueEventListener>>()

        contentIds.take(50).forEach { id ->
            val safeId = sanitizeKey(id)
            val ref = votesRefInstance.child(safeId).child("upvotes")
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
                votesRefInstance.child(safeId).child("upvotes").removeEventListener(listener)
            }
        }
    }

    /** Get top upvoted content IDs globally, sorted by vote count descending */
    suspend fun getTopVotedIds(limit: Int = 50): List<Pair<String, Int>> {
        val votesRefInstance = votesRef ?: return emptyList()
        return try {
            val snapshot = votesRefInstance.get().await()
            snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                val upvotes = child.child("upvotes").getValue(Int::class.java) ?: 0
                if (upvotes > 0) key to upvotes else null
            }.sortedByDescending { it.second }.take(limit)
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) android.util.Log.e("VoteRepo", "getTopVotedIds failed: ${e.message}")
            emptyList()
        }
    }

    fun sanitizeKey(id: String): String =
        id.replace(Regex("[.#$\\[\\]/]"), "_")
}

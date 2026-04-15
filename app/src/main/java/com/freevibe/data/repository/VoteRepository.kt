package com.freevibe.data.repository

import android.content.Context
import android.util.Log
import com.freevibe.service.CommunityIdentityProvider
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

private val FIREBASE_KEY_REGEX = Regex("[.#$\\[\\]/]")

internal fun sanitizeVoteKey(id: String): String =
    id.replace(FIREBASE_KEY_REGEX, "_")

internal fun matchesHiddenIds(hiddenIds: Set<String>, vararg candidateIds: String?): Boolean =
    candidateIds.asSequence()
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .any { candidate ->
            candidate in hiddenIds || sanitizeVoteKey(candidate) in hiddenIds
        }

private fun expandHiddenIds(ids: Set<String>): Set<String> = buildSet(ids.size * 2) {
    ids.forEach { id ->
        val normalized = id.trim()
        if (normalized.isNotEmpty()) {
            add(normalized)
            add(sanitizeVoteKey(normalized))
        }
    }
}

/**
 * Community voting + admin moderation via Firebase Realtime Database.
 *
 * Firebase structure:
 *   /votes/{contentId}/upvotes = Int                 (community vote tally)
 *   /votes/{contentId}/voters/{deviceId} = true      (prevents double-voting, transactional)
 *   /voters/{contentId}/{deviceId} = true            (legacy path still read for compatibility)
 *   /moderation/{contentId} = true                   (admin global hide — removes for ALL users)
 *
 * Regular downvote = local-only hide (SharedPreferences).
 * Admin downvote = global hide via /moderation (visible to no one).
 */
@Singleton
class VoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
) {
    private val db by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }
    private val votesRef get() = db?.child("votes")
    private val votersRef get() = db?.child("voters")
    private val moderationRef get() = db?.child("moderation")

    /**
     * Admin device IDs stored as SHA-256 hashes so plaintext IDs aren't in the APK.
     * TODO: Move admin authorization to Firebase Custom Claims / Security Rules.
     *       Client-side checks are spoofable on rooted devices.
     */
    private val adminDeviceIdHashes = setOf(
        "70221777b62eabc52f5d0625fe7fd27f6a96f1a314231f0a33e7db98cb7da49b",
        "8d5c02d2bc8767d04eb1cdc9a662a16a735fb130374d6c98b189ff787b78f80c",
    )

    /** Admin Firebase UIDs can be added here until custom claims/rules are in place */
    private val adminUserIds = emptySet<String>()

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    val isAdmin: Boolean
        get() = identityProvider.currentUserId() in adminUserIds ||
            sha256(identityProvider.legacyDeviceId) in adminDeviceIdHashes

    // ── Local hidden IDs (user's personal downvotes) ──

    private val _localHiddenIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        val prefs = context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
        _localHiddenIds.value = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()
    }

    // ── Global moderation list (admin-hidden, synced from Firebase) ���─

    private val _moderatedIds = MutableStateFlow<Set<String>>(emptySet())
    private var moderationListener: ValueEventListener? = null

    init {
        // Listen for moderation list changes
        try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _moderatedIds.value = snapshot.children.mapNotNull { it.key }.toSet()
                }
                override fun onCancelled(error: DatabaseError) {
                    if (com.freevibe.BuildConfig.DEBUG) Log.w("VoteRepo", "Moderation listener cancelled: ${error.message}")
                }
            }
            moderationRef?.addValueEventListener(listener)
            moderationListener = listener
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) Log.w("VoteRepo", "Firebase init failed: ${e.message}")
        }
    }

    /** Combined hidden IDs: local downvotes + global moderation */
    val hiddenIds: Flow<Set<String>> = combine(_localHiddenIds, _moderatedIds) { local, moderated ->
        expandHiddenIds(local) + expandHiddenIds(moderated)
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

    suspend fun hasVoted(contentId: String, alreadySanitized: Boolean = false): Boolean {
        val safeId = if (alreadySanitized) contentId else sanitizeKey(contentId)
        return try {
            identityProvider.knownIdentityIds()
                .map(::sanitizeKey)
                .any { voterId ->
                    votesRef?.child(safeId)?.child("voters")?.child(voterId)?.get()?.await()?.exists() == true ||
                        votersRef?.child(safeId)?.child(voterId)?.get()?.await()?.exists() == true
                }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    suspend fun upvote(contentId: String): Boolean {
        val votesRefInstance = votesRef ?: return false
        val votersRefInstance = votersRef
        val safeId = sanitizeKey(contentId)
        val voterId = sanitizeKey(identityProvider.ensureSignedIn())
        if (hasVoted(safeId, alreadySanitized = true)) return false
        return suspendCancellableCoroutine { continuation ->
            votesRefInstance.child(safeId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val nestedVoters = data.child("voters")
                    if (nestedVoters.child(voterId).getValue(Boolean::class.java) == true) {
                        return Transaction.abort()
                    }

                    val currentVotes = data.child("upvotes").getValue(Int::class.java) ?: 0
                    data.child("upvotes").value = currentVotes + 1
                    data.child("voters").child(voterId).value = true
                    return Transaction.success(data)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (!continuation.isActive) return

                    if (error != null) {
                        if (com.freevibe.BuildConfig.DEBUG) {
                            Log.w("VoteRepo", "upvote failed for $safeId: ${error.message}")
                        }
                        continuation.resume(false)
                        return
                    }

                    if (committed) {
                        // Mirror the voter marker to the legacy path so older installs keep seeing the vote.
                        votersRefInstance?.child(safeId)?.child(voterId)?.setValue(true)
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            })
        }
    }

    // ── Downvote / Hide ──

    /** Regular user: hide locally. Admin: hide globally for everyone. */
    suspend fun downvote(contentId: String) {
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.d("VoteRepo", "downvote($contentId) userId=${identityProvider.currentUserId()} isAdmin=$isAdmin")
        }
        if (isAdmin) {
            moderateHide(contentId)
        } else {
            hideLocally(contentId)
        }
    }

    /** Local-only hide (regular users) */
    fun hideLocally(contentId: String) {
        val updated = _localHiddenIds.updateAndGet { it + contentId }
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
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Unhide locally */
    fun unhideLocally(contentId: String) {
        val updated = _localHiddenIds.updateAndGet { it - contentId }
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit().putStringSet("hidden_ids", updated).apply()
    }

    fun isHidden(contentId: String): Boolean =
        matchesHiddenIds(_localHiddenIds.value, contentId) ||
            matchesHiddenIds(_moderatedIds.value, contentId)

    // ── Batch ──

    fun getVoteCounts(contentIds: List<String>): Flow<Map<String, Int>> = callbackFlow {
        val votesRefInstance = votesRef
        if (votesRefInstance == null) { trySend(emptyMap()); awaitClose {}; return@callbackFlow }
        val counts = java.util.concurrent.ConcurrentHashMap<String, Int>()
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
        sanitizeVoteKey(id)
}

package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * First-launch worker that materializes the Aura Originals CC0 sound pack to
 * `filesDir/aura_originals/`. Roadmap N-5.
 *
 * - Reads the curated list from `assets/aura_originals_manifest.json`.
 * - Each entry is downloaded once over HTTPS into a temp file, sha256-verified,
 *   then atomically renamed. Mismatched files are deleted and retried via
 *   WorkManager exponential backoff.
 * - Each file capped at 5 MB (per-ringtone ceiling); manifest's `totalBytes`
 *   is the soft total ceiling enforced before any download starts.
 * - Honors `requiresUnmeteredNetwork()` so the bundle doesn't burn cellular
 *   data by default.
 *
 * The worker is idempotent: if every file already matches its expected hash,
 * doWork returns immediately. BundledContentProvider consults the same
 * `filesDir/aura_originals/` directory to surface local-file-backed bundled
 * sounds in the Sounds tab.
 */
@HiltWorker
class AuraOriginalsDownloader @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val manifestLoader: AuraOriginalsManifestLoader,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val manifest = manifestLoader.load() ?: return@withContext Result.success()
            if (manifest.sounds.isEmpty()) return@withContext Result.success()
            val targetDir = File(applicationContext.filesDir, BUNDLE_DIRNAME).apply { mkdirs() }
            val targetCanonical = targetDir.canonicalFile
            // Reject manifests whose declared total exceeds our hard cap. A zero or
            // negative `totalBytes` is treated as "unknown" and we still enforce the
            // running cap per-entry + globally below.
            val declaredTotal = manifest.totalBytes
            if (declaredTotal > MAX_TOTAL_BYTES) {
                if (com.freevibe.BuildConfig.DEBUG) {
                    android.util.Log.w(
                        TAG,
                        "Manifest claims $declaredTotal bytes which exceeds $MAX_TOTAL_BYTES; refusing to download",
                    )
                }
                return@withContext Result.failure()
            }

            var failedAny = false
            var runningBytes = 0L
            manifest.sounds.forEach { entry ->
                val outcome = downloadEntry(entry, targetCanonical, runningBytes)
                if (outcome.success) {
                    runningBytes += outcome.bytesAdded
                } else {
                    failedAny = true
                }
            }
            if (failedAny) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "doWork failed: ${e.message}", e)
            }
            Result.retry()
        }
    }

    private data class EntryOutcome(val success: Boolean, val bytesAdded: Long)

    private fun downloadEntry(
        entry: AuraOriginalsEntry,
        targetDir: File,
        runningBytes: Long,
    ): EntryOutcome {
        // Defense-in-depth: the manifest is bundled in the APK and not user-supplied,
        // but a sanitized id keeps `entry.id` from escaping the bundle directory via
        // "../foo" or absolute paths, and rejects bad characters that would corrupt
        // the on-disk layout.
        val safeId = sanitizeEntryId(entry.id) ?: run {
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Rejecting entry with unsafe id: '${entry.id}'")
            }
            return EntryOutcome(success = false, bytesAdded = 0L)
        }
        // HTTPS-only — refuse any other scheme so a typo or a tampered manifest can't
        // produce a cleartext or local-file fetch.
        if (!isAllowedDownloadUrl(entry.url)) {
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Rejecting non-HTTPS url for ${entry.id}: ${entry.url}")
            }
            return EntryOutcome(success = false, bytesAdded = 0L)
        }
        if (entry.sha256.isBlank()) {
            // verifyHash already rejects this, but failing here gives a clearer log
            // and saves a network round trip.
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Rejecting entry ${entry.id}: missing sha256")
            }
            return EntryOutcome(success = false, bytesAdded = 0L)
        }
        val finalFile = File(targetDir, "$safeId.${guessExtension(entry.url)}")
        // Final guard against any sneaky path manipulation that survived sanitization.
        if (!isInside(targetDir, finalFile)) {
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Refusing to write outside bundle dir for ${entry.id}")
            }
            return EntryOutcome(success = false, bytesAdded = 0L)
        }
        if (finalFile.exists() && verifyHash(finalFile, entry.sha256)) {
            return EntryOutcome(success = true, bytesAdded = finalFile.length())
        }
        val remainingBudget = (MAX_TOTAL_BYTES - runningBytes).coerceAtLeast(0L)
        if (remainingBudget <= 0L) {
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Bundle budget exhausted; skipping ${entry.id}")
            }
            return EntryOutcome(success = false, bytesAdded = 0L)
        }
        val perFileBudget = remainingBudget.coerceAtMost(MAX_PER_FILE_BYTES)
        val tmpFile = File(targetDir, "$safeId.tmp")
        try {
            val request = Request.Builder().url(entry.url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return EntryOutcome(false, 0L)
                val body = response.body ?: return EntryOutcome(false, 0L)
                val length = body.contentLength()
                if (length > perFileBudget) {
                    if (com.freevibe.BuildConfig.DEBUG) {
                        android.util.Log.w(TAG, "Entry ${entry.id} too large: $length bytes (budget=$perFileBudget)")
                    }
                    return EntryOutcome(false, 0L)
                }
                tmpFile.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    val source = body.byteStream()
                    var written = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > perFileBudget) {
                            tmpFile.delete()
                            return EntryOutcome(false, 0L)
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!verifyHash(tmpFile, entry.sha256)) {
                tmpFile.delete()
                return EntryOutcome(false, 0L)
            }
            val finalLength = tmpFile.length()
            val renamed = tmpFile.renameTo(finalFile) || tmpFile.copyToWithCleanup(finalFile)
            return EntryOutcome(success = renamed, bytesAdded = if (renamed) finalLength else 0L)
        } catch (e: CancellationException) {
            tmpFile.delete()
            throw e
        } catch (e: Exception) {
            tmpFile.delete()
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "downloadEntry ${entry.id} failed: ${e.message}")
            }
            return EntryOutcome(false, 0L)
        }
    }

    private fun File.copyToWithCleanup(target: File): Boolean = try {
        copyTo(target, overwrite = true)
        delete()
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val TAG = "AuraOriginals"
        private const val BUNDLE_DIRNAME = "aura_originals"
        private const val MAX_PER_FILE_BYTES = 5L * 1024L * 1024L // 5 MB per ringtone
        private const val MAX_TOTAL_BYTES = 80L * 1024L * 1024L // 80 MB total pack
        private const val UNIQUE_WORK_NAME = "aura_originals_download"

        /** Bundled-content directory exposed to BundledContentProvider for local lookups. */
        fun bundleDir(context: Context): File = File(context.filesDir, BUNDLE_DIRNAME)

        /**
         * Enqueue the worker once per cold launch. Wi-Fi-only by default to avoid
         * burning user data. Marked expedited so the first-run UX gets the bundle
         * promptly when the user is on Wi-Fi.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = OneTimeWorkRequestBuilder<AuraOriginalsDownloader>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Returns a path-safe filename base for an entry id, or null if the id is
         * empty / contains characters that would let it escape its parent directory.
         * Allowed chars: ASCII letters, digits, dash, underscore, dot (but not "..").
         */
        internal fun sanitizeEntryId(id: String): String? {
            if (id.isBlank()) return null
            // Reject any path separator or null byte outright.
            if (id.contains('/') || id.contains('\\') || id.contains(' ')) return null
            // Reject dot-only ids that would resolve to "." or "..".
            if (id == "." || id == "..") return null
            // Reject anything that isn't a safe filename character.
            val safe = id.all { ch ->
                ch == '-' || ch == '_' || ch == '.' ||
                    (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9')
            }
            if (!safe) return null
            // Bound the length so an absurd id can't produce an absurd filename.
            if (id.length > 64) return null
            return id
        }

        /**
         * HTTPS-only gate for manifest URLs. Anything else (http://, file://, content://,
         * data:, ftp://) is rejected so a typo or a tampered manifest can't redirect the
         * download to cleartext or a local path.
         */
        internal fun isAllowedDownloadUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            val schemeEnd = trimmed.indexOf(':')
            if (schemeEnd <= 0) return false
            val scheme = trimmed.substring(0, schemeEnd).lowercase(java.util.Locale.ROOT)
            return scheme == "https"
        }

        /**
         * True iff `child` resolves inside (or equal to) `parent` after canonicalization.
         * Used as the final guard against any path-escape the sanitizer missed.
         */
        internal fun isInside(parent: File, child: File): Boolean = try {
            val parentPath = parent.canonicalPath
            val childPath = child.canonicalFile.path
            childPath == parentPath || childPath.startsWith(parentPath + File.separator)
        } catch (_: Exception) {
            false
        }

        internal fun guessExtension(url: String): String = when {
            url.endsWith(".ogg", ignoreCase = true) -> "ogg"
            url.endsWith(".m4a", ignoreCase = true) -> "m4a"
            url.endsWith(".wav", ignoreCase = true) -> "wav"
            url.endsWith(".flac", ignoreCase = true) -> "flac"
            else -> "mp3"
        }

        internal fun verifyHash(file: File, expectedSha256: String): Boolean {
            if (expectedSha256.isBlank()) return false
            val expected = expectedSha256.lowercase()
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            return hex == expected
        }
    }
}

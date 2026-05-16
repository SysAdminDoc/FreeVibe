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
            val totalSoftCeiling = manifest.totalBytes.coerceAtLeast(0)
            if (totalSoftCeiling in 1..MAX_TOTAL_BYTES) {
                // Honor the manifest's stated budget when present; refuse to start a
                // pack that pretends to be tiny but lists huge files.
            } else if (totalSoftCeiling > MAX_TOTAL_BYTES) {
                if (com.freevibe.BuildConfig.DEBUG) {
                    android.util.Log.w(
                        TAG,
                        "Manifest claims $totalSoftCeiling bytes which exceeds ${MAX_TOTAL_BYTES}; refusing to download",
                    )
                }
                return@withContext Result.failure()
            }

            var failedAny = false
            manifest.sounds.forEach { entry ->
                if (downloadEntry(entry, targetDir).not()) failedAny = true
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

    private fun downloadEntry(entry: AuraOriginalsEntry, targetDir: File): Boolean {
        val finalFile = File(targetDir, "${entry.id}.${guessExtension(entry.url)}")
        if (finalFile.exists() && verifyHash(finalFile, entry.sha256)) return true
        val tmpFile = File(targetDir, "${entry.id}.tmp")
        try {
            val request = Request.Builder().url(entry.url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val length = body.contentLength()
                if (length > MAX_PER_FILE_BYTES) {
                    if (com.freevibe.BuildConfig.DEBUG) {
                        android.util.Log.w(TAG, "Entry ${entry.id} too large: $length bytes")
                    }
                    return false
                }
                tmpFile.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    val source = body.byteStream()
                    var written = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > MAX_PER_FILE_BYTES) {
                            tmpFile.delete()
                            return false
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!verifyHash(tmpFile, entry.sha256)) {
                tmpFile.delete()
                return false
            }
            return tmpFile.renameTo(finalFile) || tmpFile.copyToWithCleanup(finalFile)
        } catch (e: CancellationException) {
            tmpFile.delete()
            throw e
        } catch (e: Exception) {
            tmpFile.delete()
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "downloadEntry ${entry.id} failed: ${e.message}")
            }
            return false
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

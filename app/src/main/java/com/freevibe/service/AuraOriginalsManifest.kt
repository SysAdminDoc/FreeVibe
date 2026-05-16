package com.freevibe.service

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aura Originals — curated CC0 sound pack. Roadmap N-5.
 *
 * The manifest lists every sound that should be available offline after the
 * first-launch download. Each entry carries:
 *  - `id`            — stable ID, also the on-disk filename base.
 *  - `category`      — ringtone | notification | alarm.
 *  - `name`          — display name in Aura's Sounds tab.
 *  - `durationSec`   — server-published duration (for UI; not authoritative).
 *  - `url`           — HTTPS source; downloader fetches once and pins by hash.
 *  - `sha256`        — required; mismatched downloads are rejected and retried
 *                       on the next worker run.
 *  - `license`       — CC0 1.0 expected for the bundled pack; tracked anyway
 *                       so attribution can ship in Settings → Licenses.
 *  - `sourceUrl`     — original page (e.g. Freesound URL) for attribution.
 *  - `tags`          — search keywords surfaced in the in-app feed.
 *
 * Curation guidance lives in docs/aura-originals-curation.md.
 */
@JsonClass(generateAdapter = true)
data class AuraOriginalsManifest(
    val version: Int = 1,
    val manifestRevision: String = "",
    val totalBytes: Long = 0,
    val sounds: List<AuraOriginalsEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AuraOriginalsEntry(
    val id: String,
    val category: String,
    val name: String,
    val durationSec: Double,
    val url: String,
    val sha256: String,
    val license: String = "CC0 1.0",
    val sourceUrl: String = "",
    val tags: List<String> = emptyList(),
)

@Singleton
class AuraOriginalsManifestLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(AuraOriginalsManifest::class.java)

    suspend fun load(): AuraOriginalsManifest? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                adapter.fromJson(reader.readText())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "AuraOriginals",
                    "Manifest load failed: ${e.message}",
                )
            }
            null
        }
    }

    companion object {
        private const val ASSET_NAME = "aura_originals_manifest.json"
    }
}

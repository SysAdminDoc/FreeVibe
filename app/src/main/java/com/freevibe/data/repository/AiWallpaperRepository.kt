package com.freevibe.data.repository

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.stability.StabilityAiApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Hoisted out of the per-call hot path. `\\s+` was being compiled on every successful
// generation just to mine 5 tag words from the prompt.
private val WHITESPACE_RE = "\\s+".toRegex()

enum class AiStyle(val label: String, val preset: String) {
    PHOTOGRAPHIC("Photo", "photographic"),
    ANIME("Anime", "anime"),
    DIGITAL_ART("Digital Art", "digital-art"),
    CINEMATIC("Cinematic", "cinematic"),
    FANTASY("Fantasy", "fantasy-art"),
    NEON_PUNK("Neon", "neon-punk"),
    PIXEL_ART("Pixel Art", "pixel-art"),
    NONE("No Style", ""),
}

@Singleton
class AiWallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: StabilityAiApi,
) {
    private val dir: File
        get() = File(context.filesDir, "ai_wallpapers").also { it.mkdirs() }

    suspend fun generate(
        prompt: String,
        style: AiStyle = AiStyle.PHOTOGRAPHIC,
        apiKey: String,
    ): Result<Wallpaper> = withContext(Dispatchers.IO) {
        // Note: `runCatching` here intentionally lets cancellation propagate. Without the
        // explicit rethrow, a ViewModel scope cancel (back-navigation mid-generate) would
        // be captured as a failure Result and surface to the user as a generic error.
        try {
            val textType = "text/plain".toMediaType()
            val parts = buildMap<String, RequestBody> {
                put("prompt", prompt.toRequestBody(textType))
                put("aspect_ratio", "9:16".toRequestBody(textType))
                put("output_format", "png".toRequestBody(textType))
                if (style.preset.isNotEmpty()) {
                    put("style_preset", style.preset.toRequestBody(textType))
                }
            }

            val response = api.generateImage(
                authHeader = "Bearer $apiKey",
                accept = "image/*",
                parts = parts,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                throw IllegalStateException(friendlyErrorMessage(response.code(), errorBody))
            }

            val body = response.body()
                ?: throw IllegalStateException("Empty response from Stability AI")

            val id = UUID.randomUUID().toString()
            val file = File(dir, "$id.png")
            val tmp = File(dir, "$id.tmp")
            try {
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } catch (e: Throwable) {
                runCatching { tmp.delete() }
                if (e is CancellationException) throw e
                throw e
            }

            // Reclaim storage *after* the new file lands so an inflight generation never
            // races against eviction. The cap is 50 files; older PNGs go first.
            runCatching { pruneOldFilesInternal(MAX_GENERATED_FILES) }

            val promptWords = prompt.split(WHITESPACE_RE)
                .map { it.lowercase().filter { c -> c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .take(5)

            Result.success(
                Wallpaper(
                    id = id,
                    source = ContentSource.AI_GENERATED,
                    thumbnailUrl = file.toURI().toString(),
                    fullUrl = file.toURI().toString(),
                    width = 576,
                    height = 1024,
                    category = "AI Generated",
                    tags = buildList {
                        add("ai-generated")
                        if (style.preset.isNotEmpty()) add(style.preset)
                        addAll(promptWords)
                    },
                    uploaderName = "AI",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }


    /** Delete generated images beyond the most recent [maxCount] to reclaim storage. */
    suspend fun pruneOldFiles(maxCount: Int = MAX_GENERATED_FILES) = withContext(Dispatchers.IO) {
        pruneOldFilesInternal(maxCount)
    }

    /** Same as [pruneOldFiles] but already on the IO dispatcher (no extra withContext). */
    private fun pruneOldFilesInternal(maxCount: Int) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        // Sweep .tmp leftovers (interrupted writes) too — they accumulate otherwise.
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "tmp" }
            ?.forEach { runCatching { it.delete() } }
        files.drop(maxCount).forEach { runCatching { it.delete() } }
    }

    companion object {
        /** Hard cap on stored generated wallpapers. Surfaced for tests. */
        internal const val MAX_GENERATED_FILES = 50

        /**
         * Map HTTP error codes from Stability AI to actionable user messages. Lives in
         * the companion object so it can be unit-tested without spinning up Hilt or a
         * fake Retrofit. Pure JVM function.
         *
         * @param code HTTP status code from the Stability AI API response.
         * @param errorBody Optional response body text; appended verbatim when non-blank.
         */
        internal fun friendlyErrorMessage(code: Int, errorBody: String?): String {
            val base = when (code) {
                401 -> "Stability AI key is invalid or expired. Update it in the key field."
                402 -> "Stability AI account is out of credits. Top up at platform.stability.ai."
                403 -> "Prompt was rejected by Stability AI's content policy. Try rewording."
                422 -> "Prompt could not be processed. Try a shorter or simpler description."
                429 -> "Stability AI rate limit hit. Wait a minute and try again."
                in 500..599 -> "Stability AI server error ($code). Try again shortly."
                else -> "Generation failed (HTTP $code)."
            }
            return if (!errorBody.isNullOrBlank()) "$base $errorBody" else base
        }
    }
}

package com.freevibe.data.repository

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.stability.StabilityAiApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
        runCatching {
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
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw IllegalStateException("Generation failed (${response.code()}): $errorBody")
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
            } catch (e: Exception) {
                try { tmp.delete() } catch (_: Exception) { }
                throw e
            }

            val promptWords = prompt.split("\\s+".toRegex())
                .map { it.lowercase().filter { c -> c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .take(5)

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
            )
        }
    }

    /** Delete generated images beyond the most recent [maxCount] to reclaim storage. */
    suspend fun pruneOldFiles(maxCount: Int = 50) = withContext(Dispatchers.IO) {
        val files = dir.listFiles()
            ?.filter { it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: return@withContext
        files.drop(maxCount).forEach { it.delete() }
    }
}

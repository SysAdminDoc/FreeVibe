package com.freevibe.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.service.ColorExtractor
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val WALLPAPER_UPLOAD_NAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\- ]")
private val WALLPAPER_UPLOAD_TAG_SANITIZE_REGEX = Regex("[^a-z0-9_\\- ]")
private val WALLPAPER_UPLOAD_WHITESPACE_REGEX = Regex("\\s+")
private val WALLPAPER_UPLOAD_STORAGE_SEGMENT_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val ALLOWED_WALLPAPER_UPLOAD_MIMES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
)
private val ALLOWED_WALLPAPER_UPLOAD_CATEGORIES = setOf(
    "abstract",
    "amoled",
    "nature",
    "minimal",
    "city",
    "space",
    "other",
)
private const val MAX_WALLPAPER_UPLOAD_BYTES = 4L * 1024L * 1024L
private const val MAX_WALLPAPER_LONG_EDGE = 2560
private const val MAX_WALLPAPER_TAGS = 8
private const val MAX_WALLPAPER_TAG_LENGTH = 24
private const val FALLBACK_PHONE_ASPECT = 9f / 16f

@Singleton
class WallpaperUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
    private val colorExtractor: ColorExtractor,
) {
    private data class WallpaperUploadInfo(
        val baseName: String,
        val originalFileName: String,
        val mimeType: String,
    )

    private data class PreparedWallpaper(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val colors: List<String>,
    )

    private val storage by lazy {
        try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
    }
    private val database by lazy {
        try { FirebaseDatabase.getInstance() } catch (_: Exception) { null }
    }
    private val wallpapersRef by lazy { database?.reference?.child("community_wallpapers") }

    suspend fun uploadWallpaper(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String>,
        onProgress: (Float) -> Unit = {},
    ): Result<Wallpaper> = withContext(Dispatchers.IO) {
        try {
            val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
            val wallpapersRefInstance = wallpapersRef ?: throw IllegalStateException("Firebase Database not available")
            val sanitizedName = name.trim().take(100)
            if (sanitizedName.isBlank()) throw IllegalArgumentException("Wallpaper name cannot be empty")

            val normalizedCategory = normalizeWallpaperUploadCategory(category)
            val normalizedTags = sanitizeWallpaperUploadTags(tags)
            val uploadInfo = resolveUploadInfo(localUri, sanitizedName)
            if (!isSupportedWallpaperUploadMime(uploadInfo.mimeType)) {
                throw IllegalArgumentException("Unsupported image format")
            }

            ensureReadableWallpaper(localUri)
            val prepared = prepareWallpaper(localUri)
            val timestamp = System.currentTimeMillis()
            val uploaderId = identityProvider.ensureSignedIn()
            val uploaderLabel = identityProvider.currentUploaderLabel()
            val storagePath =
                "wallpapers/${sanitizeWallpaperUploadStorageSegment(uploaderId)}/${timestamp}_${uploadInfo.baseName}.jpg"
            val storageRef = storageInstance.reference.child(storagePath)
            var metadataSaved = false

            try {
                val uploadTask = storageRef.putBytes(
                    prepared.bytes,
                    StorageMetadata.Builder().setContentType("image/jpeg").build(),
                )
                uploadTask.addOnProgressListener { snapshot ->
                    val totalByteCount = snapshot.totalByteCount.takeIf { it > 0 } ?: 0L
                    val progress = if (totalByteCount > 0) {
                        snapshot.bytesTransferred.toFloat() / totalByteCount
                    } else {
                        0f
                    }
                    onProgress(progress.coerceIn(0f, 1f))
                }
                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                val pushRef = wallpapersRefInstance.push()
                val key = pushRef.key ?: throw IllegalStateException("Could not create wallpaper record")
                val metadata = mapOf(
                    "name" to sanitizedName,
                    "category" to normalizedCategory,
                    "tags" to normalizedTags,
                    "colors" to prepared.colors,
                    "thumbnailUrl" to downloadUrl,
                    "fullUrl" to downloadUrl,
                    "downloadUrl" to downloadUrl,
                    "width" to prepared.width,
                    "height" to prepared.height,
                    "fileSize" to prepared.bytes.size,
                    "fileType" to "image/jpeg",
                    "originalFileName" to uploadInfo.originalFileName,
                    "uploadedAt" to timestamp,
                    "uploaderId" to uploaderId,
                    "uploaderLabel" to uploaderLabel,
                    "votes" to 0,
                )
                pushRef.setValue(metadata).await()
                metadataSaved = true

                Result.success(
                    Wallpaper(
                        id = communityWallpaperId(key),
                        source = ContentSource.COMMUNITY,
                        thumbnailUrl = downloadUrl,
                        fullUrl = downloadUrl,
                        width = prepared.width,
                        height = prepared.height,
                        category = normalizedCategory,
                        tags = normalizedTags,
                        colors = prepared.colors,
                        fileSize = prepared.bytes.size.toLong(),
                        fileType = "image/jpeg",
                        uploaderName = uploaderLabel,
                    ),
                )
            } finally {
                if (!metadataSaved) runCatching { storageRef.delete().await() }
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Result.failure(e)
        }
    }

    suspend fun getCommunityWallpapers(limit: Int = 60): SearchResult<Wallpaper> = withContext(Dispatchers.IO) {
        val ref = wallpapersRef ?: return@withContext SearchResult(emptyList(), 0, 1, false)
        val snapshot = ref.orderByChild("uploadedAt").limitToLast(limit).get().await()
        val wallpapers = snapshot.children.mapNotNull(::snapshotToWallpaper)
            .sortedWith(
                compareByDescending<Wallpaper> { wallpaper ->
                    snapshot.child(wallpaper.id.removePrefix("cw_")).child("votes").getValue(Int::class.java) ?: 0
                }.thenByDescending { wallpaper ->
                    snapshot.child(wallpaper.id.removePrefix("cw_")).child("uploadedAt").getValue(Long::class.java) ?: 0L
                },
            )
        SearchResult(
            items = wallpapers,
            totalCount = wallpapers.size,
            currentPage = 1,
            hasMore = false,
        )
    }

    private fun snapshotToWallpaper(child: DataSnapshot): Wallpaper? {
        val key = child.key ?: return null
        val votes = child.child("votes").getValue(Int::class.java) ?: 0
        if (!shouldDisplayCommunityWallpaper(votes)) return null
        val fullUrl = child.child("fullUrl").getValue(String::class.java)
            ?: child.child("downloadUrl").getValue(String::class.java)
            ?: return null
        val thumbnailUrl = child.child("thumbnailUrl").getValue(String::class.java) ?: fullUrl
        val uploaderId = child.child("uploaderId").getValue(String::class.java).orEmpty()
        val uploaderLabel = child.child("uploaderLabel").getValue(String::class.java).orEmpty()
        return Wallpaper(
            id = communityWallpaperId(key),
            source = ContentSource.COMMUNITY,
            thumbnailUrl = thumbnailUrl,
            fullUrl = fullUrl,
            width = child.child("width").getValue(Int::class.java) ?: 0,
            height = child.child("height").getValue(Int::class.java) ?: 0,
            category = child.child("category").getValue(String::class.java).orEmpty(),
            tags = child.stringList("tags"),
            colors = child.stringList("colors"),
            fileSize = child.child("fileSize").getValue(Long::class.java) ?: 0L,
            fileType = child.child("fileType").getValue(String::class.java).orEmpty(),
            uploaderName = uploaderLabel.ifBlank { uploaderId.take(8) },
        )
    }

    private fun prepareWallpaper(localUri: Uri): PreparedWallpaper {
        val aspect = phoneAspectRatio()
        val cropped = decodeCenterCroppedBitmap(localUri, aspect)
        return try {
            val colors = paletteColorsToHex(colorExtractor.extractFromBitmap(cropped))
            val bytes = compressWallpaperForUpload(cropped)
            PreparedWallpaper(
                bytes = bytes,
                width = cropped.width,
                height = cropped.height,
                colors = colors,
            )
        } finally {
            cropped.recycle()
        }
    }

    private fun decodeCenterCroppedBitmap(localUri: Uri, targetAspect: Float): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(localUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw IllegalArgumentException("Selected image is unreadable")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Selected image is not a valid wallpaper")
        }

        val cropBounds = centerCropBounds(bounds.outWidth, bounds.outHeight, targetAspect)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(cropBounds.width(), cropBounds.height(), MAX_WALLPAPER_LONG_EDGE)
        }
        val decoded = resolver.openInputStream(localUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("Selected image is unreadable")

        val scaleX = decoded.width.toFloat() / bounds.outWidth.toFloat()
        val scaleY = decoded.height.toFloat() / bounds.outHeight.toFloat()
        val scaledRect = Rect(
            (cropBounds.left * scaleX).roundToInt().coerceIn(0, decoded.width - 1),
            (cropBounds.top * scaleY).roundToInt().coerceIn(0, decoded.height - 1),
            (cropBounds.right * scaleX).roundToInt().coerceIn(1, decoded.width),
            (cropBounds.bottom * scaleY).roundToInt().coerceIn(1, decoded.height),
        )
        val safeRect = normalizeCropRect(scaledRect, decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
        if (cropped !== decoded) decoded.recycle()
        return scaleDownLongEdge(cropped, MAX_WALLPAPER_LONG_EDGE)
    }

    private fun scaleDownLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun resolveUploadInfo(localUri: Uri, fallbackName: String): WallpaperUploadInfo {
        val resolver = context.contentResolver
        val originalFileName = resolver.query(localUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: (localUri.lastPathSegment?.substringAfterLast('/') ?: fallbackName)

        val inferredExtension = originalFileName.substringAfterLast('.', "")
            .lowercase(java.util.Locale.ROOT)
            .takeIf { it.isNotBlank() }
        val mimeType = resolver.getType(localUri)
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(java.util.Locale.ROOT)
            ?: inferredExtension
                ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
                ?.lowercase(java.util.Locale.ROOT)
            ?: ""
        val baseName = originalFileName.substringBeforeLast('.')
            .ifBlank { fallbackName }
            .replace(WALLPAPER_UPLOAD_NAME_SANITIZE_REGEX, "")
            .take(40)
            .ifBlank { "wallpaper" }

        return WallpaperUploadInfo(
            baseName = baseName,
            originalFileName = originalFileName,
            mimeType = mimeType,
        )
    }

    private fun ensureReadableWallpaper(localUri: Uri) {
        val firstByte = context.contentResolver.openInputStream(localUri)?.use { input ->
            input.read()
        } ?: -1
        if (firstByte == -1) throw IllegalArgumentException("Selected image is empty or unreadable")
    }

    private fun phoneAspectRatio(): Float {
        val metrics = context.resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels).takeIf { it > 0 } ?: 9
        val longSide = max(metrics.widthPixels, metrics.heightPixels).takeIf { it > 0 } ?: 16
        return (shortSide.toFloat() / longSide.toFloat()).takeIf { it > 0f } ?: FALLBACK_PHONE_ASPECT
    }
}

internal fun normalizeWallpaperUploadCategory(category: String): String {
    val normalized = category.trim().lowercase(java.util.Locale.ROOT)
    require(normalized in ALLOWED_WALLPAPER_UPLOAD_CATEGORIES) { "Invalid wallpaper category" }
    return normalized
}

internal fun sanitizeWallpaperUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            tag.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(WALLPAPER_UPLOAD_TAG_SANITIZE_REGEX, "")
                .replace(WALLPAPER_UPLOAD_WHITESPACE_REGEX, " ")
        }
        .filter { it.length in 2..MAX_WALLPAPER_TAG_LENGTH }
        .distinct()
        .take(MAX_WALLPAPER_TAGS)
        .toList()

internal fun isSupportedWallpaperUploadMime(mimeType: String): Boolean =
    mimeType.lowercase(java.util.Locale.ROOT) in ALLOWED_WALLPAPER_UPLOAD_MIMES

internal fun sanitizeWallpaperUploadStorageSegment(segment: String): String =
    segment.trim()
        .replace(WALLPAPER_UPLOAD_STORAGE_SEGMENT_SANITIZE_REGEX, "_")
        .trim('_')
        .ifBlank { "user" }

internal fun shouldDisplayCommunityWallpaper(votes: Int): Boolean = votes >= 0

internal data class WallpaperCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

internal fun centerCropBounds(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): WallpaperCropBounds {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    require(targetAspect > 0f) { "Target aspect must be positive" }
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceAspect > targetAspect) {
        val cropWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
        val left = ((sourceWidth - cropWidth) / 2f).roundToInt().coerceIn(0, sourceWidth - cropWidth)
        WallpaperCropBounds(left, 0, left + cropWidth, sourceHeight)
    } else {
        val cropHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
        val top = ((sourceHeight - cropHeight) / 2f).roundToInt().coerceIn(0, sourceHeight - cropHeight)
        WallpaperCropBounds(0, top, sourceWidth, top + cropHeight)
    }
}

internal fun paletteColorsToHex(palette: ColorExtractor.WallpaperPalette): List<String> =
    listOf(
        palette.bestAccentColor,
        palette.dominantColor,
        palette.vibrantColor,
        palette.mutedColor,
        palette.vibrantDark,
        palette.mutedDark,
    )
        .filter { it != 0 }
        .map { "#%06X".format(0xFFFFFF and it) }
        .distinct()
        .take(6)

internal fun compressWallpaperForUpload(
    bitmap: Bitmap,
    maxBytes: Long = MAX_WALLPAPER_UPLOAD_BYTES,
): ByteArray {
    var working = bitmap
    var ownsWorking = false
    var quality = 92
    var attempts = 0
    val output = ByteArrayOutputStream()

    while (attempts < 16) {
        output.reset()
        working.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (output.size().toLong() <= maxBytes) {
            val bytes = output.toByteArray()
            if (ownsWorking) working.recycle()
            return bytes
        }

        if (quality > 72) {
            quality -= 8
        } else {
            val nextWidth = (working.width * 0.86f).roundToInt().coerceAtLeast(720)
            val nextHeight = (working.height * 0.86f).roundToInt().coerceAtLeast(1280)
            if (nextWidth >= working.width || nextHeight >= working.height) break
            val scaled = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
            if (ownsWorking) working.recycle()
            working = scaled
            ownsWorking = true
            quality = 84
        }
        attempts++
    }

    if (ownsWorking) working.recycle()
    throw IllegalArgumentException("Wallpaper could not be compressed under 4MB")
}

internal fun communityWallpaperId(key: String): String = "cw_$key"

private fun calculateSampleSize(rawW: Int, rawH: Int, maxLongEdge: Int): Int {
    var sample = 1
    var width = rawW
    var height = rawH
    while (max(width, height) / 2 >= maxLongEdge && sample < (1 shl 28)) {
        sample *= 2
        width /= 2
        height /= 2
    }
    return sample.coerceAtLeast(1)
}

private fun normalizeCropRect(rect: Rect, maxWidth: Int, maxHeight: Int): Rect {
    val left = rect.left.coerceIn(0, maxWidth - 1)
    val top = rect.top.coerceIn(0, maxHeight - 1)
    val right = rect.right.coerceIn(left + 1, maxWidth)
    val bottom = rect.bottom.coerceIn(top + 1, maxHeight)
    return Rect(left, top, right, bottom)
}

private fun DataSnapshot.stringList(childName: String): List<String> =
    child(childName).children.mapNotNull { it.getValue(String::class.java) }

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

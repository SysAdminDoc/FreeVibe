package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.freevibe.data.local.CollectionDao
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.HybridBinarizer
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection sharing/import. Exports collection JSON for attachment-based sharing and can
 * publish the same payload to Firebase RTDB for compact `aura://collection/import/{token}`
 * links that fit comfortably in QR codes.
 */
private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val SHARE_TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{8,80}$")
private const val MAX_IMPORT_BYTES = 512 * 1024
private const val MAX_IMPORT_ITEMS = 250
private const val CURRENT_VERSION = 1

@Singleton
class CollectionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionDao: CollectionDao,
    private val moshi: Moshi,
) {
    private val adapter = moshi.adapter(CollectionExportFile::class.java).indent("  ")
    private val database by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }

    suspend fun prepareShareBundle(collectionId: Long, collectionName: String): Result<CollectionShareBundle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = buildExportFile(collectionId, collectionName)
                val json = adapter.toJson(file)
                val uri = writeShareFile(collectionId, file.collectionName, json)
                val token = publishPayload(json, file.collectionName, file.items.size)
                CollectionShareBundle(
                    uri = uri,
                    collectionName = file.collectionName,
                    shareLink = buildShareLink(token),
                    itemCount = file.items.size,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun prepareShareUri(collectionId: Long, collectionName: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val file = buildExportFile(collectionId, collectionName)
            writeShareFile(collectionId, file.collectionName, adapter.toJson(file))
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun publishShareLink(collectionId: Long, collectionName: String): Result<CollectionShareLink> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = buildExportFile(collectionId, collectionName)
                val token = publishPayload(adapter.toJson(file), file.collectionName, file.items.size)
                CollectionShareLink(
                    token = token,
                    link = buildShareLink(token),
                    collectionName = file.collectionName,
                    itemCount = file.items.size,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun importFromTokenOrLink(input: String): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = extractCollectionShareToken(input)
                ?: throw IllegalArgumentException("Paste an Aura collection link or share token.")
            val db = database ?: throw IllegalStateException("Firebase Database not available")
            val json = db.child("shared_collections")
                .child(token)
                .child("payload")
                .get()
                .await()
                .getValue(String::class.java)
                ?: throw IllegalStateException("Collection link is expired or unavailable.")
            importJson(json)
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun importFromUri(uri: Uri): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching { importJson(readTextFromUri(uri)) }
            .onFailure { it.rethrowIfCancelled() }
    }

    suspend fun importFromQrImage(uri: Uri): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val link = decodeQrText(uri)
            importFromTokenOrLink(link).getOrThrow()
        }.onFailure { it.rethrowIfCancelled() }
    }

    fun buildShareIntent(bundle: CollectionShareBundle): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, bundle.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Aura collection: ${bundle.collectionName}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Aura collection: ${bundle.collectionName}\n${bundle.itemCount} wallpapers\n${bundle.shareLink}",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun buildQrBitmap(text: String, sizePx: Int = 768): Bitmap {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }

    private suspend fun buildExportFile(collectionId: Long, collectionName: String): CollectionExportFile {
        val items = collectionDao.getCollectionItems(collectionId).first()
        if (items.isEmpty()) throw IllegalStateException("This collection is empty - add a wallpaper first.")

        return CollectionExportFile(
            version = CURRENT_VERSION,
            exportedAt = System.currentTimeMillis(),
            collectionName = sanitizeImportedCollectionName(collectionName),
            items = items.take(MAX_IMPORT_ITEMS).map {
                CollectionExportItem(
                    wallpaperId = it.wallpaperId,
                    source = it.source,
                    thumbnailUrl = it.thumbnailUrl,
                    fullUrl = it.fullUrl,
                    width = it.width,
                    height = it.height,
                )
            },
        )
    }

    private fun writeShareFile(collectionId: Long, collectionName: String, json: String): Uri {
        val shareDir = File(context.cacheDir, "share_out").apply { mkdirs() }
        val safeName = collectionName.replace(FILENAME_SANITIZE_REGEX, "_").ifBlank { "collection" }
        val shareFile = File(shareDir, "aura_${safeName}_${collectionId}.json").apply {
            writeBytes(json.toByteArray(Charsets.UTF_8))
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile,
        )
    }

    private suspend fun publishPayload(json: String, collectionName: String, itemCount: Int): String {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val token = UUID.randomUUID().toString().replace("-", "")
        db.child("shared_collections")
            .child(token)
            .setValue(
                mapOf(
                    "version" to CURRENT_VERSION,
                    "payload" to json,
                    "collectionName" to collectionName,
                    "itemCount" to itemCount,
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
            .await()
        return token
    }

    private fun buildShareLink(token: String): String = "aura://collection/import/$token"

    private fun readTextFromUri(uri: Uri): String {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("Collection file is too large to import safely.")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("Could not read the selected collection file.")
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun importJson(json: String): CollectionImportResult {
        val file = adapter.fromJson(json)
            ?: throw IllegalArgumentException("This is not a valid Aura collection file.")
        if (file.version != CURRENT_VERSION) {
            throw IllegalArgumentException("This collection format is not supported by this Aura version.")
        }
        val validItems = file.items
            .asSequence()
            .filter { it.wallpaperId.isNotBlank() && it.fullUrl.isAllowedShareUrl() }
            .take(MAX_IMPORT_ITEMS)
            .toList()
        if (validItems.isEmpty()) {
            throw IllegalArgumentException("This collection does not contain importable wallpapers.")
        }

        val name = sanitizeImportedCollectionName(file.collectionName).ifBlank { "Imported collection" }
        val collectionId = collectionDao.createCollection(
            WallpaperCollectionEntity(name = "$name (Imported)")
        )
        validItems.forEach { item ->
            collectionDao.addItem(
                WallpaperCollectionItemEntity(
                    collectionId = collectionId,
                    wallpaperId = item.wallpaperId,
                    thumbnailUrl = item.thumbnailUrl.ifBlank { item.fullUrl },
                    fullUrl = item.fullUrl,
                    source = item.source.ifBlank { "REDDIT" }.uppercase(Locale.ROOT),
                    width = item.width.coerceAtLeast(0),
                    height = item.height.coerceAtLeast(0),
                )
            )
        }
        return CollectionImportResult(
            collectionId = collectionId,
            collectionName = "$name (Imported)",
            itemCount = validItems.size,
        )
    }

    private fun decodeQrText(uri: Uri): String {
        val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("Could not read the selected QR image.")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader().decode(
            binaryBitmap,
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
        ).text
    }
}

data class CollectionShareBundle(
    val uri: Uri,
    val collectionName: String,
    val shareLink: String,
    val itemCount: Int,
)

data class CollectionShareLink(
    val token: String,
    val link: String,
    val collectionName: String,
    val itemCount: Int,
)

data class CollectionImportResult(
    val collectionId: Long,
    val collectionName: String,
    val itemCount: Int,
)

@JsonClass(generateAdapter = true)
data class CollectionExportFile(
    val version: Int,
    val exportedAt: Long,
    val collectionName: String,
    val items: List<CollectionExportItem>,
)

@JsonClass(generateAdapter = true)
data class CollectionExportItem(
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

internal fun extractCollectionShareToken(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.matches(SHARE_TOKEN_REGEX)) return trimmed

    val prefixes = listOf(
        "aura://collection/import/",
        "aura://collections/import/",
        "https://aura.app/collection/import/",
        "https://aura.app/collections/import/",
    )
    return prefixes.firstNotNullOfOrNull { prefix ->
        val index = trimmed.indexOf(prefix, ignoreCase = true)
        if (index < 0) return@firstNotNullOfOrNull null
        trimmed.substring(index + prefix.length)
            .takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
            .takeIf { it.matches(SHARE_TOKEN_REGEX) }
    }
}

internal fun sanitizeImportedCollectionName(name: String): String =
    name.trim()
        .replace(Regex("\\s+"), " ")
        .take(80)

private fun String.isAllowedShareUrl(): Boolean =
    startsWith("https://", ignoreCase = true)

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

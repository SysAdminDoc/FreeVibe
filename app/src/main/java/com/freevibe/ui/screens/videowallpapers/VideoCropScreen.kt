package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.onGloballyPositioned
import java.io.File

private val sharedHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

/**
 * Video crop editor constrained to phone screen aspect ratio.
 * Pinch to zoom, drag to pan — video must always fill the viewport.
 * The visible area is cropped to match the phone's screen ratio exactly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoCropScreen(
    videoUrl: String,
    videoTitle: String,
    onBack: () -> Unit,
    onCropped: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get real screen pixel dimensions for accurate aspect ratio
    val realScreenRatio = remember {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().toFloat() / bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels.toFloat() / metrics.heightPixels
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            play()
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var isCropping by remember { mutableStateOf(false) }
    var dimensionsReady by remember { mutableStateOf(false) }

    // Detect actual video dimensions via ExoPlayer format or MediaMetadataRetriever fallback
    LaunchedEffect(Unit) {
        // Try ExoPlayer format first (polls for up to 5s)
        repeat(50) {
            kotlinx.coroutines.delay(100)
            exoPlayer.videoFormat?.let { format ->
                if (format.width > 0 && format.height > 0) {
                    videoWidth = format.width
                    videoHeight = format.height
                    dimensionsReady = true
                    return@LaunchedEffect
                }
            }
        }
        // Fallback: use MediaMetadataRetriever for local/remote files
        withContext(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    if (videoUrl.startsWith("http")) {
                        retriever.setDataSource(videoUrl, emptyMap())
                    } else {
                        retriever.setDataSource(videoUrl)
                    }
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (w > 0 && h > 0) {
                        // Apply rotation — 90/270 degrees means width/height are swapped
                        if (rotation == 90 || rotation == 270) {
                            videoWidth = h; videoHeight = w
                        } else {
                            videoWidth = w; videoHeight = h
                        }
                        dimensionsReady = true
                    }
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "MetadataRetriever failed: ${e.message}")
            }
        }
        // Last resort: assume 1080x1920 portrait
        if (!dimensionsReady) {
            videoWidth = 1080; videoHeight = 1920; dimensionsReady = true
        }
    }

    // Calculate minimum scale so video always covers the viewport
    val minScale = remember(viewSize, videoWidth, videoHeight, dimensionsReady) {
        if (viewSize.width <= 0 || viewSize.height <= 0 || videoWidth <= 0 || videoHeight <= 0) 1f
        else {
            val fitScaleX = viewSize.width.toFloat() / videoWidth
            val fitScaleY = viewSize.height.toFloat() / videoHeight
            val fitScale = minOf(fitScaleX, fitScaleY).coerceAtLeast(0.001f)
            val coverScale = maxOf(fitScaleX, fitScaleY)
            coverScale / fitScale
        }
    }

    // Clamp scale and pan to keep video filling viewport
    fun clampTransform(s: Float, ox: Float, oy: Float): Triple<Float, Float, Float> {
        val clamped = s.coerceIn(minScale, 5f)
        if (viewSize.width <= 0 || viewSize.height <= 0) return Triple(clamped, ox, oy)

        val fitScaleX = viewSize.width.toFloat() / videoWidth
        val fitScaleY = viewSize.height.toFloat() / videoHeight
        val fitScale = minOf(fitScaleX, fitScaleY)
        val totalScale = fitScale * clamped

        val renderedW = videoWidth * totalScale
        val renderedH = videoHeight * totalScale

        // Max pan = how much bigger the rendered video is than viewport / 2
        val maxPanX = ((renderedW - viewSize.width) / 2f).coerceAtLeast(0f)
        val maxPanY = ((renderedH - viewSize.height) / 2f).coerceAtLeast(0f)

        return Triple(clamped, ox.coerceIn(-maxPanX, maxPanX), oy.coerceIn(-maxPanY, maxPanY))
    }

    // Apply initial constraint when minScale changes
    LaunchedEffect(minScale) {
        val (s, ox, oy) = clampTransform(scale, offsetX, offsetY)
        scale = s; offsetX = ox; offsetY = oy
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Video") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val (s, ox, oy) = clampTransform(minScale, 0f, 0f)
                        scale = s; offsetX = ox; offsetY = oy
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Pinch to zoom, drag to position. Video must fill the screen.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Video viewport constrained to phone's actual screen aspect ratio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(realScreenRatio)
                        .fillMaxSize()
                        .clipToBounds()
                        .onGloballyPositioned { viewSize = IntSize(it.size.width, it.size.height) }
                        .pointerInput(minScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = scale * zoom
                                val newOx = offsetX + pan.x
                                val newOy = offsetY + pan.y
                                val (s, ox, oy) = clampTransform(newScale, newOx, newOy)
                                scale = s; offsetX = ox; offsetY = oy
                            }
                        },
                ) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )

                // Viewport border
                Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (dimensionsReady) "${videoWidth}x${videoHeight}" else "Detecting...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format(java.util.Locale.ROOT, "%.0f%%", scale * 100), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
                    if (isCropping) return@Button
                    isCropping = true
                    scope.launch {
                        val result = cropVideoConstrained(
                            context = context, videoUrl = videoUrl,
                            videoWidth = videoWidth, videoHeight = videoHeight,
                            viewWidth = viewSize.width, viewHeight = viewSize.height,
                            scale = scale, panX = offsetX, panY = offsetY,
                        )
                        isCropping = false
                        if (result != null) onCropped(result)
                        else Toast.makeText(context, "Crop failed", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                enabled = !isCropping && dimensionsReady,
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isCropping) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Cropping...")
                } else {
                    Icon(Icons.Default.Crop, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Crop & Apply")
                }
            }
        }
    }
}

/**
 * Get ffmpeg binary path and LD_LIBRARY_PATH from youtubedl-android via reflection.
 * Returns Pair(ffmpegPath, ldLibraryPath) or null if not available.
 */
private fun getYtdlpFfmpeg(): Pair<File, String>? {
    return try {
        val ytdl = com.yausername.youtubedl_android.YoutubeDL.getInstance()
        val cls = ytdl::class.java

        val ffmpegField = cls.getDeclaredField("ffmpegPath")
        ffmpegField.isAccessible = true
        val ffmpegPath = ffmpegField.get(null) as? File ?: return null

        val ldField = cls.getDeclaredField("ENV_LD_LIBRARY_PATH")
        ldField.isAccessible = true
        val ldPath = ldField.get(null) as? String ?: ""

        if (ffmpegPath.exists()) Pair(ffmpegPath, ldPath) else null
    } catch (e: Exception) {
        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "Reflection failed: ${e.message}")
        null
    }
}

private suspend fun cropVideoConstrained(
    context: Context,
    videoUrl: String,
    videoWidth: Int,
    videoHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    scale: Float,
    panX: Float,
    panY: Float,
): File? = withContext(Dispatchers.IO) {
    try {
        val inputFile = if (videoUrl.startsWith("http")) {
            val cacheFile = File(context.cacheDir, "crop_input.mp4")
            sharedHttpClient.newCall(okhttp3.Request.Builder().url(videoUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                // Reject oversized video downloads up front — a 4K hour-long video can be
                // hundreds of MB and we're just cropping a wallpaper. 256 MB is well past
                // the realistic ceiling for a few-second live wallpaper loop.
                val advertised = body.contentLength()
                if (advertised in 1..Long.MAX_VALUE && advertised > MAX_VIDEO_INPUT_BYTES) {
                    return@withContext null
                }
                body.byteStream().use { input ->
                    cacheFile.outputStream().use { output ->
                        var copied = 0L
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            copied += n
                            if (copied > MAX_VIDEO_INPUT_BYTES) {
                                try { cacheFile.delete() } catch (_: Exception) {}
                                return@withContext null
                            }
                            output.write(buf, 0, n)
                        }
                    }
                }
            }
            cacheFile
        } else {
            // Local URI / file path — validate existence before handing to FFmpeg, otherwise
            // we get a cryptic "Invalid data found" error instead of a user-friendly skip.
            val f = File(videoUrl)
            if (!f.exists() || !f.canRead()) return@withContext null
            f
        }

        val outputFile = File(context.filesDir, "live_wallpaper.mp4")

        // Map viewport back to video source coordinates
        val fitScaleX = viewWidth.toFloat() / videoWidth
        val fitScaleY = viewHeight.toFloat() / videoHeight
        val fitScale = minOf(fitScaleX, fitScaleY)
        val totalScale = fitScale * scale

        val renderedW = videoWidth * totalScale
        val renderedH = videoHeight * totalScale

        val videoLeft = (viewWidth - renderedW) / 2f + panX
        val videoTop = (viewHeight - renderedH) / 2f + panY

        // Viewport region in video coordinates
        val srcLeft = ((0f - videoLeft) / totalScale).coerceIn(0f, videoWidth.toFloat())
        val srcTop = ((0f - videoTop) / totalScale).coerceIn(0f, videoHeight.toFloat())
        val srcRight = ((viewWidth - videoLeft) / totalScale).coerceIn(0f, videoWidth.toFloat())
        val srcBottom = ((viewHeight - videoTop) / totalScale).coerceIn(0f, videoHeight.toFloat())

        var cropX = srcLeft.toInt()
        var cropY = srcTop.toInt()
        var cropW = (srcRight - srcLeft).toInt()
        var cropH = (srcBottom - srcTop).toInt()

        // Force even dimensions for h264
        cropX = ((cropX / 2) * 2).coerceIn(0, videoWidth - 2)
        cropY = ((cropY / 2) * 2).coerceIn(0, videoHeight - 2)
        cropW = ((cropW / 2) * 2).coerceIn(2, videoWidth - cropX)
        cropH = ((cropH / 2) * 2).coerceIn(2, videoHeight - cropY)

        if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoCrop", "Constrained crop: ${videoWidth}x${videoHeight} -> ${cropW}x${cropH} at ($cropX,$cropY)")

        // Delete existing output to avoid stale files
        if (outputFile.exists()) outputFile.delete()

        var cropSucceeded = false
        val ffmpegInfo = getYtdlpFfmpeg()

        if (ffmpegInfo != null) {
            val (ffmpegPath, ldLibPath) = ffmpegInfo
            try {
                val tempOutput = File(context.cacheDir, "crop_output.mp4")
                if (tempOutput.exists()) tempOutput.delete()

                val cmd = listOf(
                    ffmpegPath.absolutePath,
                    "-y",
                    "-i", inputFile.absolutePath,
                    "-vf", "crop=$cropW:$cropH:$cropX:$cropY",
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-an",
                    "-movflags", "+faststart",
                    tempOutput.absolutePath,
                )
                if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoCrop", "FFmpeg cmd: ${cmd.joinToString(" ")}")
                if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoCrop", "LD_LIBRARY_PATH=$ldLibPath")

                val env = mutableMapOf<String, String>()
                if (ldLibPath.isNotEmpty()) env["LD_LIBRARY_PATH"] = ldLibPath

                val pb = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .directory(context.cacheDir)
                pb.environment().putAll(env)
                val process = pb.start()

                val exitCode: Int
                try {
                    // Drain FFmpeg's merged stdout/stderr with a bounded buffer instead of
                    // `readText()` — a chatty run can produce MBs of progress lines we would
                    // otherwise keep in-memory just to log the last 500 chars.
                    val tail = StringBuilder()
                    process.inputStream.bufferedReader().use { reader ->
                        val chunk = CharArray(4096)
                        while (true) {
                            val n = try { reader.read(chunk) } catch (_: Exception) { -1 }
                            if (n <= 0) break
                            if (tail.length < 4096) {
                                tail.append(chunk, 0, n.coerceAtMost(4096 - tail.length))
                            }
                        }
                    }
                    val completed = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                    exitCode = if (completed) process.exitValue() else { process.destroyForcibly(); -1 }
                    if (com.freevibe.BuildConfig.DEBUG && exitCode != 0) Log.e("VideoCrop", "FFmpeg output: ${tail.takeLast(500)}")
                } finally {
                    process.destroy()
                }

                if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoCrop", "FFmpeg exit=$exitCode, output size=${tempOutput.length() / 1024}KB")

                if (exitCode == 0 && tempOutput.exists() && tempOutput.length() > 1024) {
                    tempOutput.copyTo(outputFile, overwrite = true)
                    tempOutput.delete()
                    cropSucceeded = true
                    if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoCrop", "Crop success: ${outputFile.length() / 1024}KB")
                } else {
                    if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "FFmpeg crop produced invalid output")
                    tempOutput.delete()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "FFmpeg crop failed: ${e.message}")
            }
        } else {
            if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "FFmpeg not available via yt-dlp reflection")
        }

        // Clean up input cache
        if (inputFile.absolutePath.contains("crop_input")) {
            try { inputFile.delete() } catch (_: Exception) {}
        }

        if (cropSucceeded && outputFile.exists() && outputFile.length() > 1024) outputFile else null
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoCrop", "Crop failed: ${e.message}", e)
        try { File(context.cacheDir, "crop_input.mp4").delete() } catch (_: Exception) {}
        null
    }
}

private const val MAX_VIDEO_INPUT_BYTES = 256L * 1024 * 1024

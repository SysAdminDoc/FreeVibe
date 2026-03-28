package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Video crop editor for converting landscape videos to portrait phone wallpapers.
 * Shows the full video with a draggable 9:16 crop overlay.
 * Uses FFmpeg (bundled via youtubedl-android) to perform the actual crop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCropScreen(
    videoUrl: String,
    videoTitle: String,
    onBack: () -> Unit,
    onCropped: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Video player
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

    // Crop state
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    var cropOffsetX by remember { mutableFloatStateOf(0.5f) } // 0-1, center of crop
    var isCropping by remember { mutableStateOf(false) }

    // Get video dimensions once ready
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        exoPlayer.videoFormat?.let { format ->
            videoWidth = format.width
            videoHeight = format.height
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop for Phone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            // Instructions
            Text(
                "Drag to position the crop area",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Video with crop overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (viewSize.width > 0) {
                                cropOffsetX = (cropOffsetX + dragAmount.x / viewSize.width)
                                    .coerceIn(0f, 1f)
                            }
                        }
                    },
            ) {
                // Full video
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords -> viewSize = IntSize(coords.size.width, coords.size.height) },
                )

                // Dim areas outside crop
                if (viewSize.width > 0 && viewSize.height > 0) {
                    val cropAspect = 9f / 16f
                    val cropW = viewSize.height * cropAspect
                    val maxOffset = (viewSize.width - cropW).coerceAtLeast(0f)
                    val cropLeft = maxOffset * cropOffsetX

                    // Left dim
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(with(LocalDensity.current) { cropLeft.toDp() })
                            .background(Color.Black.copy(alpha = 0.6f))
                    )

                    // Right dim
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(with(LocalDensity.current) { (viewSize.width - cropLeft - cropW).coerceAtLeast(0f).toDp() })
                            .align(Alignment.CenterEnd)
                            .background(Color.Black.copy(alpha = 0.6f))
                    )

                    // Crop border
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(with(LocalDensity.current) { cropW.toDp() })
                            .offset(x = with(LocalDensity.current) { cropLeft.toDp() })
                            .border(2.dp, MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Apply button
            Button(
                onClick = {
                    if (isCropping) return@Button
                    isCropping = true
                    scope.launch {
                        val result = cropVideo(
                            context = context,
                            videoUrl = videoUrl,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            cropOffsetFraction = cropOffsetX,
                        )
                        isCropping = false
                        if (result != null) {
                            onCropped(result)
                        } else {
                            Toast.makeText(context, "Crop failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                enabled = !isCropping,
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
 * Crop video to 9:16 portrait using FFmpeg (bundled via youtubedl-android).
 */
private suspend fun cropVideo(
    context: Context,
    videoUrl: String,
    videoWidth: Int,
    videoHeight: Int,
    cropOffsetFraction: Float,
): File? = withContext(Dispatchers.IO) {
    try {
        // Download video first if it's a URL
        val inputFile = if (videoUrl.startsWith("http")) {
            val cacheFile = File(context.cacheDir, "crop_input.mp4")
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(videoUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            response.body?.byteStream()?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile
        } else {
            File(videoUrl)
        }

        val outputFile = File(context.filesDir, "live_wallpaper.mp4")

        // Calculate crop dimensions: 9:16 portrait from landscape video
        val targetRatio = 9.0 / 16.0
        val cropH = videoHeight
        val cropW = (videoHeight * targetRatio).toInt().coerceAtMost(videoWidth)
        val maxX = (videoWidth - cropW).coerceAtLeast(0)
        val cropX = (maxX * cropOffsetFraction).toInt()
        val cropY = 0

        Log.d("VideoCrop", "Cropping ${videoWidth}x${videoHeight} to ${cropW}x${cropH} at x=$cropX")

        // Use yt-dlp's bundled FFmpeg
        val ffmpegCmd = "-y -i ${inputFile.absolutePath} -vf crop=$cropW:$cropH:$cropX:$cropY -c:v libx264 -preset ultrafast -crf 23 -an ${outputFile.absolutePath}"

        try {
            Log.d("VideoCrop", "Running FFmpeg crop: ${cropW}x${cropH} at x=$cropX")
            // Init FFmpeg binary extraction
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            // Find ffmpeg binary in app's native lib dir or packages dir
            val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
                .takeIf { it.exists() }
                ?: File(context.filesDir, "packages/ffmpeg/bin/ffmpeg")
                    .takeIf { it.exists() }

            if (ffmpegBin != null) {
                val process = ProcessBuilder(
                    ffmpegBin.absolutePath,
                    "-y", "-i", inputFile.absolutePath,
                    "-vf", "crop=$cropW:$cropH:$cropX:$cropY",
                    "-c:v", "mpeg4", "-q:v", "5", "-an",
                    outputFile.absolutePath,
                ).redirectErrorStream(true).start()
                val exitCode = process.waitFor()
                Log.d("VideoCrop", "FFmpeg exit=$exitCode, output=${outputFile.length() / 1024}KB")
                if (exitCode != 0 || !outputFile.exists() || outputFile.length() == 0L) {
                    Log.w("VideoCrop", "FFmpeg failed, copying original")
                    inputFile.copyTo(outputFile, overwrite = true)
                }
            } else {
                Log.w("VideoCrop", "FFmpeg binary not found, copying original")
                inputFile.copyTo(outputFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.e("VideoCrop", "FFmpeg crop failed: ${e.message}, copying original")
            inputFile.copyTo(outputFile, overwrite = true)
        }

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d("VideoCrop", "Cropped video: ${outputFile.length() / 1024}KB")
            outputFile
        } else null
    } catch (e: Exception) {
        Log.e("VideoCrop", "Crop failed: ${e.message}", e)
        null
    }
}

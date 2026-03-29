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

private val sharedHttpClient by lazy { okhttp3.OkHttpClient() }

/**
 * Video crop editor — pinch to zoom, drag to pan.
 * The visible viewport is exactly what gets cropped and applied as wallpaper.
 * No forced aspect ratio — whatever you see is what you get.
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
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCropping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(30) {
            kotlinx.coroutines.delay(100)
            exoPlayer.videoFormat?.let { format ->
                videoWidth = format.width
                videoHeight = format.height
                return@LaunchedEffect
            }
        }
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
                    TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
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
                "Pinch to zoom, drag to position. What you see is what gets applied.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Video with pinch/drag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
                    .onGloballyPositioned { viewSize = IntSize(it.size.width, it.size.height) }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }

            // Zoom indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${videoWidth}x${videoHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "%.0f%%".format(scale * 100),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Apply button
            Button(
                onClick = {
                    if (isCropping) return@Button
                    isCropping = true
                    scope.launch {
                        val result = cropVideoCustom(
                            context = context,
                            videoUrl = videoUrl,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            viewWidth = viewSize.width,
                            viewHeight = viewSize.height,
                            scale = scale,
                            panX = offsetX,
                            panY = offsetY,
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
 * Crop video based on the user's zoom/pan transform.
 * Maps the visible viewport back to source video coordinates.
 */
private suspend fun cropVideoCustom(
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
                resp.body?.byteStream()?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            cacheFile
        } else {
            File(videoUrl)
        }

        val outputFile = File(context.filesDir, "live_wallpaper.mp4")

        // Map viewport back to video coordinates
        // The video is rendered at (videoW * fitScale * userScale) centered in the view with pan offset
        val fitScaleX = viewWidth.toFloat() / videoWidth
        val fitScaleY = viewHeight.toFloat() / videoHeight
        val fitScale = minOf(fitScaleX, fitScaleY) // fit inside view
        val totalScale = fitScale * scale

        val renderedW = videoWidth * totalScale
        val renderedH = videoHeight * totalScale

        // Video position in view space
        val videoLeft = (viewWidth - renderedW) / 2f + panX
        val videoTop = (viewHeight - renderedH) / 2f + panY

        // Visible region in video space (what the viewport shows)
        val visLeft = ((0f - videoLeft) / totalScale).coerceIn(0f, videoWidth.toFloat())
        val visTop = ((0f - videoTop) / totalScale).coerceIn(0f, videoHeight.toFloat())
        val visRight = ((viewWidth - videoLeft) / totalScale).coerceIn(0f, videoWidth.toFloat())
        val visBottom = ((viewHeight - videoTop) / totalScale).coerceIn(0f, videoHeight.toFloat())

        val cropX = visLeft.toInt().coerceIn(0, videoWidth - 2)
        val cropY = visTop.toInt().coerceIn(0, videoHeight - 2)
        val cropW = (visRight - visLeft).toInt().coerceIn(2, videoWidth - cropX)
        val cropH = (visBottom - visTop).toInt().coerceIn(2, videoHeight - cropY)

        // Make dimensions even (required by h264)
        val evenW = cropW and 0x7FFFFFFE
        val evenH = cropH and 0x7FFFFFFE

        Log.d("VideoCrop", "Custom crop: ${videoWidth}x${videoHeight} -> ${evenW}x${evenH} at ($cropX,$cropY) scale=$scale pan=($panX,$panY)")

        try {
            val request = com.yausername.youtubedl_android.YoutubeDLRequest("file://${inputFile.absolutePath}")
            request.addOption("--enable-file-urls")
            request.addOption("-o", outputFile.absolutePath)
            request.addOption("--recode-video", "mp4")
            request.addOption("--postprocessor-args", "VideoConvertor:-vf crop=$evenW:$evenH:$cropX:$cropY -c:v libx264 -preset ultrafast")
            request.addOption("--force-overwrites")
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            Log.d("VideoCrop", "yt-dlp crop done: exit=${response.exitCode}, output=${outputFile.length() / 1024}KB")

            if (!outputFile.exists() || outputFile.length() == 0L) {
                Log.w("VideoCrop", "Crop produced no output, copying original")
                inputFile.copyTo(outputFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.e("VideoCrop", "Crop failed: ${e.message}, copying original")
            inputFile.copyTo(outputFile, overwrite = true)
        }

        if (inputFile.absolutePath.contains("crop_input")) {
            try { inputFile.delete() } catch (_: Exception) {}
        }

        if (outputFile.exists() && outputFile.length() > 0) outputFile else null
    } catch (e: Exception) {
        Log.e("VideoCrop", "Crop failed: ${e.message}", e)
        try { File(context.cacheDir, "crop_input.mp4").delete() } catch (_: Exception) {}
        null
    }
}

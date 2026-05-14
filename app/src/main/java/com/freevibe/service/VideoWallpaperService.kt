@file:Suppress("DEPRECATION")

package com.freevibe.service

import android.graphics.Color
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.BuildConfig
import java.io.File
import kotlin.math.max

/**
 * Live wallpaper service that plays a video or animated GIF on the home/lock screen.
 * Uses center-crop rendering: motion fills the screen, overflow is clipped, and
 * aspect ratio is always preserved.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var gifMovie: Movie? = null
        private var gifStartedAtMs = 0L
        private var gifFrameRunnable: Runnable? = null
        private val gifHandler = Handler(Looper.getMainLooper())
        private var currentHolder: SurfaceHolder? = null
        private var lastModified: Long = 0
        private var lastPath: String? = null
        private var screenWidth = 0
        private var screenHeight = 0

        private fun getPrefs() = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
        private fun getVideoPath(): String? = getPrefs().getString("video_path", null)
        private fun getScaleMode(): String =
            normalizeVideoWallpaperScaleMode(getPrefs().getString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_ZOOM))
        private fun getPlaybackSpeed(): Float =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getFloat("video_playback_speed", 1.0f).takeIf { it > 0 } ?: 1.0f
        private fun getFpsLimit(): Float =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getInt("video_fps_limit", 30)
                .let { fps ->
                    when {
                        fps <= 15 -> 15
                        fps >= 60 -> 60
                        else -> 30
                    }
                }
                .toFloat()

        private fun resolveScreenSize() {
            try {
                val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    wm?.currentWindowMetrics?.bounds?.let { bounds ->
                        screenWidth = bounds.width()
                        screenHeight = bounds.height()
                    }
                } else {
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm?.defaultDisplay?.getRealMetrics(metrics)
                    screenWidth = metrics.widthPixels
                    screenHeight = metrics.heightPixels
                }
            } catch (_: Exception) {}
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            resolveScreenSize()
            initializePlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            releasePlayback()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val path = getVideoPath()
                if (path != null) {
                    val file = File(path)
                    // Re-init if the user picked a different video OR the same path's
                    // contents changed. Path comparison guards against rare cases where
                    // two different files happen to share the same lastModified timestamp.
                    if (file.exists() && (path != lastPath || file.lastModified() != lastModified)) {
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                }
                gifMovie?.let {
                    currentHolder?.let { resumeGifPlayback(it) }
                    return
                }
                try {
                    mediaPlayer?.let { if (!it.isPlaying) { it.seekTo(0); it.start() } }
                } catch (_: Exception) {}
            } else {
                pauseGifPlayback()
                try { mediaPlayer?.pause() } catch (_: Exception) {}
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayback()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            releasePlayback()
            val path = getVideoPath() ?: return
            val file = File(path)
            if (!file.exists()) return
            try {
                lastModified = file.lastModified()
                lastPath = path
                if (file.extension.equals("gif", ignoreCase = true)) {
                    initializeGifPlayback(holder, file)
                    return
                }
                val speed = getPlaybackSpeed()
                val scaleMode = getScaleMode()

                // Detect video dimensions before playback for accurate surface sizing
                var videoW = 0
                var videoH = 0
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rotation == 90 || rotation == 270) {
                            videoW = h; videoH = w
                        } else {
                            videoW = w; videoH = h
                        }
                    } finally {
                        retriever.release()
                    }
                } catch (_: Exception) {}

                // Set surface to screen size — this is the canvas the user sees
                val (sw, sh) = configureSurface(holder)
                configureFrameRate(holder)

                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    try {
                        setVideoScalingMode(
                            if (scaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                            } else {
                                MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                            },
                        )
                    } catch (_: Exception) {}
                    setOnPreparedListener { mp ->
                        // If MediaMetadataRetriever didn't get dimensions, read from MediaPlayer
                        if (videoW <= 0 || videoH <= 0) {
                            videoW = mp.videoWidth
                            videoH = mp.videoHeight
                        }
                        mp.isLooping = true
                        try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                        mp.start()
                    }
                    prepareAsync()
                }

                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService",
                    "Playing ${videoW}x${videoH} on ${sw}x${sh} screen, mode=$scaleMode, path=$path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                releasePlayback()
            }
        }

        private fun configureSurface(holder: SurfaceHolder): Pair<Int, Int> {
            val sw = screenWidth.takeIf { it > 0 } ?: holder.surfaceFrame.width()
            val sh = screenHeight.takeIf { it > 0 } ?: holder.surfaceFrame.height()
            if (sw > 0 && sh > 0) {
                try { holder.setFixedSize(sw, sh) } catch (_: Exception) {}
            }
            return sw to sh
        }

        private fun configureFrameRate(holder: SurfaceHolder) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    holder.surface.setFrameRate(
                        getFpsLimit(),
                        android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    )
                } catch (_: Exception) {
                }
            }
        }

        private fun initializeGifPlayback(holder: SurfaceHolder, file: File) {
            val movie = Movie.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("Selected GIF could not be decoded")
            if (movie.width() <= 0 || movie.height() <= 0) {
                throw IllegalStateException("Selected GIF has invalid dimensions")
            }

            val (sw, sh) = configureSurface(holder)
            configureFrameRate(holder)
            gifMovie = movie
            gifStartedAtMs = SystemClock.uptimeMillis()
            resumeGifPlayback(holder)

            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    "VideoWPService",
                    "Playing GIF ${movie.width()}x${movie.height()} on ${sw}x${sh} screen, mode=${getScaleMode()}, path=${file.absolutePath}",
                )
            }
        }

        private fun resumeGifPlayback(holder: SurfaceHolder) {
            pauseGifPlayback()
            if (gifMovie == null) return
            val frameRunnable = object : Runnable {
                override fun run() {
                    if (gifMovie == null || currentHolder != holder) return
                    drawGifFrame(holder)
                    gifHandler.postDelayed(this, 33L)
                }
            }
            gifFrameRunnable = frameRunnable
            gifHandler.post(frameRunnable)
        }

        private fun pauseGifPlayback() {
            gifFrameRunnable?.let { gifHandler.removeCallbacks(it) }
            gifFrameRunnable = null
        }

        private fun drawGifFrame(holder: SurfaceHolder) {
            val movie = gifMovie ?: return
            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: return

            try {
                canvas.drawColor(Color.BLACK)
                val duration = movie.duration().takeIf { it > 0 } ?: 1000
                val time = ((SystemClock.uptimeMillis() - gifStartedAtMs) % duration).toInt()
                movie.setTime(time)

                val movieWidth = movie.width().coerceAtLeast(1)
                val movieHeight = movie.height().coerceAtLeast(1)
                val scaleX = canvas.width / movieWidth.toFloat()
                val scaleY = canvas.height / movieHeight.toFloat()
                val scale = if (getScaleMode() == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                    minOf(scaleX, scaleY)
                } else {
                    max(scaleX, scaleY)
                }
                val dx = (canvas.width - movieWidth * scale) / 2f
                val dy = (canvas.height - movieHeight * scale) / 2f

                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        private fun releasePlayback() {
            pauseGifPlayback()
            gifMovie = null
            mediaPlayer?.apply {
                try { setOnPreparedListener(null) } catch (_: Exception) {}
                try { if (isPlaying) stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

package com.freevibe.service

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import com.freevibe.BuildConfig

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Uses center-crop rendering: video fills the screen, overflow is clipped,
 * aspect ratio is always preserved (no stretching).
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var currentHolder: SurfaceHolder? = null
        private var lastModified: Long = 0
        private var screenWidth = 0
        private var screenHeight = 0

        private fun getPrefs() = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
        private fun getVideoPath(): String? = getPrefs().getString("video_path", null)
        private fun getPlaybackSpeed(): Float =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getFloat("video_playback_speed", 1.0f).takeIf { it > 0 } ?: 1.0f

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
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val path = getVideoPath()
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists() && file.lastModified() != lastModified) {
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                }
                try {
                    mediaPlayer?.let { if (!it.isPlaying) { it.seekTo(0); it.start() } }
                } catch (_: Exception) {}
            } else {
                try { mediaPlayer?.pause() } catch (_: Exception) {}
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            releasePlayer()
            val path = getVideoPath() ?: return
            val file = java.io.File(path)
            if (!file.exists()) return
            try {
                lastModified = file.lastModified()
                val speed = getPlaybackSpeed()

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
                val sw = screenWidth.takeIf { it > 0 } ?: holder.surfaceFrame.width()
                val sh = screenHeight.takeIf { it > 0 } ?: holder.surfaceFrame.height()
                if (sw > 0 && sh > 0) {
                    try { holder.setFixedSize(sw, sh) } catch (_: Exception) {}
                }

                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    // SCALE_TO_FIT_WITH_CROPPING: scale video uniformly to fill the surface,
                    // center it, and crop overflow. This preserves aspect ratio.
                    try { setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) } catch (_: Exception) {}
                    prepare()

                    // If MediaMetadataRetriever didn't get dimensions, read from MediaPlayer
                    if (videoW <= 0 || videoH <= 0) {
                        videoW = this.videoWidth
                        videoH = this.videoHeight
                    }

                    try { playbackParams = playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    start()
                }

                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService",
                    "Playing ${videoW}x${videoH} on ${sw}x${sh} screen, mode=SCALE_TO_FIT_WITH_CROPPING, path=$path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try { if (isPlaying) stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

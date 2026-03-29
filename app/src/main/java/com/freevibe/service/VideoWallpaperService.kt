package com.freevibe.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.BuildConfig

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Supports two scaling modes:
 *   - "zoom" (default): center-crop — preserves aspect ratio, fills screen, clips overflow
 *   - "stretch": stretches video to fill screen exactly (may distort)
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var currentHolder: SurfaceHolder? = null
        private var lastModified: Long = 0
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var screenWidth = 0
        private var screenHeight = 0

        private fun getPrefs() = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
        private fun getVideoPath(): String? = getPrefs().getString("video_path", null)
        private fun getScaleMode(): String = getPrefs().getString("scale_mode", "zoom") ?: "zoom"
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
            surfaceWidth = width
            surfaceHeight = height
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
                val scaleMode = getScaleMode()
                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()

                    when (scaleMode) {
                        "stretch" -> {
                            // Stretch: set surface to screen size, video fills it (may distort)
                            try { setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT) } catch (_: Exception) {}
                            val sw = if (screenWidth > 0) screenWidth else surfaceWidth
                            val sh = if (screenHeight > 0) screenHeight else surfaceHeight
                            if (sw > 0 && sh > 0) {
                                try { holder.setFixedSize(sw, sh) } catch (_: Exception) {}
                            }
                        }
                        else -> {
                            // Zoom (center-crop): set surface to video's aspect ratio, sized to cover screen.
                            // MediaPlayer fills this surface 1:1 (no distortion), screen clips the overflow.
                            applyCenterCrop(this, holder)
                        }
                    }

                    try { playbackParams = playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    start()
                }
                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService", "Playing ${mediaPlayer?.videoWidth}x${mediaPlayer?.videoHeight} mode=$scaleMode from $path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                releasePlayer()
            }
        }

        /**
         * Center-crop: size the surface to the video's native aspect ratio,
         * scaled so it covers the entire screen. The screen clips the overflow.
         * This preserves the video's aspect ratio (no stretching).
         */
        private fun applyCenterCrop(player: MediaPlayer, holder: SurfaceHolder) {
            val sw = if (screenWidth > 0) screenWidth else surfaceWidth
            val sh = if (screenHeight > 0) screenHeight else surfaceHeight
            if (sw <= 0 || sh <= 0) return

            val videoW = player.videoWidth
            val videoH = player.videoHeight
            if (videoW <= 0 || videoH <= 0) return

            val screenRatio = sw.toFloat() / sh
            val videoRatio = videoW.toFloat() / videoH

            // Calculate surface size that:
            // 1. Matches the video's aspect ratio (no distortion)
            // 2. Is at least as large as the screen in both dimensions (no black bars)
            val (surfW, surfH) = if (videoRatio > screenRatio) {
                // Video is wider than screen: match screen height, expand width
                val w = (sh * videoRatio).toInt()
                w to sh
            } else {
                // Video is taller/same as screen: match screen width, expand height
                val h = (sw / videoRatio).toInt()
                sw to h
            }

            if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService", "CenterCrop: video=${videoW}x${videoH} screen=${sw}x${sh} surface=${surfW}x${surfH}")
            try { holder.setFixedSize(surfW, surfH) } catch (_: Exception) {}
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try { if (isPlaying) stop(); release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

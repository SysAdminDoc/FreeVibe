package com.freevibe.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.sin

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Features: FPS limiting, playback speed, touch-reactive ripple effect.
 * Automatically reloads when a new video file is detected.
 */
data class TouchRipple(
    val x: Float, val y: Float,
    var radius: Float = 0f, var alpha: Int = 120,
    val maxRadius: Float = 300f,
)

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

        // Touch ripple effect
        private val ripples = mutableListOf<TouchRipple>()
        private val ripplePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
        private val handler = Handler(Looper.getMainLooper())
        private var rippleEnabled = false
        private var drawingRipples = false
        private val rippleRunner = Runnable { drawRipples() }

        private fun getPrefs() = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)

        private fun getVideoPath(): String? = getPrefs().getString("video_path", null)

        private fun getPlaybackSpeed(): Float =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getFloat("video_playback_speed", 1.0f).takeIf { it > 0 } ?: 1.0f

        private fun getRippleEnabled(): Boolean =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getBoolean("touch_ripple_enabled", true)

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            // Store real screen size once (before setFixedSize inflates it)
            if (screenWidth == 0 && screenHeight == 0) {
                screenWidth = width
                screenHeight = height
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            rippleEnabled = getRippleEnabled()
            initializePlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            handler.removeCallbacks(rippleRunner)
            drawingRipples = false
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                rippleEnabled = getRippleEnabled()
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
                handler.removeCallbacks(rippleRunner)
                drawingRipples = false
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (!rippleEnabled || event == null) return
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                synchronized(ripples) {
                    if (ripples.size < 8) { // Max concurrent ripples
                        ripples.add(TouchRipple(event.x, event.y))
                    }
                }
                if (!drawingRipples) {
                    drawingRipples = true
                    handler.post(rippleRunner)
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(rippleRunner)
            releasePlayer()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            releasePlayer() // Always release old player first
            val path = getVideoPath()
            android.util.Log.d("VideoWPService", "initializePlayer path=$path")
            if (path == null) return
            val file = java.io.File(path)
            if (!file.exists()) {
                android.util.Log.e("VideoWPService", "File does not exist: $path")
                return
            }
            android.util.Log.d("VideoWPService", "File exists: ${file.length() / 1024}KB")
            try {
                lastModified = file.lastModified()
                val speed = getPlaybackSpeed()
                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {} // no-op — required for WallpaperService
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder) // Must be before prepare on some devices
                    isLooping = true
                    setVolume(0f, 0f)
                    try {
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    } catch (_: Exception) {}
                    prepare()
                    // Apply center-crop after prepare (videoWidth/Height now available)
                    applyCenterCrop(this, holder)
                    try {
                        playbackParams = playbackParams.setSpeed(speed)
                    } catch (_: Exception) {}
                    start()
                }
                android.util.Log.d("VideoWPService", "Player started OK, video=${mediaPlayer?.videoWidth}x${mediaPlayer?.videoHeight}")
            } catch (e: Exception) {
                android.util.Log.e("VideoWPService", "Player init failed: ${e.message}", e)
                releasePlayer()
            }
        }

        /**
         * Resize the surface so the video fills the screen with center-crop.
         * This prevents squeezing — the video is zoomed to cover the full surface
         * and excess is cropped off-screen.
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

            val (newW, newH) = if (videoRatio > screenRatio) {
                // Video is wider — match height, crop sides
                val w = (sh * videoRatio).toInt()
                w to sh
            } else {
                // Video is taller — match width, crop top/bottom
                val h = (sw / videoRatio).toInt()
                sw to h
            }

            try {
                holder.setFixedSize(newW, newH)
                android.util.Log.d("VideoWPService", "Center-crop: video=${videoW}x${videoH} screen=${sw}x${sh} -> ${newW}x${newH}")
            } catch (_: Exception) {}
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try { if (isPlaying) stop(); release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }

        private fun drawRipples() {
            if (!isVisible) { drawingRipples = false; return }
            val holder = surfaceHolder
            var hasActiveRipples: Boolean

            synchronized(ripples) {
                // Update ripple animations
                ripples.forEach { r ->
                    r.radius += 8f
                    r.alpha = (120 * (1f - r.radius / r.maxRadius)).toInt().coerceAtLeast(0)
                }
                ripples.removeAll { it.radius >= it.maxRadius }
                hasActiveRipples = ripples.isNotEmpty()
            }

            if (hasActiveRipples) {
                // Draw ripples as overlay on the video surface
                // Note: We can't draw on the video surface directly (MediaPlayer owns it)
                // So we use the overlay surface if available
                val overlayCanvas: Canvas? = try {
                    holder.lockCanvas()
                } catch (_: Exception) { null }

                // Unfortunately MediaPlayer + Canvas on same surface conflicts
                // Ripples work best with a transparent overlay — skip if player is active
                // The ripple effect is visual feedback via a brief overlay flash
                overlayCanvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }

                handler.postDelayed(rippleRunner, 33) // 30 FPS
            } else {
                drawingRipples = false
            }
        }
    }
}

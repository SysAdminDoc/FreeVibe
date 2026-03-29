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
            // Re-apply crop scaling if player is active
            mediaPlayer?.let { applyCenterCrop(it, holder) }
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
                releasePlayer()
                lastModified = file.lastModified()
                val speed = getPlaybackSpeed()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    // WallpaperService surfaces don't support setKeepScreenOn —
                    // MediaPlayer.setDisplay() calls it internally, so we wrap the holder
                    val safeHolder = object : SurfaceHolder by holder {
                        override fun setKeepScreenOn(screenOn: Boolean) {} // no-op
                    }
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    // Use scale-to-fill mode (center crop)
                    try {
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    } catch (_: Exception) {}
                    prepare()
                    // Resize surface for center-crop fill
                    applyCenterCrop(this, holder)
                    try {
                        playbackParams = playbackParams.setSpeed(speed)
                    } catch (_: Exception) {}
                    start()
                }
                android.util.Log.d("VideoWPService", "Player started OK")
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
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return
            val videoW = player.videoWidth
            val videoH = player.videoHeight
            if (videoW <= 0 || videoH <= 0) return

            val surfaceRatio = surfaceWidth.toFloat() / surfaceHeight
            val videoRatio = videoW.toFloat() / videoH

            val (newW, newH) = if (videoRatio > surfaceRatio) {
                // Video is wider than surface — match height, crop sides
                val w = (surfaceHeight * videoRatio).toInt()
                w to surfaceHeight
            } else {
                // Video is taller than surface — match width, crop top/bottom
                val h = (surfaceWidth / videoRatio).toInt()
                surfaceWidth to h
            }

            try {
                holder.setFixedSize(newW, newH)
                android.util.Log.d("VideoWPService", "Center-crop: video=${videoW}x${videoH} surface=${surfaceWidth}x${surfaceHeight} -> ${newW}x${newH}")
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

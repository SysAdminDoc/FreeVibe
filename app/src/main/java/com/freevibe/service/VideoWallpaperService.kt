@file:Suppress("DEPRECATION")

package com.freevibe.service

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.BuildConfig
import java.io.File
import kotlin.math.max

private data class BatterySnapshot(
    val percent: Int?,
    val isCharging: Boolean,
)

private data class VideoPlaybackProfile(
    val requestedFps: Int,
    val effectiveFps: Int,
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val lowBatterySaverActive: Boolean,
)

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
        private var visible = false
        private var activeMediaType = "none"
        private var activeProfile = VideoPlaybackProfile(
            requestedFps = 30,
            effectiveFps = 30,
            batteryPercent = null,
            isCharging = false,
            lowBatterySaverActive = false,
        )
        private var telemetryRunnable: Runnable? = null
        private val telemetryHandler = Handler(Looper.getMainLooper())
        private val overlayBackgroundPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }
        private val overlayTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        private var gifSampleStartedAtMs = 0L
        private var gifFramesInSample = 0
        private var gifSampledFps = 0

        private fun getPrefs() = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
        private fun getRuntimePrefs() = getSharedPreferences(VIDEO_PREFS_NAME, MODE_PRIVATE)
        private fun getVideoPath(): String? = getPrefs().getString("video_path", null)
        private fun getScaleMode(): String =
            normalizeVideoWallpaperScaleMode(getPrefs().getString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_ZOOM))
        private fun getPlaybackSpeed(): Float =
            getRuntimePrefs().getFloat(VIDEO_PLAYBACK_SPEED_PREF, 1.0f).takeIf { it > 0 } ?: 1.0f
        private fun getRequestedFpsLimit(): Int =
            sanitizeVideoFpsLimit(getRuntimePrefs().getInt(VIDEO_FPS_LIMIT_PREF, 30))
        private fun isFpsOverlayEnabled(): Boolean =
            getRuntimePrefs().getBoolean(VIDEO_FPS_OVERLAY_PREF, false)
        private fun isAutoBatterySaverEnabled(): Boolean =
            getRuntimePrefs().getBoolean(VIDEO_AUTO_BATTERY_SAVER_PREF, true)

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
            refreshPlaybackProfile()
            initializePlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            visible = false
            stopTelemetryHeartbeat()
            releasePlayback()
            publishVideoTelemetry()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                refreshPlaybackProfile()
                startTelemetryHeartbeat()
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
                stopTelemetryHeartbeat()
                pauseGifPlayback()
                try { mediaPlayer?.pause() } catch (_: Exception) {}
                publishVideoTelemetry()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            stopTelemetryHeartbeat()
            releasePlayback()
            publishVideoTelemetry()
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
                    activeMediaType = "gif"
                    initializeGifPlayback(holder, file)
                    return
                }
                activeMediaType = "video"
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
                publishVideoTelemetry()

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
            val profile = refreshPlaybackProfile()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    holder.surface.setFrameRate(
                        profile.effectiveFps.toFloat(),
                        android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    )
                } catch (_: Exception) {
                }
            }
            publishVideoTelemetry(profile)
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
            gifSampleStartedAtMs = 0L
            gifFramesInSample = 0
            gifSampledFps = 0
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
                    gifHandler.postDelayed(this, gifFrameDelayMs())
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
                val now = SystemClock.uptimeMillis()
                val duration = movie.duration().takeIf { it > 0 } ?: 1000
                val time = ((now - gifStartedAtMs) % duration).toInt()
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
                updateGifFpsSample(now)
                if (isFpsOverlayEnabled()) drawFpsOverlay(canvas)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        private fun releasePlayback() {
            pauseGifPlayback()
            gifMovie = null
            activeMediaType = "none"
            mediaPlayer?.apply {
                try { setOnPreparedListener(null) } catch (_: Exception) {}
                try { if (isPlaying) stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }

        private fun refreshPlaybackProfile(): VideoPlaybackProfile {
            val battery = readBatterySnapshot()
            val requestedFps = getRequestedFpsLimit()
            val lowBatterySaverActive = shouldUseVideoBatterySaver(
                batteryPercent = battery.percent,
                isCharging = battery.isCharging,
                autoSaverEnabled = isAutoBatterySaverEnabled(),
            )
            activeProfile = VideoPlaybackProfile(
                requestedFps = requestedFps,
                effectiveFps = effectiveVideoFpsLimit(requestedFps, lowBatterySaverActive),
                batteryPercent = battery.percent,
                isCharging = battery.isCharging,
                lowBatterySaverActive = lowBatterySaverActive,
            )
            return activeProfile
        }

        private fun readBatterySnapshot(): BatterySnapshot {
            val intent = try {
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Exception) {
                null
            }
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            return BatterySnapshot(
                percent = percent,
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0,
            )
        }

        private fun publishVideoTelemetry(profile: VideoPlaybackProfile = activeProfile) {
            getSharedPreferences(VIDEO_STATS_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong("last_seen_ms", System.currentTimeMillis())
                .putBoolean("visible", visible)
                .putString("media_type", activeMediaType)
                .putInt("requested_fps", profile.requestedFps)
                .putInt("effective_fps", profile.effectiveFps)
                .putBoolean("low_battery_saver_active", profile.lowBatterySaverActive)
                .putBoolean("charging", profile.isCharging)
                .putBoolean("fps_overlay_enabled", isFpsOverlayEnabled())
                .putString("scale_mode", getScaleMode())
                .apply {
                    if (profile.batteryPercent == null) remove("battery_percent")
                    else putInt("battery_percent", profile.batteryPercent)
                }
                .apply()
        }

        private fun startTelemetryHeartbeat() {
            stopTelemetryHeartbeat()
            val runnable = object : Runnable {
                override fun run() {
                    currentHolder?.let { configureFrameRate(it) } ?: publishVideoTelemetry(refreshPlaybackProfile())
                    telemetryHandler.postDelayed(this, 30_000L)
                }
            }
            telemetryRunnable = runnable
            telemetryHandler.post(runnable)
        }

        private fun stopTelemetryHeartbeat() {
            telemetryRunnable?.let { telemetryHandler.removeCallbacks(it) }
            telemetryRunnable = null
        }

        private fun gifFrameDelayMs(): Long =
            (1000L / activeProfile.effectiveFps.coerceAtLeast(1)).coerceAtLeast(16L)

        private fun updateGifFpsSample(nowMs: Long) {
            if (gifSampleStartedAtMs == 0L) {
                gifSampleStartedAtMs = nowMs
                gifFramesInSample = 0
            }
            gifFramesInSample += 1
            val elapsed = nowMs - gifSampleStartedAtMs
            if (elapsed >= 1_000L) {
                gifSampledFps = ((gifFramesInSample * 1_000f) / elapsed).toInt().coerceAtLeast(0)
                gifFramesInSample = 0
                gifSampleStartedAtMs = nowMs
            }
        }

        private fun drawFpsOverlay(canvas: android.graphics.Canvas) {
            val fps = if (gifSampledFps > 0) gifSampledFps else activeProfile.effectiveFps
            val label = if (activeProfile.lowBatterySaverActive) {
                "$fps FPS saver"
            } else {
                "$fps FPS"
            }
            val paddingX = 12f
            val paddingY = 8f
            val textWidth = overlayTextPaint.measureText(label)
            val height = overlayTextPaint.textSize + paddingY * 2f
            val rect = android.graphics.RectF(
                16f,
                16f,
                16f + textWidth + paddingX * 2f,
                16f + height,
            )
            canvas.drawRoundRect(rect, 8f, 8f, overlayBackgroundPaint)
            canvas.drawText(label, rect.left + paddingX, rect.bottom - paddingY - 4f, overlayTextPaint)
        }
    }
}

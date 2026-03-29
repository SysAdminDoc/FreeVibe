package com.freevibe.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.BuildConfig

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Center-crop scaling, playback speed, auto-reload on file change.
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
        private fun getPlaybackSpeed(): Float =
            getSharedPreferences("freevibe_prefs", MODE_PRIVATE)
                .getFloat("video_playback_speed", 1.0f).takeIf { it > 0 } ?: 1.0f

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            if (screenWidth == 0 && screenHeight == 0) {
                screenWidth = width
                screenHeight = height
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
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
                val safeHolder = object : SurfaceHolder by holder {
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setDisplay(safeHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    try { setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) } catch (_: Exception) {}
                    prepare()
                    applyCenterCrop(this, holder)
                    try { playbackParams = playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    start()
                }
                if (BuildConfig.DEBUG) android.util.Log.d("VideoWPService", "Playing ${mediaPlayer?.videoWidth}x${mediaPlayer?.videoHeight} from $path")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("VideoWPService", "Init failed: ${e.message}")
                releasePlayer()
            }
        }

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
                (sh * videoRatio).toInt() to sh
            } else {
                sw to (sw / videoRatio).toInt()
            }
            try { holder.setFixedSize(newW, newH) } catch (_: Exception) {}
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try { if (isPlaying) stop(); release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

package com.freevibe.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Detects when a new video is applied and auto-reloads on next visibility change.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var currentHolder: SurfaceHolder? = null
        private var lastModified: Long = 0

        private fun getVideoPath(): String? =
            getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
                .getString("video_path", null)

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
                // Check if video file changed (new video applied from app)
                val path = getVideoPath()
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists() && file.lastModified() != lastModified) {
                        // New video — reload
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                }
                // Resume existing playback
                mediaPlayer?.let {
                    if (!it.isPlaying) {
                        it.seekTo(0)
                        it.start()
                    }
                }
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            val path = getVideoPath() ?: return
            val file = java.io.File(path)
            if (!file.exists()) return
            try {
                releasePlayer()
                lastModified = file.lastModified()
                val screenW = resources.displayMetrics.widthPixels
                val screenH = resources.displayMetrics.heightPixels

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    val wrappedHolder = object : SurfaceHolder by holder {
                        override fun setKeepScreenOn(screenOn: Boolean) {}
                    }
                    setDisplay(wrappedHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()

                    // Scale surface to fill screen (zoom-to-fill, crop excess)
                    val vw = videoWidth
                    val vh = videoHeight
                    if (vw > 0 && vh > 0) {
                        val videoRatio = vw.toFloat() / vh
                        val screenRatio = screenW.toFloat() / screenH
                        val (surfW, surfH) = if (videoRatio > screenRatio) {
                            // Video is wider than screen — match height, overflow width
                            val w = (screenH * videoRatio).toInt()
                            w to screenH
                        } else {
                            // Video is taller — match width, overflow height
                            val h = (screenW / videoRatio).toInt()
                            screenW to h
                        }
                        holder.setFixedSize(surfW, surfH)
                    }

                    start()
                }
            } catch (_: Exception) {
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try { if (isPlaying) stop(); release() } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

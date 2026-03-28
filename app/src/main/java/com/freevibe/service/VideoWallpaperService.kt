package com.freevibe.service

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/**
 * Live wallpaper service using ExoPlayer for better video scaling.
 * Automatically reloads when a new video file is detected.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var exoPlayer: ExoPlayer? = null
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
                val path = getVideoPath()
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists() && file.lastModified() != lastModified) {
                        currentHolder?.let { initializePlayer(it) }
                        return
                    }
                }
                exoPlayer?.let {
                    if (!it.isPlaying) {
                        it.seekTo(0)
                        it.play()
                    }
                }
            } else {
                exoPlayer?.pause()
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

                exoPlayer = ExoPlayer.Builder(this@VideoWallpaperService).build().apply {
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f

                    // When video size is known, scale surface to fill screen
                    addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                val videoRatio = videoSize.width.toFloat() / videoSize.height
                                val screenRatio = screenW.toFloat() / screenH
                                if (videoRatio > screenRatio) {
                                    // Video wider than screen — scale by height, crop width
                                    val surfW = (screenH * videoRatio).toInt()
                                    holder.setFixedSize(surfW, screenH)
                                } else {
                                    // Video taller — scale by width, crop height
                                    val surfH = (screenW / videoRatio).toInt()
                                    holder.setFixedSize(screenW, surfH)
                                }
                            }
                        }
                    })

                    setVideoSurfaceHolder(holder)
                    prepare()
                    play()
                }
            } catch (_: Exception) {
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            exoPlayer?.apply {
                try { stop(); release() } catch (_: Exception) {}
            }
            exoPlayer = null
        }
    }
}

package com.freevibe.service

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Uses MediaPlayer with a WallpaperService Engine to render video frames
 * to the wallpaper surface.
 *
 * Battery optimization: video plays once on screen wake, then pauses on last frame.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var videoPath: String? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Load video path from SharedPreferences
            val prefs = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
            videoPath = prefs.getString("video_path", null)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            initializePlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
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
            val path = videoPath ?: return
            try {
                releasePlayer()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)

                    // Wrap surface holder to prevent setKeepScreenOn crash
                    val wrappedHolder = object : SurfaceHolder by holder {
                        override fun setKeepScreenOn(screenOn: Boolean) {
                            // No-op: WallpaperService surfaces don't support this
                        }
                    }
                    setDisplay(wrappedHolder)

                    isLooping = false  // Play once per wake, battery optimization
                    setVolume(0f, 0f)  // Silent
                    prepare()
                    start()

                    setOnCompletionListener {
                        // Freeze on last frame - don't loop to save battery
                    }
                }
            } catch (e: Exception) {
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) stop()
                    release()
                } catch (_: Exception) {}
            }
            mediaPlayer = null
        }
    }
}

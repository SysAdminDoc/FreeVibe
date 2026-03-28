package com.freevibe.service

import android.content.SharedPreferences
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Live wallpaper service that plays a video file on the home/lock screen.
 * Automatically reloads when a new video is applied via SharedPreferences.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var mediaPlayer: MediaPlayer? = null
        private var videoPath: String? = null
        private var currentHolder: SurfaceHolder? = null
        private lateinit var prefs: SharedPreferences

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = getSharedPreferences("freevibe_live_wp", MODE_PRIVATE)
            videoPath = prefs.getString("video_path", null)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "video_path") {
                val newPath = prefs.getString("video_path", null)
                if (newPath != null && newPath != videoPath) {
                    videoPath = newPath
                    currentHolder?.let { initializePlayer(it) }
                }
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
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            releasePlayer()
        }

        private fun initializePlayer(holder: SurfaceHolder) {
            val path = videoPath ?: return
            if (!java.io.File(path).exists()) return
            try {
                releasePlayer()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    val wrappedHolder = object : SurfaceHolder by holder {
                        override fun setKeepScreenOn(screenOn: Boolean) {}
                    }
                    setDisplay(wrappedHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()
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

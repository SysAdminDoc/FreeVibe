package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.data.remote.weather.WeatherEffect

/**
 * Live wallpaper service that renders a static wallpaper image with
 * weather particle effects overlay (rain, snow, fog, stars).
 * Pauses rendering when not visible to save battery.
 * Targets 30 FPS for particle updates.
 */
class WeatherWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherEngine()

    inner class WeatherEngine : Engine() {
        private var renderer: WeatherParticleRenderer? = null
        private var wallpaperBitmap: Bitmap? = null
        private var scaledBitmap: Bitmap? = null
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val frameInterval = 33L // ~30 FPS

        private val drawRunner = Runnable { draw() }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            loadWallpaperBitmap()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer = WeatherParticleRenderer(width, height)
            scaledBitmap = wallpaperBitmap?.let { scaleBitmap(it, width, height) }
            loadWeatherFromPrefs()
            if (visible) scheduleDraw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                loadWeatherFromPrefs()
                scheduleDraw()
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            wallpaperBitmap?.recycle()
            scaledBitmap?.recycle()
        }

        private fun loadWallpaperBitmap() {
            val prefs = getSharedPreferences("freevibe_weather_wp", MODE_PRIVATE)
            val path = prefs.getString("wallpaper_path", null) ?: return
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    wallpaperBitmap = BitmapFactory.decodeFile(path)
                }
            } catch (_: Exception) {}
        }

        private fun loadWeatherFromPrefs() {
            val prefs = getSharedPreferences("freevibe_weather_wp", MODE_PRIVATE)
            val effectName = prefs.getString("weather_effect", "CLEAR_DAY") ?: "CLEAR_DAY"
            val wind = prefs.getFloat("wind_speed", 0f).toDouble()
            try {
                renderer?.setWeather(WeatherEffect.valueOf(effectName), wind)
            } catch (_: Exception) {
                renderer?.setWeather(WeatherEffect.CLEAR_DAY)
            }
        }

        private fun scheduleDraw() {
            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.post(drawRunner)
            }
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    // Draw wallpaper background
                    scaledBitmap?.let {
                        canvas.drawBitmap(it, 0f, 0f, null)
                    } ?: canvas.drawColor(android.graphics.Color.BLACK)

                    // Update and draw weather particles
                    renderer?.update()
                    renderer?.draw(canvas)
                }
            } catch (_: Exception) {
            } finally {
                canvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
            }

            if (visible) {
                handler.postDelayed(drawRunner, frameInterval)
            }
        }

        private fun scaleBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
            val scaleX = targetW.toFloat() / src.width
            val scaleY = targetH.toFloat() / src.height
            val scale = maxOf(scaleX, scaleY) // Fill (crop to fit)
            val matrix = Matrix().apply { setScale(scale, scale) }
            val scaledW = (src.width * scale).toInt()
            val scaledH = (src.height * scale).toInt()
            val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            // Center crop
            val x = (scaledW - targetW) / 2
            val y = (scaledH - targetH) / 2
            return if (x > 0 || y > 0) {
                Bitmap.createBitmap(scaled, x.coerceAtLeast(0), y.coerceAtLeast(0),
                    targetW.coerceAtMost(scaled.width - x.coerceAtLeast(0)),
                    targetH.coerceAtMost(scaled.height - y.coerceAtLeast(0)))
            } else scaled
        }
    }
}

package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.freevibe.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

/**
 * Live wallpaper service that creates a parallax/depth effect by splitting
 * a wallpaper image into foreground and background layers using ML Kit
 * Selfie Segmentation, then shifting layers at different rates based
 * on device tilt (accelerometer).
 *
 * Falls back to displaying the image normally if segmentation fails.
 */
class ParallaxWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ParallaxEngine()

    inner class ParallaxEngine : Engine(), SensorEventListener {

        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null

        private var originalBitmap: Bitmap? = null
        private var backgroundLayer: Bitmap? = null
        private var foregroundLayer: Bitmap? = null
        private var fallbackBitmap: Bitmap? = null

        private var screenWidth = 0
        private var screenHeight = 0

        // Smoothed tilt values (low-pass filtered)
        private var tiltX = 0f
        private var tiltY = 0f

        // Raw sensor gravity for low-pass filter
        private val gravity = FloatArray(3)

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val frameInterval = 33L // ~30 FPS
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Max parallax offset in pixels
        private val maxOffset = 30f
        // Foreground moves 1.5x the background offset
        private val fgMultiplier = 1.5f

        private val drawRunner = Runnable { draw() }

        private fun getPrefs() = getSharedPreferences("freevibe_parallax", MODE_PRIVATE)
        private fun getImagePath(): String? = getPrefs().getString("image_path", null)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            loadImage()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width
            screenHeight = height
            originalBitmap?.let { scaleAndSegment(it) }
                ?: loadImage()
            if (visible) scheduleDraw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                registerSensor()
                scheduleDraw()
            } else {
                unregisterSensor()
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
            unregisterSensor()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            unregisterSensor()
            recycleBitmaps()
        }

        // -- Sensor --

        private fun registerSensor() {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        private fun unregisterSensor() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            // Low-pass filter to smooth sensor noise
            val alpha = 0.15f
            gravity[0] = alpha * event.values[0] + (1 - alpha) * gravity[0]
            gravity[1] = alpha * event.values[1] + (1 - alpha) * gravity[1]

            // Normalize: ~9.8 at rest, tilt gives deviation
            // X: left/right tilt, Y: forward/back tilt
            tiltX = (gravity[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            tiltY = ((gravity[1] - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        // -- Image Loading --

        private fun loadImage() {
            val path = getImagePath() ?: return
            try {
                val file = java.io.File(path)
                if (!file.exists()) return
                val bmp = BitmapFactory.decodeFile(path) ?: return
                originalBitmap?.recycle()
                originalBitmap = bmp
                if (screenWidth > 0 && screenHeight > 0) {
                    scaleAndSegment(bmp)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("ParallaxWP", "Load failed: ${e.message}")
            }
        }

        private fun scaleAndSegment(source: Bitmap) {
            if (screenWidth <= 0 || screenHeight <= 0) return

            // Scale with extra padding for parallax movement (add maxOffset on each side)
            val padded = scaleBitmapCenterCrop(
                source,
                screenWidth + (maxOffset * 2).toInt(),
                screenHeight + (maxOffset * 2).toInt(),
            )
            fallbackBitmap?.recycle()
            fallbackBitmap = padded

            // Attempt ML Kit segmentation
            segmentImage(padded)
        }

        private fun segmentImage(bitmap: Bitmap) {
            try {
                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
                val segmenter = Segmentation.getClient(options)
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                segmenter.process(inputImage)
                    .addOnSuccessListener { mask ->
                        try {
                            if (bitmap.isRecycled) return@addOnSuccessListener
                            val maskBuffer = mask.buffer
                            val maskWidth = mask.width
                            val maskHeight = mask.height

                            // Convert ByteBuffer to FloatBuffer for confidence values
                            maskBuffer.rewind()
                            val floatBuffer = maskBuffer.asFloatBuffer()

                            // Build foreground bitmap using confidence mask
                            val bmpW = bitmap.width
                            val bmpH = bitmap.height
                            val pixels = IntArray(bmpW * bmpH)
                            bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)

                            val fgPixels = IntArray(bmpW * bmpH)

                            for (y in 0 until bmpH) {
                                for (x in 0 until bmpW) {
                                    val idx = y * bmpW + x
                                    // Map bitmap coords to mask coords
                                    val mx = (x * maskWidth / bmpW).coerceIn(0, maskWidth - 1)
                                    val my = (y * maskHeight / bmpH).coerceIn(0, maskHeight - 1)
                                    val maskIdx = my * maskWidth + mx
                                    val confidence = if (maskIdx < floatBuffer.limit()) {
                                        floatBuffer.get(maskIdx)
                                    } else 0f

                                    if (confidence > 0.5f) {
                                        // Foreground pixel: apply alpha based on confidence
                                        val srcPixel = pixels[idx]
                                        val a = (confidence * 255f).toInt().coerceIn(0, 255)
                                        fgPixels[idx] = (a shl 24) or (srcPixel and 0x00FFFFFF)
                                    } else {
                                        fgPixels[idx] = 0 // transparent
                                    }
                                }
                            }

                            val fgBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            fgBitmap.setPixels(fgPixels, 0, bmpW, 0, 0, bmpW, bmpH)

                            foregroundLayer?.recycle()
                            foregroundLayer = fgBitmap

                            // Background is the full image (foreground overlays it)
                            backgroundLayer?.recycle()
                            backgroundLayer = bitmap.copy(Bitmap.Config.ARGB_8888, false)

                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("ParallaxWP", "Segmentation succeeded: ${bmpW}x${bmpH}, mask ${maskWidth}x${maskHeight}")
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) android.util.Log.e("ParallaxWP", "Segment result error: ${e.message}")
                        }
                    }
                    .addOnFailureListener { e ->
                        // Segmentation failed - use fallback (single image, no parallax split)
                        if (BuildConfig.DEBUG) android.util.Log.w("ParallaxWP", "Segmentation failed, using fallback: ${e.message}")
                        backgroundLayer?.recycle()
                        backgroundLayer = null
                        foregroundLayer?.recycle()
                        foregroundLayer = null
                    }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("ParallaxWP", "Segmenter init error: ${e.message}")
            }
        }

        // -- Drawing --

        private fun scheduleDraw() {
            handler.removeCallbacks(drawRunner)
            if (visible) handler.post(drawRunner)
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)

                    val bgOffsetX = -tiltX * maxOffset
                    val bgOffsetY = -tiltY * maxOffset
                    val fgOffsetX = -tiltX * maxOffset * fgMultiplier
                    val fgOffsetY = -tiltY * maxOffset * fgMultiplier

                    // Center the padded bitmap on screen
                    val baseX = -maxOffset
                    val baseY = -maxOffset

                    val bg = backgroundLayer
                    val fg = foregroundLayer
                    val fb = fallbackBitmap

                    if (bg != null && fg != null) {
                        // Draw background layer with base offset
                        canvas.drawBitmap(bg, baseX + bgOffsetX, baseY + bgOffsetY, paint)
                        // Draw foreground layer with enhanced offset
                        canvas.drawBitmap(fg, baseX + fgOffsetX, baseY + fgOffsetY, paint)
                    } else if (fb != null) {
                        // Fallback: single image with slight parallax movement
                        canvas.drawBitmap(fb, baseX + bgOffsetX, baseY + bgOffsetY, paint)
                    }
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

        // -- Utilities --

        private fun scaleBitmapCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
            val scaleX = targetW.toFloat() / src.width
            val scaleY = targetH.toFloat() / src.height
            val scale = maxOf(scaleX, scaleY)
            val matrix = Matrix().apply { setScale(scale, scale) }
            val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            val scaledW = scaled.width
            val scaledH = scaled.height
            val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
            val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
            val cropW = targetW.coerceAtMost(scaledW - x).coerceAtLeast(1)
            val cropH = targetH.coerceAtMost(scaledH - y).coerceAtLeast(1)
            return if (x > 0 || y > 0) {
                Bitmap.createBitmap(scaled, x, y, cropW, cropH).also {
                    if (scaled != src) scaled.recycle()
                }
            } else scaled
        }

        private fun recycleBitmaps() {
            originalBitmap?.recycle(); originalBitmap = null
            backgroundLayer?.recycle(); backgroundLayer = null
            foregroundLayer?.recycle(); foregroundLayer = null
            fallbackBitmap?.recycle(); fallbackBitmap = null
        }
    }
}

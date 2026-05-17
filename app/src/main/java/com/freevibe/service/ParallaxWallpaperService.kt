package com.freevibe.service

import android.graphics.Bitmap
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
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions

/**
 * Live wallpaper service that creates a parallax/depth effect by splitting
 * a wallpaper image into foreground and background layers using ML Kit
 * Subject Segmentation (multi-subject, GA — replaced the long-running
 * selfie-segmentation beta per ROADMAP N-3), then shifting layers at
 * different rates based on device tilt (accelerometer).
 *
 * The segmenter model is unbundled — downloaded on first use via Google Play
 * services. We proactively request the install at engine creation so the
 * first apply isn't a silent no-op.
 *
 * Falls back to displaying the image normally if segmentation fails.
 */
class ParallaxWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ParallaxEngine()

    inner class ParallaxEngine : Engine(), SensorEventListener {

        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null

        private val bitmapLock = Any()
        private var originalBitmap: Bitmap? = null
        private var backgroundLayer: Bitmap? = null
        private var foregroundLayer: Bitmap? = null
        private var fallbackBitmap: Bitmap? = null
        private var activeSegmenter: SubjectSegmenter? = null

        private var screenWidth = 0
        private var screenHeight = 0

        // Smoothed tilt values (low-pass filtered)
        private var tiltX = 0f
        private var tiltY = 0f

        // Raw sensor gravity for low-pass filter
        private val gravity = FloatArray(3)

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        @Volatile private var destroyed = false
        private var segmentGeneration = 0L
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
            requestSegmenterModuleInstall()
        }

        /**
         * The Subject Segmentation model ships unbundled via Google Play services.
         * Asking for it once at engine create avoids the first-apply silent failure
         * mode where segmenter.process() returns MlKitException.UNAVAILABLE because
         * the module hasn't been delivered yet.
         */
        private fun requestSegmenterModuleInstall() {
            try {
                // Use a placeholder client to declare the module dependency. We don't
                // care about the install Task's result; downstream segmenter.process
                // already handles the not-yet-installed case by falling back to the
                // single-image path. This is best-effort warm-up only.
                val placeholderClient = SubjectSegmentation.getClient(
                    SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
                )
                val request = ModuleInstallRequest.newBuilder()
                    .addApi(placeholderClient)
                    .build()
                ModuleInstall.getClient(applicationContext)
                    .installModules(request)
                    .addOnCompleteListener {
                        try { placeholderClient.close() } catch (_: Exception) {}
                    }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("ParallaxWP", "Segmenter module install request failed: ${e.message}")
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            loadImage()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width
            screenHeight = height
            val bmp = synchronized(bitmapLock) { originalBitmap }
            if (bmp != null) scaleAndSegment(bmp)
            else loadImage()
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
            destroyed = true
            handler.removeCallbacks(drawRunner)
            unregisterSensor()
            activeSegmenter?.close()
            activeSegmenter = null
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
            Thread {
                try {
                    val file = java.io.File(path)
                    if (!file.exists()) return@Thread
                    val (targetWidth, targetHeight) = resolveDecodeTarget()
                    val bmp = BitmapSampling.decodeSampledBitmap(path, targetWidth, targetHeight)
                        ?: return@Thread
                    handler.post {
                        if (destroyed) { bmp.recycle(); return@post }
                        synchronized(bitmapLock) {
                            originalBitmap?.recycle()
                            originalBitmap = bmp
                        }
                        if (screenWidth > 0 && screenHeight > 0) {
                            scaleAndSegment(bmp)
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) android.util.Log.e("ParallaxWP", "Load failed: ${e.message}")
                }
            }.start()
        }

        private fun resolveDecodeTarget(): Pair<Int, Int> {
            val padding = (maxOffset * 2).toInt()
            val width = if (screenWidth > 0) screenWidth else resources.displayMetrics.widthPixels
            val height = if (screenHeight > 0) screenHeight else resources.displayMetrics.heightPixels
            return (width + padding).coerceAtLeast(1) to (height + padding).coerceAtLeast(1)
        }

        private fun scaleAndSegment(source: Bitmap) {
            if (destroyed || screenWidth <= 0 || screenHeight <= 0 || source.isRecycled) return

            // Scale with extra padding for parallax movement (add maxOffset on each side)
            val padded = scaleBitmapCenterCrop(
                source,
                screenWidth + (maxOffset * 2).toInt(),
                screenHeight + (maxOffset * 2).toInt(),
            )
            val generation = synchronized(bitmapLock) {
                fallbackBitmap?.recycle()
                fallbackBitmap = padded
                segmentGeneration += 1
                segmentGeneration
            }

            // Attempt ML Kit segmentation
            segmentImage(padded, generation)
        }

        private fun segmentImage(bitmap: Bitmap, generation: Long) {
            try {
                // Close-and-null BEFORE creating the next segmenter so a lingering
                // success/failure callback from the previous generation can't race us
                // into closing the NEW segmenter mid-flight.
                val previous = activeSegmenter
                activeSegmenter = null
                try { previous?.close() } catch (_: Exception) {}
                // Subject Segmentation returns one foreground-confidence mask (sum of
                // all detected subjects). Per-subject masks are also available but
                // Aura collapses everything in front of the background into a single
                // parallax foreground, matching the previous selfie-segmenter behavior.
                val options = SubjectSegmenterOptions.Builder()
                    .enableForegroundConfidenceMask()
                    .build()
                val segmenter = SubjectSegmentation.getClient(options)
                activeSegmenter = segmenter
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                segmenter.process(inputImage)
                    .addOnSuccessListener { result ->
                        // Guard against double-close if a newer segmenter already took over
                        synchronized(bitmapLock) {
                            if (activeSegmenter === segmenter) activeSegmenter = null
                        }
                        try { segmenter.close() } catch (_: Exception) {}
                        if (destroyed || bitmap.isRecycled) return@addOnSuccessListener

                        // bgBitmap and fgBitmap are allocated outside the lock (they can be
                        // large) and may OOM mid-flight. We must recycle whichever ones
                        // already exist before we bail out, or the native allocation leaks
                        // until the process dies — wallpaper-service processes are very
                        // long-lived, so the leak is observable.
                        var bgBitmap: Bitmap? = null
                        var fgBitmap: Bitmap? = null
                        var publishedToLayers = false
                        try {
                            // The foreground-confidence mask matches the input bitmap's
                            // dimensions when enableForegroundConfidenceMask() is set,
                            // so no width/height remapping is needed (a simplification
                            // over the old selfie-segmenter which returned a smaller mask).
                            val floatBuffer = result.foregroundConfidenceMask
                                ?: run {
                                    if (BuildConfig.DEBUG) {
                                        android.util.Log.w("ParallaxWP", "No foreground mask in result, using fallback")
                                    }
                                    return@addOnSuccessListener
                                }

                            // Extract pixels under lock to prevent race with recycleBitmaps()
                            val pixels: IntArray
                            val bmpW: Int
                            val bmpH: Int
                            synchronized(bitmapLock) {
                                if (bitmap.isRecycled) return@addOnSuccessListener
                                bmpW = bitmap.width
                                bmpH = bitmap.height
                                pixels = IntArray(bmpW * bmpH)
                                bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
                                // bitmap.copy() can return null on low-memory devices; fall back
                                // to a fresh ARGB_8888 allocation populated from the pixel array
                                // we just extracted so we always end up with a usable background.
                                bgBitmap = try { bitmap.copy(Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { null }
                            }
                            if (bgBitmap == null) {
                                // Reconstruct from the IntArray we have on hand rather than
                                // dropping out — keeps the parallax layered effect even when
                                // the OS short-circuits a direct bitmap.copy.
                                bgBitmap = try {
                                    Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                        .also { it.setPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH) }
                                } catch (_: OutOfMemoryError) {
                                    null
                                }
                            }

                            floatBuffer.rewind()
                            val fgPixels = IntArray(bmpW * bmpH)
                            val maskLimit = floatBuffer.limit()

                            for (i in 0 until bmpW * bmpH) {
                                val confidence = if (i < maskLimit) floatBuffer.get(i) else 0f
                                if (confidence > 0.5f) {
                                    val srcPixel = pixels[i]
                                    val a = (confidence * 255f).toInt().coerceIn(0, 255)
                                    fgPixels[i] = (a shl 24) or (srcPixel and 0x00FFFFFF)
                                } else {
                                    fgPixels[i] = 0
                                }
                            }

                            fgBitmap = try {
                                Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            } catch (e: OutOfMemoryError) {
                                if (BuildConfig.DEBUG) android.util.Log.w("ParallaxWP", "fgBitmap OOM: ${e.message}")
                                return@addOnSuccessListener
                            }
                            fgBitmap!!.setPixels(fgPixels, 0, bmpW, 0, 0, bmpW, bmpH)

                            synchronized(bitmapLock) {
                                if (generation != segmentGeneration) return@addOnSuccessListener
                                val oldFg = foregroundLayer
                                val oldBg = backgroundLayer
                                foregroundLayer = fgBitmap
                                backgroundLayer = bgBitmap
                                oldFg?.recycle()
                                oldBg?.recycle()
                                // Only retire the fallback if we actually have both layers; otherwise
                                // keep it so draw() has SOMETHING to render. bgBitmap may legitimately
                                // be null if reconstruction also failed.
                                if (foregroundLayer != null && backgroundLayer != null) {
                                    fallbackBitmap?.recycle()
                                    fallbackBitmap = null
                                }
                                publishedToLayers = true
                            }

                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("ParallaxWP", "Subject segmentation succeeded: ${bmpW}x${bmpH}, mask cap $maskLimit")
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) android.util.Log.e("ParallaxWP", "Segment result error: ${e.message}")
                        } finally {
                            if (!publishedToLayers) {
                                // Anything we allocated has to be recycled here — otherwise the
                                // native allocations stay parked until the wallpaper engine dies.
                                try { fgBitmap?.recycle() } catch (_: Throwable) {}
                                try { bgBitmap?.recycle() } catch (_: Throwable) {}
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        synchronized(bitmapLock) {
                            if (activeSegmenter === segmenter) activeSegmenter = null
                        }
                        try { segmenter.close() } catch (_: Exception) {}
                        if (destroyed) return@addOnFailureListener
                        if (BuildConfig.DEBUG) android.util.Log.w("ParallaxWP", "Subject segmentation failed, using fallback: ${e.message}")
                        synchronized(bitmapLock) {
                            if (generation != segmentGeneration) return@addOnFailureListener
                            val oldBg = backgroundLayer
                            val oldFg = foregroundLayer
                            backgroundLayer = null
                            foregroundLayer = null
                            oldBg?.recycle()
                            oldFg?.recycle()
                        }
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

                    val bg: Bitmap?
                    val fg: Bitmap?
                    val fb: Bitmap?
                    synchronized(bitmapLock) {
                        bg = backgroundLayer
                        fg = foregroundLayer
                        fb = fallbackBitmap
                    }

                    if (bg != null && fg != null && !bg.isRecycled && !fg.isRecycled) {
                        // Draw background layer with base offset
                        canvas.drawBitmap(bg, baseX + bgOffsetX, baseY + bgOffsetY, paint)
                        // Draw foreground layer with enhanced offset
                        canvas.drawBitmap(fg, baseX + fgOffsetX, baseY + fgOffsetY, paint)
                    } else if (fb != null && !fb.isRecycled) {
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
                // If createBitmap throws (OOM / invalid rect), we still need to recycle
                // `scaled` or it leaks as a native allocation orphan.
                val cropped = try {
                    Bitmap.createBitmap(scaled, x, y, cropW, cropH)
                } catch (t: Throwable) {
                    if (scaled !== src) try { scaled.recycle() } catch (_: Throwable) {}
                    throw t
                }
                if (cropped !== scaled && scaled !== src) {
                    try { scaled.recycle() } catch (_: Throwable) {}
                }
                cropped
            } else {
                if (scaled === src) src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) else scaled
            }
        }

        private fun recycleBitmaps() {
            synchronized(bitmapLock) {
                originalBitmap?.recycle(); originalBitmap = null
                backgroundLayer?.recycle(); backgroundLayer = null
                foregroundLayer?.recycle(); foregroundLayer = null
                fallbackBitmap?.recycle(); fallbackBitmap = null
            }
        }
    }
}

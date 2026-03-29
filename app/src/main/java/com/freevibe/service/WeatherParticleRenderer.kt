package com.freevibe.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.freevibe.data.remote.weather.WeatherEffect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas-based particle renderer for weather effects overlay on wallpapers.
 * Renders rain, snow, fog, and sun rays as lightweight particle systems.
 * Designed for 30 FPS cap with minimal battery impact.
 */
class WeatherParticleRenderer(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    private var particles = mutableListOf<Particle>()
    private var effect: WeatherEffect = WeatherEffect.CLEAR_DAY
    private var windSpeed: Double = 0.0
    private var flashAlpha = 0f // For thunderstorm lightning
    private var fogOffset = 0f

    private val rainPaint = Paint().apply { strokeWidth = 2f; isAntiAlias = true }
    private val snowPaint = Paint().apply { isAntiAlias = true }
    private val fogPaint = Paint().apply { isAntiAlias = true }
    private val flashPaint = Paint()

    data class Particle(
        var x: Float,
        var y: Float,
        var speed: Float,
        var size: Float,
        var alpha: Int = 255,
        var drift: Float = 0f, // horizontal wind drift
        var rotation: Float = 0f,
    )

    fun setWeather(effect: WeatherEffect, windSpeed: Double = 0.0) {
        this.effect = effect
        this.windSpeed = windSpeed
        initParticles()
    }

    private fun initParticles() {
        particles.clear()
        val count = when (effect) {
            WeatherEffect.RAIN -> 150
            WeatherEffect.THUNDERSTORM -> 200
            WeatherEffect.SNOW -> 80
            WeatherEffect.FOG -> 0 // Fog uses gradient overlay, not particles
            else -> 0
        }
        repeat(count) {
            particles.add(createParticle(randomY = true))
        }
    }

    private fun createParticle(randomY: Boolean = false): Particle {
        val windDrift = (windSpeed * 0.3).toFloat()
        return when (effect) {
            WeatherEffect.RAIN, WeatherEffect.THUNDERSTORM -> Particle(
                x = Random.nextFloat() * screenWidth,
                y = if (randomY) Random.nextFloat() * screenHeight else -Random.nextFloat() * 50,
                speed = 15f + Random.nextFloat() * 10f,
                size = 10f + Random.nextFloat() * 20f,
                alpha = 60 + Random.nextInt(80),
                drift = windDrift + Random.nextFloat() * 2f - 1f,
            )
            WeatherEffect.SNOW -> Particle(
                x = Random.nextFloat() * screenWidth,
                y = if (randomY) Random.nextFloat() * screenHeight else -Random.nextFloat() * 50,
                speed = 1.5f + Random.nextFloat() * 2f,
                size = 3f + Random.nextFloat() * 5f,
                alpha = 150 + Random.nextInt(105),
                drift = windDrift + Random.nextFloat() * 1.5f - 0.75f,
                rotation = Random.nextFloat() * 360f,
            )
            else -> Particle(0f, 0f, 0f, 0f)
        }
    }

    /** Update particle positions. Call at 30 FPS. */
    fun update() {
        particles.forEachIndexed { i, p ->
            p.y += p.speed
            p.x += p.drift
            if (effect == WeatherEffect.SNOW) {
                p.x += sin(p.y * 0.01f) * 0.5f // Gentle sine wave drift
                p.rotation += 0.5f
            }
            // Reset particles that go off screen
            if (p.y > screenHeight + 10 || p.x < -20 || p.x > screenWidth + 20) {
                val fresh = createParticle(randomY = false)
                p.x = fresh.x; p.y = fresh.y; p.speed = fresh.speed
                p.size = fresh.size; p.alpha = fresh.alpha; p.drift = fresh.drift
            }
        }

        // Thunderstorm flash
        if (effect == WeatherEffect.THUNDERSTORM) {
            if (flashAlpha > 0) {
                flashAlpha -= 8f
            } else if (Random.nextInt(120) == 0) { // ~0.25s avg between flashes at 30fps
                flashAlpha = 80f + Random.nextFloat() * 60f
            }
        }

        // Fog drift
        if (effect == WeatherEffect.FOG) {
            fogOffset += 0.3f
            if (fogOffset > screenWidth) fogOffset = 0f
        }
    }

    /** Draw weather effects onto the canvas. */
    fun draw(canvas: Canvas) {
        when (effect) {
            WeatherEffect.RAIN, WeatherEffect.THUNDERSTORM -> drawRain(canvas)
            WeatherEffect.SNOW -> drawSnow(canvas)
            WeatherEffect.FOG -> drawFog(canvas)
            WeatherEffect.CLEAR_NIGHT -> drawStars(canvas)
            else -> {} // Clear day, cloudy — no overlay
        }

        // Lightning flash
        if (effect == WeatherEffect.THUNDERSTORM && flashAlpha > 0) {
            flashPaint.color = Color.argb(flashAlpha.toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), flashPaint)
        }
    }

    private fun drawRain(canvas: Canvas) {
        particles.forEach { p ->
            rainPaint.color = Color.argb(p.alpha, 180, 200, 220)
            rainPaint.strokeWidth = if (p.size > 20) 2.5f else 1.5f
            val endX = p.x + p.drift * 2
            val endY = p.y + p.size
            canvas.drawLine(p.x, p.y, endX, endY, rainPaint)
        }
    }

    private fun drawSnow(canvas: Canvas) {
        particles.forEach { p ->
            snowPaint.color = Color.argb(p.alpha, 255, 255, 255)
            canvas.drawCircle(p.x, p.y, p.size, snowPaint)
        }
    }

    private fun drawFog(canvas: Canvas) {
        // Multiple semi-transparent gradient layers drifting slowly
        for (layer in 0..2) {
            val y = screenHeight * (0.3f + layer * 0.2f)
            val alpha = (40 - layer * 10).coerceAtLeast(10)
            fogPaint.shader = RadialGradient(
                screenWidth / 2f + fogOffset * (layer + 1) * 0.3f,
                y,
                screenWidth * 0.8f,
                Color.argb(alpha, 200, 210, 220),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), fogPaint)
        }
    }

    private fun drawStars(canvas: Canvas) {
        // Static twinkling stars for clear night with per-star phase offset
        val starPaint = Paint().apply { isAntiAlias = true }
        val seed = 12345L
        val rng = Random(seed)
        repeat(30) {
            val x = rng.nextFloat() * screenWidth
            val y = rng.nextFloat() * screenHeight * 0.6f
            val size = 1f + rng.nextFloat() * 2f
            val phase = rng.nextFloat() * (2 * Math.PI)
            val twinkle = (sin(System.currentTimeMillis() * 0.003 + phase) * 0.5 + 0.5).toFloat()
            starPaint.color = Color.argb((twinkle * 180).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, size, starPaint)
        }
    }
}

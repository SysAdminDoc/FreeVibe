package com.freevibe.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Decorative VFX particle overlays for wallpapers (non-weather).
 * Supports: fireflies, sakura petals, embers, bubbles, leaves, sparkles.
 * All effects are Canvas-based at 30 FPS with minimal battery impact.
 */
class VfxParticleRenderer(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    enum class VfxEffect { FIREFLIES, SAKURA, EMBERS, BUBBLES, LEAVES, SPARKLES, NONE }

    private var effect = VfxEffect.NONE
    private val particles = mutableListOf<VfxParticle>()
    private val paint = Paint().apply { isAntiAlias = true }

    data class VfxParticle(
        var x: Float, var y: Float,
        var speed: Float, var size: Float,
        var alpha: Float = 1f, var drift: Float = 0f,
        var rotation: Float = 0f, var rotSpeed: Float = 0f,
        var phase: Float = 0f, // for sine wave movement
        var color: Int = Color.WHITE,
    ) {
        fun copyFrom(other: VfxParticle) {
            x = other.x; y = other.y; speed = other.speed; size = other.size
            alpha = other.alpha; drift = other.drift; rotation = other.rotation
            rotSpeed = other.rotSpeed; phase = other.phase; color = other.color
        }
    }

    fun setEffect(effect: VfxEffect) {
        this.effect = effect
        particles.clear()
        if (effect == VfxEffect.NONE) return
        val count = when (effect) {
            VfxEffect.FIREFLIES -> 25
            VfxEffect.SAKURA -> 40
            VfxEffect.EMBERS -> 50
            VfxEffect.BUBBLES -> 20
            VfxEffect.LEAVES -> 30
            VfxEffect.SPARKLES -> 35
            VfxEffect.NONE -> 0
        }
        repeat(count) { particles.add(createParticle(randomPos = true)) }
    }

    private fun createParticle(randomPos: Boolean = false): VfxParticle {
        val ry = if (randomPos) Random.nextFloat() * screenHeight else 0f
        return when (effect) {
            VfxEffect.FIREFLIES -> VfxParticle(
                x = Random.nextFloat() * screenWidth,
                y = screenHeight * 0.4f + Random.nextFloat() * screenHeight * 0.6f,
                speed = 0f, size = 3f + Random.nextFloat() * 4f,
                phase = Random.nextFloat() * 6.28f,
                color = Color.argb(255, 200, 255, 100),
            )
            VfxEffect.SAKURA -> VfxParticle(
                x = Random.nextFloat() * screenWidth * 1.2f,
                y = if (randomPos) ry else -Random.nextFloat() * 100f,
                speed = 1.2f + Random.nextFloat() * 1.5f,
                size = 6f + Random.nextFloat() * 8f,
                drift = 0.5f + Random.nextFloat() * 1f,
                rotation = Random.nextFloat() * 360f,
                rotSpeed = 1f + Random.nextFloat() * 2f,
                color = Color.argb(255, 255, 182, 193),
            )
            VfxEffect.EMBERS -> VfxParticle(
                x = Random.nextFloat() * screenWidth,
                y = if (randomPos) ry else screenHeight + Random.nextFloat() * 50f,
                speed = -(2f + Random.nextFloat() * 3f), // upward
                size = 2f + Random.nextFloat() * 4f,
                drift = Random.nextFloat() * 2f - 1f,
                phase = Random.nextFloat() * 6.28f,
                color = Color.argb(255, 255, (100 + Random.nextInt(100)), 0),
            )
            VfxEffect.BUBBLES -> VfxParticle(
                x = Random.nextFloat() * screenWidth,
                y = if (randomPos) ry else screenHeight + Random.nextFloat() * 50f,
                speed = -(0.8f + Random.nextFloat() * 1.2f), // upward
                size = 8f + Random.nextFloat() * 20f,
                drift = Random.nextFloat() * 0.5f - 0.25f,
                phase = Random.nextFloat() * 6.28f,
                color = Color.argb(60, 200, 220, 255),
            )
            VfxEffect.LEAVES -> VfxParticle(
                x = Random.nextFloat() * screenWidth * 1.2f,
                y = if (randomPos) ry else -Random.nextFloat() * 80f,
                speed = 1.5f + Random.nextFloat() * 2f,
                size = 8f + Random.nextFloat() * 10f,
                drift = 0.3f + Random.nextFloat() * 1.5f,
                rotation = Random.nextFloat() * 360f,
                rotSpeed = 0.5f + Random.nextFloat() * 2f,
                color = listOf(
                    Color.argb(255, 200, 120, 30), Color.argb(255, 180, 80, 20),
                    Color.argb(255, 220, 160, 40), Color.argb(255, 160, 100, 20),
                ).random(),
            )
            VfxEffect.SPARKLES -> VfxParticle(
                x = Random.nextFloat() * screenWidth,
                y = Random.nextFloat() * screenHeight,
                speed = 0f, size = 2f + Random.nextFloat() * 3f,
                phase = Random.nextFloat() * 6.28f,
                color = Color.WHITE,
            )
            VfxEffect.NONE -> VfxParticle(0f, 0f, 0f, 0f)
        }
    }

    fun update() {
        if (effect == VfxEffect.NONE) return
        particles.forEachIndexed { i, p ->
            when (effect) {
                VfxEffect.FIREFLIES -> {
                    p.phase += 0.03f
                    p.x += sin(p.phase * 1.5f) * 1.5f
                    p.y += cos(p.phase) * 0.8f
                    p.alpha = (sin(p.phase * 2f) * 0.4f + 0.6f).coerceIn(0.1f, 1f)
                    // Wrap around
                    if (p.x < -20) p.x = screenWidth + 10f
                    if (p.x > screenWidth + 20) p.x = -10f
                    if (p.y < screenHeight * 0.3f) p.y = screenHeight.toFloat()
                    if (p.y > screenHeight + 10) p.y = screenHeight * 0.4f
                }
                VfxEffect.SAKURA, VfxEffect.LEAVES -> {
                    p.y += p.speed
                    p.x += p.drift + sin(p.y * 0.008f) * 1.5f
                    p.rotation += p.rotSpeed
                    if (p.y > screenHeight + 20 || p.x < -30) {
                        val fresh = createParticle()
                        p.copyFrom(fresh)
                    }
                }
                VfxEffect.EMBERS -> {
                    p.y += p.speed
                    p.x += p.drift + sin(p.phase) * 0.5f
                    p.phase += 0.05f
                    p.alpha = (1f - (screenHeight - p.y).coerceAtLeast(0f) / screenHeight * 1.5f).coerceIn(0.1f, 1f)
                    if (p.y < -20) {
                        val fresh = createParticle()
                        p.copyFrom(fresh)
                    }
                }
                VfxEffect.BUBBLES -> {
                    p.y += p.speed
                    p.x += sin(p.phase) * 0.3f
                    p.phase += 0.02f
                    if (p.y < -30) {
                        val fresh = createParticle()
                        p.copyFrom(fresh)
                    }
                }
                VfxEffect.SPARKLES -> {
                    p.phase += 0.06f
                    p.alpha = (sin(p.phase) * 0.5f + 0.5f).coerceIn(0f, 1f)
                }
                VfxEffect.NONE -> {}
            }
        }
    }

    fun draw(canvas: Canvas) {
        if (effect == VfxEffect.NONE) return
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 1f
        particles.forEach { p ->
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            when (effect) {
                VfxEffect.FIREFLIES -> {
                    // Glow circle
                    paint.alpha = (p.alpha * 80).toInt().coerceIn(0, 255)
                    canvas.drawCircle(p.x, p.y, p.size * 3, paint)
                    paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                }
                VfxEffect.SAKURA -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    // Simple petal shape: two overlapping ovals
                    canvas.drawOval(-p.size, -p.size * 0.4f, p.size * 0.3f, p.size * 0.4f, paint)
                    canvas.drawOval(-p.size * 0.3f, -p.size * 0.3f, p.size, p.size * 0.3f, paint)
                    canvas.restore()
                }
                VfxEffect.EMBERS -> {
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                    // Slight glow
                    paint.alpha = (p.alpha * 40).toInt().coerceIn(0, 255)
                    canvas.drawCircle(p.x, p.y, p.size * 2.5f, paint)
                }
                VfxEffect.BUBBLES -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                    // Highlight
                    paint.style = Paint.Style.FILL
                    paint.alpha = (p.alpha * 100).toInt().coerceIn(0, 255)
                    canvas.drawCircle(p.x - p.size * 0.3f, p.y - p.size * 0.3f, p.size * 0.2f, paint)
                    paint.style = Paint.Style.FILL
                }
                VfxEffect.LEAVES -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    // Leaf shape: pointed oval
                    val path = Path().apply {
                        moveTo(-p.size, 0f)
                        quadTo(-p.size * 0.5f, -p.size * 0.4f, 0f, 0f)
                        quadTo(-p.size * 0.5f, p.size * 0.4f, -p.size, 0f)
                        moveTo(0f, 0f)
                        quadTo(p.size * 0.5f, -p.size * 0.3f, p.size, 0f)
                        quadTo(p.size * 0.5f, p.size * 0.3f, 0f, 0f)
                    }
                    canvas.drawPath(path, paint)
                    canvas.restore()
                }
                VfxEffect.SPARKLES -> {
                    // 4-point star
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    val s = p.size * p.alpha
                    paint.strokeWidth = 1.5f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(-s, 0f, s, 0f, paint)
                    canvas.drawLine(0f, -s, 0f, s, paint)
                    canvas.drawLine(-s * 0.6f, -s * 0.6f, s * 0.6f, s * 0.6f, paint)
                    canvas.drawLine(-s * 0.6f, s * 0.6f, s * 0.6f, -s * 0.6f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.restore()
                }
                VfxEffect.NONE -> {}
            }
        }
    }
}

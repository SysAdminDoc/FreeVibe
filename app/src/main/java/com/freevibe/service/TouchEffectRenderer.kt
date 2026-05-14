package com.freevibe.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun parseTouchEffectStrength(value: String?): TouchEffectRenderer.Strength =
    when (value?.trim()?.uppercase(Locale.ROOT)) {
        "SUBTLE" -> TouchEffectRenderer.Strength.SUBTLE
        "STRONG" -> TouchEffectRenderer.Strength.STRONG
        else -> TouchEffectRenderer.Strength.OFF
    }

class TouchEffectRenderer(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    enum class Strength { OFF, SUBTLE, STRONG }

    private data class Burst(
        val x: Float,
        val y: Float,
        val startedAtMs: Long,
        val strength: Strength,
    )

    private val bursts = mutableListOf<Burst>()
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var strength = Strength.OFF

    fun setStrength(strength: Strength) {
        this.strength = strength
        if (strength == Strength.OFF) bursts.clear()
    }

    fun onTouch(x: Float, y: Float, nowMs: Long = SystemClock.uptimeMillis()) {
        if (strength == Strength.OFF) return
        bursts.add(Burst(x, y, nowMs, strength))
        while (bursts.size > MAX_TOUCH_BURSTS) bursts.removeAt(0)
    }

    fun update(nowMs: Long = SystemClock.uptimeMillis()) {
        bursts.removeAll { nowMs - it.startedAtMs > TOUCH_BURST_DURATION_MS }
    }

    fun draw(canvas: Canvas, nowMs: Long = SystemClock.uptimeMillis()) {
        if (bursts.isEmpty()) return
        bursts.forEach { burst ->
            val progress = ((nowMs - burst.startedAtMs).toFloat() / TOUCH_BURST_DURATION_MS)
                .coerceIn(0f, 1f)
            val fade = 1f - progress
            val maxRadius = minOf(screenWidth, screenHeight) *
                if (burst.strength == Strength.STRONG) 0.18f else 0.11f
            val radius = maxRadius * easeOut(progress)

            ripplePaint.color = Color.argb((110 * fade).toInt().coerceIn(0, 110), 255, 255, 255)
            ripplePaint.strokeWidth = if (burst.strength == Strength.STRONG) 3.2f else 2.0f
            canvas.drawCircle(burst.x, burst.y, radius, ripplePaint)

            val sparkleCount = if (burst.strength == Strength.STRONG) 10 else 5
            val sparkleLength = if (burst.strength == Strength.STRONG) 18f else 11f
            sparklePaint.color = Color.argb((170 * fade).toInt().coerceIn(0, 170), 255, 255, 255)
            sparklePaint.strokeWidth = if (burst.strength == Strength.STRONG) 2.0f else 1.4f
            repeat(sparkleCount) { index ->
                val angle = (2.0 * PI * index / sparkleCount).toFloat()
                val start = radius * 0.7f
                val end = start + sparkleLength * fade
                val sx = burst.x + cos(angle) * start
                val sy = burst.y + sin(angle) * start
                val ex = burst.x + cos(angle) * end
                val ey = burst.y + sin(angle) * end
                canvas.drawLine(sx, sy, ex, ey, sparklePaint)
            }
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private companion object {
        const val TOUCH_BURST_DURATION_MS = 650L
        const val MAX_TOUCH_BURSTS = 8
    }
}

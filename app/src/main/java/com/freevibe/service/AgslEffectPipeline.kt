package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/**
 * AGSL (Android Graphics Shading Language) runtime-shader pipeline. Roadmap N-3.
 *
 * This is the scaffold layer Aura's wallpaper editor and live-wallpaper engines call
 * into for GPU-accelerated image effects. Effects can be composed (apply N filters in
 * sequence) and respect Aura's existing Canvas fallback for pre-Android-13 devices.
 *
 * Effects ship in this file as small AGSL programs. Concrete effects to land next:
 *   - DEPTH_SHADE       — darken pixels in the background mask region (Pixel "Cinematic")
 *   - SUBJECT_TINT_PASS — apply a hue shift to background only, subject untinted
 *   - SHAPE_BACKDROP    — color-matched solid fill behind a subject cutout
 *   - WEATHER_FADE      — alpha-fade onto an overlay (replaces VfxParticleRenderer's
 *                          additive blend so we can off-load to GPU)
 *
 * Usage:
 *   ```
 *   val pipeline = AgslEffectPipeline()
 *   if (pipeline.isSupported) {
 *       val output = pipeline.apply(input, AgslEffect.IDENTITY)
 *   } else {
 *       // Canvas fallback path (existing Aura behavior)
 *   }
 *   ```
 *
 * NOTE: AGSL `RuntimeShader` is API 33+ (Android 13). Aura minSdk is 26; callers MUST
 * check [isSupported] before calling [apply]. Tests should mock `isSupported = false`
 * to exercise the fallback path.
 */
class AgslEffectPipeline {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Apply an effect to a source bitmap and return a new bitmap with the result.
     * Caller owns the returned bitmap; recycle when done.
     *
     * Falls back to a straight bitmap copy on unsupported devices so the caller can
     * write code that doesn't branch.
     */
    fun apply(source: Bitmap, effect: AgslEffect): Bitmap {
        if (!isSupported || effect == AgslEffect.IDENTITY) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Defensive: isSupported already gated above, but keep the guard explicit
            // so a future minSdk bump that crosses 33 doesn't leak a NoClassDefFoundError.
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        return applyAgsl(source, effect)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyAgsl(source: Bitmap, effect: AgslEffect): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val shader = RuntimeShader(effect.agsl).apply {
            setInputShader("src", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            effect.applyUniforms(this)
        }
        val paint = Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return output
    }
}

/**
 * Catalog of AGSL effects. Each effect is a small AGSL fragment program + the uniform
 * setup that drives it. Add new effects here, then wire them into the wallpaper editor
 * + live-wallpaper services in follow-up passes (see ROADMAP N-3 / NX-1 / NX-2).
 */
sealed class AgslEffect(internal val agsl: String) {

    internal open fun applyUniforms(@Suppress("UNUSED_PARAMETER") shader: Any) = Unit

    /**
     * Passthrough — used by the fallback path. Kept as an explicit variant so callers
     * can `apply(bitmap, AgslEffect.IDENTITY)` instead of branching on `isSupported`.
     */
    object IDENTITY : AgslEffect(
        """
        uniform shader src;
        half4 main(float2 c) { return src.eval(c); }
        """.trimIndent()
    )

    /**
     * Background darken at intensity ∈ [0, 1]. Subject-aware variants will take a
     * mask shader as a second uniform; this scaffold version operates on the whole
     * bitmap so wallpaper-editor brightness/contrast can hand off to it without
     * additional dependencies.
     */
    class DEPTH_SHADE(private val intensity: Float) : AgslEffect(
        """
        uniform shader src;
        uniform half intensity;
        half4 main(float2 c) {
            half4 px = src.eval(c);
            return half4(px.rgb * (1.0 - intensity), px.a);
        }
        """.trimIndent()
    ) {
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun applyUniforms(shader: Any) {
            (shader as RuntimeShader).setFloatUniform("intensity", intensity.coerceIn(0f, 1f))
        }
    }
}

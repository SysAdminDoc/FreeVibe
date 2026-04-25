package com.freevibe.service

/**
 * Pure-JVM accent selector. Lives outside [ColorExtractor] so the ladder logic
 * can be unit-tested without an Android shim around `Color.colorToHSL` or
 * Palette.
 *
 * Why this exists: `Palette.getDominantColor()` happily returns a near-gray
 * for cartoon or solid-color images, and Material You preview rendered with
 * a gray "accent" reads as broken to users. The fix is a fallback ladder
 * that prefers more saturated swatches when dominant fails a quality gate.
 */
object ColorAccentSelector {

    /** HSL[0]=hue, HSL[1]=saturation 0..1, HSL[2]=lightness 0..1. */
    fun interface HslProvider {
        fun hslOf(color: Int): FloatArray
    }

    /**
     * Saturation under this threshold is "gray-ish enough" that we'd rather
     * fall through to the next swatch.
     */
    private const val MIN_SATURATION = 0.20f

    /** Lightness floor — below this is "almost black", a poor accent. */
    private const val MIN_LIGHTNESS = 0.18f

    /** Lightness ceiling — above this is "almost white", a poor accent. */
    private const val MAX_LIGHTNESS = 0.82f

    fun selectAccent(
        dominant: Int,
        vibrantDark: Int,
        vibrant: Int,
        vibrantLight: Int,
        mutedDark: Int,
        muted: Int,
        mutedLight: Int,
        hslOf: HslProvider,
    ): Int {
        if (passesQualityGate(dominant, hslOf)) return dominant

        // Order: prefer saturated-and-not-too-light swatches first, then less-
        // saturated ones, but always non-zero. The dominant goes last as a
        // final non-zero floor (it might be a useless gray, but it beats 0).
        val ladder = intArrayOf(
            vibrantDark, vibrant, vibrantLight,
            mutedDark, muted, mutedLight,
        )
        for (color in ladder) {
            if (color != 0 && passesQualityGate(color, hslOf)) return color
        }
        // Relax the gate: any non-zero swatch beats 0.
        for (color in ladder) {
            if (color != 0) return color
        }
        // Final fallback — even gray dominant is preferred over 0/transparent.
        return dominant
    }

    private fun passesQualityGate(color: Int, hslOf: HslProvider): Boolean {
        if (color == 0) return false
        val hsl = hslOf.hslOf(color)
        val saturation = hsl[1]
        val lightness = hsl[2]
        return saturation >= MIN_SATURATION &&
            lightness in MIN_LIGHTNESS..MAX_LIGHTNESS
    }
}

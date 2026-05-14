package com.freevibe.service

internal const val VIDEO_PREFS_NAME = "freevibe_prefs"
internal const val VIDEO_STATS_PREFS_NAME = "freevibe_video_stats"
internal const val VIDEO_FPS_LIMIT_PREF = "video_fps_limit"
internal const val VIDEO_PLAYBACK_SPEED_PREF = "video_playback_speed"
internal const val VIDEO_FPS_OVERLAY_PREF = "video_fps_overlay_enabled"
internal const val VIDEO_AUTO_BATTERY_SAVER_PREF = "video_auto_battery_saver"

internal fun sanitizeVideoFpsLimit(fps: Int): Int = when {
    fps <= 15 -> 15
    fps >= 60 -> 60
    else -> 30
}

internal fun shouldUseVideoBatterySaver(
    batteryPercent: Int?,
    isCharging: Boolean,
    autoSaverEnabled: Boolean,
): Boolean =
    autoSaverEnabled && !isCharging && batteryPercent != null && batteryPercent in 0..14

internal fun effectiveVideoFpsLimit(
    requestedFps: Int,
    lowBatterySaverActive: Boolean,
): Int {
    val sanitized = sanitizeVideoFpsLimit(requestedFps)
    return if (lowBatterySaverActive) minOf(sanitized, 15) else sanitized
}

internal fun videoBatteryImpactLabel(
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    lowBatterySaverActive: Boolean,
): String {
    if (lowBatterySaverActive) return "Low battery saver"
    val sanitized = sanitizeVideoFpsLimit(requestedFps)
    return when {
        sanitized <= 15 -> "Light"
        sanitized >= 60 || fpsOverlayEnabled -> "High"
        else -> "Balanced"
    }
}

internal fun videoBatteryImpactSummary(
    requestedFps: Int,
    effectiveFps: Int,
    fpsOverlayEnabled: Boolean,
    lowBatterySaverActive: Boolean,
): String {
    val impact = videoBatteryImpactLabel(requestedFps, fpsOverlayEnabled, lowBatterySaverActive)
    return when {
        lowBatterySaverActive -> "$impact - capped at ${effectiveFps} FPS until battery recovers"
        fpsOverlayEnabled -> "$impact - ${effectiveFps} FPS target with debug overlay enabled"
        else -> "$impact - ${effectiveFps} FPS target"
    }
}

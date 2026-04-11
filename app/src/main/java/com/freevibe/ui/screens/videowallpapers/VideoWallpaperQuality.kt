package com.freevibe.ui.screens.videowallpapers

enum class VideoFocusFilter { BEST, LOOP_SAFE, LOW_BATTERY, PHONE_FIT }

internal fun rankVideoWallpapers(
    items: List<VideoWallpaperItem>,
    filter: VideoFocusFilter,
    orientation: OrientationFilter,
): List<VideoWallpaperItem> {
    val filtered = if (filter == VideoFocusFilter.BEST) {
        items
    } else {
        items.filter { it.matchesFilter(filter, orientation) }
    }
    val rankedBase = filtered.ifEmpty { items }
    val curated = applyVideoQualityFloor(
        rankedBase
            .distinctBy { it.id }
            .map { item -> item to videoQualityScore(item, filter, orientation) }
            .sortedByDescending { it.second }
    ).map { it.first }
    val grouped = curated
        .distinctBy { it.id }
        .groupBy { it.source }
        .mapValues { (_, sourceItems) ->
            sourceItems.sortedByDescending { videoQualityScore(it, filter, orientation) }.toMutableList()
        }
        .toMutableMap()
    val mixed = mutableListOf<VideoWallpaperItem>()
    while (grouped.values.any { it.isNotEmpty() }) {
        grouped.keys.sorted().forEach { key ->
            grouped[key]?.let { sourceItems ->
                if (sourceItems.isNotEmpty()) {
                    mixed += sourceItems.removeAt(0)
                }
            }
        }
    }
    return mixed
}

internal fun VideoWallpaperItem.loopBadge(): String =
    if (isLoopFriendly()) "Loop-safe" else "Dynamic"

internal fun VideoWallpaperItem.batteryBadge(): String = when (batteryTier()) {
    BatteryTier.LOW -> "Low battery"
    BatteryTier.MEDIUM -> "Balanced"
    BatteryTier.HIGH -> "High motion"
}

internal fun VideoWallpaperItem.fitBadge(orientation: OrientationFilter): String = when {
    !hasDimensions -> "Flexible"
    orientation == OrientationFilter.PORTRAIT && isPortrait -> "Phone fit"
    orientation == OrientationFilter.LANDSCAPE && isLandscape -> "Wide fit"
    orientation == OrientationFilter.ALL && isPortrait -> "Phone fit"
    else -> "Needs crop"
}

internal fun VideoWallpaperItem.previewAspectRatio(): Float = when {
    hasDimensions -> (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.56f, 1.8f)
    else -> 9f / 16f
}

private fun videoQualityScore(
    item: VideoWallpaperItem,
    filter: VideoFocusFilter,
    orientation: OrientationFilter,
): Int {
    var score = 35
    score += when (item.source) {
        "Pexels" -> 16
        "Pixabay" -> 15
        "Reddit" -> 14
        "YouTube" -> 12
        else -> 8
    }
    score += when {
        item.duration in 6..18 -> 16
        item.duration in 4..30 -> 10
        item.duration in 31..50 -> 4
        else -> -8
    }
    score += when (batteryTierOf(item)) {
        BatteryTier.LOW -> 12
        BatteryTier.MEDIUM -> 6
        BatteryTier.HIGH -> -6
    }
    if (item.isLoopFriendly()) score += 12
    if (!item.hasDimensions && item.source == "YouTube") score -= 4
    score += when {
        !item.hasDimensions -> 4
        orientation == OrientationFilter.PORTRAIT && item.isPortrait -> 18
        orientation == OrientationFilter.LANDSCAPE && item.isLandscape -> 18
        orientation == OrientationFilter.ALL && item.isPortrait -> 8
        else -> -10
    }
    score += when (filter) {
        VideoFocusFilter.BEST -> 0
        VideoFocusFilter.LOOP_SAFE -> if (item.isLoopFriendly()) 20 else -10
        VideoFocusFilter.LOW_BATTERY -> when (batteryTierOf(item)) {
            BatteryTier.LOW -> 20
            BatteryTier.MEDIUM -> 8
            BatteryTier.HIGH -> -12
        }
        VideoFocusFilter.PHONE_FIT -> when {
            !item.hasDimensions -> 12
            orientation == OrientationFilter.LANDSCAPE && item.isLandscape -> 16
            orientation != OrientationFilter.LANDSCAPE && item.isPortrait -> 16
            else -> -10
        }
    }
    score += minOf(12, (item.popularity / 5_000L).toInt())
    return score
}

private fun applyVideoQualityFloor(
    scored: List<Pair<VideoWallpaperItem, Int>>,
): List<Pair<VideoWallpaperItem, Int>> {
    if (scored.size < 5) return scored
    val topScore = scored.first().second
    val qualityFloor = maxOf(50, topScore - 28)
    val curated = scored.filterIndexed { index, (_, score) ->
        index < 3 || score >= qualityFloor
    }
    return if (curated.size >= minOf(scored.size, 4)) curated else scored
}

private fun VideoWallpaperItem.matchesFilter(filter: VideoFocusFilter, orientation: OrientationFilter): Boolean = when (filter) {
    VideoFocusFilter.BEST -> true
    VideoFocusFilter.LOOP_SAFE -> isLoopFriendly()
    VideoFocusFilter.LOW_BATTERY -> batteryTier() != BatteryTier.HIGH
    VideoFocusFilter.PHONE_FIT -> when {
        !hasDimensions -> true
        orientation == OrientationFilter.LANDSCAPE -> isLandscape
        else -> isPortrait
    }
}

private fun VideoWallpaperItem.isLoopFriendly(): Boolean {
    val title = title.lowercase()
    return LOOP_TERMS.any { it in title } ||
        duration in 4..18 ||
        source == "Pixabay"
}

private fun VideoWallpaperItem.batteryTier(): BatteryTier = batteryTierOf(this)

private fun batteryTierOf(item: VideoWallpaperItem): BatteryTier {
    val pixels = item.videoWidth.toLong() * item.videoHeight.toLong()
    return when {
        pixels >= 4_500_000L || item.duration > 30 -> BatteryTier.HIGH
        pixels >= 2_000_000L || item.duration > 15 -> BatteryTier.MEDIUM
        else -> BatteryTier.LOW
    }
}

private enum class BatteryTier { LOW, MEDIUM, HIGH }

private val LOOP_TERMS = setOf(
    "loop", "cinemagraph", "ambient", "waves", "rain", "particles", "abstract", "clouds", "neon",
)
